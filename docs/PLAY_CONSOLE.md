# Google Play Console — préparation (à réaliser par l'éditeur)

> Ce document liste les étapes **à effectuer dans la Play Console**. Rien de ce qui suit n'a été configuré automatiquement
> ni n'a été vérifié depuis l'environnement de développement : les identifiants, prix et déclarations doivent être saisis
> et validés par l'éditeur.

## 1. Application
- Nom : **SimpleTools NFC Reader**. Éditeur : **AIWORKPAY**. Collection : SimpleTools.
- Identifiant d'application (package) : `com.simpletools.nfcreader` (déjà défini dans `app/build.gradle.kts`).
- Langues de la fiche : français (par défaut) et anglais.
- Gratuite, avec achats intégrés.

## 2. Produit PRO (achat intégré)
- Type : **Produit géré** (achat unique, non consommable).
- Identifiant du produit : **`simpletools_nfc_pro_lifetime`** (identique à `PRO_PRODUCT_ID` dans le code).
- Prix : **2,99 € TTC** pour la France. Le prix des autres pays est défini par l'éditeur dans la Play Console.
- Statut : **Actif** avant toute validation en piste de test.

## 3. Signature
- Activer **Play App Signing**. Google gère la clé de signature de distribution.
- Clé d'envoi (upload key) : créée par l'éditeur. **Elle n'est jamais dans le dépôt.**
- Le dépôt produit un AAB **non signé** (`SimpleTools-NFC-Reader-release-unsigned-aab` dans les artefacts CI).
  Pour l'envoyer, il faut le signer avec la clé d'envoi, soit en local avec les variables :
  `SIMPLETOOLS_KEYSTORE_PATH`, `SIMPLETOOLS_KEYSTORE_PASSWORD`, `SIMPLETOOLS_KEY_ALIAS`, `SIMPLETOOLS_KEY_PASSWORD`,
  soit avec `jarsigner`/`apksigner` selon la procédure Google.

## 4. Déclarations obligatoires
- **Politique de confidentialité** : URL publique requise. Le contenu de référence est `docs/PRIVACY.md`, à publier par l'éditeur.
- **Sécurité des données** : déclarer
  - aucune donnée collectée ni partagée par l'éditeur (pas de serveur) ;
  - achats gérés par Google Play (SDK Billing) ;
  - aucune donnée chiffrée en transit par l'application (aucun accès réseau de l'application elle-même).
- **Accès aux permissions NFC** : permission normale, aucune déclaration sensible requise.
- **Public cible et contenu** : application utilitaire, sans contenu généré par les utilisateurs, sans publicité.
- **Annonces** : déclarer « Aucune publicité ».

## 5. Tests de facturation
- Ajouter les comptes testeurs dans **Configuration › Testeurs de licence**.
- Tester l'achat, la restauration, le remboursement et les achats en attente sur une piste **interne** avant publication.
- Les testeurs de licence ne sont pas facturés ; les achats de test sont annulés automatiquement après quelques minutes.

## 6. Piste et publication
- Commencer par une **piste interne** (APK/AAB signé via Play App Signing).
- Une piste ouverte ou de production ne doit être ouverte qu'après la **recette NFC sur un téléphone réel** (voir `docs/NFC_RECETTE_CHECKLIST.md`).

## 7. Points non vérifiés depuis l'environnement de développement
- Création réelle du produit, prix et disponibilité par pays.
- Achat réel sur un compte Google Play (impossible sans Play Store et sans compte).
- Déclarations de confidentialité et de sécurité des données.
