package net.morsecode.shared.media

/**
 * Extension-based categorization (Section E.3). Categories are non-exclusive:
 * a large .docx appears in both "Documents" and "Big Files".
 */
object FileCategorizer {
    val DOCUMENTS = setOf("doc", "docx", "ppt", "pptx", "xls", "xlsx", "wps", "pdf", "odt")
    val EBOOKS = setOf("umd", "ebk", "txt", "chm", "epub", "mobi")
    val APKS = setOf("apk")
    val ARCHIVES = setOf("7z", "rar", "zip", "iso", "tar", "gz")
    const val BIG_FILE_THRESHOLD = 50L * 1024 * 1024

    val CATEGORIES = listOf(
        FileCategory("documents", "Documents"),
        FileCategory("ebooks", "Ebooks"),
        FileCategory("apks", "APKs"),
        FileCategory("archives", "Archives"),
        FileCategory("big", "Big Files"),
    )

    fun extensionOf(file: GenericFile): String = file.extension.lowercase().trimStart('.')

    fun inCategory(file: GenericFile, categoryId: String): Boolean {
        val ext = extensionOf(file)
        return when (categoryId) {
            "documents" -> ext in DOCUMENTS
            "ebooks" -> ext in EBOOKS
            "apks" -> ext in APKS
            "archives" -> ext in ARCHIVES
            "big" -> file.sizeBytes > BIG_FILE_THRESHOLD
            else -> false
        }
    }

    fun categorize(file: GenericFile): List<String> =
        CATEGORIES.map { it.id }.filter { inCategory(file, it) }

    fun counts(files: List<GenericFile>): Map<String, Int> {
        val out = HashMap<String, Int>()
        for (c in CATEGORIES) out[c.id] = files.count { inCategory(it, c.id) }
        return out
    }

    fun filter(files: List<GenericFile>, categoryId: String): List<GenericFile> =
        files.filter { inCategory(it, categoryId) }
}
