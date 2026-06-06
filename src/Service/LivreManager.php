<?php

namespace App\Service;

use App\Entity\Livre;

class LivreManager
{
    public function validate(Livre $livre): bool
    {
        if (empty($livre->getTitre())) {
            throw new \InvalidArgumentException('Le titre est obligatoire');
        }

        if (empty($livre->getDescription())) {
            throw new \InvalidArgumentException('La description est obligatoire');
        }

        if (mb_strlen(trim((string) $livre->getDescription())) < 10) {
            throw new \InvalidArgumentException('La description doit contenir au moins 10 caracteres');
        }

        if ($livre->getDateCreation() > new \DateTime()) {
            throw new \InvalidArgumentException('La date ne peut pas être dans le futur');
        }

        if ($livre->getType() === null) {
            throw new \InvalidArgumentException('Le type est obligatoire');
        }

        if ($livre->getCollection() === null) {
            throw new \InvalidArgumentException('La collection est obligatoire');
        }

        if ($livre->getImage() === null) {
            throw new \InvalidArgumentException('L\'image de couverture est obligatoire');
        }

        if (empty($livre->getCategorie())) {
            throw new \InvalidArgumentException('La categorie est obligatoire');
        }

        $categorie = trim((string) $livre->getCategorie());
        if (mb_strlen($categorie) < 2) {
            throw new \InvalidArgumentException('La categorie doit contenir au moins 2 caracteres');
        }

        if (mb_strlen($categorie) > 255) {
            throw new \InvalidArgumentException('La categorie ne peut pas depasser 255 caracteres');
        }

        if ($livre->getPrixLocation() === null) {
            throw new \InvalidArgumentException('Le prix est obligatoire');
        }

        if ($livre->getPrixLocation() <= 0) {
            throw new \InvalidArgumentException('Le prix doit être supérieur à 0');
        }

        if ($livre->getFichierPdf() === null) {
            throw new \InvalidArgumentException('Le fichier PDF est obligatoire');
        }

        return true;
    }
}
