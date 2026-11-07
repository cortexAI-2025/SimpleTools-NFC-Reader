# Confidentialité — SimpleTools NFC Reader

- **Aucun serveur, aucun compte, aucune publicité, aucune télémétrie.**
- **Aucune permission réseau** : l'APK ne déclare pas `INTERNET`. Il ne peut techniquement pas envoyer de données.
- Seule permission Android déclarée : `android.permission.NFC` (permission normale, sans invite).
- Les lectures ne sont **jamais enregistrées automatiquement** : l'utilisateur doit appuyer sur « Enregistrer ».
- L'historique est stocké localement (Room/SQLite). Sauvegarde cloud et transfert d'appareil sont désactivés
  (`allowBackup=false`, règles d'extraction vides).
- L'UID est facultatif à l'enregistrement et à l'export (case « Inclure l'identifiant »). Un réglage permet de masquer
  les identifiants à l'écran.
- Les exports (PDF/JSON) sont créés dans le cache privé de l'app et partagés via `FileProvider` (accès temporaire en lecture
  accordé au seul destinataire choisi). « Effacer » supprime l'historique, les exports temporaires, la dernière lecture
  et les préférences.
- Le contenu des puces est traité comme **non fiable** : texte brut uniquement, caractères de contrôle supprimés,
  aucune URL ouverte sans confirmation explicite (seuls http/https sont ouvrables), aucun filtre d'intent NFC
  (l'app ne s'ouvre jamais seule sur une puce).
- Aucun UID n'est écrit dans les logs.
- Le presse-papiers reçoit le contenu avec le drapeau « sensible » (Android 13+).


## Mise à jour V1.3
- **Écriture NFC (PRO)** : le contenu écrit est saisi par l'utilisateur et écrit sur le tag. Le mot de passe Wi-Fi est demandé à chaque écriture et n'est **jamais enregistré** (ni dans l'historique, ni dans les modèles, ni dans les sauvegardes).
- **Modèles, inventaire, historique** : stockés localement dans la base de l'application. Effacés par « Effacer toutes les données locales ».
- **Sauvegarde locale (PRO)** : le fichier JSON est créé ou lu à l'emplacement choisi par l'utilisateur (Storage Access Framework). Il contient les lectures, modèles et inventaire, et peut contenir des UID si l'utilisateur les a enregistrés. Il n'est jamais envoyé automatiquement.
- **Achat PRO** : géré par Google Play (SDK Google Play Billing), avec la permission `com.android.vending.BILLING`. L'application ne reçoit que l'état de l'achat et n'envoie rien à un serveur de l'éditeur. Il n'existe aucun serveur.
- **Exports CSV (PRO)** : l'UID n'est inclus que si l'utilisateur coche la case. Les valeurs commençant par `=`, `+`, `-` ou `@` sont préfixées d'une apostrophe pour éviter l'exécution de formules dans un tableur.
