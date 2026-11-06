package com.simpletools.nfcreader.feature.authentication

import com.simpletools.nfcreader.core.models.NdefRecordInfo
import com.simpletools.nfcreader.core.models.NdefRecordKind
import com.simpletools.nfcreader.core.models.TagSnapshot
import com.simpletools.nfcreader.core.nfc.UrlInfo
import com.simpletools.nfcreader.core.nfc.UrlSafety

/** Service de vérification officiel d'une marque. L'application n'est affiliée à aucune marque. */
data class Brand(val name: String, val officialUrl: String, val officialDomain: String)

object BrandRegistry {
    val MONCLER = Brand("Moncler", "https://code.moncler.com/", "moncler.com")
    val all = listOf(MONCLER)

    /** Correspondance stricte : le domaine exact ou un sous-domaine réel, en https. */
    fun match(info: UrlInfo): Brand? = all.firstOrNull { b ->
        info.isHttps && (info.host == b.officialDomain || info.host.endsWith("." + b.officialDomain))
    }
}

data class AnalyzedUrl(val url: String, val info: UrlInfo?, val brand: Brand?)

/**
 * Verdict volontairement limité : AUCUN verdict « authentique ». Même un domaine reconnu
 * ne prouve rien : seule la vérification sur le service officiel de la marque fait foi.
 */
enum class AuthVerdict { NO_PUBLIC_DATA, NO_URL, URL_OFFICIAL_DOMAIN, URL_UNRECOGNIZED_DOMAIN }

data class AuthAnalysis(
    val verdict: AuthVerdict,
    val urls: List<AnalyzedUrl>,
    val hasPublicData: Boolean,
)

object ProductAuthAnalyzer {
    fun analyze(snapshot: TagSnapshot): AuthAnalysis {
        val records = flatten(snapshot.ndefRecords).filter { it.kind != NdefRecordKind.EMPTY }
        val raw = linkedSetOf<String>()
        records.forEach { r ->
            r.uri?.let { raw += it }
            r.text?.let { raw += UrlSafety.findUrlsInText(it) }
        }
        val urls = raw.map { u ->
            val info = UrlSafety.parse(u)
            AnalyzedUrl(u, info, info?.let(BrandRegistry::match))
        }
        val verdict = when {
            records.isEmpty() -> AuthVerdict.NO_PUBLIC_DATA
            urls.isEmpty() -> AuthVerdict.NO_URL
            urls.any { it.brand != null } -> AuthVerdict.URL_OFFICIAL_DOMAIN
            else -> AuthVerdict.URL_UNRECOGNIZED_DOMAIN
        }
        return AuthAnalysis(verdict, urls, records.isNotEmpty())
    }

    private fun flatten(r: List<NdefRecordInfo>): List<NdefRecordInfo> = r.flatMap { listOf(it) + flatten(it.children) }
}
