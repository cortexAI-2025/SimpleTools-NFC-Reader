# Modèle économique — SimpleTools NFC Reader

## Principe
- **FREE** : gratuit à vie. Aucune limite quotidienne de lecture.
- **PRO** : **achat unique de 2,99 € TTC** (prix cible, affiché au prix réel renvoyé par Google Play).
- Sans abonnement, sans publicité, sans compte obligatoire, fonctionnement hors ligne.
- Identifiant du produit Google Play : `simpletools_nfc_pro_lifetime` (produit **non consommable**).

## Répartition des fonctions

| Fonction | FREE | PRO |
|---|:-:|:-:|
| Détection des tags NFC compatibles, technologies, UID, type et capacité | ✓ | ✓ |
| Lecture NDEF (texte, URL, contacts compatibles), indicateur lecture seule / inscriptible | ✓ | ✓ |
| Historique local, copie, partage, export PDF et JSON | ✓ | ✓ |
| Authentification produit (ouverture volontaire d'un lien vérifié) | ✓ | ✓ |
| Écriture NFC : texte, URL, contact, Wi-Fi WPA2 ou ouvert | — | ✓ |
| Réécriture de tags NDEF compatibles, vérification après écriture | — | ✓ |
| Écriture en série assistée (un tag à la fois, confirmation de chaque écriture) | — | ✓ |
| Bibliothèque de modèles (catégories, duplication, réutilisation) | — | ✓ |
| Vue hexadécimale des données accessibles | — | ✓ |
| Comparaison de deux lectures | — | ✓ |
| Export CSV | — | ✓ |
| Inventaire de tags (collections, notes, recherche, export) | — | ✓ |
| Sauvegarde et restauration locales (fichier JSON choisi par l'utilisateur) | — | ✓ |

**Règle de principe :** aucune fonction FREE existante n'a été retirée ou restreinte lors du passage en V1.3.

## Politique de licence (implémentée dans `core/license/`)

1. **Source de vérité : Google Play.** Une licence PRO n'existe que si Google Play renvoie un achat à l'état `PURCHASED`. Aucun mode « test » ou « démo » n'accorde la licence en production.
2. **Acquittement.** Un achat non acquitté est acquitté immédiatement. Si l'acquittement échoue, l'accès **n'est pas** accordé (sans acquittement, Google rembourse après 3 jours).
3. **Achat en attente** (`PENDING`) : pas d'accès tant que Google Play ne confirme pas ; l'état « en attente » est affiché.
4. **Hors ligne.** L'état PRO est stocké localement (DataStore). Une absence de réseau ou une erreur Google Play **ne retire jamais** un accès déjà acquis. La vérification est relancée à chaque démarrage et à chaque restauration.
5. **Remboursement / révocation.** Si, lors d'une vérification réussie, Google Play ne renvoie plus l'achat, l'accès PRO est retiré.
6. **Restauration.** Le bouton « Restaurer mes achats » relance la vérification, par exemple après réinstallation ou sur un nouvel appareil.
7. **Indication de revérification.** Après 30 jours sans vérification, l'écran PRO invite à restaurer (l'accès n'est pas retiré pour autant).
8. **Effacement des données locales.** « Effacer toutes les données locales » supprime historique, modèles, inventaire et préférences. **La licence n'est pas effacée** : elle appartient au compte Google et sera revérifiée.

## Données et confidentialité liées à l'achat
- Le paiement et la gestion de l'achat sont réalisés par Google Play. SimpleTools NFC Reader **n'envoie aucune donnée d'achat à un serveur de l'éditeur** : il n'existe pas de serveur.
- Le SDK Google Play Billing communique avec Google Play selon les conditions de Google. Cela est indiqué dans la déclaration « Sécurité des données » (voir `docs/PLAY_CONSOLE.md`).

## Limites assumées
- Le contenu écrit sur un tag n'est pas garanti : le tag peut être retiré, verrouillé ou trop petit. L'application le signale et vérifie par relecture, mais ne peut pas garantir l'écriture matérielle.
- L'écriture Wi-Fi suit le format WFA Simple Configuration (WPA2-Personnel AES ou réseau ouvert). La connexion automatique dépend du téléphone et de sa version d'Android.
