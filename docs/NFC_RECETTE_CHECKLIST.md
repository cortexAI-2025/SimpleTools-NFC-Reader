# Checklist de recette NFC — SimpleTools NFC Reader V1.3

> Les tests automatiques (JVM, Robolectric, instrumenté PDF) utilisent des **fixtures et des faux tags**. Ils ne valident
> pas le matériel. Cette checklist doit être exécutée sur un téléphone **avec NFC** (Samsung Galaxy A14 sous Android 15,
> si la variante possède le NFC) et sur plusieurs tags physiques. Noter pour chaque ligne : OK / KO / non testé.

## A. Matériel et état NFC
| # | Test | Attendu |
|---|---|---|
| A1 | Téléphone sans NFC (variante) | Message « NFC indisponible », aucune lecture simulée, pas de crash |
| A2 | NFC désactivé | Message « NFC désactivé » et bouton vers les réglages ; retour → le lecteur redevient actif |
| A3 | Activer le NFC pendant que l'écran est ouvert | Le lecteur s'active sans relancer l'application |

## B. Lecture (FREE)
| # | Tag | Attendu |
|---|---|---|
| B1 | NDEF texte (NTAG213/215) | Texte, langue, UID, technologies, capacité, lecture seule / inscriptible |
| B2 | NDEF URL | URL affichée, « Ouvrir » demande confirmation, rien ne s'ouvre seul |
| B3 | Tag non NDEF (badge MIFARE Classic ou carte) | Technologies et UID, « aucune donnée NDEF », aucune tentative de clé |
| B4 | Tag vierge formatable | Indication « formatable NDEF », aucune écriture |
| B5 | Tag retiré très vite | Pas de crash ; lecture partielle signalée ou rien |
| B6 | Rotation et retour en arrière-plan | Résultat conservé, lecteur réactivé |

## C. Écriture (PRO) — uniquement avec un tag **non verrouillé**
| # | Test | Attendu |
|---|---|---|
| C1 | Écrire un texte sur un tag vierge | Confirmation, écriture, relecture, « écrit et vérifié » |
| C2 | Écrire une URL https | Idem ; URL relue identique |
| C3 | Écrire un contact (vCard) | Contenu relu ; lecture dans l'application : type text/vcard |
| C4 | Écrire un Wi-Fi WPA2 | Relecture du format ; le mot de passe n'apparaît jamais dans les écrans ni l'historique |
| C5 | Tag contenant déjà du NDEF | Affichage du contenu existant ; écriture seulement après « Remplacer le contenu » |
| C6 | Tag **verrouillé** (en écriture) | Refus « Tag verrouillé », aucune modification |
| C7 | Tag **trop petit** pour le contenu | Refus « contenu trop volumineux », aucune modification |
| C8 | Tag retiré pendant l'écriture | Échec signalé (pas de succès affiché) ; relire le tag puis recommencer |
| C9 | Annuler la confirmation | Aucune écriture |
| C10 | Série de 3 contenus | Un tag à la fois, confirmation de chacun, rapport final avec succès / échecs |

## D. Modèles, inventaire, historique (PRO)
| # | Test | Attendu |
|---|---|---|
| D1 | Créer, dupliquer, modifier, supprimer un modèle | Persistance après fermeture de l'application |
| D2 | « Utiliser » un modèle | L'écran d'écriture est pré-rempli ; le mot de passe Wi-Fi est redemandé |
| D3 | Ajouter un tag à une collection | Élément visible, recherche par nom, UID et catégorie |
| D4 | Export CSV (avec et sans UID) | Fichier ouvert dans un tableur ; sans UID : aucun UID dans le fichier |
| D5 | Comparer deux lectures | Champs identiques / différents correctement affichés |
| D6 | Vue hexadécimale | Octets affichés ; mention de troncature à 256 octets si besoin |

## E. Achats et licence (piste de test Google Play)
| # | Test | Attendu |
|---|---|---|
| E1 | Version FREE : ouvrir une fonction PRO | Écran verrouillé avec « Débloquer PRO » ; aucune écriture possible |
| E2 | Achat de test (compte testeur) | Prix réel affiché ; accès PRO activé après acquittement |
| E3 | Achat en attente | Indication « en attente », pas d'accès tant que non confirmé |
| E4 | Annulation de l'achat | Aucun changement, aucun message d'erreur bloquant |
| E5 | Mode avion après achat | Accès PRO conservé |
| E6 | Restauration après réinstallation | « Achat PRO restauré » |
| E7 | Remboursement de test | Accès PRO retiré à la vérification suivante en ligne |

## F. Données et mise à jour
| # | Test | Attendu |
|---|---|---|
| F1 | Installer V1.0, créer des lectures, puis mettre à jour vers V1.3 | Lectures conservées (migration de base) |
| F2 | Sauvegarde locale puis restauration sur base vide | Lectures, modèles et inventaire identiques |
| F3 | « Effacer toutes les données locales » | Historique, modèles, inventaire supprimés ; licence conservée |
| F4 | Mode avion complet | Toutes les fonctions FREE et PRO fonctionnent hors ligne (hors achat) |

## G. Compte rendu à fournir
Modèle exact du téléphone (Réglages › À propos), version Android, version de l'application, tags utilisés (fabricant et référence),
résultat ligne par ligne, captures d'écran des erreurs.
