<?php

declare(strict_types=1);

namespace DoctrineMigrations;

use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

/**
 * Auto-generated Migration: Please modify to your needs!
 */
final class Version20260512101600 extends AbstractMigration
{
    public function getDescription(): string
    {
        return '';
    }

    public function up(Schema $schema): void
    {
        // this up() migration is auto-generated, please modify it to your needs
        $this->addSql('CREATE TABLE collections (id INT AUTO_INCREMENT NOT NULL, titre VARCHAR(255) NOT NULL, description LONGTEXT NOT NULL, artiste_id INT NOT NULL, INDEX IDX_D325D3EE21D25844 (artiste_id), PRIMARY KEY (id)) DEFAULT CHARACTER SET utf8mb4');
        $this->addSql('CREATE TABLE commentaire (id INT AUTO_INCREMENT NOT NULL, texte VARCHAR(255) NOT NULL, date_commentaire DATE NOT NULL, user_id INT NOT NULL, oeuvre_id INT NOT NULL, INDEX IDX_67F068BCA76ED395 (user_id), INDEX IDX_67F068BC88194DE8 (oeuvre_id), PRIMARY KEY (id)) DEFAULT CHARACTER SET utf8mb4');
        $this->addSql('CREATE TABLE evenement (id INT AUTO_INCREMENT NOT NULL, titre VARCHAR(255) NOT NULL, description LONGTEXT NOT NULL, date_debut DATETIME NOT NULL, date_fin DATETIME NOT NULL, date_creation DATE NOT NULL, type VARCHAR(255) NOT NULL, image_couverture VARCHAR(2048) NOT NULL, statut VARCHAR(255) NOT NULL, capacite_max INT NOT NULL, prix_ticket DOUBLE PRECISION NOT NULL, galerie_id INT NOT NULL, artiste_id INT NOT NULL, INDEX IDX_B26681E825396CB (galerie_id), INDEX IDX_B26681E21D25844 (artiste_id), PRIMARY KEY (id)) DEFAULT CHARACTER SET utf8mb4');
        $this->addSql('CREATE TABLE galerie (id INT AUTO_INCREMENT NOT NULL, nom VARCHAR(255) NOT NULL, adresse VARCHAR(255) NOT NULL, localisation VARCHAR(255) NOT NULL, description VARCHAR(255) DEFAULT NULL, capacite_max INT NOT NULL, PRIMARY KEY (id)) DEFAULT CHARACTER SET utf8mb4');
        $this->addSql('CREATE TABLE `like` (id INT AUTO_INCREMENT NOT NULL, liked TINYINT NOT NULL, user_id INT DEFAULT NULL, oeuvre_id INT DEFAULT NULL, INDEX IDX_AC6340B3A76ED395 (user_id), INDEX IDX_AC6340B388194DE8 (oeuvre_id), PRIMARY KEY (id)) DEFAULT CHARACTER SET utf8mb4');
        $this->addSql('CREATE TABLE livre (categorie VARCHAR(255) NOT NULL, prix_location DOUBLE PRECISION NOT NULL, fichier_pdf VARCHAR(255) NOT NULL, id INT NOT NULL, PRIMARY KEY (id)) DEFAULT CHARACTER SET utf8mb4');
        $this->addSql('CREATE TABLE location_livre (id INT AUTO_INCREMENT NOT NULL, date_debut DATETIME NOT NULL, etat VARCHAR(255) NOT NULL, nombre_de_jours INT DEFAULT NULL, user_id INT NOT NULL, livre_id INT NOT NULL, INDEX IDX_DA3F0ABCA76ED395 (user_id), INDEX IDX_DA3F0ABC37D925CB (livre_id), PRIMARY KEY (id)) DEFAULT CHARACTER SET utf8mb4');
        $this->addSql('CREATE TABLE musique (genre VARCHAR(255) NOT NULL, audio VARCHAR(255) DEFAULT NULL, updated_at DATETIME DEFAULT NULL, id INT NOT NULL, PRIMARY KEY (id)) DEFAULT CHARACTER SET utf8mb4');
        $this->addSql('CREATE TABLE oeuvre (id INT AUTO_INCREMENT NOT NULL, titre VARCHAR(255) NOT NULL, description LONGTEXT NOT NULL, date_creation DATE NOT NULL, image VARCHAR(2048) NOT NULL, is_public TINYINT NOT NULL, type VARCHAR(255) NOT NULL, embedding JSON DEFAULT NULL, image_embedding JSON DEFAULT NULL, collection_id INT NOT NULL, classe VARCHAR(255) NOT NULL, INDEX IDX_35FE2EFE514956FD (collection_id), PRIMARY KEY (id)) DEFAULT CHARACTER SET utf8mb4');
        $this->addSql('CREATE TABLE oeuvre_user (oeuvre_id INT NOT NULL, user_id INT NOT NULL, INDEX IDX_7A340C0F88194DE8 (oeuvre_id), INDEX IDX_7A340C0FA76ED395 (user_id), PRIMARY KEY (oeuvre_id, user_id)) DEFAULT CHARACTER SET utf8mb4');
        $this->addSql('CREATE TABLE playlist (id INT AUTO_INCREMENT NOT NULL, nom VARCHAR(255) NOT NULL, description VARCHAR(255) DEFAULT NULL, date_creation DATE NOT NULL, image VARCHAR(255) DEFAULT NULL, user_id INT NOT NULL, INDEX IDX_D782112DA76ED395 (user_id), PRIMARY KEY (id)) DEFAULT CHARACTER SET utf8mb4');
        $this->addSql('CREATE TABLE playlist_musique (playlist_id INT NOT NULL, musique_id INT NOT NULL, INDEX IDX_512241A66BBD148 (playlist_id), INDEX IDX_512241A625E254A1 (musique_id), PRIMARY KEY (playlist_id, musique_id)) DEFAULT CHARACTER SET utf8mb4');
        $this->addSql('CREATE TABLE reclamation (id INT AUTO_INCREMENT NOT NULL, texte LONGTEXT NOT NULL, date_creation DATE NOT NULL, statut VARCHAR(255) NOT NULL, type VARCHAR(255) NOT NULL, file_name VARCHAR(255) DEFAULT NULL, updated_at DATETIME NOT NULL, is_archived TINYINT NOT NULL, user_id INT NOT NULL, INDEX IDX_CE606404A76ED395 (user_id), PRIMARY KEY (id)) DEFAULT CHARACTER SET utf8mb4');
        $this->addSql('CREATE TABLE reponse (id INT AUTO_INCREMENT NOT NULL, contenu LONGTEXT NOT NULL, date_reponse DATE NOT NULL, reclamation_id INT NOT NULL, user_admin_id INT DEFAULT NULL, INDEX IDX_5FB6DEC72D6BA2D9 (reclamation_id), INDEX IDX_5FB6DEC784A66610 (user_admin_id), PRIMARY KEY (id)) DEFAULT CHARACTER SET utf8mb4');
        $this->addSql('CREATE TABLE ticket (id INT AUTO_INCREMENT NOT NULL, code_qr VARCHAR(255) NOT NULL, date_achat DATE NOT NULL, is_used TINYINT DEFAULT NULL, used_at DATETIME DEFAULT NULL, scan_count INT DEFAULT NULL, evenement_id INT NOT NULL, user_id INT NOT NULL, UNIQUE INDEX UNIQ_97A0ADA35115D31F (code_qr), INDEX IDX_97A0ADA3FD02F13 (evenement_id), INDEX IDX_97A0ADA3A76ED395 (user_id), PRIMARY KEY (id)) DEFAULT CHARACTER SET utf8mb4');
        $this->addSql('CREATE TABLE user (photo_profil VARCHAR(255) DEFAULT NULL, reset_token VARCHAR(255) DEFAULT NULL, reset_token_expires DATETIME DEFAULT NULL, id INT AUTO_INCREMENT NOT NULL, nom VARCHAR(255) NOT NULL, prenom VARCHAR(255) NOT NULL, date_naissance DATE NOT NULL, email VARCHAR(255) NOT NULL, mdp VARCHAR(255) NOT NULL, role VARCHAR(255) NOT NULL, statut VARCHAR(255) NOT NULL, date_inscription DATE NOT NULL, num_tel VARCHAR(255) NOT NULL, ville VARCHAR(255) NOT NULL, biographie LONGTEXT DEFAULT NULL, specialite VARCHAR(255) DEFAULT NULL, centre_interet LONGTEXT DEFAULT NULL, photo_reference_path VARCHAR(255) DEFAULT NULL, PRIMARY KEY (id)) DEFAULT CHARACTER SET utf8mb4');
        $this->addSql('CREATE TABLE user_connection (id INT AUTO_INCREMENT NOT NULL, connected_at DATETIME NOT NULL, ip_address VARCHAR(45) NOT NULL, user_agent VARCHAR(255) DEFAULT NULL, user_id INT NOT NULL, INDEX IDX_8E90B58AA76ED395 (user_id), PRIMARY KEY (id)) DEFAULT CHARACTER SET utf8mb4');
        $this->addSql('CREATE TABLE messenger_messages (id BIGINT AUTO_INCREMENT NOT NULL, body LONGTEXT NOT NULL, headers LONGTEXT NOT NULL, queue_name VARCHAR(190) NOT NULL, created_at DATETIME NOT NULL, available_at DATETIME NOT NULL, delivered_at DATETIME DEFAULT NULL, INDEX IDX_75EA56E0FB7336F0E3BD61CE16BA31DBBF396750 (queue_name, available_at, delivered_at, id), PRIMARY KEY (id)) DEFAULT CHARACTER SET utf8mb4');
        $this->addSql('ALTER TABLE collections ADD CONSTRAINT FK_D325D3EE21D25844 FOREIGN KEY (artiste_id) REFERENCES user (id)');
        $this->addSql('ALTER TABLE commentaire ADD CONSTRAINT FK_67F068BCA76ED395 FOREIGN KEY (user_id) REFERENCES user (id)');
        $this->addSql('ALTER TABLE commentaire ADD CONSTRAINT FK_67F068BC88194DE8 FOREIGN KEY (oeuvre_id) REFERENCES oeuvre (id)');
        $this->addSql('ALTER TABLE evenement ADD CONSTRAINT FK_B26681E825396CB FOREIGN KEY (galerie_id) REFERENCES galerie (id)');
        $this->addSql('ALTER TABLE evenement ADD CONSTRAINT FK_B26681E21D25844 FOREIGN KEY (artiste_id) REFERENCES user (id)');
        $this->addSql('ALTER TABLE `like` ADD CONSTRAINT FK_AC6340B3A76ED395 FOREIGN KEY (user_id) REFERENCES user (id)');
        $this->addSql('ALTER TABLE `like` ADD CONSTRAINT FK_AC6340B388194DE8 FOREIGN KEY (oeuvre_id) REFERENCES oeuvre (id)');
        $this->addSql('ALTER TABLE livre ADD CONSTRAINT FK_AC634F99BF396750 FOREIGN KEY (id) REFERENCES oeuvre (id) ON DELETE CASCADE');
        $this->addSql('ALTER TABLE location_livre ADD CONSTRAINT FK_DA3F0ABCA76ED395 FOREIGN KEY (user_id) REFERENCES user (id)');
        $this->addSql('ALTER TABLE location_livre ADD CONSTRAINT FK_DA3F0ABC37D925CB FOREIGN KEY (livre_id) REFERENCES livre (id)');
        $this->addSql('ALTER TABLE musique ADD CONSTRAINT FK_EE1D56BCBF396750 FOREIGN KEY (id) REFERENCES oeuvre (id) ON DELETE CASCADE');
        $this->addSql('ALTER TABLE oeuvre ADD CONSTRAINT FK_35FE2EFE514956FD FOREIGN KEY (collection_id) REFERENCES collections (id)');
        $this->addSql('ALTER TABLE oeuvre_user ADD CONSTRAINT FK_7A340C0F88194DE8 FOREIGN KEY (oeuvre_id) REFERENCES oeuvre (id) ON DELETE CASCADE');
        $this->addSql('ALTER TABLE oeuvre_user ADD CONSTRAINT FK_7A340C0FA76ED395 FOREIGN KEY (user_id) REFERENCES user (id) ON DELETE CASCADE');
        $this->addSql('ALTER TABLE playlist ADD CONSTRAINT FK_D782112DA76ED395 FOREIGN KEY (user_id) REFERENCES user (id)');
        $this->addSql('ALTER TABLE playlist_musique ADD CONSTRAINT FK_512241A66BBD148 FOREIGN KEY (playlist_id) REFERENCES playlist (id) ON DELETE CASCADE');
        $this->addSql('ALTER TABLE playlist_musique ADD CONSTRAINT FK_512241A625E254A1 FOREIGN KEY (musique_id) REFERENCES musique (id) ON DELETE CASCADE');
        $this->addSql('ALTER TABLE reclamation ADD CONSTRAINT FK_CE606404A76ED395 FOREIGN KEY (user_id) REFERENCES user (id)');
        $this->addSql('ALTER TABLE reponse ADD CONSTRAINT FK_5FB6DEC72D6BA2D9 FOREIGN KEY (reclamation_id) REFERENCES reclamation (id)');
        $this->addSql('ALTER TABLE reponse ADD CONSTRAINT FK_5FB6DEC784A66610 FOREIGN KEY (user_admin_id) REFERENCES user (id)');
        $this->addSql('ALTER TABLE ticket ADD CONSTRAINT FK_97A0ADA3FD02F13 FOREIGN KEY (evenement_id) REFERENCES evenement (id)');
        $this->addSql('ALTER TABLE ticket ADD CONSTRAINT FK_97A0ADA3A76ED395 FOREIGN KEY (user_id) REFERENCES user (id)');
        $this->addSql('ALTER TABLE user_connection ADD CONSTRAINT FK_8E90B58AA76ED395 FOREIGN KEY (user_id) REFERENCES user (id)');
    }

    public function down(Schema $schema): void
    {
        // this down() migration is auto-generated, please modify it to your needs
        $this->addSql('ALTER TABLE collections DROP FOREIGN KEY FK_D325D3EE21D25844');
        $this->addSql('ALTER TABLE commentaire DROP FOREIGN KEY FK_67F068BCA76ED395');
        $this->addSql('ALTER TABLE commentaire DROP FOREIGN KEY FK_67F068BC88194DE8');
        $this->addSql('ALTER TABLE evenement DROP FOREIGN KEY FK_B26681E825396CB');
        $this->addSql('ALTER TABLE evenement DROP FOREIGN KEY FK_B26681E21D25844');
        $this->addSql('ALTER TABLE `like` DROP FOREIGN KEY FK_AC6340B3A76ED395');
        $this->addSql('ALTER TABLE `like` DROP FOREIGN KEY FK_AC6340B388194DE8');
        $this->addSql('ALTER TABLE livre DROP FOREIGN KEY FK_AC634F99BF396750');
        $this->addSql('ALTER TABLE location_livre DROP FOREIGN KEY FK_DA3F0ABCA76ED395');
        $this->addSql('ALTER TABLE location_livre DROP FOREIGN KEY FK_DA3F0ABC37D925CB');
        $this->addSql('ALTER TABLE musique DROP FOREIGN KEY FK_EE1D56BCBF396750');
        $this->addSql('ALTER TABLE oeuvre DROP FOREIGN KEY FK_35FE2EFE514956FD');
        $this->addSql('ALTER TABLE oeuvre_user DROP FOREIGN KEY FK_7A340C0F88194DE8');
        $this->addSql('ALTER TABLE oeuvre_user DROP FOREIGN KEY FK_7A340C0FA76ED395');
        $this->addSql('ALTER TABLE playlist DROP FOREIGN KEY FK_D782112DA76ED395');
        $this->addSql('ALTER TABLE playlist_musique DROP FOREIGN KEY FK_512241A66BBD148');
        $this->addSql('ALTER TABLE playlist_musique DROP FOREIGN KEY FK_512241A625E254A1');
        $this->addSql('ALTER TABLE reclamation DROP FOREIGN KEY FK_CE606404A76ED395');
        $this->addSql('ALTER TABLE reponse DROP FOREIGN KEY FK_5FB6DEC72D6BA2D9');
        $this->addSql('ALTER TABLE reponse DROP FOREIGN KEY FK_5FB6DEC784A66610');
        $this->addSql('ALTER TABLE ticket DROP FOREIGN KEY FK_97A0ADA3FD02F13');
        $this->addSql('ALTER TABLE ticket DROP FOREIGN KEY FK_97A0ADA3A76ED395');
        $this->addSql('ALTER TABLE user_connection DROP FOREIGN KEY FK_8E90B58AA76ED395');
        $this->addSql('DROP TABLE collections');
        $this->addSql('DROP TABLE commentaire');
        $this->addSql('DROP TABLE evenement');
        $this->addSql('DROP TABLE galerie');
        $this->addSql('DROP TABLE `like`');
        $this->addSql('DROP TABLE livre');
        $this->addSql('DROP TABLE location_livre');
        $this->addSql('DROP TABLE musique');
        $this->addSql('DROP TABLE oeuvre');
        $this->addSql('DROP TABLE oeuvre_user');
        $this->addSql('DROP TABLE playlist');
        $this->addSql('DROP TABLE playlist_musique');
        $this->addSql('DROP TABLE reclamation');
        $this->addSql('DROP TABLE reponse');
        $this->addSql('DROP TABLE ticket');
        $this->addSql('DROP TABLE user');
        $this->addSql('DROP TABLE user_connection');
        $this->addSql('DROP TABLE messenger_messages');
    }
}
