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
