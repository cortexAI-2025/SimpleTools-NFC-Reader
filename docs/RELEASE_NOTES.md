# Notes de version — SimpleTools NFC Reader

## V1.3.0 — Version FREE / PRO

### Français
**Nouveautés PRO (achat unique 2,99 €)**
- Écriture NFC : texte, URL, contact (vCard) et Wi-Fi (WPA2 ou réseau ouvert), avec vérification par relecture.
- Confirmation explicite avant toute écriture ; demande de remplacement si le tag contient déjà des données.
- Écriture en série assistée : un tag à la fois, confirmation et vérification de chaque écriture, rapport final.
- Bibliothèque de modèles réutilisables, par catégorie, avec duplication.
- Vue hexadécimale des données accessibles, comparaison de deux lectures, export CSV.
- Inventaire de tags : collections, catégories, notes, recherche et export.
- Sauvegarde et restauration locales dans un fichier choisi par l'utilisateur.
- Écran « SimpleTools NFC Reader PRO » avec comparatif FREE / PRO, achat, restauration des achats et état de licence.

**Inchangé (FREE, gratuit à vie)**
- Lecture NFC, UID, technologies, type et capacité, lecture seule / inscriptible, historique, copie, partage, exports PDF et JSON, authentification produit, hors ligne.

**Corrections et technique**
- Base de données : migration V1 → V2 ; les lectures existantes sont conservées.
- Permission ajoutée : `BILLING` (achat intégré). Aucune permission réseau.

**Limites**
- L'écriture n'est pas garantie : tags verrouillés, trop petits ou retirés en cours d'opération sont refusés ou signalés.
- Le Wi-Fi suit le format WPA2-Personnel ou ouvert ; la connexion automatique dépend du téléphone.
- Les achats PRO ne peuvent être testés qu'avec un compte testeur Google Play.

### English
**New PRO features (one-time purchase, €2.99)**
- NFC writing: text, URL, contact (vCard) and Wi-Fi (WPA2 or open network), verified by read-back.
- Explicit confirmation before any write; a replacement prompt when the tag already holds data.
- Assisted batch writing: one tag at a time, confirmation and verification of each write, final report.
- Reusable template library, by category, with duplication.
- Hexadecimal view of accessible data, comparison of two readings, CSV export.
- Tag inventory: collections, categories, notes, search and export.
- Local backup and restore to a file chosen by the user.
- "SimpleTools NFC Reader PRO" screen with a FREE / PRO comparison, purchase, purchase restore and licence status.

**Unchanged (FREE, free forever)**
- NFC reading, UID, technologies, type and capacity, read-only / writable state, history, copy, sharing, PDF and JSON exports, product authentication, offline use.

**Fixes and technical**
- Database: V1 → V2 migration; existing readings are kept.
- Permission added: `BILLING` (in-app purchase). No network permission.

**Limits**
- Writing is not guaranteed: locked, too small or removed tags are refused or reported.
- Wi-Fi follows WPA2-Personal or open networks; automatic connection depends on the phone.
- PRO purchases can only be tested with a Google Play licence-tester account.
