<?php

namespace App\Service;

use App\Entity\Reclamation;

class ReclamationManager
{
    public function validate(Reclamation $reclamation): bool
    {
        if ($reclamation->getUser() === null) {
            throw new \InvalidArgumentException('L\'utilisateur est obligatoire');
        }

        $texte = trim((string) $reclamation->getTexte());
        if ($texte === '') {
            throw new \InvalidArgumentException('Le texte de la reclamation est obligatoire');
        }

        if (mb_strlen($texte) < 10) {
            throw new \InvalidArgumentException('Le texte doit contenir au minimum 10 caracteres');
        }

        if (mb_strlen($texte) > 2000) {
            throw new \InvalidArgumentException('Le texte ne peut pas depasser 2000 caracteres');
        }

        if ($reclamation->getDateCreation() === null) {
            throw new \InvalidArgumentException('La date de creation est obligatoire');
        }

        if ($reclamation->getDateCreation() > new \DateTime()) {
            throw new \InvalidArgumentException('La date de creation ne peut pas être dans le futur');
        }

        if ($reclamation->getStatut() === null) {
            throw new \InvalidArgumentException('Le statut est obligatoire');
        }

        if ($reclamation->getType() === null) {
            throw new \InvalidArgumentException('Le type de reclamation est obligatoire');
        }

        return true;
    }
}
