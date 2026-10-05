package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.util.Base64
import android.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.math.max
import kotlin.math.min

object ImageUtils {

    fun saveBitmapToInternalStorage(
        context: Context,
        bitmap: Bitmap,
        filenamePrefix: String = "exam_scan"
    ): String {
        val dir = File(context.filesDir, "exam_scans")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val file = File(dir, "${filenamePrefix}_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)
        }
        return file.absolutePath
    }

    fun bitmapToBase64(bitmap: Bitmap, quality: Int = 85): String {
        val outputStream = ByteArrayOutputStream()
        // Resize down if too large to conserve bandwidth & tokens
        val resized = getDownscaledBitmap(bitmap, 1600)
        resized.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }

    fun loadBitmapFromPath(filePath: String, maxDimension: Int = 1600): Bitmap? {
        val file = File(filePath)
        if (!file.exists()) return null
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(filePath, options)

            var scale = 1
            while ((options.outWidth / scale / 2) >= maxDimension || (options.outHeight / scale / 2) >= maxDimension) {
                scale *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = scale
            }
            val raw = BitmapFactory.decodeFile(filePath, decodeOptions) ?: return null
            fixOrientation(filePath, raw)
        } catch (_: Exception) {
            null
        }
    }

    fun loadBitmapFromUri(context: Context, uri: Uri, maxDimension: Int = 1600): Bitmap? {
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream?.close()

            var scale = 1
            while ((options.outWidth / scale / 2) >= maxDimension || (options.outHeight / scale / 2) >= maxDimension) {
                scale *= 2
            }

            val decodeStream = context.contentResolver.openInputStream(uri)
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = scale
            }
            val raw = BitmapFactory.decodeStream(decodeStream, null, decodeOptions)
            decodeStream?.close()
            raw
        } catch (_: Exception) {
            null
        }
    }

    private fun getDownscaledBitmap(source: Bitmap, maxDim: Int): Bitmap {
        val width = source.width
        val height = source.height
        if (width <= maxDim && height <= maxDim) return source

        val ratio = min(maxDim.toFloat() / width, maxDim.toFloat() / height)
        val targetWidth = (width * ratio).toInt()
        val targetHeight = (height * ratio).toInt()
        return Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)
    }

    private fun fixOrientation(filePath: String, bitmap: Bitmap): Bitmap {
        return try {
            val exif = ExifInterface(filePath)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            val matrix = android.graphics.Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                else -> return bitmap
            }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (_: Exception) {
            bitmap
        }
    }

    /**
     * Renders a clean exam worksheet card bitmap for quick testing without requiring physical paper.
     */
    fun renderProblemCardBitmap(
        subject: String,
        topic: String,
        title: String,
        questionText: String
    ): Bitmap {
        val width = 1000
        val height = 750
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Background paper texture
        canvas.drawColor(Color.WHITE)

        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1E293B")
            textSize = 34f
            isFakeBoldText = true
        }

        val badgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#4F46E5")
        }

        val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 24f
            isFakeBoldText = true
        }

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            textSize = 28f
        }

        val linePaint = Paint().apply {
            color = Color.parseColor("#E2E8F0")
            strokeWidth = 3f
        }

        // Draw top header
        canvas.drawRoundRect(RectF(40f, 40f, 260f, 90f), 16f, 16f, badgeBgPaint)
        canvas.drawText(subject.uppercase(), 65f, 75f, badgeTextPaint)

        canvas.drawText("EXAM QUESTION PAPER", 290f, 77f, headerPaint)
        canvas.drawLine(40f, 110f, (width - 40).toFloat(), 110f, linePaint)

        // Topic & Title
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#64748B")
            textSize = 24f
            isFakeBoldText = true
        }
        canvas.drawText("Topic: $topic  •  $title", 45f, 150f, subPaint)

        // Question box
        val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F8FAFC")
            style = Paint.Style.FILL
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#CBD5E1")
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        val questionRect = RectF(40f, 180f, (width - 40).toFloat(), (height - 60).toFloat())
        canvas.drawRoundRect(questionRect, 18f, 18f, boxPaint)
        canvas.drawRoundRect(questionRect, 18f, 18f, borderPaint)

        // Word wrap question text
        val maxWidth = (width - 140).toFloat()
        var currentY = 240f
        val words = questionText.split(" ")
        var line = StringBuilder()

        for (word in words) {
            val testLine = if (line.isEmpty()) word else "$line $word"
            if (textPaint.measureText(testLine) > maxWidth) {
                canvas.drawText(line.toString(), 70f, currentY, textPaint)
                currentY += 44f
                line = StringBuilder(word)
            } else {
                line = StringBuilder(testLine)
            }
        }
        if (line.isNotEmpty()) {
            canvas.drawText(line.toString(), 70f, currentY, textPaint)
        }

        // Footer water mark
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#94A3B8")
            textSize = 20f
        }
        canvas.drawText("ExamSnap AI Study Assistant  •  Show all necessary steps & units", 70f, (height - 90).toFloat(), footerPaint)

        return bitmap
    }
}
