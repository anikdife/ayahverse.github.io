import { env, createExecutionContext, waitOnExecutionContext, SELF } from 'cloudflare:test';
import { describe, it, expect } from 'vitest';
import worker from '../src/index';

// For now, you'll need to do something like this to get a correctly-typed
// `Request` to pass to `worker.fetch()`.
const IncomingRequest = Request<unknown, IncomingRequestCfProperties>;

describe('Worker', () => {
	it('responds with Missing parameters at root (unit style)', async () => {
		const request = new IncomingRequest('http://example.com');
		// Create an empty context to pass to `worker.fetch()`.
		const ctx = createExecutionContext();
		const response = await worker.fetch(request, env, ctx);
		// Wait for all `Promise`s passed to `ctx.waitUntil()` to settle before running test assertions
		await waitOnExecutionContext(ctx);
		expect(await response.text()).toMatchInlineSnapshot(`"Missing parameters"`);
	});

	it('uses meaning_lang as the tafseer translation language', async () => {
		const request = new IncomingRequest('http://example.com?surah=1&ayah=1&meaning_lang=ur');
		const ctx = createExecutionContext();
		const response = await worker.fetch(request, env, ctx);
		await waitOnExecutionContext(ctx);
		expect(response.status).toBe(200);
		const json = (await response.json()) as any;
		expect(json.lang).toBe('ur');
		expect(json.meaning_lang).toBe('ur');
		expect(json.tafseers).toEqual([]);
	});

	it('translates via /translate (no-op when source_lang == target_lang)', async () => {
		const request = new IncomingRequest('http://example.com/translate', {
			method: 'POST',
			headers: { 'content-type': 'application/json' },
			body: JSON.stringify({ text: 'Salaam', source_lang: 'en', target_lang: 'en' }),
		});
		const ctx = createExecutionContext();
		const response = await worker.fetch(request, env, ctx);
		await waitOnExecutionContext(ctx);
		expect(response.status).toBe(200);
		const json = (await response.json()) as any;
		expect(json.translated_text).toBe('Salaam');
		expect(json.source).toBe('noop');
	});

	it('responds with Missing parameters at root (integration style)', async () => {
		const response = await SELF.fetch('https://example.com');
		expect(await response.text()).toMatchInlineSnapshot(`"Missing parameters"`);
	});
});
