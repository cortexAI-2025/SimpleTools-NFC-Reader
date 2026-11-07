package com.simpletools.nfcreader.core.templates

import com.simpletools.nfcreader.core.database.TemplateDao
import com.simpletools.nfcreader.core.database.TemplateEntity
import com.simpletools.nfcreader.core.write.WritePayload
import kotlinx.coroutines.flow.Flow
import org.json.JSONObject

/** Modèle réutilisable (PRO). Les champs sont stockés en JSON ; aucun mot de passe Wi-Fi n'est conservé. */
data class Template(
    val id: Long,
    val name: String,
    val category: String,
    val kind: String,
    val fields: Map<String, String>,
)

class TemplateRepository(private val dao: TemplateDao, private val clock: () -> Long = System::currentTimeMillis) {

    fun observe(): Flow<List<TemplateEntity>> = dao.observeAll()

    suspend fun get(id: Long): Template? = dao.get(id)?.let(::toModel)

    suspend fun save(id: Long?, name: String, category: String, fields: Map<String, String>, kind: String): Long {
        val cleanName = name.trim().take(60).ifEmpty { "Sans nom" }
        val cleanCategory = category.trim().take(40).ifEmpty { "Général" }
        val safeFields = fields.filterKeys { it != "password" }   // jamais de secret persistant
        val json = JSONObject(safeFields as Map<*, *>).toString()
        return if (id == null) {
            dao.insert(TemplateEntity(name = cleanName, category = cleanCategory, kind = kind, fieldsJson = json, updatedAt = clock()))
        } else {
            dao.get(id)?.let { dao.update(it.copy(name = cleanName, category = cleanCategory, kind = kind, fieldsJson = json, updatedAt = clock())) }
            id
        }
    }

    /** Duplication : crée une copie indépendante. */
    suspend fun duplicate(id: Long): Long? {
        val t = get(id) ?: return null
        return save(null, "${t.name} (copie)", t.category, t.fields, t.kind)
    }

    suspend fun delete(id: Long) = dao.delete(id)

    private fun toModel(e: TemplateEntity): Template {
        val o = JSONObject(e.fieldsJson)
        val fields = o.keys().asSequence().associateWith { o.optString(it) }
        return Template(e.id, e.name, e.category, e.kind, fields)
    }

    companion object {
        const val KIND_TEXT = "text"
        const val KIND_URL = "url"
        const val KIND_CONTACT = "contact"
        const val KIND_WIFI = "wifi"

        /** Transforme un modèle en contenu écrivable. Le mot de passe Wi-Fi doit être saisi à chaque écriture. */
        fun toPayload(t: Template, wifiPassword: String? = null): WritePayload? = when (t.kind) {
            KIND_TEXT -> WritePayload.Text(t.fields["text"].orEmpty(), t.fields["language"]?.ifBlank { null } ?: "fr")
            KIND_URL -> WritePayload.Url(t.fields["url"].orEmpty())
            KIND_CONTACT -> WritePayload.Contact(
                t.fields["firstName"].orEmpty(), t.fields["lastName"].orEmpty(), t.fields["phone"].orEmpty(),
                t.fields["email"].orEmpty(), t.fields["organization"].orEmpty(),
            )
            KIND_WIFI -> WritePayload.Wifi(t.fields["ssid"].orEmpty(), wifiPassword.orEmpty(), open = t.fields["open"] == "true")
            else -> null
        }
    }
}
