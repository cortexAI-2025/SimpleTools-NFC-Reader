package com.simpletools.nfcreader.core.export

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.OutputStream

/** Rendu PDF A4 (android.graphics.pdf.PdfDocument) d'un [ReportDocument], avec pagination. */
object PdfReportWriter {
    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN = 40f
    private const val BLUE = 0xFF1565C0.toInt()

    fun write(doc: ReportDocument, out: OutputStream) {
        val pdf = PdfDocument()
        val title = paint(20f, bold = true, color = BLUE)
        val subtitle = paint(11f, color = Color.DKGRAY)
        val heading = paint(14f, bold = true, color = BLUE)
        val label = paint(10f, bold = true, color = Color.BLACK)
        val value = paint(10f, color = Color.BLACK)
        val small = paint(9f, color = Color.DKGRAY)
        val width = PAGE_W - 2 * MARGIN
        val labelWidth = 150f
        val valueWidth = width - labelWidth - 8f

        var pageNo = 0
        lateinit var page: PdfDocument.Page
        var y = 0f
        fun newPage() {
            if (pageNo > 0) pdf.finishPage(page)
            pageNo++
            page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create())
            y = MARGIN
        }
        fun ensure(h: Float) { if (y + h > PAGE_H - MARGIN) newPage() }

        newPage()
        page.canvas.drawText(doc.title, MARGIN, y + 20f, title); y += 28f
        page.canvas.drawText(doc.subtitle, MARGIN, y + 11f, subtitle); y += 26f

        for (section in doc.sections) {
            ensure(40f)
            page.canvas.drawText(section.heading, MARGIN, y + 14f, heading); y += 22f
            for (row in section.rows) {
                val labelLines = wrap(row.label, label, labelWidth)
                val valueLines = wrap(row.value, value, valueWidth)
                val lines = maxOf(labelLines.size, valueLines.size)
                val lineH = 13f
                var i = 0
                while (i < lines) {
                    ensure(lineH)
                    labelLines.getOrNull(i)?.let { page.canvas.drawText(it, MARGIN, y + 10f, label) }
                    valueLines.getOrNull(i)?.let { page.canvas.drawText(it, MARGIN + labelWidth + 8f, y + 10f, value) }
                    y += lineH
                    i++
                }
                y += 3f
            }
            y += 8f
        }

        val disclaimerLines = wrap(doc.disclaimer, small, width)
        ensure(disclaimerLines.size * 12f + 16f)
        y += 8f
        page.canvas.drawLine(MARGIN, y, PAGE_W - MARGIN, y, small); y += 12f
        disclaimerLines.forEach { page.canvas.drawText(it, MARGIN, y + 9f, small); y += 12f }

        pdf.finishPage(page)
        try { pdf.writeTo(out) } finally { pdf.close() }
    }

    private fun paint(size: Float, bold: Boolean = false, color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }

    /** Retour à la ligne par mesure (coupe aux espaces, sinon au caractère pour les longues chaînes hexa). */
    internal fun wrap(text: String, paint: Paint, maxWidth: Float): List<String> {
        val result = mutableListOf<String>()
        for (paragraph in text.split('\n')) {
            var rest = paragraph
            if (rest.isEmpty()) { result += ""; continue }
            while (rest.isNotEmpty()) {
                var count = paint.breakText(rest, true, maxWidth, null)
                if (count <= 0) count = 1
                if (count < rest.length) {
                    val space = rest.lastIndexOf(' ', count)
                    if (space > 0) count = space
                }
                result += rest.substring(0, count).trimEnd()
                rest = rest.substring(count).trimStart()
            }
        }
        return result
    }
}
