package com.livora.corbett.data.api

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.serializer

/** Single Json configuration used everywhere (network, storage). */
val AppJson: Json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    isLenient = true
    explicitNulls = false
    encodeDefaults = true
}

/** Accepts a string, number or bool where a String is expected (e.g. roomNumber 101 vs "101"). */
object FlexStringSerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("FlexString", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): String {
        val jd = decoder as? JsonDecoder ?: return decoder.decodeString()
        return when (val el = jd.decodeJsonElement()) {
            is JsonNull -> ""
            is JsonPrimitive -> el.content
            else -> ""
        }
    }
    override fun serialize(encoder: Encoder, value: String) = encoder.encodeString(value)
}

/** Accepts bool / number / "true" where a Boolean is expected. */
object FlexBoolSerializer : KSerializer<Boolean> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("FlexBool", PrimitiveKind.BOOLEAN)
    override fun deserialize(decoder: Decoder): Boolean {
        val jd = decoder as? JsonDecoder ?: return decoder.decodeBoolean()
        return when (val el = jd.decodeJsonElement()) {
            is JsonNull -> false
            is JsonPrimitive -> el.booleanOrNull ?: ((el.doubleOrNull ?: 0.0) > 0.0)
            else -> false
        }
    }
    override fun serialize(encoder: Encoder, value: Boolean) = encoder.encodeBoolean(value)
}

/**
 * Populated-or-not reference: the API returns either "id-string" or a populated object
 * ({_id, name, slug, imageUrls, firstName, ...}). Decoding never fails.
 */
@kotlinx.serialization.Serializable(with = RefSerializer::class)
data class Ref(
    val id: String = "",
    val name: String = "",
    val slug: String = "",
    val imageUrls: List<String> = emptyList(),
    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val phone: String = "",
    val roomNumber: String = "",
) {
    val displayName: String
        get() = name.ifBlank { "$firstName $lastName".trim() }
}

object RefSerializer : KSerializer<Ref> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Ref")

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull

    override fun deserialize(decoder: Decoder): Ref {
        val jd = decoder as? JsonDecoder ?: return Ref(id = decoder.decodeString())
        return when (val el = jd.decodeJsonElement()) {
            is JsonObject -> Ref(
                id = el.str("_id") ?: el.str("id") ?: "",
                name = el.str("name") ?: "",
                slug = el.str("slug") ?: "",
                imageUrls = (el["imageUrls"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } ?: emptyList(),
                firstName = el.str("firstName") ?: "",
                lastName = el.str("lastName") ?: "",
                email = el.str("email") ?: "",
                phone = el.str("phone") ?: "",
                roomNumber = el.str("roomNumber") ?: "",
            )
            is JsonNull -> Ref()
            is JsonPrimitive -> Ref(id = el.content)
            else -> Ref()
        }
    }

    override fun serialize(encoder: Encoder, value: Ref) = encoder.encodeString(value.id)
}

/** Decode a list from a JSON array, or from the first array value inside a wrapper object. Bad items are skipped. */
fun <T> Json.decodeListTolerant(el: JsonElement, ser: KSerializer<T>): List<T> {
    val arr: JsonArray = when (el) {
        is JsonArray -> el
        is JsonObject -> el.values.filterIsInstance<JsonArray>().firstOrNull() ?: return emptyList()
        else -> return emptyList()
    }
    return arr.mapNotNull { item ->
        try {
            decodeFromJsonElement(ser, item)
        } catch (e: Exception) {
            null
        }
    }
}

inline fun <reified T> Json.decodeList(el: JsonElement): List<T> = decodeListTolerant(el, serializer<T>())
