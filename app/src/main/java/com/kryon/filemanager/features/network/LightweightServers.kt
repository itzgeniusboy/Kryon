package com.kryon.filemanager.features.network

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import com.kryon.filemanager.core.FileSystemProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.net.ServerSocket
import java.net.Socket
import java.util.Locale

object LightweightServers {
    private const val TAG = "LightweightServers"
    private var serverJob: Job? = null
    private var serverSocket: ServerSocket? = null
    
    var isServerRunning = false
        private set
        
    var serverPort = 8080
        private set

    fun getLocalIpAddress(context: Context): String {
        return try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val ipAddress = wifiManager.connectionInfo.ipAddress
            val ipString = String.format(
                Locale.US,
                "%d.%d.%d.%d",
                ipAddress and 0xff,
                ipAddress shr 8 and 0xff,
                ipAddress shr 16 and 0xff,
                ipAddress shr 24 and 0xff
            )
            if (ipString == "0.0.0.0") "127.0.0.1" else ipString
        } catch (e: Exception) {
            "127.0.0.1"
        }
    }

    fun startServer(context: Context, port: Int = 8080, onStatusChanged: (Boolean) -> Unit) {
        if (isServerRunning) return
        serverPort = port
        
        serverJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                serverSocket = ServerSocket(port)
                isServerRunning = true
                launch(Dispatchers.Main) { onStatusChanged(true) }
                Log.d(TAG, "Kryon Lightweight WebDAV-HTTP Server started on port $port")

                while (isServerRunning) {
                    val socket = serverSocket?.accept() ?: break
                    launch(Dispatchers.IO) {
                        handleClient(socket)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in web server main loop", e)
                stopServer(onStatusChanged)
            }
        }
    }

    fun stopServer(onStatusChanged: (Boolean) -> Unit) {
        isServerRunning = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        serverSocket = null
        serverJob?.cancel()
        serverJob = null
        CoroutineScope(Dispatchers.Main).launch { onStatusChanged(false) }
        Log.d(TAG, "Kryon Lightweight WebDAV-HTTP Server stopped")
    }

    private fun handleClient(socket: Socket) {
        try {
            val reader = socket.getInputStream().bufferedReader()
            val writer = socket.getOutputStream().bufferedWriter()

            val requestLine = reader.readLine() ?: return
            Log.d(TAG, "Request: $requestLine")

            val parts = requestLine.split(" ")
            if (parts.size < 2) return
            val method = parts[0]
            val uri = java.net.URLDecoder.decode(parts[1], "UTF-8")

            if (method == "GET") {
                serveFileOrDirectory(uri, writer, socket)
            } else {
                sendResponse(writer, "405 Method Not Allowed", "text/plain", "Method not supported.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling socket client", e)
        } finally {
            try {
                socket.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun serveFileOrDirectory(uri: String, writer: java.io.BufferedWriter, socket: Socket) {
        val rootPath = FileSystemProvider.getPrimaryStoragePath()
        // Map URI to local file
        val cleanUri = if (uri.startsWith("/")) uri.substring(1) else uri
        val targetFile = File(rootPath, cleanUri)

        if (!targetFile.exists()) {
            sendResponse(writer, "404 Not Found", "text/plain", "File or folder not found.")
            return
        }

        if (targetFile.isDirectory) {
            // Render beautiful WebDAV style files list in HTML
            val files = targetFile.listFiles() ?: emptyArray()
            val html = buildString {
                append("<html><head><title>Kryon WebDAV Command Center</title>")
                append("<style>")
                append("body { font-family: -apple-system, sans-serif; background: #0c0c0e; color: #e1e1e6; padding: 24px; }")
                append("h1 { color: #8a2be2; border-bottom: 1px solid #23232a; padding-bottom: 12px; }")
                append("a { color: #a970ff; text-decoration: none; font-size: 16px; }")
                append("a:hover { text-decoration: underline; }")
                append("table { width: 100%; border-collapse: collapse; margin-top: 16px; }")
                append("th, td { text-align: left; padding: 12px; border-bottom: 1px solid #1c1c24; }")
                append("th { background: #16161c; color: #8b8b93; }")
                append("tr:hover { background: #121217; }")
                append("</style></head><body>")
                append("<h1>📁 Kryon File Directory: ${targetFile.name.ifEmpty { "Primary Storage" }}</h1>")
                append("<p>Local Path: <code>${targetFile.absolutePath}</code></p>")
                append("<a href='../'>⬅️ Parent Directory</a><br><br>")
                append("<table><thead><tr><th>Name</th><th>Size</th><th>Type</th></tr></thead><tbody>")
                
                files.forEach { file ->
                    val relativePath = file.absolutePath.substringAfter(rootPath).replace("\\", "/")
                    val displayName = if (file.isDirectory) "${file.name}/" else file.name
                    val sizeStr = if (file.isDirectory) "-" else "${file.length() / 1024} KB"
                    val typeStr = if (file.isDirectory) "Folder" else "File"
                    
                    append("<tr>")
                    append("<td><a href='/$relativePath'>$displayName</a></td>")
                    append("<td>$sizeStr</td>")
                    append("<td>$typeStr</td>")
                    append("</tr>")
                }
                
                append("</tbody></table></body></html>")
            }
            sendResponse(writer, "200 OK", "text/html", html)
        } else {
            // Serve file contents directly
            try {
                val mimeType = getMimeType(targetFile)
                val outStream = socket.getOutputStream()
                val fileInputStream = java.io.FileInputStream(targetFile)

                // Write HTTP header
                val header = buildString {
                    append("HTTP/1.1 200 OK\r\n")
                    append("Content-Type: $mimeType\r\n")
                    append("Content-Length: ${targetFile.length()}\r\n")
                    append("Connection: close\r\n")
                    append("\r\n")
                }
                outStream.write(header.toByteArray())

                // Stream content
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (fileInputStream.read(buffer).also { bytesRead = it } != -1) {
                    outStream.write(buffer, 0, bytesRead)
                }
                outStream.flush()
                fileInputStream.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error transferring file binary data", e)
            }
        }
    }

    private fun sendResponse(writer: java.io.BufferedWriter, status: String, contentType: String, content: String) {
        try {
            writer.write("HTTP/1.1 $status\r\n")
            writer.write("Content-Type: $contentType\r\n")
            writer.write("Content-Length: ${content.toByteArray().size}\r\n")
            writer.write("Connection: close\r\n")
            writer.write("\r\n")
            writer.write(content)
            writer.flush()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getMimeType(file: File): String {
        return when (file.extension.lowercase()) {
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "html" -> "text/html"
            "txt", "log", "cfg", "env" -> "text/plain"
            "pdf" -> "application/pdf"
            "zip", "rar" -> "application/zip"
            "apk" -> "application/vnd.android.package-archive"
            else -> "application/octet-stream"
        }
    }
}
