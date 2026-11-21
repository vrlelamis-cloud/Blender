# Blender — éditeur multimédia (Kotlin / Jetpack Compose / Media3)

Vidéo, image et son dans une seule application Android (minSdk 29).

## Compiler
1. Poussez ce dossier sur un dépôt GitHub (branche `main`).
2. L'onglet **Actions** lance `Build Blender APK` (Gradle 8.9, JDK 17).
3. Téléchargez l'artefact `Blender-apk` et installez l'APK.

## Fonctions incluses
- Communes : annuler/rétablir (500 niveaux), édition non destructive (seuls les paramètres sont stockés), sauvegarde automatique des projets, presets en un tap, mode Simple/Avancé, export en plusieurs formats/qualités.
- Vidéo : multi-clips, découpe, scission, réordonnancement, fusion, vitesse 0,25x–4x, filtres + réglages, rotation, formats 9:16 / 1:1 / 16:9, titre, coupure du son, export H.264/AAC.
- Image : filtres, recadrage par ratio, rotation/miroir, luminosité, contraste, saturation, chaleur, netteté, vignette, texte, export JPEG/PNG/WEBP.
- Son : forme d'onde, découpe, rognage des silences, vitesse, changement de voix (hauteur), volume, fondus, enregistrement micro, export M4A.

## Feuille de route (nécessite des modèles IA / DSP dédiés)
Sous-titres automatiques, stabilisation, suppression d'arrière-plan/objets, détection du BPM, réduction de bruit, égaliseur/compresseur, transitions, keyframes, calques/masques.
