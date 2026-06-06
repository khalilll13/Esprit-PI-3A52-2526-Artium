<?php

namespace App\Service;

use App\Entity\User;

class UserManager
{
    public function validate(User $user): bool
    {
        $nom = trim((string) $user->getNom());
        if ($nom === '') {
            throw new \InvalidArgumentException('Le nom est obligatoire');
        }

        if (mb_strlen($nom) < 2) {
            throw new \InvalidArgumentException('Le nom doit contenir au moins 2 caracteres');
        }

        if (mb_strlen($nom) > 255) {
            throw new \InvalidArgumentException('Le nom ne peut pas depasser 255 caracteres');
        }

        if (preg_match('/^[a-zA-ZÀ-ÿ\s\'-]+$/u', $nom) !== 1) {
            throw new \InvalidArgumentException('Le nom ne doit contenir que des lettres, espaces, apostrophes et tirets');
        }

        $prenom = trim((string) $user->getPrenom());
        if ($prenom === '') {
            throw new \InvalidArgumentException('Le prenom est obligatoire');
        }

        if (mb_strlen($prenom) < 2) {
            throw new \InvalidArgumentException('Le prenom doit contenir au moins 2 caracteres');
        }

        if (mb_strlen($prenom) > 255) {
            throw new \InvalidArgumentException('Le prenom ne peut pas depasser 255 caracteres');
        }

        if (preg_match('/^[a-zA-ZÀ-ÿ\s\'-]+$/u', $prenom) !== 1) {
            throw new \InvalidArgumentException('Le prenom ne doit contenir que des lettres, espaces, apostrophes et tirets');
        }

        $dateNaissance = $user->getDateNaissance();
        if ($dateNaissance === null) {
            throw new \InvalidArgumentException('La date de naissance est obligatoire');
        }

        $today = new \DateTime('today');
        if ($dateNaissance >= $today) {
            throw new \InvalidArgumentException('La date de naissance doit être dans le passé');
        }

        $maxOldDate = (new \DateTime('today'))->modify('-120 years');
        if ($dateNaissance <= $maxOldDate) {
            throw new \InvalidArgumentException('La date de naissance n\'est pas valide');
        }

        $email = trim((string) $user->getEmail());
        if ($email === '') {
            throw new \InvalidArgumentException('L\'email est obligatoire');
        }

        if (mb_strlen($email) > 255) {
            throw new \InvalidArgumentException('L\'email ne peut pas depasser 255 caracteres');
        }

        if (filter_var($email, FILTER_VALIDATE_EMAIL) === false) {
            throw new \InvalidArgumentException('L\'email n\'est pas valide');
        }

        if (empty($user->getMdp())) {
            throw new \InvalidArgumentException('Le mot de passe est obligatoire');
        }

        if ($user->getRole() === null) {
            throw new \InvalidArgumentException('Le role est obligatoire');
        }

        if ($user->getStatut() === null) {
            throw new \InvalidArgumentException('Le statut est obligatoire');
        }

        if ($user->getDateInscription() === null) {
            throw new \InvalidArgumentException('La date d\'inscription est obligatoire');
        }

        $numTel = trim((string) $user->getNumTel());
        if ($numTel === '') {
            throw new \InvalidArgumentException('Le numero de telephone est obligatoire');
        }

        if (preg_match('/^[2459]\d{7}$/', $numTel) !== 1) {
            throw new \InvalidArgumentException('Le numero de telephone doit contenir 8 chiffres et commencer par 2, 4, 5 ou 9');
        }

        $ville = trim((string) $user->getVille());
        if ($ville === '') {
            throw new \InvalidArgumentException('La ville est obligatoire');
        }

        if (mb_strlen($ville) < 2) {
            throw new \InvalidArgumentException('La ville doit contenir au moins 2 caracteres');
        }

        if (mb_strlen($ville) > 255) {
            throw new \InvalidArgumentException('La ville ne peut pas depasser 255 caracteres');
        }

        if ($user->getBiographie() !== null && mb_strlen($user->getBiographie()) > 1000) {
            throw new \InvalidArgumentException('La biographie ne peut pas depasser 1000 caracteres');
        }

        return true;
    }
}
