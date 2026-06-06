<?php

namespace App\Tests\Service;

use App\Entity\User;
use App\Enum\Role;
use App\Enum\Statut;
use App\Service\UserManager;
use PHPUnit\Framework\TestCase;

class UserManagerTest extends TestCase
{
    private function createValidUser(): User
    {
        $user = new User();
        $user->setNom('Doe');
        $user->setPrenom('John');
        $user->setDateNaissance(new \DateTime('1995-01-01'));
        $user->setEmail('john.doe@example.com');
        $user->setMdp('secret123');
        $user->setRole(Role::AMATEUR);
        $user->setStatut(Statut::ACTIVE);
        $user->setDateInscription(new \DateTime());
        $user->setNumTel('91234567');
        $user->setVille('Tunis');

        return $user;
    }

    public function testValidUser()
    {
        $user = $this->createValidUser();

        $manager = new UserManager();

        $this->assertTrue($manager->validate($user));
    }

    public function testUserWithoutNom()
    {
        $this->expectException(\InvalidArgumentException::class);
        $this->expectExceptionMessage('Le nom est obligatoire');

        $user = $this->createValidUser();
        $user->setNom(null);

        $manager = new UserManager();

        $manager->validate($user);
    }

    public function testUserWithoutPrenom()
    {
        $this->expectException(\InvalidArgumentException::class);
        $this->expectExceptionMessage('Le prenom est obligatoire');

        $user = $this->createValidUser();
        $user->setPrenom(null);

        $manager = new UserManager();

        $manager->validate($user);
    }

    public function testUserWithNomTooShort()
    {
        $this->expectException(\InvalidArgumentException::class);
        $this->expectExceptionMessage('Le nom doit contenir au moins 2 caracteres');

        $user = $this->createValidUser();
        $user->setNom('A');

        $manager = new UserManager();

        $manager->validate($user);
    }

    public function testUserWithInvalidNomCharacters()
    {
        $this->expectException(\InvalidArgumentException::class);
        $this->expectExceptionMessage('Le nom ne doit contenir que des lettres, espaces, apostrophes et tirets');

        $user = $this->createValidUser();
        $user->setNom('Doe123');

        $manager = new UserManager();

        $manager->validate($user);
    }

    public function testUserWithPrenomTooShort()
    {
        $this->expectException(\InvalidArgumentException::class);
        $this->expectExceptionMessage('Le prenom doit contenir au moins 2 caracteres');

        $user = $this->createValidUser();
        $user->setPrenom('J');

        $manager = new UserManager();

        $manager->validate($user);
    }

    public function testUserWithInvalidPrenomCharacters()
    {
        $this->expectException(\InvalidArgumentException::class);
        $this->expectExceptionMessage('Le prenom ne doit contenir que des lettres, espaces, apostrophes et tirets');

        $user = $this->createValidUser();
        $user->setPrenom('John@');

        $manager = new UserManager();

        $manager->validate($user);
    }

    public function testUserWithFutureBirthDate()
    {
        $this->expectException(\InvalidArgumentException::class);
        $this->expectExceptionMessage('La date de naissance doit être dans le passé');

        $user = $this->createValidUser();

        $futureDate = new \DateTime();
        $futureDate->modify('+1 day');
        $user->setDateNaissance($futureDate);

        $manager = new UserManager();

        $manager->validate($user);
    }

    public function testUserWithTooOldBirthDate()
    {
        $this->expectException(\InvalidArgumentException::class);
        $this->expectExceptionMessage('La date de naissance n\'est pas valide');

        $user = $this->createValidUser();
        $user->setDateNaissance(new \DateTime('-121 years'));

        $manager = new UserManager();

        $manager->validate($user);
    }

    public function testUserWithoutEmail()
    {
        $this->expectException(\InvalidArgumentException::class);
        $this->expectExceptionMessage('L\'email est obligatoire');

        $user = $this->createValidUser();
        $user->setEmail(null);

        $manager = new UserManager();

        $manager->validate($user);
    }

    public function testUserWithInvalidEmail()
    {
        $this->expectException(\InvalidArgumentException::class);
        $this->expectExceptionMessage('L\'email n\'est pas valide');

        $user = $this->createValidUser();
        $user->setEmail('invalid-email');

        $manager = new UserManager();

        $manager->validate($user);
    }

    public function testUserWithTooLongEmail()
    {
        $this->expectException(\InvalidArgumentException::class);
        $this->expectExceptionMessage('L\'email ne peut pas depasser 255 caracteres');

        $user = $this->createValidUser();
        $user->setEmail(str_repeat('a', 250) . '@x.com');

        $manager = new UserManager();

        $manager->validate($user);
    }

    public function testUserWithoutPassword()
    {
        $this->expectException(\InvalidArgumentException::class);
        $this->expectExceptionMessage('Le mot de passe est obligatoire');

        $user = $this->createValidUser();
        $user->setMdp('');

        $manager = new UserManager();

        $manager->validate($user);
    }

    public function testUserWithoutRole()
    {
        $this->expectException(\InvalidArgumentException::class);
        $this->expectExceptionMessage('Le role est obligatoire');

        $user = $this->createValidUser();
        $user->setRole(null);

        $manager = new UserManager();

        $manager->validate($user);
    }

    public function testUserWithoutStatut()
    {
        $this->expectException(\InvalidArgumentException::class);
        $this->expectExceptionMessage('Le statut est obligatoire');

        $user = $this->createValidUser();
        $user->setStatut(null);

        $manager = new UserManager();

        $manager->validate($user);
    }

    public function testUserWithoutDateInscription()
    {
        $this->expectException(\InvalidArgumentException::class);
        $this->expectExceptionMessage('La date d\'inscription est obligatoire');

        $user = new User();
        $user->setNom('Doe');
        $user->setPrenom('John');
        $user->setDateNaissance(new \DateTime('1995-01-01'));
        $user->setEmail('john.doe@example.com');
        $user->setMdp('secret123');
        $user->setRole(Role::AMATEUR);
        $user->setStatut(Statut::ACTIVE);
        $user->setNumTel('91234567');
        $user->setVille('Tunis');

        $manager = new UserManager();

        $manager->validate($user);
    }

    public function testUserWithoutNumTel()
    {
        $this->expectException(\InvalidArgumentException::class);
        $this->expectExceptionMessage('Le numero de telephone est obligatoire');

        $user = $this->createValidUser();
        $user->setNumTel(null);

        $manager = new UserManager();

        $manager->validate($user);
    }

    public function testUserWithInvalidNumTel()
    {
        $this->expectException(\InvalidArgumentException::class);
        $this->expectExceptionMessage('Le numero de telephone doit contenir 8 chiffres et commencer par 2, 4, 5 ou 9');

        $user = $this->createValidUser();
        $user->setNumTel('11234567');

        $manager = new UserManager();

        $manager->validate($user);
    }

    public function testUserWithoutVille()
    {
        $this->expectException(\InvalidArgumentException::class);
        $this->expectExceptionMessage('La ville est obligatoire');

        $user = $this->createValidUser();
        $user->setVille(null);

        $manager = new UserManager();

        $manager->validate($user);
    }

    public function testUserWithVilleTooShort()
    {
        $this->expectException(\InvalidArgumentException::class);
        $this->expectExceptionMessage('La ville doit contenir au moins 2 caracteres');

        $user = $this->createValidUser();
        $user->setVille('A');

        $manager = new UserManager();

        $manager->validate($user);
    }

    public function testUserWithTooLongBiography()
    {
        $this->expectException(\InvalidArgumentException::class);
        $this->expectExceptionMessage('La biographie ne peut pas depasser 1000 caracteres');

        $user = $this->createValidUser();
        $user->setBiographie(str_repeat('a', 1001));

        $manager = new UserManager();

        $manager->validate($user);
    }
}
