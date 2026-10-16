package com.jensen.draculanightbynight.about

/** A font bundled with the app and the licence text that travels with it in `assets/licenses/`. */
data class Licence(val name: String, val licence: String, val asset: String)

val Licences = listOf(
    Licence("Atkinson Hyperlegible", "SIL Open Font License 1.1", "AtkinsonHyperlegible-OFL.txt"),
    Licence("OpenDyslexic", "SIL Open Font License 1.1", "OpenDyslexic-OFL.txt"),
    Licence("IM Fell English", "SIL Open Font License 1.1", "IMFellEnglish-OFL.txt"),
    Licence("Special Elite", "Apache License 2.0", "SpecialElite-LICENSE.txt"),
)

const val SOURCE_URL = "https://github.com/kjmikkel/Dracula-Night-by-Night"
const val PRIVACY_URL = "https://github.com/kjmikkel/Dracula-Night-by-Night/blob/main/docs/privacy.md"
