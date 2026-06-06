<?php

namespace App\Service;

use App\Entity\Evenement;

class EvenementManager
{
    public function validate(Evenement $evenement): bool
    {
        $titre = trim((string) $evenement->getTitre());
        if ($titre === '') {
            throw new \InvalidArgumentException('Le titre de l\'événement est obligatoire');
        }

        if (mb_strlen($titre) < 3) {
            throw new \InvalidArgumentException('Le titre doit contenir au minimum 3 caractères');
        }

        if (mb_strlen($titre) > 255) {
            throw new \InvalidArgumentException('Le titre ne peut pas dépasser 255 caractères');
        }

        $description = trim((string) $evenement->getDescription());
        if ($description === '') {
            throw new \InvalidArgumentException('La description est obligatoire');
        }

        if (mb_strlen($description) < 10) {
            throw new \InvalidArgumentException('La description doit contenir au minimum 10 caractères');
        }

        if ($evenement->getDateDebut() === null) {
            throw new \InvalidArgumentException('La date de début est obligatoire');
        }

        if ($evenement->getDateFin() === null) {
            throw new \InvalidArgumentException('La date de fin est obligatoire');
        }

        if ($evenement->getDateFin() <= $evenement->getDateDebut()) {
            throw new \InvalidArgumentException('La date de fin doit être après la date de début');
        }

        if ($evenement->getDateCreation() === null) {
            throw new \InvalidArgumentException('La date de creation est obligatoire');
        }

        if ($evenement->getType() === null) {
            throw new \InvalidArgumentException('Le type d\'événement est obligatoire');
        }

        if ($evenement->getImageCouverture() === null) {
            throw new \InvalidArgumentException('L\'image de couverture est obligatoire');
        }

        if ($evenement->getStatut() === null) {
            throw new \InvalidArgumentException('Le statut est obligatoire');
        }

        if ($evenement->getCapaciteMax() === null) {
            throw new \InvalidArgumentException('La capacité maximale est obligatoire');
        }

        if ($evenement->getCapaciteMax() < 1 || $evenement->getCapaciteMax() > 100000) {
            throw new \InvalidArgumentException('La capacité doit être entre 1 et 100000');
        }

        if ($evenement->getGalerie() === null) {
            throw new \InvalidArgumentException('La galerie est obligatoire');
        }

        if ($evenement->getArtiste() === null) {
            throw new \InvalidArgumentException('L\'artiste est obligatoire');
        }

        if ($evenement->getPrixTicket() === null) {
            throw new \InvalidArgumentException('Le prix du ticket est obligatoire');
        }

        if ($evenement->getPrixTicket() < 0.01 || $evenement->getPrixTicket() > 10000) {
            throw new \InvalidArgumentException('Le prix doit être entre 0.01€ et 10000€');
        }

        return true;
    }
}
