package com.jensen.draculanightbynight.reader

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A request to open one entry in the reader, handed between the split view and the reader route.
 * [seq] tells two taps on the same entry apart, so the second still counts as a new request.
 */
@Serializable
data class OpenEntry(
    val id: String,
    val filtered: Boolean,
    val followingAll: Boolean = false,
    val seq: Long = 0,
)

/** The reader route for this request. */
fun OpenEntry.route(): String = "reader/$id?filtered=$filtered&all=$followingAll"

/** Text form of the request, so it fits in a `SavedStateHandle`. */
fun OpenEntry.encode(): String = Json.encodeToString(OpenEntry.serializer(), this)

fun decodeOpenEntry(text: String): OpenEntry = Json.decodeFromString(OpenEntry.serializer(), text)
