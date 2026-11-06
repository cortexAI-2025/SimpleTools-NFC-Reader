package com.simpletools.nfcreader.core.reader

/**
 * Point d'extension pour de futurs lecteurs (V2+). En V1, seul le NFC intégré est réellement
 * pris en charge. Les lecteurs externes ci-dessous sont VOLONTAIREMENT sans implémentation :
 * aucun protocole matériel n'est inventé. Ils seront ajoutés après choix d'un appareil
 * disposant d'une documentation technique fiable.
 *
 * Rappel : les puces animales ISO 11784/11785 fonctionnent à 134,2 kHz, le NFC d'un smartphone
 * à 13,56 MHz : le téléphone ne peut pas les lire seul.
 */
enum class ReaderKind(val frequency: String) {
    BUILT_IN_NFC("13,56 MHz"),
    BLUETOOTH_RFID("134,2 kHz (lecteur externe)"),
    USB_C_RFID("134,2 kHz (lecteur externe)"),
}

enum class ReaderSupport { SUPPORTED, PLANNED }

interface ReaderSource {
    val kind: ReaderKind
    val support: ReaderSupport
}

object ReaderCatalog {
    val all: List<ReaderSource> = listOf(
        object : ReaderSource { override val kind = ReaderKind.BUILT_IN_NFC; override val support = ReaderSupport.SUPPORTED },
        object : ReaderSource { override val kind = ReaderKind.BLUETOOTH_RFID; override val support = ReaderSupport.PLANNED },
        object : ReaderSource { override val kind = ReaderKind.USB_C_RFID; override val support = ReaderSupport.PLANNED },
    )
}
