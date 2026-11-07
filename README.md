# SimpleTools NFC Reader

> **V1.3** — version FREE gratuite à vie + version PRO (achat unique 2,99 €). Voir [docs/MONETIZATION.md](docs/MONETIZATION.md).

Lecteur NFC Android **en lecture seule** de l'écosystème **SimpleTools by AIWORKPAY**.
Gratuit · sans publicité · sans compte · sans abonnement · 100 % local · aucune télémétrie · **aucune permission Internet**.

- Package : `com.simpletools.nfcreader` · Kotlin · Jetpack Compose (Material 3) · MVVM · Room · DataStore
- minSdk 26 · targetSdk 35 (Android 15) · cible de test : Samsung Galaxy A14
- Langues : français (défaut) / anglais · thèmes clair / sombre / système

## Fonctions FREE (gratuit à vie)

Lecture NFC, UID, technologies, type et capacité, lecture seule / inscriptible, historique, copie, partage, exports PDF et JSON, authentification produit, hors ligne.

## Fonctions PRO (achat unique 2,99 €)

Écriture NFC (texte, URL, contact, Wi-Fi) avec vérification, écriture en série, modèles, vue hexadécimale, comparaison, export CSV, inventaire, sauvegarde locale.

## Fonctions détaillées (V1.0)
| Fonction | Détail |
|---|---|
| Scanner NFC | `NfcAdapter.enableReaderMode` (NFC-A/B/F/V), détection NFC absent / désactivé (+ accès aux réglages), animation, résultats immédiats |
| Informations techniques | Technologies Android, UID hex/décimal, type NFC, normes ISO, capacité NDEF, lecture seule, ATQA/SAK, MIFARE Classic, fabricant (indicatif, UID 7 octets uniquement) |
| Contenu NDEF | Texte, URL (table de préfixes complète), MIME, Smart Poster, type externe, binaire (hexa tronqué) |
| Authentification produit | Extrait les URL publiques, reconnaît strictement les domaines officiels (Moncler : `code.moncler.com`), propose d'ouvrir le service officiel **après confirmation**. Ne dit jamais « authentique » |
| Historique | Room, enregistrement **volontaire**, renommer, notes, recherche, suppression, tout effacer, masquage des UID |
| Export | PDF (`PdfDocument`), JSON, copie presse-papiers (marquée sensible), partage Android via `FileProvider` |
| Extension future | `core/reader/ReaderSource` : NFC intégré / RFID Bluetooth / RFID USB-C (non implémentés, aucun protocole inventé) |

Ce que l'app **ne fait pas** : écriture, clonage, récupération de clés, contournement de contrôle d'accès, ouverture automatique de liens, transmission de données.

## Architecture
Module Gradle unique `app` (pas de multi-module : coût > intérêt) organisé par packages :

```
app/src/main/java/com/simpletools/nfcreader/
├── core/models/     TagSnapshot, NdefRecordInfo…
├── core/nfc/        TagAccess (abstraction), AndroidTagAccess, TagInterpreter, NdefParser, UrlSafety
├── core/reader/     ReaderSource (extension RFID animaux)
├── core/database/   Room (ReadingEntity/Dao) + HistoryRepository
├── core/settings/   DataStore
├── core/export/     SnapshotJson, ReportBuilder, PdfReportWriter, ExportService
├── feature/{scanner,tagdetails,history,authentication,settings,home,info}
└── ui/              thème Material 3 + composants
```
`TagAccess` isole l'API `android.nfc` : toute la logique de lecture est testable en JVM avec des fixtures.

## Build
```bash
./gradlew lintDebug testDebugUnitTest assembleDebug   # APK : app/build/outputs/apk/debug/app-debug.apk
./gradlew bundleRelease                               # AAB release NON signé (signature : voir docs/PLAY_CONSOLE.md)
./gradlew connectedDebugAndroidTest                   # test PDF réel (émulateur/appareil)
```
JDK 17, Android SDK 35. CI : `.github/workflows/ci.yml` (lint, tests unitaires, APK debug en artefact, test instrumenté PDF sur émulateur).

## Tests
Voir [docs/NFC_RECETTE_CHECKLIST.md](docs/NFC_RECETTE_CHECKLIST.md) pour la recette matérielle (FREE et PRO), [docs/TESTING_GALAXY_A14.md](docs/TESTING_GALAXY_A14.md) pour la procédure de base, et [docs/PLAY_CONSOLE.md](docs/PLAY_CONSOLE.md) pour la publication. Les tests automatiques utilisent des
**fixtures simulées** : ils ne valident pas le matériel NFC.
Confidentialité : [docs/PRIVACY.md](docs/PRIVACY.md).

## Limites connues (V1)
- Puces animales ISO 11784/11785 (134,2 kHz) : illisibles par le NFC 13,56 MHz d'un téléphone ; lecteur externe prévu en version ultérieure.
- Le modèle exact des NTAG/Ultralight et la mémoire totale ne sont pas déductibles des API Android : non affichés plutôt qu'inventés.
- APK signé debug uniquement. L'AAB release est produit non signé : la signature de publication et la configuration Play Console sont à faire par l'éditeur.
- Achats PRO : testables uniquement sur une piste Google Play avec compte testeur ; non vérifiables hors Play Store.
- Non affilié à Moncler ni à aucune marque.
