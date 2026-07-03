package com.kryon.filemanager.core

import java.io.Serializable

data class FileItem(
    val path: String,
    val name: String,
    val size: Long,
    val isDirectory: Boolean,
    val lastModified: Long,
    val permissions: String = "rw-rw-r--",
    val extension: String = "",
    val isSelected: Boolean = false
) : Serializable {
    val isArchive: Boolean
        get() = extension.lowercase() in listOf("zip", "rar", "7z", "tar", "gz")
    
    val isImage: Boolean
        get() = extension.lowercase() in listOf("png", "jpg", "jpeg", "webp", "gif", "bmp")

    val isVideo: Boolean
        get() = extension.lowercase() in listOf("mp4", "mkv", "avi", "mov", "webm", "3gp")

    val isText: Boolean
        get() = extension.lowercase() in listOf("txt", "json", "xml", "html", "css", "js", "ts", "kt", "java", "properties", "sh", "py", "cpp", "h", "c", "md", "yaml", "yml", "gradle", "kts", "prop", "conf")

    val isApk: Boolean
        get() = extension.lowercase() == "apk"

    val isDb: Boolean
        get() = extension.lowercase() in listOf("db", "sqlite", "sqlite3")
}
