# ARTIUM

## Description

ARTIUM est une application de bureau collaborative développée en JavaFX pour les projets artistiques et culturels dans le cadre du projet PIDEV. L'application propose des espaces distincts selon les rôles utilisateurs : l'administration dispose d'un espace de gestion globale (supervision des comptes, modération des contenus et suivi des réclamations), les artistes peuvent publier et administrer leurs oeuvres, collections et musiques, tandis que les amateurs peuvent parcourir les galeries, louer et lire des livres, générer des playlists intelligentes par IA, et acheter des tickets d'événements. Le projet intègre des fonctionnalités avancées de paiement (Stripe), d'envoi de SMS (Twilio), de génération de PDF (Apache PDFBox) et de recherche sémantique locale. La configuration et la gestion des clés d'API et secrets du projet sont centralisées de manière sécurisée dans un fichier `.env` unique.

## Technologies utilisées

Frontend : JavaFX (FXML, CSS)
Backend : Java 17, Maven, jBCrypt, Apache PDFBox, Stripe SDK, Twilio SDK, Apache HttpClient, org.json
Base de données : MySQL

## Prérequis

- JDK 17+ (configuré avec `JAVA_HOME`)
- Maven 3.8+
- Serveur MySQL 8+ ou MariaDB compatible
- Ollama (pour la recherche sémantique locale)
- Whisper CLI (optionnel, pour la transcription d'audios)

## Installation

1. Installer les dépendances Java avec Maven.

```bash
mvn clean install -DskipTests
```

2. Créer le fichier d'environnement local.

```powershell
Copy-Item .env.example .env
```

3. Ouvrir `.env` et renseigner les clés nécessaires (Stripe, Twilio, Gemini, Google Client ID/Secret, SMTP, etc.).

Le fichier `.env.example` est celui à partager ou à pousser sur Git.

4. Installer Ollama et télécharger le modèle utilisé par le projet.

Le projet utilise `nomic-embed-text` pour la recherche sémantique locale des événements.

```bash
ollama pull nomic-embed-text
```

Si Ollama n'est pas encore lancé, démarrez-le avant de tester la recherche sémantique.

5. Télécharger et préparer le modèle Vosk (pour la reconnaissance vocale).

L'application intègre une fonctionnalité de commande vocale (Speech-to-Text) qui utilise Vosk en local. En raison de sa taille, le modèle linguistique n'est pas inclus dans le repository Git.
- Téléchargez le modèle français de taille réduite (ex: `vosk-model-small-fr-0.22`) depuis le site [Alphacephei Vosk Models](https://alphacephei.com/vosk/models).
- Créez le dossier `src/main/resources/vosk-model-fr` s'il n'existe pas.
- Extrayez-y le contenu du modèle téléchargé (de façon à ce que le dossier `am`, le fichier `README` et les autres fichiers du modèle se trouvent directement sous `src/main/resources/vosk-model-fr`).

## Services locaux à lancer

Avant de démarrer l'application, vérifiez que ces services locaux sont disponibles sur la machine:

- Serveur MySQL local (généralement sur le port `3306`)
- Serveur Ollama local (généralement sur le port `11434`)

## Lancement

```bash
mvn clean javafx:run
```

Pour démarrer en ligne de commande ou via un script, vous pouvez utiliser :

```bash
run.bat
```

## Préparation de la base de données

1. Assurez-vous que votre serveur MySQL est démarré.
2. Créez la base de données `artium_db` via votre outil préféré (ex: phpMyAdmin ou CLI MySQL).
3. Renseignez les identifiants d'accès dans le fichier `.env` (`DB_URL`, `DB_USER`, `DB_PASSWORD`).
4. Les tables de l'application seront initialisées ou utilisées directement par les controleurs JDBC du projet.

## Variables d'environnement

Voir [.env.example](.env.example).
Toutes les clés d'API, secrets Google/Stripe et identifiants SMTP doivent résider **uniquement** dans le fichier `.env` à la racine du projet. Aucun autre fichier de propriétés local ne doit contenir de clés d'API ou de secrets.

## Recherche sémantique locale

La recherche d'événements dans le module amateur utilise un modèle d'embedding sémantique local via Ollama (`nomic-embed-text:latest`). Elle effectue une recherche en temps réel lors de la saisie, trie les événements par score de similarité cosinus par rapport au prompt de l'utilisateur, et affiche un score de pertinence sur 10 directly sur les cartes d'événements.

## Démo

Vidéo : https://www.youtube.com/watch?v=LXt9yFnbeq4&list=PLaxA49z0jsugwN5JIb9uLEhbhtYCT0w2E&index=1&t=4s
