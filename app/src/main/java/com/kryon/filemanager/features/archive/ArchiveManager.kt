package com.kryon.filemanager.features.archive

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object ArchiveManager {
    
    // Extract a ZIP archive to a destination folder
    suspend fun extractZip(zipFilePath: String, destDirPath: String, onProgress: (String) -> Unit): Boolean = withContext(Dispatchers.IO) {
        try {
            val destDir = File(destDirPath)
            if (!destDir.exists()) {
                destDir.mkdirs()
            }
            
            ZipInputStream(BufferedInputStream(FileInputStream(zipFilePath))).use { zipIn ->
                var entry: ZipEntry? = zipIn.nextEntry
                while (entry != null) {
                    val filePath = destDirPath + File.separator + entry.name
                    if (!entry.isDirectory) {
                        // Create parent directories if they don't exist
                        val file = File(filePath)
                        file.parentFile?.mkdirs()
                        
                        // Extract file
                        onProgress("Extracting: ${entry.name}")
                        BufferedOutputStream(FileOutputStream(file)).use { bos ->
                            val buffer = ByteArray(4096)
                            var read: Int
                            while (zipIn.read(buffer).also { read = it } != -1) {
                                bos.write(buffer, 0, read)
                            }
                        }
                    } else {
                        // Direct directory entry
                        val dir = File(filePath)
                        dir.mkdirs()
                    }
                    zipIn.closeEntry()
                    entry = zipIn.nextEntry
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // Compress a file or folder into a ZIP archive
    suspend fun compressToZip(srcPath: String, destZipFilePath: String, onProgress: (String) -> Unit): Boolean = withContext(Dispatchers.IO) {
        try {
            val srcFile = File(srcPath)
            ZipOutputStream(BufferedOutputStream(FileOutputStream(destZipFilePath))).use { zos ->
                if (srcFile.isDirectory) {
                    compressDirectoryToZip(srcFile, srcFile.name, zos, onProgress)
                } else {
                    onProgress("Compressing: ${srcFile.name}")
                    compressFileToZip(srcFile, "", zos)
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun compressDirectoryToZip(dir: File, baseName: String, zos: ZipOutputStream, onProgress: (String) -> Unit) {
        val files = dir.listFiles() ?: return
        for (file in files) {
            val entryPath = "$baseName/${file.name}"
            if (file.isDirectory) {
                compressDirectoryToZip(file, entryPath, zos, onProgress)
            } else {
                onProgress("Compressing: ${file.name}")
                compressFileToZip(file, entryPath, zos)
            }
        }
    }

    private fun compressFileToZip(file: File, entryPath: String, zos: ZipOutputStream) {
        val name = entryPath.ifEmpty { file.name }
        zos.putNextEntry(ZipEntry(name))
        BufferedInputStream(FileInputStream(file)).use { bis ->
            val buffer = ByteArray(4096)
            var read: Int
            while (bis.read(buffer).also { read = it } != -1) {
                zos.write(buffer, 0, read)
            }
        }
        zos.closeEntry()
    }
}
