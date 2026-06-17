package com.example.api

import android.util.Log
import com.example.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

object GeminiClient {
    private const val TAG = "GeminiClient"
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Sends a transcription or translation request to Gemini API.
     */
    suspend fun processTextWithGemini(
        prompt: String,
        apiKey: String = BuildConfig.GEMINI_API_KEY
    ): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            Log.e(TAG, "Gemini API Key is missing or placeholder!")
            return@withContext Result.failure(Exception("Gemini API Key no configurada. Ve a Ajustes o configúrala en el panel de Secrets."))
        }

        try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"
            
            // Build Gemini JSON body
            val root = JSONObject()
            val contentsArray = JSONArray()
            val contentObj = JSONObject()
            val partsArray = JSONArray()
            val partObj = JSONObject()
            
            partObj.put("text", prompt)
            partsArray.put(partObj)
            contentObj.put("parts", partsArray)
            contentsArray.put(contentObj)
            root.put("contents", contentsArray)

            val requestBody = root.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val code = response.code
                    val body = response.body?.string() ?: ""
                    Log.e(TAG, "Request failed with code $code: $body")
                    return@withContext Result.failure(Exception("API returned code $code. $body"))
                }

                val responseBody = response.body?.string() ?: ""
                val jsonResponse = JSONObject(responseBody)
                val candidates = jsonResponse.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val firstCandidate = candidates.getJSONObject(0)
                    val content = firstCandidate.optJSONObject("content")
                    if (content != null) {
                        val parts = content.optJSONArray("parts")
                        if (parts != null && parts.length() > 0) {
                            val part = parts.getJSONObject(0)
                            val textResult = part.optString("text")
                            return@withContext Result.success(textResult.trim())
                        }
                    }
                }
                
                return@withContext Result.failure(Exception("No se pudo parsear la respuesta de Gemini AI."))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in processTextWithGemini", e)
            return@withContext Result.failure(e)
        }
    }
}
