# Procédure de validation matérielle — Samsung Galaxy A14 (Android 15)

> Procédure détaillée V1.3 : voir aussi [NFC_RECETTE_CHECKLIST.md](NFC_RECETTE_CHECKLIST.md) (couvre écriture, modèles, inventaire et achats).

> Les tests automatiques (fixtures, Robolectric) **ne valident pas le matériel NFC**. Cette procédure est nécessaire
> avant tout « GO validation matérielle ».

## 0. Prérequis
- Un Galaxy A14 **avec NFC** (certaines variantes du A14 n'en ont pas) : Réglages › Connexions › NFC et paiements sans contact.
- Plusieurs tags physiques : un tag **NDEF texte**, un tag **NDEF URL** (NTAG213/215), un tag **non NDEF** (ex. MIFARE Classic
  ou carte de transport/badge), un tag **vierge**, et si possible une étiquette de vêtement.
- Récupérer l'APK : GitHub › Actions › dernier run vert › artefact **SimpleTools-NFC-Reader-debug-apk**.

## 1. Installation
1. Copier `app-debug.apk` sur le téléphone, autoriser « Installer des applis inconnues » pour le gestionnaire de fichiers.
2. Installer puis ouvrir. Vérifier : pas de crash au démarrage, l'écran d'accueil s'affiche.
3. Réglages › Applications › SimpleTools NFC Reader › Autorisations : **aucune** autorisation à accorder.

## 2. Matrice de tests
| # | Test | Attendu |
|---|------|---------|
| 1 | Scanner avec NFC **désactivé** | Message « NFC désactivé » + bouton vers les réglages ; retour → le scanner devient actif sans relancer |
| 2 | Scan d'un tag **NDEF texte** | Texte + langue, UID hex/décimal, technologies, capacité NDEF, état lecture seule |
| 3 | Scan d'un tag **NDEF URL** | URL affichée ; « Ouvrir le lien… » demande confirmation, **rien ne s'ouvre seul** |
| 4 | Scan d'un tag **non NDEF** (MIFARE Classic, badge) | Technologies + UID, « aucune donnée NDEF » ; aucune tentative d'authentification/clé |
| 5 | Scan d'un tag **vierge** | « formatable en NDEF » sans écriture |
| 6 | Retirer le tag **très vite** | Pas de crash ; soit lecture partielle avec avertissement, soit rien |
| 7 | **Rotation** pendant/après un scan | Le résultat reste affiché |
| 8 | **Home puis retour** (arrière-plan) | Le scanner se réactive, le dernier résultat est conservé |
| 9 | Enregistrer › Historique › renommer, rechercher (nom, UID), supprimer, tout effacer | Fonctionne ; rien n'est enregistré sans action |
| 10 | Export **PDF** puis **JSON** (avec / sans UID) | La feuille de partage Android s'ouvre ; fichiers lisibles ; sans UID → aucun UID dans le fichier |
| 11 | Authentification produit sur une étiquette Moncler / autre | Affiche URL et domaine, **jamais** « authentique » ; le bouton officiel demande confirmation avant d'ouvrir https://code.moncler.com/ |
| 12 | Thème clair/sombre, langue FR/EN | Appliqués immédiatement |
| 13 | Mode avion + réseau coupé | Tout fonctionne (l'app n'utilise pas Internet) |

## 3. Compte rendu à fournir
Pour chaque ligne : OK/KO, modèle exact (Réglages › À propos), version Android, tags utilisés (référence), captures d'écran.
