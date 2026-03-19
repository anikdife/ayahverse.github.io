package com.ayahverse.quran.data.local.registry

import com.google.gson.annotations.SerializedName

/**
 * Represents one entry in tafseer_registry.json.
 *
 * This model is intentionally tolerant: unknown/extra fields in JSON are ignored by Gson.
 */

data class TafseerRegistryEntry(
	val id: String,
	val name: String,
	val author: String? = null,
	val source: String,
	val lang: String? = null,
	@SerializedName(value = "baseUrl", alternate = ["base_url"]) val baseUrl: String? = null,
	@SerializedName(value = "endpointPattern", alternate = ["endpoint_pattern"]) val endpointPattern: String? = null,
	val note: String? = null,

	// Back-compat / optional fields for other registry formats
	@SerializedName(value = "resource_id", alternate = ["resourceId"]) val resourceId: Int? = null,
	@SerializedName(value = "translation_id", alternate = ["translationId"]) val translationId: Int? = null,
	@SerializedName(value = "edition", alternate = ["edition_id", "editionId", "identifier"]) val edition: String? = null,
)
