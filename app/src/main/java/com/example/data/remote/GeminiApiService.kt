package com.example.data.remote

import android.util.Log
import com.example.BuildConfig
import com.example.data.model.ExamStep
import com.example.data.model.ParsedExamSolution
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GeminiApiService {

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun analyzeExamQuestion(
        imageBase64: String,
        mimeType: String = "image/jpeg"
    ): Result<ParsedExamSolution> = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext Result.failure(
                IllegalStateException("Gemini API key is not configured. Please add your key in the AI Studio Secrets panel.")
            )
        }

        val prompt = """
            You are an expert exam problem solver and academic professor across STEM and humanities.
            Analyze this exam question image carefully.
            
            Extract the exact question, identify the subject and topic, and provide an accurate, step-by-step solution.
            
            You MUST reply with a JSON object in this exact structure:
            {
              "subject": "e.g. Mathematics, Physics, Chemistry, Biology, or Computer Science",
              "topic": "e.g. Integration by Parts, Projectile Motion, Stoichiometry, etc.",
              "difficulty": "Easy, Intermediate, or Advanced",
              "questionText": "Accurate transcription of the question including numbers, formulas, options, and units",
              "finalAnswer": "Direct, definitive final answer (e.g. x = 5 / 2 or Option C)",
              "steps": [
                {
                  "stepNumber": 1,
                  "title": "Clear step title",
                  "explanation": "Detailed step-by-step reasoning and calculations",
                  "formula": "Key formula or theorem used in this step",
                  "tip": "Helpful exam tip or common trap to avoid"
                }
              ],
              "summary": "A concise summary of key concepts and advice for exam review."
            }
            Output ONLY valid JSON.
        """.trimIndent()

        // Build Gemini Request Payload
        val requestJson = JSONObject().apply {
            val contentsArray = JSONArray().apply {
                val contentObj = JSONObject().apply {
                    val partsArray = JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", prompt)
                        })
                        put(JSONObject().apply {
                            val inlineData = JSONObject().apply {
                                put("mimeType", mimeType)
                                put("data", imageBase64)
                            }
                            put("inlineData", inlineData)
                        })
                    }
                    put("parts", partsArray)
                }
                put(contentObj)
            }
            put("contents", contentsArray)

            val generationConfig = JSONObject().apply {
                put("temperature", 0.2)
                put("topP", 0.95)
            }
            put("generationConfig", generationConfig)
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"
        val requestBody = requestJson.toString().toRequestBody(jsonMediaType)
        val httpRequest = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        try {
            val response = okHttpClient.newCall(httpRequest).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                Log.e("GeminiApiService", "API Error ${response.code}: $responseBody")
                val errorMessage = try {
                    val errJson = JSONObject(responseBody)
                    errJson.optJSONObject("error")?.optString("message") ?: "API Error HTTP ${response.code}"
                } catch (_: Exception) {
                    "API Error HTTP ${response.code}: $responseBody"
                }
                return@withContext Result.failure(Exception(errorMessage))
            }

            val parsedSolution = parseGeminiResponse(responseBody)
            Result.success(parsedSolution)
        } catch (e: Exception) {
            Log.e("GeminiApiService", "Network exception", e)
            Result.failure(e)
        }
    }

    private fun parseGeminiResponse(rawResponseBody: String): ParsedExamSolution {
        val root = JSONObject(rawResponseBody)
        val candidates = root.optJSONArray("candidates")
        val candidate = candidates?.optJSONObject(0)
        val content = candidate?.optJSONObject("content")
        val parts = content?.optJSONArray("parts")
        val rawText = parts?.optJSONObject(0)?.optString("text") ?: ""

        // Extract JSON string even if wrapped in ```json ... ```
        val jsonCleaned = cleanJsonString(rawText)

        return try {
            val solutionObj = JSONObject(jsonCleaned)
            val subject = solutionObj.optString("subject", "Exam Question")
            val topic = solutionObj.optString("topic", "General Topic")
            val difficulty = solutionObj.optString("difficulty", "Intermediate")
            val questionText = solutionObj.optString("questionText", "Exam Question")
            val finalAnswer = solutionObj.optString("finalAnswer", "Solution Found")
            val summary = solutionObj.optString("summary", "Review the steps carefully.")

            val stepsList = mutableListOf<ExamStep>()
            val stepsArray = solutionObj.optJSONArray("steps")
            if (stepsArray != null) {
                for (i in 0 until stepsArray.length()) {
                    val stepObj = stepsArray.getJSONObject(i)
                    stepsList.add(ExamStep.fromJson(stepObj))
                }
            }

            if (stepsList.isEmpty()) {
                stepsList.add(
                    ExamStep(
                        stepNumber = 1,
                        title = "Detailed Explanation",
                        explanation = rawText,
                        formula = null,
                        tip = "Double-check work before submitting."
                    )
                )
            }

            ParsedExamSolution(
                subject = subject,
                topic = topic,
                difficulty = difficulty,
                questionText = questionText,
                finalAnswer = finalAnswer,
                steps = stepsList,
                summary = summary,
                fullMarkdown = buildMarkdownFromParsed(subject, topic, questionText, finalAnswer, stepsList, summary)
            )
        } catch (e: Exception) {
            Log.w("GeminiApiService", "Failed parsing structured JSON, fallback to plain text parser", e)
            fallbackMarkdownParser(rawText)
        }
    }

    private fun cleanJsonString(text: String): String {
        var trimmed = text.trim()
        if (trimmed.startsWith("```json")) {
            trimmed = trimmed.removePrefix("```json")
        } else if (trimmed.startsWith("```")) {
            trimmed = trimmed.removePrefix("```")
        }
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.removeSuffix("```")
        }
        val firstBrace = trimmed.indexOf('{')
        val lastBrace = trimmed.lastIndexOf('}')
        if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
            return trimmed.substring(firstBrace, lastBrace + 1)
        }
        return trimmed.trim()
    }

    private fun fallbackMarkdownParser(rawText: String): ParsedExamSolution {
        val steps = mutableListOf<ExamStep>()
        val lines = rawText.lines()
        var currentTitle = "Step 1: Solution Analysis"
        val currentExplanation = StringBuilder()
        var stepCount = 1

        for (line in lines) {
            if (line.startsWith("### Step") || line.startsWith("Step ")) {
                if (currentExplanation.isNotEmpty()) {
                    steps.add(
                        ExamStep(
                            stepNumber = stepCount++,
                            title = currentTitle,
                            explanation = currentExplanation.toString().trim()
                        )
                    )
                    currentExplanation.clear()
                }
                currentTitle = line.replace("#", "").trim()
            } else {
                currentExplanation.append(line).append("\n")
            }
        }
        if (currentExplanation.isNotEmpty()) {
            steps.add(
                ExamStep(
                    stepNumber = stepCount,
                    title = currentTitle,
                    explanation = currentExplanation.toString().trim()
                )
            )
        }

        return ParsedExamSolution(
            subject = "Exam Solution",
            topic = "General Problem",
            difficulty = "Intermediate",
            questionText = "Scanned Exam Question",
            finalAnswer = "Refer to step-by-step breakdown below",
            steps = if (steps.isNotEmpty()) steps else listOf(
                ExamStep(1, "Complete Solution", rawText)
            ),
            summary = "Step-by-step solution provided by multimodal AI model.",
            fullMarkdown = rawText
        )
    }

    private fun buildMarkdownFromParsed(
        subject: String,
        topic: String,
        question: String,
        answer: String,
        steps: List<ExamStep>,
        summary: String
    ): String {
        val sb = StringBuilder()
        sb.append("## ").append(subject).append(" • ").append(topic).append("\n\n")
        sb.append("### Question\n").append(question).append("\n\n")
        sb.append("### Final Answer\n**").append(answer).append("**\n\n")
        sb.append("### Step-by-Step Breakdown\n\n")
        for (step in steps) {
            sb.append("#### Step ").append(step.stepNumber).append(": ").append(step.title).append("\n")
            sb.append(step.explanation).append("\n")
            if (!step.formula.isNullOrBlank()) {
                sb.append("*Formula:* `").append(step.formula).append("`\n")
            }
            if (!step.tip.isNullOrBlank()) {
                sb.append("💡 *Tip:* ").append(step.tip).append("\n")
            }
            sb.append("\n")
        }
        if (summary.isNotBlank()) {
            sb.append("### Key Takeaways\n").append(summary).append("\n")
        }
        return sb.toString()
    }
}
