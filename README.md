# ARTIUM

## Description

ARTIUM est une plateforme Symfony dédiée aux projets artistiques et culturels. Le projet permet aux artistes de publier des événements, des oeuvres et des collections, tandis que les autres utilisateurs peuvent consulter le contenu, interagir avec la plateforme et réserver des tickets. L'administration dispose d'un espace de gestion pour modérer les comptes, suivre les réclamations et administrer les contenus. Le projet intègre aussi des fonctionnalités de paiement, d'upload de fichiers, de génération de PDF et de recherche. Plusieurs services externes sont branchés via des variables d'environnement, notamment Stripe, Meilisearch, reCAPTCHA et des API d'IA. L'objectif est de pouvoir installer et lancer l'application rapidement sur une nouvelle machine avec une base de données prête à l'emploi.

## Technologies utilisées

Frontend : Twig, JavaScript, Symfony UX, Asset Mapper
Backend : PHP 8.1+, Symfony 6.4, Doctrine ORM, Doctrine Migrations
Base de données : MySQL

## Prérequis

- PHP 8.1+
- Composer
- Symfony CLI
- MySQL 8+ ou MariaDB compatible
- Docker (optionnel)

## Installation

1. Installer les dépendances PHP.

```bash
composer install
```

2. Créer le fichier d'environnement local.

```powershell
Copy-Item .env.example .env
```

3. Ouvrir `.env` et renseigner les clés nécessaires.

Le fichier `.env.example` est celui à partager ou à pousser sur Git.

4. Installer Ollama et télécharger les modèles utilisés par le projet.

Le projet utilise `nomic-embed-text` pour la recherche sémantique et `llama3.2:3b` pour l'estimation des tickets.

```bash
ollama pull nomic-embed-text
ollama pull llama3.2:3b
```

Si Ollama n'est pas encore lancé, démarrez-le avant de tester l'application.

## Services locaux à lancer

Avant de démarrer Symfony, vérifiez que ce service local est disponible sur la machine:

- Service de reconnaissance faciale: `http://127.0.0.1:8002/compare`

Si ce service n'est pas lancé, la connexion faciale ne fonctionnera pas correctement.

5. Préparer le service de reconnaissance faciale.

La connexion par Face ID n'importe pas un modèle directement dans Symfony. Le projet appelle un service Python externe sur `http://127.0.0.1:8002/compare` via [src/Service/FaceRecognitionService.php](src/Service/FaceRecognitionService.php). Il faut donc télécharger ce service localement sur la machine, installer ses dépendances, récupérer ou entraîner le modèle de reconnaissance faciale prévu par son projet, puis lancer le serveur Python pour le rendre disponible.

Exemple de préparation du service local :

```bash
git clone <url-du-service-face-id>
cd <dossier-du-service-face-id>
pip install -r requirements.txt
python app.py
```

Adaptez la commande de lancement au projet Python utilisé, mais le service doit rester accessible sur le port `8002` et exposer `POST /compare`.

Si vous ne démarrez pas ce service, la connexion faciale ne pourra pas fonctionner.

## Lancement

```bash
symfony server:start
```

Pour vérifier rapidement que les modèles sont bien installés :

```bash
ollama list
```

## Préparation de la base de données

1. Créer la base de données.

```bash
php bin/console doctrine:database:create
```

2. Appliquer les migrations.

```bash
php bin/console doctrine:migrations:migrate
```

3. Si le schéma a changé après une modification des entités, générer une nouvelle migration avant de la rejouer.

```bash
php bin/console make:migration
php bin/console doctrine:migrations:migrate
```

## Variables d'environnement

Voir [.env.example](.env.example).

## Connexion faciale

- Démarrer le service Python sur le port `8002`.
- Vérifier que l'endpoint `POST /compare` répond bien.
- Le projet Symfony envoie deux images au service pour comparaison.

## Démo

Vidéo : https://www.youtube.com/watch?v=LXt9yFnbeq4&list=PLaxA49z0jsugwN5JIb9uLEhbhtYCT0w2E&index=1&t=4s
