package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.data.model.AttendanceLog
import com.example.data.model.ManagementAlert
import com.example.data.model.Student
import com.example.data.model.StudentStatus
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object QrAndPdfUtils {

    fun currentTimeLabel(): String =
        SimpleDateFormat("hh:mm a", Locale.US).format(Date())

    fun currentDateKey(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    fun currentReadableDate(): String =
        SimpleDateFormat("EEEE, dd MMM yyyy", Locale.US).format(Date())

    fun generateQrBitmap(content: String, sizePx: Int = 512): Bitmap? {
        return try {
            val hints = mapOf(EncodeHintType.MARGIN to 1)
            val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
            val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val darkColor = Color.rgb(18, 28, 24)
            val lightColor = Color.WHITE
            for (x in 0 until sizePx) {
                for (y in 0 until sizePx) {
                    bitmap.setPixel(x, y, if (matrix[x, y]) darkColor else lightColor)
                }
            }
            bitmap
        } catch (_: Exception) {
            null
        }
    }

    suspend fun exportDailyPdfReport(
        context: Context,
        students: List<Student>,
        logs: List<AttendanceLog>,
        alerts: List<ManagementAlert>
    ): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val pdf = PdfDocument()
            val pageWidth = 595
            val pageHeight = 842
            var pageNumber = 1
            var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            var page = pdf.startPage(pageInfo)
            var canvas: Canvas = page.canvas

            val emeraldPaint = Paint().apply { color = Color.rgb(0, 106, 78) }
            val crimsonPaint = Paint().apply { color = Color.rgb(200, 16, 46) }
            val headerTextPaint = Paint().apply {
                color = Color.WHITE
                textSize = 18f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val subHeaderPaint = Paint().apply {
                color = Color.rgb(220, 245, 235)
                textSize = 11f
            }
            val sectionPaint = Paint().apply {
                color = Color.rgb(0, 106, 78)
                textSize = 13f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val bodyPaint = Paint().apply {
                color = Color.rgb(25, 35, 30)
                textSize = 10.5f
            }
            val mutedPaint = Paint().apply {
                color = Color.rgb(95, 110, 102)
                textSize = 9.5f
            }
            val linePaint = Paint().apply {
                color = Color.rgb(210, 220, 214)
                strokeWidth = 1f
            }

            // Header Banner
            canvas.drawRect(0f, 0f, pageWidth.toFloat(), 86f, emeraldPaint)
            canvas.drawRect(0f, 82f, pageWidth.toFloat(), 86f, crimsonPaint)
            canvas.drawText("BEPS CONNECT — DAILY ARRIVAL & DISPERSAL REPORT", 32f, 36f, headerTextPaint)
            canvas.drawText(
                "Bangladesh English Private School, Ras Al Khaimah, UAE  •  ${currentReadableDate()}",
                32f,
                58f,
                subHeaderPaint
            )

            var y = 112f
            val arrivedCount = students.count { it.statusEnum() != StudentStatus.NOT_ARRIVED }
            val inClassCount = students.count { it.statusEnum() == StudentStatus.IN_CLASSROOM }
            val collectedCount = students.count {
                it.statusEnum() == StudentStatus.COLLECTED || it.statusEnum() == StudentStatus.BOARDED_BUS
            }
            val openAlertsCount = alerts.count { it.status != "RESOLVED" }

            canvas.drawText("CAMPUS SUMMARY", 32f, y, sectionPaint)
            y += 18f
            canvas.drawText(
                "Total Enrolled: ${students.size}    |    Arrived Today: $arrivedCount    |    In Class: $inClassCount    |    Safely Released: $collectedCount    |    Open Alerts: $openAlertsCount",
                32f,
                y,
                bodyPaint
            )
            y += 16f
            canvas.drawLine(32f, y, pageWidth - 32f, y, linePaint)
            y += 22f

            canvas.drawText("STUDENT STATUS ROSTER", 32f, y, sectionPaint)
            y += 18f

            fun ensureSpace(needed: Float) {
                if (y + needed > pageHeight - 40f) {
                    pdf.finishPage(page)
                    pageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                    page = pdf.startPage(pageInfo)
                    canvas = page.canvas
                    y = 44f
                }
            }

            students.forEach { student ->
                ensureSpace(34f)
                val collector = if (student.verifiedCollectorName.isNotBlank()) " • Collector: ${student.verifiedCollectorName}" else ""
                canvas.drawText(
                    "${student.studentCode}  ${student.fullName} (${student.grade})",
                    32f,
                    y,
                    bodyPaint
                )
                y += 14f
                canvas.drawText(
                    "Status: ${student.statusEnum().label}  •  Arrival: ${student.arrivalTimeLabel.ifBlank { "--" }}  •  Collection: ${student.collectionTimeLabel.ifBlank { "--" }}$collector",
                    42f,
                    y,
                    mutedPaint
                )
                y += 16f
            }

            ensureSpace(44f)
            y += 8f
            canvas.drawLine(32f, y, pageWidth - 32f, y, linePaint)
            y += 22f
            canvas.drawText("VERIFIED HANDOVER & AUDIT LOGS (${logs.size})", 32f, y, sectionPaint)
            y += 18f

            logs.take(40).forEach { log ->
                ensureSpace(30f)
                canvas.drawText(
                    "[${log.timeLabel}] ${log.studentName} — ${log.title} (By ${log.actorName}, ${log.actorRole})",
                    32f,
                    y,
                    bodyPaint
                )
                y += 13f
                val detail = log.message.take(92)
                canvas.drawText(detail, 42f, y, mutedPaint)
                y += 15f
            }

            pdf.finishPage(page)

            val reportsDir = File(context.cacheDir, "reports").apply { mkdirs() }
            val file = File(reportsDir, "BEPS_Daily_Report_${currentDateKey()}.pdf")
            FileOutputStream(file).use { out -> pdf.writeTo(out) }
            pdf.close()

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            Result.success(uri)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
