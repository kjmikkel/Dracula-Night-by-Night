package com.jensen.draculanightbynight.user

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Order of the bookmarks list. */
@Serializable
enum class BookmarkSort {
    @SerialName("story")
    STORY,

    @SerialName("recent")
    RECENT,
}
