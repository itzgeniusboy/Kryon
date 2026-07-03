package com.kryon.filemanager.core

import android.content.Context
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import kotlinx.coroutines.flow.MutableStateFlow

enum class AccessMode {
    LOCAL_SAF,
    ADB,
    ROOT
}

object FileSystemProvider {
    private const val TAG = "FileSystemProvider"
    
    val currentModeFlow = MutableStateFlow(AccessMode.LOCAL_SAF)
    var currentMode: AccessMode
        get() = currentModeFlow.value
        set(value) {
            currentModeFlow.value = value
        }
        
    var adbPort: Int? = null

    init {
        // Auto-detect root on launch
        if (ShellService.isRootAvailable()) {
            currentMode = AccessMode.ROOT
        }
    }

    // Get primary storage root
    fun getPrimaryStoragePath(): String {
        return Environment.getExternalStorageDirectory().absolutePath
    }

    // List files in a path
    suspend fun listFiles(path: String): List<FileItem> = withContext(Dispatchers.IO) {
        try {
            val file = File(path)
            
            // Check if standard File API works first (fastest)
            if (file.exists() && file.canRead()) {
                val list = file.listFiles()
                if (list != null) {
                    return@withContext list.map { f ->
                        FileItem(
                            path = f.absolutePath,
                            name = f.name,
                            size = if (f.isDirectory) 0L else f.length(),
                            isDirectory = f.isDirectory,
                            lastModified = f.lastModified(),
                            permissions = getFilePermissionsString(f),
                            extension = f.extension
                        )
                    }.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                }
            }

            // Fallback to Shell (Root or ADB) if standard fails
            if (currentMode == AccessMode.ROOT || currentMode == AccessMode.ADB) {
                return@withContext listFilesViaShell(path)
            }
            
            emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error listing files in $path", e)
            emptyList()
        }
    }

    private suspend fun listFilesViaShell(path: String): List<FileItem> {
        val cmd = "ls -la \"$path\""
        val result = ShellService.executeCommand(cmd, runAsRoot = (currentMode == AccessMode.ROOT), adbPort = adbPort)
        if (!result.isSuccess) return emptyList()

        val items = mutableListOf<FileItem>()
        val lines = result.stdout.split("\n")
        
        for (line in lines) {
            if (line.trim().isEmpty() || line.startsWith("total")) continue
            try {
                // Parse standard ls -la output: drwxrwxrwx 1 root root 4096 2026-07-03 02:40 name
                val parts = line.split("\\s+".toRegex()).filter { it.isNotEmpty() }
                if (parts.size >= 8) {
                    val permissions = parts[0]
                    val isDir = permissions.startsWith("d") || permissions.startsWith("l")
                    val size = parts[4].toLongOrNull() ?: 0L
                    
                    // Reconstruct name (handles names with spaces)
                    // The date is typically around columns 5-7. In android "ls -la":
                    // permissions, links, owner, group, size, date, time, name
                    // Let's find index where filename starts
                    val nameStartIndex = line.indexOf(parts[7])
                    var name = if (nameStartIndex != -1) line.substring(nameStartIndex).trim() else parts.last()
                    
                    // Handle symbolic links: name -> target
                    if (permissions.startsWith("l") && name.contains(" -> ")) {
                        name = name.split(" -> ")[0].trim()
                    }

                    if (name == "." || name == "..") continue

                    val itemPath = if (path.endsWith("/")) "$path$name" else "$path/$name"
                    val fileExtension = File(itemPath).extension

                    items.add(
                        FileItem(
                            path = itemPath,
                            name = name,
                            size = if (isDir) 0L else size,
                            isDirectory = isDir,
                            lastModified = System.currentTimeMillis(), // Shell ls doesn't always have easy-parse modified times
                            permissions = permissions,
                            extension = fileExtension
                        )
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse ls line: $line", e)
            }
        }
        return items.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
    }

    // Copy file or folder
    suspend fun copy(src: String, dest: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val srcFile = File(src)
            val destFile = File(dest)

            // Standard Copy
            if (srcFile.exists() && srcFile.canRead() && destFile.parentFile?.canWrite() == true) {
                if (srcFile.isDirectory) {
                    return@withContext srcFile.copyRecursively(destFile, overwrite = true)
                } else {
                    srcFile.copyTo(destFile, overwrite = true)
                    return@withContext true
                }
            }

            // Shell Copy (Root / ADB)
            val cmd = if (srcFile.isDirectory) "cp -R \"$src\" \"$dest\"" else "cp \"$src\" \"$dest\""
            val result = ShellService.executeCommand(cmd, runAsRoot = (currentMode == AccessMode.ROOT), adbPort = adbPort)
            result.isSuccess
        } catch (e: Exception) {
            Log.e(TAG, "Error copying $src to $dest", e)
            false
        }
    }

    // Move file or folder
    suspend fun move(src: String, dest: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val srcFile = File(src)
            val destFile = File(dest)

            // Standard Move
            if (srcFile.exists() && srcFile.renameTo(destFile)) {
                return@withContext true
            }

            // Shell Move (Root / ADB)
            val cmd = "mv \"$src\" \"$dest\""
            val result = ShellService.executeCommand(cmd, runAsRoot = (currentMode == AccessMode.ROOT), adbPort = adbPort)
            result.isSuccess
        } catch (e: Exception) {
            Log.e(TAG, "Error moving $src to $dest", e)
            false
        }
    }

    // Delete file or folder
    suspend fun delete(path: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(path)
            if (file.exists() && file.deleteRecursively()) {
                return@withContext true
            }

            // Shell Delete (Root / ADB)
            val cmd = "rm -rf \"$path\""
            val result = ShellService.executeCommand(cmd, runAsRoot = (currentMode == AccessMode.ROOT), adbPort = adbPort)
            result.isSuccess
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting $path", e)
            false
        }
    }

    // Rename file or folder
    suspend fun rename(path: String, newName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(path)
            val parent = file.parent ?: return@withContext false
            val destFile = File(parent, newName)
            
            if (file.exists() && file.renameTo(destFile)) {
                return@withContext true
            }

            // Shell Rename (Root / ADB)
            val cmd = "mv \"$path\" \"${destFile.absolutePath}\""
            val result = ShellService.executeCommand(cmd, runAsRoot = (currentMode == AccessMode.ROOT), adbPort = adbPort)
            result.isSuccess
        } catch (e: Exception) {
            Log.e(TAG, "Error renaming $path to $newName", e)
            false
        }
    }

    // Create a new empty file
    suspend fun createFile(parentPath: String, name: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(parentPath, name)
            if (file.createNewFile()) {
                return@withContext true
            }

            // Shell touch
            val cmd = "touch \"${file.absolutePath}\""
            val result = ShellService.executeCommand(cmd, runAsRoot = (currentMode == AccessMode.ROOT), adbPort = adbPort)
            result.isSuccess
        } catch (e: Exception) {
            Log.e(TAG, "Error creating file $name in $parentPath", e)
            false
        }
    }

    // Create a new directory
    suspend fun createDirectory(parentPath: String, name: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(parentPath, name)
            if (file.mkdirs() || file.exists()) {
                return@withContext true
            }

            // Shell mkdir
            val cmd = "mkdir -p \"${file.absolutePath}\""
            val result = ShellService.executeCommand(cmd, runAsRoot = (currentMode == AccessMode.ROOT), adbPort = adbPort)
            result.isSuccess
        } catch (e: Exception) {
            Log.e(TAG, "Error creating directory $name in $parentPath", e)
            false
        }
    }

    // Read full contents of file as string (max size limit for safety)
    suspend fun readFileText(path: String): String = withContext(Dispatchers.IO) {
        try {
            val file = File(path)
            if (file.exists() && file.canRead()) {
                return@withContext file.readText()
            }

            // Shell cat
            val cmd = "cat \"$path\""
            val result = ShellService.executeCommand(cmd, runAsRoot = (currentMode == AccessMode.ROOT), adbPort = adbPort)
            result.stdout
        } catch (e: Exception) {
            Log.e(TAG, "Error reading file $path", e)
            "Error: ${e.message}"
        }
    }

    // Write full text to file
    suspend fun writeFileText(path: String, content: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(path)
            if (file.parentFile?.exists() == true || file.parentFile?.mkdirs() == true) {
                file.writeText(content)
                return@withContext true
            }

            // Shell echo redirect (use base64 to handle multiline safely)
            val base64Content = android.util.Base64.encodeToString(content.toByteArray(), android.util.Base64.NO_WRAP)
            val cmd = "echo \"$base64Content\" | base64 -d > \"$path\""
            val result = ShellService.executeCommand(cmd, runAsRoot = (currentMode == AccessMode.ROOT), adbPort = adbPort)
            result.isSuccess
        } catch (e: Exception) {
            Log.e(TAG, "Error writing to file $path", e)
            false
        }
    }

    // Read binary file (for Hex Editor)
    suspend fun readFileBytes(path: String, limit: Int = 100 * 1024): ByteArray = withContext(Dispatchers.IO) {
        try {
            val file = File(path)
            if (file.exists() && file.canRead()) {
                val size = file.length().coerceAtMost(limit.toLong()).toInt()
                val bytes = ByteArray(size)
                FileInputStream(file).use { stream ->
                    stream.read(bytes)
                }
                return@withContext bytes
            }

            // Fallback: Use base64 over shell
            val cmd = "base64 \"$path\" | head -c $limit"
            val result = ShellService.executeCommand(cmd, runAsRoot = (currentMode == AccessMode.ROOT), adbPort = adbPort)
            if (result.isSuccess && result.stdout.isNotEmpty()) {
                android.util.Base64.decode(result.stdout, android.util.Base64.DEFAULT)
            } else {
                ByteArray(0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading bytes from $path", e)
            ByteArray(0)
        }
    }

    // Write binary bytes to file
    suspend fun writeFileBytes(path: String, bytes: ByteArray): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(path)
            if (file.parentFile?.exists() == true || file.parentFile?.mkdirs() == true) {
                FileOutputStream(file).use { stream ->
                    stream.write(bytes)
                }
                return@withContext true
            }

            // Fallback: Write base64 string via Shell
            val base64Str = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            val cmd = "echo \"$base64Str\" | base64 -d > \"$path\""
            val result = ShellService.executeCommand(cmd, runAsRoot = (currentMode == AccessMode.ROOT), adbPort = adbPort)
            result.isSuccess
        } catch (e: Exception) {
            Log.e(TAG, "Error writing bytes to $path", e)
            false
        }
    }

    // CHMOD permission update (Chmod for Root/ADB)
    suspend fun chmod(path: String, mode: String): Boolean = withContext(Dispatchers.IO) {
        val cmd = "chmod $mode \"$path\""
        val result = ShellService.executeCommand(cmd, runAsRoot = (currentMode == AccessMode.ROOT), adbPort = adbPort)
        result.isSuccess
    }

    // Get simple permission string
    private fun getFilePermissionsString(file: File): String {
        val r = if (file.canRead()) "r" else "-"
        val w = if (file.canWrite()) "w" else "-"
        val x = if (file.canExecute()) "x" else "-"
        return "$r$w$x"
    }
}
