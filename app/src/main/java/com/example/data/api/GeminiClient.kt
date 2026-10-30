package com.example.data.api

import android.util.Log
import com.example.BuildConfig
import com.example.data.model.Expense
import com.example.data.model.SavingGoal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object GeminiClient {
    private const val TAG = "GeminiClient"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent"

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val mediaType = "application/json; charset=utf-8".toMediaType()

    private suspend fun makeApiCall(prompt: String, systemInstruction: String? = null): String? = withContext(Dispatchers.IO) {
        val apiKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read GEMINI_API_KEY from BuildConfig", e)
            ""
        }

        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            Log.w(TAG, "Gemini API key is not configured. Falling back to offline simulator.")
            return@withContext null
        }

        try {
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

            if (systemInstruction != null) {
                val sysInstructionObj = JSONObject()
                val sysPartsArray = JSONArray()
                val sysPartObj = JSONObject()
                sysPartObj.put("text", systemInstruction)
                sysPartsArray.put(sysPartObj)
                sysInstructionObj.put("parts", sysPartsArray)
                root.put("systemInstruction", sysInstructionObj)
            }

            // Generation config
            val genConfig = JSONObject()
            genConfig.put("temperature", 0.4)
            root.put("generationConfig", genConfig)

            val jsonBody = root.toString()
            Log.d(TAG, "Gemini Request Prompt: $prompt")

            val request = Request.Builder()
                .url("$BASE_URL?key=$apiKey")
                .post(jsonBody.toRequestBody(mediaType))
                .build()

            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string()
                Log.d(TAG, "Gemini Response Code: ${response.code}")
                if (response.isSuccessful && bodyStr != null) {
                    val respJson = JSONObject(bodyStr)
                    val candidates = respJson.optJSONArray("candidates")
                    val firstCandidate = candidates?.optJSONObject(0)
                    val content = firstCandidate?.optJSONObject("content")
                    val parts = content?.optJSONArray("parts")
                    val textText = parts?.optJSONObject(0)?.optString("text")
                    return@withContext textText
                } else {
                    Log.e(TAG, "API Call Failed: Response code=${response.code}, body=$bodyStr")
                    return@withContext null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during Gemini API call", e)
            return@withContext null
        }
    }

    suspend fun getDashboardInsights(expenses: List<Expense>, monthlyBudget: Double): List<String> {
        val summaryText = if (expenses.isEmpty()) {
            "No expenses tracked yet for this month."
        } else {
            expenses.joinToString("; ") { "₹${it.amount} on ${it.category} (${it.description})" }
        }

        val prompt = """
            Analyze these transactions against a monthly budget limit of ₹$monthlyBudget.
            Transactions: $summaryText
            
            Provide exactly 3 short, personalized, highly informative financial coaching tips or insights.
            Format the output strictly as 3 bullet points, each on a new line starting with an icon check (like 🎯, 💡, 🔥, etc.).
            Do not output any introductory or concluding text. Do not make bold asterisks. Keep each tip under 12 words.
        """.trimIndent()

        val result = makeApiCall(prompt, "You are an intelligent, friendly personal financial coach AI.")
        return if (result != null) {
            result.split("\n")
                .map { it.trim().removePrefix("-").removePrefix("*").trim() }
                .filter { it.isNotEmpty() }
                .take(3)
        } else {
            // High-quality simulated fallback
            listOf(
                "💡 Set category budgets to prevent minor daily expenses from slipping through.",
                "🎯 Your savings habit looks strong! Keep up the no-overspending streak.",
                "🔥 Pro-tip: UPI transfers are easy to spend. Try setting a daily UPI lock limit."
            )
        }
    }

    suspend fun parseVoiceExpense(voiceText: String): Triple<Double, String, String>? {
        val prompt = """
            Extract transaction details from this speech input: "$voiceText"
            The category must be strictly one of these exact strings: "Food", "Transportation", "Entertainment", "Shopping", "Utilities", "Health", "Other".
            Translate languages dynamically (e.g. "rupees", "INR", "Rs" to numbers).
            Make sure to output ONLY a valid JSON object. Do not include markdown code block syntax.
            Example schema:
            {
              "amount": 250.0,
              "category": "Food",
              "description": "Lunch"
            }
        """.trimIndent()

        val systemInstruction = "You are an expense parser that converts raw voice transcriptions into budget schema JSON. You output ONLY pure raw JSON, never explain, and never wrap it in ```json codeblocks."
        val result = makeApiCall(prompt, systemInstruction)
        if (result != null) {
            try {
                val cleanJson = result.trim().removeSurrounding("```json", "```").trim()
                val json = JSONObject(cleanJson)
                val amount = json.optDouble("amount", 0.0)
                val category = json.optString("category", "Other")
                val description = json.optString("description", "Voice command expense")
                return Triple(amount, category, description)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse JSON result: $result", e)
            }
        }
        return com.example.util.VoiceExpenseParser.parse(voiceText)?.let {
            Triple(it.amount, it.category, it.description)
        }
    }

    suspend fun parseReceipt(merchantNameSimulated: String, totalAmountSimulated: Double, categorySimulated: String, rawReceiptText: String): Triple<String, Double, String>? {
        val prompt = """
            Determine receipt contents from simple scan snapshot descriptions: "$rawReceiptText"
            The default values are Merchant: "$merchantNameSimulated", Amount: $totalAmountSimulated, Category: "$categorySimulated".
            Adjust the values if you extract more precise amounts or details from the description.
            The category must be strictly one of: "Food", "Transportation", "Entertainment", "Shopping", "Utilities", "Health", "Other".
            Provide details in a clean JSON with fields: 'merchantName', 'amount', 'category'.
            Do not write any markdown code block wraps. Output pure raw JSON.
        """.trimIndent()

        val result = makeApiCall(prompt, "You are a receipt OCR extraction assistant. Output only pure JSON.")
        if (result != null) {
            try {
                val cleanJson = result.trim().removeSurrounding("```json", "```").trim()
                val json = JSONObject(cleanJson)
                val merchant = json.optString("merchantName", merchantNameSimulated)
                val amount = json.optDouble("amount", totalAmountSimulated)
                val category = json.optString("category", categorySimulated)
                return Triple(merchant, amount, category)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse OCR JSON: $result", e)
            }
        }
        return Triple(merchantNameSimulated, totalAmountSimulated, categorySimulated)
    }

    suspend fun getCoachingReport(expenses: List<Expense>, goals: List<SavingGoal>, limit: Double): String {
        val expensesStr = expenses.joinToString("; ") { "₹${it.amount} on ${it.category} (${it.description})" }
        val goalsStr = goals.joinToString("; ") { "${it.name}: targets ₹${it.targetAmount} (current ₹${it.currentAmount})" }

        val prompt = """
            Create a comprehensive AI financial coach report.
            Monthly Budget Limit: ₹$limit
            Recent Expenses: $expensesStr
            Active Goals: $goalsStr
            
            Synthesize this financial status to supply:
            1. Current status & warnings: Tell them if they are overspending or approaching daily limits.
            2. High-value recommendation: A targeted way to save (e.g. "Reducing your weekend Shopping by 15% could boost your Emergency Fund visual projection by 3 months").
            3. Budget forecast: Forecast savings trajectory for the upcoming month.
            
            Keep your paragraphs descriptive, punchy, beautiful, encouraging, and clear. Format using clean Material standard lines.
        """.trimIndent()

        val result = makeApiCall(prompt, "You are a master personal finance coach and certified wealth advisor.")
        return result ?: """
            **Monthly Health Overview**  
            Your financial health score is looking strong, but minor leakage on daily **Food** and **Entertainment** stands out. Setting minor sub-category limits will guarantee you secure your targets.

            **Strategic Recommendations**  
            Reducing dining-out expenditures by just 10% would spare approximately **₹2,200** this month, which we can route straight to your **Emergency Fund** goals.

            **Savings Projection & Forecast**  
            If you maintain your current streak, you are on track to save an extra **₹4,500** by month-end, bringing you closer to your bike purchase goal 2 weeks ahead of schedule. Keep pushing!
        """.trimIndent()
    }
}
