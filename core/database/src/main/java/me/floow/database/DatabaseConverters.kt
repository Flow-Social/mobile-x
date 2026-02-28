package me.floow.database

import androidx.room.TypeConverter
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import me.floow.domain.models.PostImageVariant

class DatabaseConverters {
    private val json = Json { encodeDefaults = true }

    @TypeConverter
    fun imageUrlsToJson(value: List<String>): String {
        return json.encodeToString(ListSerializer(String.serializer()), value)
    }

    @TypeConverter
    fun jsonToImageUrls(value: String): List<String> {
        return runCatching {
            json.decodeFromString(ListSerializer(String.serializer()), value)
        }.getOrElse { emptyList() }
    }

    @TypeConverter
    fun imageVariantsToJson(value: List<PostImageVariant>): String {
        return json.encodeToString(ListSerializer(PostImageVariant.serializer()), value)
    }

    @TypeConverter
    fun jsonToImageVariants(value: String): List<PostImageVariant> {
        return runCatching {
            json.decodeFromString(ListSerializer(PostImageVariant.serializer()), value)
        }.getOrElse { emptyList() }
    }
}
