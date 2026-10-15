package com.jensen.draculanightbynight.content

/** The reader-facing name of a kind of document, as in "Mina Harker · Letter". */
fun typeLabel(type: DocumentType): String = when (type) {
    DocumentType.DIARY -> "Journal"
    DocumentType.PHONOGRAPH -> "Phonograph diary"
    DocumentType.LETTER -> "Letter"
    DocumentType.TELEGRAM -> "Telegram"
    DocumentType.NEWSPAPER -> "Newspaper"
    DocumentType.MEMO -> "Memorandum"
    DocumentType.LOG -> "Ship's log"
}
