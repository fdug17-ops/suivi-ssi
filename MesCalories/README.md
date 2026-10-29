# Mes Calories

Application Android personnelle de suivi des calories et des apports nutritionnels, inspirée de Yazio.
Sans publicité, sans IA, sans compte : tout reste sur le téléphone.

## Fonctionnalités

- **Journal du jour** : petit-déjeuner, déjeuner, dîner, collations ; anneau des calories restantes,
  barres glucides / protéines / lipides, navigation jour par jour, « copier le repas d'hier ».
- **Objectif personnalisé** : calcul du métabolisme de base (Mifflin-St Jeor), de la dépense journalière
  selon l'activité, et de l'objectif selon le but (perte, maintien, prise) et le rythme choisi.
  Objectif manuel possible, répartition des macros réglable (préréglages équilibré, protéiné, low-carb, céto).
- **Base d'aliments intégrée** (~230 aliments courants, valeurs pour 100 g, avec portions usuelles),
  recherche sans accents.
- **Open Food Facts** : recherche en ligne et **scan des codes-barres** (base libre et gratuite).
- **Mes aliments** : création/modification d'aliments, favoris, aliments récents.
- **Recettes** : liste d'ingrédients, nombre de portions, poids cuit ; valeurs calculées par portion.
- **Ajout rapide** de calories, **activités physiques** (calcul par MET ou saisie libre), **suivi de l'eau**.
- **Progrès** : courbe de poids avec objectif, IMC, historique des calories sur 7 j / 30 j / 3 mois, moyennes.
- **Sauvegarde** : export / import de toutes les données dans un fichier JSON.

## Installer l'application

Chaque modification poussée sur GitHub déclenche la compilation (GitHub Actions, workflow
« Mes Calories (APK Android) »). L'APK est publié dans la release **mescalories-latest** du dépôt :

1. Sur le téléphone, ouvrez la page *Releases* du dépôt et téléchargez `MesCalories.apk`.
2. Autorisez l'installation depuis le navigateur / gestionnaire de fichiers si Android le demande.
3. Les versions suivantes s'installent par-dessus sans perdre les données, **à condition** que la clé
   de signature soit configurée (voir ci-dessous).

### Clé de signature (à faire une fois)

Pour que chaque nouvelle version garde la même signature, la clé est fournie au build via deux
secrets du dépôt (*Settings → Secrets and variables → Actions → New repository secret*) :

- `MESCALORIES_KEYSTORE_BASE64` : le fichier `.jks` encodé en base64 ;
- `MESCALORIES_KEYSTORE_PASSWORD` : son mot de passe (alias de la clé : `mescalories`).

Sans ces secrets, l'APK est signé avec une clé de debug temporaire : il s'installe, mais une mise à
jour obligera à désinstaller l'application (pensez alors à exporter vos données avant).

## Compiler soi-même

Android Studio (ou JDK 17 + SDK Android) puis :

```
./gradlew assembleRelease
```

L'APK se trouve dans `app/build/outputs/apk/release/`.

## Technique

Kotlin, Jetpack Compose (Material 3), Room, DataStore, Navigation Compose, ZXing pour le scanner.
Android 8.0 minimum.
