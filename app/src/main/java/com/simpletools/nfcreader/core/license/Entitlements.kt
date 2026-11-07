package com.simpletools.nfcreader.core.license

/** Fonctions réservées à la version PRO. Les fonctions FREE ne passent jamais par ce contrôle. */
enum class ProFeature(val id: String) {
    WRITE_NFC("write"),
    TEMPLATES("templates"),
    HEX_VIEW("hex"),
    COMPARE("compare"),
    CSV_EXPORT("csv"),
    INVENTORY("inventory"),
    BATCH_WRITE("batch"),
    BACKUP("backup"),
}

/** Politique de licence : état local confirmé par Google Play, utilisable hors ligne. */
data class LicenseState(
    val owned: Boolean = false,
    /** Dernière vérification réussie auprès de Google Play (epoch ms), 0 si jamais. */
    val lastVerifiedAt: Long = 0L,
    val pending: Boolean = false,
) {
    val isPro: Boolean get() = owned
}

/** Résultat d'une vérification auprès de Google Play. */
sealed interface PlayCheck {
    /** Google Play a répondu : [owned] est la vérité actuelle (true = achat acquitté et actif). */
    data class Answered(val owned: Boolean, val pending: Boolean) : PlayCheck
    /** Google Play inaccessible (réseau, service indisponible) : on ne change PAS l'état local. */
    data class Unavailable(val reason: String) : PlayCheck
}

object LicensePolicy {
    /** Au-delà de ce délai sans vérification, l'accès PRO reste actif mais l'interface invite à restaurer. */
    const val REVERIFY_HINT_MS: Long = 30L * 24 * 3600 * 1000

    /** Applique le résultat d'une vérification. Une réponse « non possédé » révoque (remboursement). */
    fun apply(current: LicenseState, check: PlayCheck, now: Long): LicenseState = when (check) {
        is PlayCheck.Unavailable -> current
        is PlayCheck.Answered -> current.copy(owned = check.owned, pending = check.pending, lastVerifiedAt = now)
    }

    fun needsReverifyHint(state: LicenseState, now: Long): Boolean =
        state.owned && state.lastVerifiedAt > 0 && now - state.lastVerifiedAt > REVERIFY_HINT_MS

    fun canUse(state: LicenseState, feature: ProFeature): Boolean = state.isPro
}
