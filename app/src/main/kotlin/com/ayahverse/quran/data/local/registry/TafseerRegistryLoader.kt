package com.ayahverse.quran.data.local.registry

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonParser

interface TafseerRegistryLoader {
	fun loadRegistry(): List<TafseerRegistryEntry>
}

class ClasspathTafseerRegistryLoader(
	private val gson: Gson = Gson(),
	private val resourceName: String = "tafseer_registry.json",
) : TafseerRegistryLoader {
	override fun loadRegistry(): List<TafseerRegistryEntry> {
		val input = javaClass.classLoader?.getResourceAsStream(resourceName)
			?: throw IllegalStateException("Missing $resourceName in resources")
		val json = input.bufferedReader().use { it.readText() }
		val root = JsonParser.parseString(json)
		val array = findArray(root)
		return array.mapNotNull { element ->
			runCatching { gson.fromJson(element, TafseerRegistryEntry::class.java) }.getOrNull()
		}
	}

	private fun findArray(root: JsonElement): List<JsonElement> {
		if (root.isJsonArray) return root.asJsonArray.toList()
		if (root.isJsonObject) {
			val obj = root.asJsonObject
			val candidates = listOf("tafsirs", "tafseers", "tafseer", "registry", "items")
			for (key in candidates) {
				val value = obj.get(key) ?: continue
				if (value.isJsonArray) return value.asJsonArray.toList()
			}
		}
		return emptyList()
	}
}
