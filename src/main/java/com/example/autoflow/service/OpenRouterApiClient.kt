package com.example.autoflow.service

import android.content.Context
import android.util.Log
import com.example.autoflow.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class OpenRouterApiClient(private val context: Context) {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .addHeader("Authorization", "Bearer ${BuildConfig.OPENROUTER_API_KEY}")
                .addHeader("HTTP-Referer", "https://autoflow-expense-tracker.app")
                .addHeader("X-Title", "AutoFlow Expense Tracker")
                .addHeader("Content-Type", "application/json")
                .build()
            chain.proceed(request)
        }
        .build()

    companion object {
        private const val TAG = "OpenRouterApiClient"
        private const val BASE_URL = "https://openrouter.ai/api/v1"
        private const val CHAT_COMPLETIONS_ENDPOINT = "$BASE_URL/chat/completions"
        
        // Available models - you can switch between these based on your needs
        const val MODEL_GPT_4O = "openai/gpt-4o"
        const val MODEL_GPT_4O_MINI = "openai/gpt-4o-mini"
        const val MODEL_CLAUDE_3_5_SONNET = "anthropic/claude-3.5-sonnet"
        const val MODEL_CLAUDE_3_HAIKU = "anthropic/claude-3-haiku"
        const val MODEL_LLAMA_3_1_8B = "meta-llama/llama-3.1-8b-instruct"
        const val MODEL_GEMINI_2_5_FLASH = "google/gemini-2.5-flash-image-preview:free"
        const val MODEL_GEMMA_2_9B = "google/gemma-2-9b-it"
        
        // Default models using free tier options from BuildConfig
        const val DEFAULT_MODEL = BuildConfig.OPENROUTER_TEXT_MODEL
        const val DEFAULT_VISION_MODEL = BuildConfig.OPENROUTER_VISION_MODEL
    }

    suspend fun parseExpenseFromText(
        text: String,
        model: String = DEFAULT_MODEL
    ): ExpenseParseResult? = withContext(Dispatchers.IO) {
        
        if (!isApiKeyConfigured()) {
            Log.w(TAG, "OpenRouter API key not configured")
            return@withContext null
        }
        
        try {
            val prompt = buildExpenseParsingPrompt(text)
            val response = sendChatRequest(prompt, model)
            
            return@withContext if (response != null) {
                parseExpenseResponse(response)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing expense with OpenRouter", e)
            null
        }
    }

    suspend fun parseExpenseFromImage(
        imageBase64: String,
        text: String = "",
        model: String = DEFAULT_VISION_MODEL // Use Gemini 2.5 Flash for vision
    ): ExpenseParseResult? = withContext(Dispatchers.IO) {
        
        if (!isApiKeyConfigured()) {
            Log.w(TAG, "OpenRouter API key not configured")
            return@withContext null
        }
        
        try {
            val prompt = buildImageExpenseParsingPrompt(text)
            val response = sendImageChatRequest(prompt, imageBase64, model)
            
            return@withContext if (response != null) {
                parseExpenseResponse(response)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing expense from image with OpenRouter", e)
            null
        }
    }

    private fun buildExpenseParsingPrompt(text: String): String {
        return """
            You are an expense parsing expert. Analyze this text and extract expense information.
            
            Text: "$text"
            
            Extract details and return ONLY a valid JSON object:
            {
                "merchant": "Merchant/vendor name (max 50 chars)",
                "amount": 0.0,
                "currency": "Currency code (INR, USD, EUR, etc.)",
                "category": "One of: Food, Travel, Fuel, Shopping, Healthcare, Grocery, Entertainment, Bills, Others",
                "description": "Brief transaction description",
                "confidence": 0.0
            }
            
            Guidelines:
            - Extract the largest monetary amount if multiple found
            - Identify merchant from app names (Swiggy→Food, Uber→Travel, etc.)
            - Confidence: 0.9+ for clear payment notifications, 0.7+ for transaction texts, 0.5+ for unclear texts
            - Default currency to INR for Indian apps, USD otherwise
            - Use exact category names listed above
            
            Return ONLY the JSON object.
        """.trimIndent()
    }

    private fun buildImageExpenseParsingPrompt(ocrText: String): String {
        return """
            You are an expert at reading receipts and extracting expense information. 
            Analyze this receipt image and extract the expense details accurately.
            
            ${if (ocrText.isNotBlank()) "Additional OCR text context: $ocrText" else ""}
            
            Extract information and return ONLY a valid JSON object with these exact fields:
            {
                "merchant": "Business/store name from the receipt header",
                "amount": 0.0,
                "currency": "Currency code (USD, INR, EUR, etc.)",
                "category": "One of: Food, Travel, Fuel, Shopping, Healthcare, Grocery, Entertainment, Bills, Others",
                "description": "Brief description of the purchase",
                "items": ["list", "of", "purchased", "items"],
                "date": "Receipt date in YYYY-MM-DD format if visible",
                "confidence": 0.0
            }
            
            Instructions:
            - Look for the TOTAL amount (usually at bottom, largest/boldest number)
            - Extract business name from header/logo area
            - Categorize based on business type and items purchased
            - List individual items if clearly visible
            - Set confidence 0.8+ if receipt is clear, 0.5-0.7 if partially readable, <0.5 if unclear
            - For currency, detect from symbols (₹=INR, $=USD, €=EUR, £=GBP)
            
            Return ONLY the JSON object, no additional text or formatting.
        """.trimIndent()
    }

    private suspend fun sendChatRequest(
        prompt: String,
        model: String
    ): String? = withContext(Dispatchers.IO) {
        
        val requestBody = JSONObject().apply {
            put("model", model)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
            put("max_tokens", 1000)
            put("temperature", 0.1)
            put("top_p", 0.9)
        }

        val request = Request.Builder()
            .url(CHAT_COMPLETIONS_ENDPOINT)
            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        return@withContext try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                parseOpenRouterResponse(responseBody)
            } else {
                Log.e(TAG, "OpenRouter API error: ${response.code} - ${response.message}")
                null
            }
        } catch (e: IOException) {
            Log.e(TAG, "Network error calling OpenRouter API", e)
            null
        }
    }

    private suspend fun sendImageChatRequest(
        prompt: String,
        imageBase64: String,
        model: String
    ): String? = withContext(Dispatchers.IO) {
        
        val requestBody = JSONObject().apply {
            put("model", model)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", JSONArray().apply {
                        put(JSONObject().apply {
                            put("type", "text")
                            put("text", prompt)
                        })
                        put(JSONObject().apply {
                            put("type", "image_url")
                            put("image_url", JSONObject().apply {
                                put("url", "data:image/jpeg;base64,$imageBase64")
                            })
                        })
                    })
                })
            })
            put("max_tokens", 1000)
            put("temperature", 0.1)
        }

        val request = Request.Builder()
            .url(CHAT_COMPLETIONS_ENDPOINT)
            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        return@withContext try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                parseOpenRouterResponse(responseBody)
            } else {
                Log.e(TAG, "OpenRouter API error: ${response.code} - ${response.message}")
                null
            }
        } catch (e: IOException) {
            Log.e(TAG, "Network error calling OpenRouter API", e)
            null
        }
    }

    private fun parseOpenRouterResponse(responseBody: String?): String? {
        return try {
            if (responseBody == null) return null
            
            val jsonResponse = JSONObject(responseBody)
            val choices = jsonResponse.getJSONArray("choices")
            if (choices.length() > 0) {
                val firstChoice = choices.getJSONObject(0)
                val message = firstChoice.getJSONObject("message")
                message.getString("content").trim()
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing OpenRouter response", e)
            null
        }
    }

    private fun parseExpenseResponse(responseText: String): ExpenseParseResult? {
        return try {
            // Find JSON in the response
            val jsonStart = responseText.indexOf('{')
            val jsonEnd = responseText.lastIndexOf('}')
            
            if (jsonStart == -1 || jsonEnd == -1) {
                Log.w(TAG, "No JSON found in response")
                return null
            }
            
            val jsonString = responseText.substring(jsonStart, jsonEnd + 1)
            val json = JSONObject(jsonString)
            
            ExpenseParseResult(
                merchant = json.optString("merchant", "Unknown Merchant"),
                amount = json.optDouble("amount", 0.0),
                currency = json.optString("currency", "INR"),
                category = json.optString("category", "Others"),
                description = json.optString("description", ""),
                items = parseItems(json.optJSONArray("items")),
                date = json.optString("date", ""),
                confidence = json.optDouble("confidence", 0.5)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing expense response", e)
            null
        }
    }

    private fun parseItems(itemsArray: JSONArray?): List<String> {
        return try {
            val items = mutableListOf<String>()
            itemsArray?.let { array ->
                for (i in 0 until array.length()) {
                    items.add(array.getString(i))
                }
            }
            items
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun isApiKeyConfigured(): Boolean {
        val apiKey = BuildConfig.OPENROUTER_API_KEY
        return !apiKey.contains("PLACEHOLDER", ignoreCase = true) && 
               apiKey.isNotBlank() && 
               apiKey.startsWith("sk-or-v1-")
    }

    // Get available models (for future use)
    suspend fun getAvailableModels(): List<String>? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$BASE_URL/models")
            .get()
            .build()

        return@withContext try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                parseModelsResponse(responseBody)
            } else {
                null
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error fetching models", e)
            null
        }
    }

    private fun parseModelsResponse(responseBody: String?): List<String>? {
        return try {
            if (responseBody == null) return null
            
            val jsonResponse = JSONObject(responseBody)
            val data = jsonResponse.getJSONArray("data")
            val models = mutableListOf<String>()
            
            for (i in 0 until data.length()) {
                val model = data.getJSONObject(i)
                models.add(model.getString("id"))
            }
            
            models
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing models response", e)
            null
        }
    }
}

data class ExpenseParseResult(
    val merchant: String,
    val amount: Double,
    val currency: String,
    val category: String,
    val description: String,
    val items: List<String> = emptyList(),
    val date: String = "",
    val confidence: Double
)
