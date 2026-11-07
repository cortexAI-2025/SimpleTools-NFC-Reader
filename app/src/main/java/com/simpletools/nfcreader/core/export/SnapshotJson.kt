package com.simpletools.nfcreader.core.export

import com.simpletools.nfcreader.core.models.NdefDetails
import com.simpletools.nfcreader.core.models.NdefRecordInfo
import com.simpletools.nfcreader.core.models.NdefRecordKind
import com.simpletools.nfcreader.core.models.ReadIssue
import com.simpletools.nfcreader.core.models.TagFamily
import com.simpletools.nfcreader.core.models.TagSnapshot
import com.simpletools.nfcreader.core.models.TechDetail
import com.simpletools.nfcreader.core.models.UidNote
import org.json.JSONArray
import org.json.JSONObject

/** Sérialisation JSON (export + stockage local). Le même format sert aux deux usages. */
object SnapshotJson {
    const val SCHEMA_VERSION = 1

    fun encode(s: TagSnapshot, includeUid: Boolean, name: String? = null, notes: String? = null): String =
        toJson(s, includeUid, name, notes).toString(2)

    fun toJson(s: TagSnapshot, includeUid: Boolean, name: String? = null, notes: String? = null): JSONObject {
        val snap = if (includeUid) s else s.withoutUid()
        return JSONObject().apply {
            put("schemaVersion", SCHEMA_VERSION)
            put("generator", "SimpleTools NFC Reader")
            name?.let { put("name", it) }
            notes?.takeIf { it.isNotBlank() }?.let { put("notes", it) }
            put("scannedAt", snap.scannedAt)
            put("family", snap.family.name)
            put("technologies", JSONArray(snap.technologies))
            put("uidIncluded", includeUid)
            snap.uidHex?.let { put("uidHex", it) }
            snap.uidDecimal?.let { put("uidDecimal", it) }
            snap.uidDecimalReversed?.let { put("uidDecimalReversed", it) }
            put("uidLength", snap.uidLength)
            snap.uidNote?.let { put("uidNote", it.name) }
            snap.nfcType?.let { put("nfcType", it) }
            put("isoStandards", JSONArray(snap.isoStandards))
            snap.memoryBytes?.let { put("memoryBytes", it) }
            snap.manufacturer?.let { put("manufacturerHint", it) }
            put("techDetails", JSONArray(snap.techDetails.map { JSONObject().put("label", it.label).put("value", it.value) }))
            put("ndefFormatable", snap.ndefFormatable)
            snap.ndef?.let { put("ndef", ndefToJson(it)) }
            put("issues", JSONArray(snap.issues.map { it.name }))
            put("disclaimer", "Technical read-out only. An NFC chip's presence does not prove a product is authentic.")
        }
    }

    private fun ndefToJson(n: NdefDetails) = JSONObject().apply {
        n.typeName?.let { put("type", it) }
        n.maxSize?.let { put("maxSize", it) }
        n.usedSize?.let { put("usedSize", it) }
        n.isWritable?.let { put("isWritable", it) }
        n.canMakeReadOnly?.let { put("canMakeReadOnly", it) }
        put("records", JSONArray(n.records.map(::recordToJson)))
    }

    private fun recordToJson(r: NdefRecordInfo): JSONObject = JSONObject().apply {
        put("kind", r.kind.name)
        put("type", r.typeName)
        put("payloadSize", r.payloadSize)
        r.text?.let { put("text", it) }
        r.language?.let { put("language", it) }
        r.uri?.let { put("uri", it) }
        r.mimeType?.let { put("mimeType", it) }
        r.payloadHex?.let { put("payloadHex", it) }
        r.rawHex?.let { put("rawHex", it) }
        if (r.children.isNotEmpty()) put("children", JSONArray(r.children.map(::recordToJson)))
    }

    /** Décode un JSON produit par [encode]. Lève [org.json.JSONException] si invalide. */
    fun decode(json: String): TagSnapshot {
        val o = JSONObject(json)
        return TagSnapshot(
            scannedAt = o.getLong("scannedAt"),
            family = enumOr(o.optString("family"), TagFamily.UNKNOWN),
            technologies = strings(o.optJSONArray("technologies")),
            uidHex = o.optStringOrNull("uidHex"),
            uidDecimal = o.optStringOrNull("uidDecimal"),
            uidDecimalReversed = o.optStringOrNull("uidDecimalReversed"),
            uidLength = o.optInt("uidLength", 0),
            uidNote = o.optStringOrNull("uidNote")?.let { enumOrNull<UidNote>(it) },
            nfcType = o.optStringOrNull("nfcType"),
            isoStandards = strings(o.optJSONArray("isoStandards")),
            memoryBytes = if (o.has("memoryBytes")) o.getInt("memoryBytes") else null,
            manufacturer = o.optStringOrNull("manufacturerHint"),
            techDetails = (o.optJSONArray("techDetails") ?: JSONArray()).let { a ->
                List(a.length()) { i -> a.getJSONObject(i).let { TechDetail(it.getString("label"), it.getString("value")) } }
            },
            ndefFormatable = o.optBoolean("ndefFormatable", false),
            ndef = o.optJSONObject("ndef")?.let(::ndefFromJson),
            issues = strings(o.optJSONArray("issues")).mapNotNull { enumOrNull<ReadIssue>(it) },
        )
    }

    private fun ndefFromJson(o: JSONObject) = NdefDetails(
        typeName = o.optStringOrNull("type"),
        maxSize = if (o.has("maxSize")) o.getInt("maxSize") else null,
        usedSize = if (o.has("usedSize")) o.getInt("usedSize") else null,
        isWritable = if (o.has("isWritable")) o.getBoolean("isWritable") else null,
        canMakeReadOnly = if (o.has("canMakeReadOnly")) o.getBoolean("canMakeReadOnly") else null,
        records = (o.optJSONArray("records") ?: JSONArray()).let { a -> List(a.length()) { recordFromJson(a.getJSONObject(it)) } },
    )

    private fun recordFromJson(o: JSONObject): NdefRecordInfo = NdefRecordInfo(
        kind = enumOr(o.optString("kind"), NdefRecordKind.UNKNOWN),
        typeName = o.optString("type", ""),
        payloadSize = o.optInt("payloadSize", 0),
        text = o.optStringOrNull("text"),
        language = o.optStringOrNull("language"),
        uri = o.optStringOrNull("uri"),
        mimeType = o.optStringOrNull("mimeType"),
        payloadHex = o.optStringOrNull("payloadHex"),
        rawHex = o.optStringOrNull("rawHex"),
        children = (o.optJSONArray("children") ?: JSONArray()).let { a -> List(a.length()) { recordFromJson(a.getJSONObject(it)) } },
    )

    private fun strings(a: JSONArray?): List<String> = if (a == null) emptyList() else List(a.length()) { a.getString(it) }
    private fun JSONObject.optStringOrNull(key: String): String? = if (has(key) && !isNull(key)) getString(key) else null
    private inline fun <reified E : Enum<E>> enumOrNull(v: String): E? = enumValues<E>().firstOrNull { it.name == v }
    private inline fun <reified E : Enum<E>> enumOr(v: String, default: E): E = enumOrNull<E>(v) ?: default
}
