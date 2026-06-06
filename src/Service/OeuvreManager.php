<?php

namespace App\Service;

use App\Entity\Oeuvre;

class OeuvreManager
{
    public function validate(Oeuvre $oeuvre): bool
    {
        if (empty($oeuvre->getTitre())) {
            throw new \InvalidArgumentException('Le titre est obligatoire');
        }

        if (empty($oeuvre->getDescription())) {
            throw new \InvalidArgumentException('La description est obligatoire');
        }

        if (mb_strlen(trim((string) $oeuvre->getDescription())) < 10) {
            throw new \InvalidArgumentException('La description doit contenir au moins 10 caracteres');
        }

        if ($oeuvre->getDateCreation() > new \DateTime()) {
            throw new \InvalidArgumentException('La date ne peut pas être dans le futur');
        }

        if ($oeuvre->getType() === null) {
            throw new \InvalidArgumentException('Le type est obligatoire');
        }

        if ($oeuvre->getCollection() === null) {
            throw new \InvalidArgumentException('La collection est obligatoire');
        }

        return true;
    }
}