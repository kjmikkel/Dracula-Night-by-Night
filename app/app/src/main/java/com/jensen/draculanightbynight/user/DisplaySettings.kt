package com.jensen.draculanightbynight.user

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Size of the story text in the reader; [scale] multiplies the reading style (the system font size still applies). */
@Serializable
enum class TextSize(val scale: Float, val label: String) {
    @SerialName("small") SMALL(0.875f, "Small"),
    @SerialName("normal") NORMAL(1f, "Normal"),
    @SerialName("large") LARGE(1.2f, "Large"),
    @SerialName("xlarge") XLARGE(1.45f, "Extra large"),
}

/** Font of the story text in the reader. */
@Serializable
enum class ReadingFont(val label: String) {
    @SerialName("serif") SERIF("Serif"),
    @SerialName("sans") SANS("Sans-serif"),
    @SerialName("atkinson") ATKINSON("Atkinson Hyperlegible"),
    @SerialName("opendyslexic") OPEN_DYSLEXIC("OpenDyslexic"),
}

@Serializable
enum class ThemeChoice(val label: String) {
    @SerialName("system") SYSTEM("System"),
    @SerialName("light") LIGHT("Light"),
    @SerialName("dark") DARK("Dark"),
}
