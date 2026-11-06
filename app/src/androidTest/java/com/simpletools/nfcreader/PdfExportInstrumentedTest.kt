package com.simpletools.nfcreader

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simpletools.nfcreader.core.export.ExportService
import com.simpletools.nfcreader.core.export.PdfReportWriter
import com.simpletools.nfcreader.core.export.ReportBuilder
import com.simpletools.nfcreader.core.export.StringProvider
import com.simpletools.nfcreader.core.models.NdefDetails
import com.simpletools.nfcreader.core.models.NdefRecordInfo
import com.simpletools.nfcreader.core.models.NdefRecordKind
import com.simpletools.nfcreader.core.models.TagFamily
import com.simpletools.nfcreader.core.models.TagSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream

/**
 * Test instrumenté (émulateur/appareil) : valide la VRAIE génération PDF via android.graphics.pdf.PdfDocument,
 * impossible à exécuter en JVM/Robolectric. Les données sont des fixtures, pas une lecture NFC réelle.
 */
@RunWith(AndroidJUnit4::class)
class PdfExportInstrumentedTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun fixture(records: Int = 1) = TagSnapshot(
        scannedAt = 1_700_000_000_000, family = TagFamily.NFC_A, technologies = listOf("NfcA", "Ndef"),
        uidHex = "04:A3:5B:12:7C:80:5C", uidDecimal = "1305511454343260", uidDecimalReversed = null, uidLength = 7,
        uidNote = null, nfcType = "NFC Forum Type 2", isoStandards = listOf("ISO/IEC 14443-3A"), memoryBytes = null,
        manufacturer = "NXP Semiconductors", techDetails = emptyList(), ndefFormatable = false,
        ndef = NdefDetails("org.nfcforum.ndef.type2", 504, 20, true, true,
            List(records) { NdefRecordInfo(NdefRecordKind.TEXT, "T", 10, text = "Ligne $it " + "x".repeat(200), language = "fr") }),
        issues = emptyList(),
    )

    @Test fun pdfIsAValidDocument() {
        val strings = StringProvider { id, args -> context.getString(id, *args) }
        val out = ByteArrayOutputStream()
        PdfReportWriter.write(ReportBuilder(strings).build(fixture(), "Veste", "note", includeUid = true), out)
        val bytes = out.toByteArray()
        assertEquals("%PDF", String(bytes, 0, 4, Charsets.US_ASCII))
        assertTrue(bytes.size > 1000)
    }

    @Test fun longReportIsPaginated() {
        val strings = StringProvider { id, args -> context.getString(id, *args) }
        val small = ByteArrayOutputStream().also { PdfReportWriter.write(ReportBuilder(strings).build(fixture(1), null, null, false), it) }
        val big = ByteArrayOutputStream().also { PdfReportWriter.write(ReportBuilder(strings).build(fixture(80), null, null, false), it) }
        assertTrue("un long rapport doit produire un PDF plus gros (plusieurs pages)", big.size() > small.size() * 2)
    }

    @Test fun exportServiceWritesShareableFiles() {
        val svc = ExportService(context, StringProvider { id, args -> context.getString(id, *args) })
        val pdf = svc.createPdfFile(fixture(), "Veste", null, includeUid = false)
        val json = svc.createJsonFile(fixture(), "Veste", null, includeUid = false)
        assertTrue(pdf.length() > 1000 && json.length() > 100)
        assertTrue(!pdf.readBytes().decodeToString().contains("04:A3:5B")) // UID exclu du PDF
        svc.clearExports()
        assertTrue(!pdf.exists() && !json.exists())
    }
}
