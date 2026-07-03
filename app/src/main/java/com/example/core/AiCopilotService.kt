package com.example.core

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

data class CopilotAction(
    val type: String, // "DELETE", "COPY", "MOVE", "ZIP_COMPRESS", "RENAME"
    val sourcePath: String,
    val destPath: String = "",
    val details: String = ""
)

data class CopilotResponse(
    val explanation: String,
    val actions: List<CopilotAction>,
    val error: String? = null
)

object AiCopilotService {
    private const val TAG = "AiCopilotService"
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    // Main API call to Claude
    suspend fun getClaudeCommandResponse(
        context: Context,
        userPrompt: String,
        currentPath: String,
        fileMetadata: String
    ): CopilotResponse = withContext(Dispatchers.IO) {
        val apiKey = SecurePreferences.getClaudeApiKey(context)
        if (apiKey.trim().isEmpty()) {
            // Local fallback rule parser if key is absent
            return@withContext parseHeuristically(userPrompt, currentPath)
        }

        val systemPrompt = """
            You are Kryon AI Copilot, a high-privileged system developer assistant.
            The user wants to perform file operations. 
            You must analyze their prompt and map it to a structured JSON object containing:
            1) "explanation": String explanation of what you will do.
            2) "actions": Array of action objects, each containing:
               - "type": "DELETE", "COPY", "MOVE", "ZIP_COMPRESS", "RENAME"
               - "sourcePath": Full absolute file path
               - "destPath": Destination path (for COPY/MOVE/ZIP_COMPRESS) or new name (for RENAME)
               - "details": Brief human readable details of the file.

            Available Files in the current directory ($currentPath):
            $fileMetadata

            Constraints:
            - Only return valid JSON matching this format:
              {
                "explanation": "Explanation of actions...",
                "actions": [
                  { "type": "DELETE", "sourcePath": "/absolute/path/file.txt", "destPath": "", "details": "Matches older than 30 days criteria" }
                ]
              }
            - If no actions can be parsed or match, return an empty actions array.
            - Ensure all paths are absolute and strictly based on the current directory or requested files.
            - NEVER include markdown enclosing blocks (like ```json ... ```) in the raw response text, just return the direct JSON.
        """.trimIndent()

        val jsonBody = JSONObject().apply {
            put("model", "claude-3-5-sonnet-20241022")
            put("max_tokens", 1500)
            put("system", systemPrompt)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", userPrompt)
                })
            })
        }

        try {
            val request = Request.Builder()
                .url("https://api.anthropic.com/v1/messages")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .header("content-type", "application/json")
                .post(jsonBody.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    Log.e(TAG, "Claude request failed with code ${response.code}: $bodyStr")
                    return@withContext parseHeuristically(userPrompt, currentPath)
                }

                val responseJson = JSONObject(bodyStr)
                val contentArray = responseJson.getJSONArray("content")
                val textResponse = contentArray.getJSONObject(0).getString("text")

                parseJsonResponse(textResponse)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing Claude request, failing back to heuristic parser", e)
            parseHeuristically(userPrompt, currentPath)
        }
    }

    // Generic summarization/text API helper
    suspend fun queryClaudeText(
        context: Context,
        systemInstruction: String,
        userContent: String
    ): String = withContext(Dispatchers.IO) {
        val apiKey = SecurePreferences.getClaudeApiKey(context)
        if (apiKey.trim().isEmpty()) {
            return@withContext "Kryon AI offline. Please configure your Claude API key in Settings."
        }

        val jsonBody = JSONObject().apply {
            put("model", "claude-3-5-sonnet-20241022")
            put("max_tokens", 1024)
            put("system", systemInstruction)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", userContent)
                })
            })
        }

        try {
            val request = Request.Builder()
                .url("https://api.anthropic.com/v1/messages")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .header("content-type", "application/json")
                .post(jsonBody.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    return@withContext "Claude Error: ${response.code}\n$bodyStr"
                }

                val responseJson = JSONObject(bodyStr)
                val contentArray = responseJson.getJSONArray("content")
                contentArray.getJSONObject(0).getString("text")
            }
        } catch (e: Exception) {
            "Network Error: ${e.localizedMessage}"
        }
    }

    private fun parseJsonResponse(rawText: String): CopilotResponse {
        return try {
            // Clean up any potential markdown formatting from AI response
            var jsonStr = rawText.trim()
            if (jsonStr.startsWith("```json")) {
                jsonStr = jsonStr.substringAfter("```json").substringBeforeLast("```").trim()
            } else if (jsonStr.startsWith("```")) {
                jsonStr = jsonStr.substringAfter("```").substringBeforeLast("```").trim()
            }

            val obj = JSONObject(jsonStr)
            val explanation = obj.optString("explanation", "Parsed commands:")
            val actionsArray = obj.optJSONArray("actions")
            val actionsList = mutableListOf<CopilotAction>()

            if (actionsArray != null) {
                for (i in 0 until actionsArray.length()) {
                    val actObj = actionsArray.getJSONObject(i)
                    actionsList.add(
                        CopilotAction(
                            type = actObj.optString("type", "DELETE"),
                            sourcePath = actObj.optString("sourcePath", ""),
                            destPath = actObj.optString("destPath", ""),
                            details = actObj.optString("details", "")
                        )
                    )
                }
            }

            CopilotResponse(explanation, actionsList)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse JSON response: $rawText", e)
            CopilotResponse("Failed to parse AI output, but parsed heuristically.", emptyList(), error = e.message)
        }
    }

    // Heuristic rule parser (works offline or without key!)
    private fun parseHeuristically(prompt: String, currentPath: String): CopilotResponse {
        val normalized = prompt.lowercase()
        val actions = mutableListOf<CopilotAction>()
        val files = File(currentPath).listFiles() ?: emptyArray()

        var explanation = "Offline Parser: "
        if (normalized.contains("delete") || normalized.contains("remove") || normalized.contains("clean")) {
            val extension = when {
                normalized.contains(".log") || normalized.contains("log files") -> "log"
                normalized.contains("image") || normalized.contains("jpg") || normalized.contains("png") -> "png"
                normalized.contains("screenshot") -> "png"
                else -> ""
            }

            val daysAgo = if (normalized.contains("30 days")) 30 else 0
            val ageLimit = System.currentTimeMillis() - (daysAgo * 24L * 60 * 60 * 1000)

            val matches = files.filter { f ->
                val extMatch = extension.isEmpty() || f.extension.equals(extension, ignoreCase = true)
                val ageMatch = daysAgo == 0 || f.lastModified() < ageLimit
                val screenshotMatch = !normalized.contains("screenshot") || f.name.lowercase().contains("screenshot")
                extMatch && ageMatch && screenshotMatch && f.isFile
            }

            matches.forEach { f ->
                actions.add(CopilotAction("DELETE", f.absolutePath, "", "Matches deletion pattern"))
            }
            explanation += "Delete all files matching criteria in this folder. Found ${matches.size} candidate files."
        } else if (normalized.contains("duplicate")) {
            // Find duplicates based on name/size heuristic
            val duplicates = files.groupBy { it.length() }.filter { it.value.size > 1 && it.key > 0L }
            var count = 0
            duplicates.forEach { (_, duplicatesList) ->
                // Keep the first, mark others for action
                for (i in 1 until duplicatesList.size) {
                    val f = duplicatesList[i]
                    actions.add(CopilotAction("DELETE", f.absolutePath, "", "Duplicate of ${duplicatesList[0].name}"))
                    count++
                }
            }
            explanation += "Fuzzy offline duplicates scanner. Found $count duplicate files based on size signatures."
        } else if (normalized.contains("compress") || normalized.contains("zip") || normalized.contains("archive")) {
            val zipName = "Kryon_Archive_${System.currentTimeMillis() / 1000}.zip"
            val zipPath = if (currentPath.endsWith("/")) "$currentPath$zipName" else "$currentPath/$zipName"
            val targetFiles = files.filter { f ->
                val screenshotMatch = !normalized.contains("screenshot") || f.name.lowercase().contains("screenshot")
                screenshotMatch && f.isFile && !f.name.endsWith(".zip")
            }

            targetFiles.forEach { f ->
                actions.add(CopilotAction("ZIP_COMPRESS", f.absolutePath, zipPath, "Compress to $zipName"))
            }
            explanation += "Compress ${targetFiles.size} items in this folder into an offline zip archive: $zipName."
        } else {
            explanation += "I heard '$prompt'. Configure Claude API in settings to unlock full natural language commands!"
        }

        return CopilotResponse(explanation, actions)
    }
}
