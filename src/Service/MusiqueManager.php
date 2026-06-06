<?php

namespace App\Service;

use App\Entity\Musique;

class MusiqueManager
{
    public function validate(Musique $musique): bool
    {
        if (empty($musique->getTitre())) {
            throw new \InvalidArgumentException('Le titre est obligatoire');
        }

        if (empty($musique->getDescription())) {
            throw new \InvalidArgumentException('La description est obligatoire');
        }

        if (mb_strlen(trim((string) $musique->getDescription())) < 10) {
            throw new \InvalidArgumentException('La description doit contenir au moins 10 caracteres');
        }

        if ($musique->getDateCreation() > new \DateTime()) {
            throw new \InvalidArgumentException('La date ne peut pas être dans le futur');
        }

        if ($musique->getType() === null) {
            throw new \InvalidArgumentException('Le type est obligatoire');
        }

        if ($musique->getCollection() === null) {
            throw new \InvalidArgumentException('La collection est obligatoire');
        }

        if ($musique->getGenre() === null) {
            throw new \InvalidArgumentException('Le genre est obligatoire');
        }

        if ($musique->getImage() === null) {
            throw new \InvalidArgumentException('L\'image de couverture est obligatoire');
        }

        if ($musique->getAudio() === null) {
            throw new \InvalidArgumentException('Le fichier audio est obligatoire');
        }

        return true;
    }
}
