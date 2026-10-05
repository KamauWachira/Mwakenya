package com.example.data.model

import org.json.JSONArray
import org.json.JSONObject

data class ExamStep(
    val stepNumber: Int,
    val title: String,
    val explanation: String,
    val formula: String? = null,
    val tip: String? = null
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("stepNumber", stepNumber)
            put("title", title)
            put("explanation", explanation)
            put("formula", formula ?: "")
            put("tip", tip ?: "")
        }
    }

    companion object {
        fun fromJson(obj: JSONObject): ExamStep {
            return ExamStep(
                stepNumber = obj.optInt("stepNumber", 1),
                title = obj.optString("title", "Step"),
                explanation = obj.optString("explanation", ""),
                formula = obj.optString("formula").takeIf { it.isNotBlank() },
                tip = obj.optString("tip").takeIf { it.isNotBlank() }
            )
        }

        fun listToJsonString(steps: List<ExamStep>): String {
            val array = JSONArray()
            for (step in steps) {
                array.put(step.toJson())
            }
            return array.toString()
        }

        fun listFromJsonString(jsonStr: String): List<ExamStep> {
            if (jsonStr.isBlank()) return emptyList()
            val list = mutableListOf<ExamStep>()
            try {
                val array = JSONArray(jsonStr)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(fromJson(obj))
                }
            } catch (_: Exception) {
                // Return whatever could be parsed
            }
            return list
        }
    }
}

data class ParsedExamSolution(
    val subject: String,
    val topic: String,
    val difficulty: String = "Intermediate",
    val questionText: String,
    val finalAnswer: String,
    val steps: List<ExamStep>,
    val summary: String,
    val fullMarkdown: String
)

data class SampleExamProblem(
    val id: String,
    val subject: String,
    val topic: String,
    val title: String,
    val questionPrompt: String,
    val placeholderColorHex: Long = 0xFF4F46E5
)
