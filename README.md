# Kcalcul

Application Android native, en français, conçue pour suivre uniquement les calories ingérées et le poids. Android 8.0 ou version ultérieure. Aucune permission réseau, aucun compte, aucune publicité.

La version actuelle est **1.14**. Le header reste fixe pendant le défilement. Le compteur de kcal utilise une jauge circulaire, avec le nombre légèrement remonté. Tous les aperçus sont actualisés dans `artifacts/previews/`.

La création du profil affiche seulement les champs à renseigner et la validation. Le bloc de formule a également été retiré du profil.

La version 1.7 remplace le petit graphique du journal par l’historique des kcal de la journée sélectionnée : heure, quantité, saisies les plus récentes en premier, modification et suppression en touchant une ligne. Les courbes sont accessibles dans l’onglet Évolution. Les mesures de poids restent modifiables depuis le journal.

La version 1.2 est nommée **Kcalcul** et utilise une interface sombre, des accents cyan/violet et des libellés directs. Les aperçus Android actualisés couvrent la création du profil, le journal, le graphique, le profil, les niveaux d’activité et la saisie.

La version 1.3 ajoute le repère **un déficit accumulé de 7 700 kcal correspond environ à la perte de 1 kg de graisse corporelle**. Le journal affiche l’équivalent théorique des kcal restantes, en kg : `max(0, plafond − kcal consommées) / 7700`. Pour aujourd’hui, il est conditionnel à l’absence d’apport supplémentaire. Sans saisie de kcal, aucune perte n’est affichée. Un excédent ne produit pas une perte positive.

Dans Évolution, le déficit cumulé additionne les soldes signés des journées terminées et renseignées jusqu’à la date consultée. Les excédents compensent les déficits ; les jours sans calories, le jour courant et les dates futures sont exclus. Ce calcul utilise les poids historiques et le profil actuel et est recalculé après toute correction. Il suppose que toutes les calories des journées renseignées ont été saisies. Le résultat est un équivalent énergétique approximatif, distinct du poids mesuré : la [règle linéaire ne prédit pas précisément la perte réelle](https://www.niddk.nih.gov/health-information/professionals/diabetes-discoveries-practice/nih-body-weight-planner).

## Utilisation

- Au premier lancement, renseigner le genre (Femme ou Homme, obligatoire), l’âge, la taille en cm, le poids initial en kg et le niveau d’activité.
- Dans **Journal**, ajouter les kcal ingérées. La date affichée et l’heure actuelle sont proposées ; toucher la date ou l’heure pour les modifier.
- Toucher la carte du poids pour ajouter une mesure.
- Le journal permet de corriger une valeur, sa date ou son heure et de supprimer une saisie après confirmation.
- Les flèches et le calendrier donnent accès aux journées précédentes.
- **Évolution** affiche les deux courbes sur le même graphique, sur 7 ou 30 jours jusqu’à la journée sélectionnée. L’axe de gauche est en kcal, celui de droite en kg. Toucher un jour pour lire ses valeurs.
- L’icône du profil permet de corriger le genre, l’âge, la taille et le niveau d’activité.

Le plafond utilise la formule de Mifflin–St Jeor et le facteur d’activité sélectionné :

`((10 × poids en kg) + (6,25 × taille en cm) − (5 × âge) + coefficient du genre) × FAP`

Le coefficient vaut **+5 pour Homme** et **−161 pour Femme**, selon la [publication de Mifflin–St Jeor](https://pubmed.ncbi.nlm.nih.gov/2305711/). La formule fournit une estimation des besoins énergétiques.

| Niveau d’activité | FAP |
| --- | --- |
| Sédentaire | 1,2 |
| Légèrement actif | 1,375 |
| Modérément actif | 1,55 |
| Très actif | 1,725 |
| Extrêmement actif | 2,0 |

Les descriptions du quotidien sont accessibles dans le choix d’activité. Le plafond et les kcal restantes sont recalculés immédiatement après l’enregistrement, la modification ou la suppression d’une mesure de poids, ou après une modification du profil. Aucun plafond calculé n’est stocké en cache. Lors d’une consultation historique, une mesure de poids postérieure à la journée consultée n’est pas utilisée ; les informations actuelles du profil (âge, taille, activité) s’appliquent.

La version 1.11 rend le genre obligatoire, sans sélection par défaut. Les profils existants contenant « Non renseigné » sont renvoyés au formulaire ; aucune estimation n’est affichée avant leur correction. La mise à jour conserve le profil et l’historique. La migration du stockage 1.0 reste prise en charge. Les anciens profils restent sur « Sédentaire » (1,2) jusqu’à modification. Installer le nouvel APK par-dessus la version précédente, sans la désinstaller.

Le poids utilisé est la dernière mesure connue à la date consultée, avec le poids initial comme référence si aucune mesure ne précède cette date. Les calories sont cumulées par journée civile. Le plafond est arrondi uniquement pour son affichage ; le calcul conserve ses décimales.

Un dépassement affiche les kcal au-delà du plafond. Les jours sans saisie ne sont pas assimilés à zéro sur le graphique. Les points de poids représentent la dernière mesure réellement enregistrée de chaque jour ; les lignes relient les points disponibles.

## Stockage

Le profil et les saisies sont stockés dans une base SQLite privée à l’application sur le téléphone. Les données survivent à la fermeture et au redémarrage. Les dates conservent le jour et l’heure locale choisis, même après un changement de fuseau horaire. La sauvegarde Android et le transfert automatique vers un autre appareil sont désactivés. Désinstaller l’application ou effacer ses données efface l’historique.

## Installation

Ouvrir `artifacts/Kcalcul.apk` sur le téléphone, puis autoriser l’installation depuis la source utilisée si Android le demande. Cet APK est signé avec une clé de développement et convient à une installation personnelle ; il n’est pas préparé pour une publication sur le Play Store.

## Compilation

Ouvrir ce dossier dans Android Studio, installer le SDK 36 et les Build Tools 35.0.0, puis lancer l’application sur un téléphone ou un émulateur. Le projet utilise Java 17, Gradle 8.13 et le plugin Android 8.11.1 ([compatibilité officielle](https://developer.android.com/build/releases/agp-8-11-0-release-notes)).

Avec le SDK installé et `JAVA_HOME` pointant vers un JDK 21 (nécessaire aux tests Android 16, le code de l’application reste compilé en Java 17) :

```sh
./gradlew assembleDebug testDebugUnitTest lintDebug
```

Android Studio configure `local.properties` pour le chemin du SDK. L’APK produit est `app/build/outputs/apk/debug/app-debug.apk`.

## Vérification

Les tests vérifient la formule, les nombres décimaux français, les limites de journée, les poids historiques, la persistance après réouverture de SQLite, l’édition et la suppression, le rollback du profil en cas d’échec, le parcours de première saisie sur les API 26 et 36 et la conservation d’une saisie lors d’une rotation. Le [moteur de test Android 16 demande Java 21](https://robolectric.org/compatibility_table/). Les images dans `artifacts/previews/` sont rendues à partir des vues Android avec Robolectric ; les données de démonstration sont uniquement dans les tests et ne sont jamais préchargées dans l’application. Ces vérifications ne remplacent pas un essai sur un téléphone physique.

Le code de l’application utilise exclusivement le SDK Android. JUnit et Robolectric sont des dépendances de test uniquement.
