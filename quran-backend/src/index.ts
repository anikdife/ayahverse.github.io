export interface Env {
  AI: any;
  TAFSEER_CACHE: KVNamespace;
}

type TranslateRequest = {
  text: string;
  target_lang: string;
  source_lang?: string;
};

type TranslateResponse = {
  text: string;
  translated_text: string;
  source_lang?: string;
  target_lang: string;
  source: string;
};

function splitTextForTranslation(text: string, maxChunkChars: number): string[] {
  const cleaned = text.replace(/\r\n/g, "\n").trim();
  if (!cleaned) return [""];
  if (cleaned.length <= maxChunkChars) return [cleaned];

  // First try splitting by paragraphs.
  const paragraphs = cleaned.split(/\n\n+/g).map((p) => p.trim()).filter(Boolean);
  const chunks: string[] = [];
  let current = "";

  const pushCurrent = () => {
    const c = current.trim();
    if (c) chunks.push(c);
    current = "";
  };

  const append = (piece: string, separator: string) => {
    if (!current) {
      current = piece;
      return;
    }
    if ((current + separator + piece).length <= maxChunkChars) {
      current += separator + piece;
    } else {
      pushCurrent();
      current = piece;
    }
  };

  const splitLargeParagraph = (p: string) => {
    // Split by sentence-ish boundaries.
    const sentences = p
      .split(/(?<=[.!?])\s+/g)
      .map((s) => s.trim())
      .filter(Boolean);

    if (sentences.length <= 1) {
      // Fallback: hard split by words.
      const words = p.split(/\s+/g).filter(Boolean);
      let buf = "";
      for (const w of words) {
        if (!buf) {
          buf = w;
        } else if ((buf + " " + w).length <= maxChunkChars) {
          buf += " " + w;
        } else {
          chunks.push(buf);
          buf = w;
        }
      }
      if (buf) chunks.push(buf);
      return;
    }

    for (const s of sentences) {
      if (s.length > maxChunkChars) {
        // Sentence still too large: hard split.
        for (let i = 0; i < s.length; i += maxChunkChars) {
          chunks.push(s.slice(i, i + maxChunkChars));
        }
        continue;
      }
      append(s, " ");
    }
    pushCurrent();
  };

  for (const p of paragraphs) {
    if (p.length > maxChunkChars) {
      pushCurrent();
      splitLargeParagraph(p);
      continue;
    }
    append(p, "\n\n");
  }

  pushCurrent();
  return chunks.length ? chunks : [cleaned];
}

async function sha256Hex(input: string): Promise<string> {
  const data = new TextEncoder().encode(input);
  const digest = await crypto.subtle.digest("SHA-256", data);
  const bytes = new Uint8Array(digest);
  let hex = "";
  for (const b of bytes) hex += b.toString(16).padStart(2, "0");
  return hex;
}

type QuranComVerseByKeyWord = {
  position?: number;
  text?: string;
  char_type_name?: string;
  transliteration?: { text?: string } | null;
  translation?: { text?: string } | null;
};

type QuranComVerseByKeyResponse = {
  verse?: {
    verse_key?: string;
    words?: QuranComVerseByKeyWord[];
  };
};

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);
    const { searchParams } = url;

    if (url.pathname === "/translate") {
      if (request.method !== "POST") {
        return new Response("Method not allowed", { status: 405 });
      }

      let body: TranslateRequest | null = null;
      try {
        body = (await request.json()) as TranslateRequest;
      } catch (_: unknown) {
        body = null;
      }

      const text = body?.text ?? "";
      const targetLang = body?.target_lang ?? "";
      const sourceLang = body?.source_lang;

      if (!text || !targetLang) {
        return new Response("Missing parameters", { status: 400 });
      }

      if (sourceLang && sourceLang === targetLang) {
        const response: TranslateResponse = {
          text,
          translated_text: text,
          source_lang: sourceLang,
          target_lang: targetLang,
          source: "noop",
        };
        return Response.json(response);
      }

      // Cache full translations (especially important for long tafseer).
      const cacheKey = `translate:${sourceLang ?? "auto"}:${targetLang}:${await sha256Hex(text)}`;
      const cached = await env.TAFSEER_CACHE.get(cacheKey);
      if (cached) {
        const response: TranslateResponse = {
          text,
          translated_text: cached,
          source_lang: sourceLang,
          target_lang: targetLang,
          source: "cache",
        };
        return Response.json(response);
      }

      try {
        // Long inputs frequently come back truncated; translate in chunks.
        const chunks = splitTextForTranslation(text, 1200);
        const translatedChunks: string[] = [];
        for (const chunk of chunks) {
          if (!chunk.trim()) {
            translatedChunks.push("");
            continue;
          }
          const aiResponse = await env.AI.run("@cf/meta/m2m100-1.2b", {
            text: chunk,
            // Workers AI expects language codes like 'en', 'ar', 'ur'...
            ...(sourceLang ? { source_lang: sourceLang } : {}),
            target_lang: targetLang,
          });
          translatedChunks.push((aiResponse?.translated_text ?? "").toString());
        }

        const translatedText = translatedChunks.join("\n\n").trim();
        if (translatedText) {
          await env.TAFSEER_CACHE.put(cacheKey, translatedText);
        }
        const response: TranslateResponse = {
          text,
          translated_text: translatedText,
          source_lang: sourceLang,
          target_lang: targetLang,
          source: "workers-ai",
        };
        return Response.json(response);
      } catch (_: unknown) {
        return new Response("Translation failed", { status: 502 });
      }
    }

    if (url.pathname === "/ayah-words") {
      const verseKey = searchParams.get("verse_key") || searchParams.get("verseKey");
      if (!verseKey) {
        return new Response("Missing parameters", { status: 400 });
      }

      const upstreamUrl = new URL(`https://api.quran.com/api/v4/verses/by_key/${encodeURIComponent(verseKey)}`);
      upstreamUrl.searchParams.set("words", "true");

      const upstreamResp = await fetch(upstreamUrl.toString(), {
        headers: {
          Accept: "application/json",
          // Quran.com sometimes blocks requests without a UA.
          "User-Agent": "quran-backend/1.0",
        },
      });

      if (!upstreamResp.ok) {
        return new Response("Upstream error", { status: 502 });
      }

      const upstreamJson = (await upstreamResp.json()) as QuranComVerseByKeyResponse;
      const words = (upstreamJson?.verse?.words ?? []).map((w) => ({
        position: w.position ?? null,
        text: w.text ?? "",
        char_type_name: w.char_type_name ?? null,
        transliteration: w.transliteration?.text ?? null,
        translation: w.translation?.text ?? null,
      }));

      return Response.json({
        verse_key: upstreamJson?.verse?.verse_key ?? verseKey,
        words,
        source: "quran.com",
      });
    }

    if (url.pathname === "/verse-translation") {
      const verseKey = searchParams.get("verse_key") || searchParams.get("verseKey");
      const translationIdRaw = searchParams.get("translation_id") || searchParams.get("translationId");

      if (!verseKey || !translationIdRaw) {
        return new Response("Missing parameters", { status: 400 });
      }

      const translationId = Number(translationIdRaw);
      const cacheKey = `verseTranslation:${translationIdRaw}:${verseKey}`;

      const cached = await env.TAFSEER_CACHE.get(cacheKey);
      if (cached) {
        return Response.json({ verse_key: verseKey, translation_id: translationId, text: cached, source: "cache" });
      }

      const upstreamUrl = new URL(
        `https://api.quran.com/api/v4/quran/translations/${encodeURIComponent(translationIdRaw)}`
      );
      upstreamUrl.searchParams.set("verse_key", verseKey);

      const upstreamResp = await fetch(upstreamUrl.toString(), {
        headers: { Accept: "application/json" },
      });

      if (!upstreamResp.ok) {
        return new Response("Upstream error", { status: 502 });
      }

      const upstreamJson: any = await upstreamResp.json();
      const text: string = upstreamJson?.translations?.[0]?.text || "";
      if (text) {
        await env.TAFSEER_CACHE.put(cacheKey, text);
      }

      return Response.json({ verse_key: verseKey, translation_id: translationId, text, source: "quran.com" });
    }

    const surah = searchParams.get("surah");
    const ayah = searchParams.get("ayah");

    // Correction: Tafseer translation language must match the "meaning" language.
    // We accept a few common query param aliases for the meaning language.
    const meaningLangParam =
      searchParams.get("meaning_lang") ||
      searchParams.get("meaning_language") ||
      searchParams.get("meaningLang") ||
      searchParams.get("meaningLanguage");

    const lang = meaningLangParam || searchParams.get("lang") || "en";
    const tafseerIds = searchParams.get("ids")?.split(",") || [];

    if (!surah || !ayah) return new Response("Missing parameters", { status: 400 });

    // This runs all Tafseer fetches in parallel for maximum speed
    const results = await Promise.all(
      tafseerIds.map(async (id) => {
        const cacheKey = `tafseer:${id}:${surah}:${ayah}:${lang}`;

        // 1. Try Cache
        const cached = await env.TAFSEER_CACHE.get(cacheKey);
        if (cached) return { id, text: cached, source: "cache" };

        // 2. Fetch from API (Placeholder: Logic to call Quran.com/AlQuranCloud)
        // For now, let's pretend we got the Arabic text
        const arabicText = `Original Arabic Tafseer for ${id}...`;

        // 3. Translate if not already in target lang
        const aiResponse = await env.AI.run("@cf/meta/m2m100-1.2b", {
          text: arabicText,
          source_lang: "ar",
          target_lang: lang,
        });

        const translatedText = aiResponse.translated_text;

        // 4. Save to Cache
        await env.TAFSEER_CACHE.put(cacheKey, translatedText);
        return { id, text: translatedText, source: "ai_translated" };
      }),
    );

    return Response.json({ surah, ayah, lang, meaning_lang: lang, tafseers: results });
  }
};