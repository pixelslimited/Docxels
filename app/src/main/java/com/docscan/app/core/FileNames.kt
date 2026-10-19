package com.docscan.app.core

/** File-name safe version of a title: letters, digits and . _ - space only; never empty. */
fun safeFileName(title: String, fallback: String = "Document"): String =
    title.replace(Regex("[^A-Za-z0-9._ -]"), "_").trim().ifEmpty { fallback }
