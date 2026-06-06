<?php

namespace App\Tests\Service;

use App\Entity\Reclamation;
use App\Entity\User;
use App\Enum\StatutReclamation;
use App\Enum\TypeReclamation;
use App\Service\ReclamationManager;
use PHPUnit\Framework\TestCase;

class ReclamationManagerTest extends TestCase
{
    private function createValidReclamation(): Reclamation
    {
        $reclamation = new Reclamation();
        $reclamation->setUser(new User());
        $reclamation->setTexte('Ce texte est suffisamment long pour être valide.');
        $reclamation->setDateCreation(new \DateTime());
        $reclamation->setStatut(StatutReclamation::NON_TRAITEE);
        $reclamation->setType(TypeReclamation::COMPTE);

        return $reclamation;
    }

    public function testValidReclamation()
    {
        $reclamation = $this->createValidReclamation();

        $manager = new ReclamationManager();

        $this->assertTrue($manager->validate($reclamation));
    }

    public function testReclamationWithoutUser()
    {
        $this->expectException(\InvalidArgumentException::class);
        $this->expectExceptionMessage('L\'utilisateur est obligatoire');

        $reclamation = $this->createValidReclamation();
        $reclamation->setUser(null);

        $manager = new ReclamationManager();

        $manager->validate($reclamation);
    }

    public function testReclamationWithoutTexte()
    {
        $this->expectException(\InvalidArgumentException::class);
        $this->expectExceptionMessage('Le texte de la reclamation est obligatoire');

        $reclamation = $this->createValidReclamation();
        $reclamation->setTexte('');

        $manager = new ReclamationManager();

        $manager->validate($reclamation);
    }

    public function testReclamationWithShortTexte()
    {
        $this->expectException(\InvalidArgumentException::class);
        $this->expectExceptionMessage('Le texte doit contenir au minimum 10 caracteres');

        $reclamation = $this->createValidReclamation();
        $reclamation->setTexte('court');

        $manager = new ReclamationManager();

        $manager->validate($reclamation);
    }

    public function testReclamationWithTooLongTexte()
    {
        $this->expectException(\InvalidArgumentException::class);
        $this->expectExceptionMessage('Le texte ne peut pas depasser 2000 caracteres');

        $reclamation = $this->createValidReclamation();
        $reclamation->setTexte(str_repeat('a', 2001));

        $manager = new ReclamationManager();

        $manager->validate($reclamation);
    }

    public function testReclamationWithoutDateCreation()
    {
        $this->expectException(\InvalidArgumentException::class);
        $this->expectExceptionMessage('La date de creation est obligatoire');

        $reclamation = new Reclamation();
        $reclamation->setUser(new User());
        $reclamation->setTexte('Ce texte est suffisamment long pour être valide.');
        $reclamation->setStatut(StatutReclamation::NON_TRAITEE);
        $reclamation->setType(TypeReclamation::COMPTE);

        $manager = new ReclamationManager();

        $manager->validate($reclamation);
    }

    public function testReclamationWithFutureDate()
    {
        $this->expectException(\InvalidArgumentException::class);
        $this->expectExceptionMessage('La date de creation ne peut pas être dans le futur');

        $reclamation = $this->createValidReclamation();
        $futureDate = new \DateTime();
        $futureDate->modify('+1 day');
        $reclamation->setDateCreation($futureDate);

        $manager = new ReclamationManager();

        $manager->validate($reclamation);
    }

    public function testReclamationWithoutStatut()
    {
        $this->expectException(\InvalidArgumentException::class);
        $this->expectExceptionMessage('Le statut est obligatoire');

        $reclamation = new Reclamation();
        $reclamation->setUser(new User());
        $reclamation->setTexte('Ce texte est suffisamment long pour être valide.');
        $reclamation->setDateCreation(new \DateTime());
        $reclamation->setType(TypeReclamation::COMPTE);

        $manager = new ReclamationManager();

        $manager->validate($reclamation);
    }

    public function testReclamationWithoutType()
    {
        $this->expectException(\InvalidArgumentException::class);
        $this->expectExceptionMessage('Le type de reclamation est obligatoire');

        $reclamation = new Reclamation();
        $reclamation->setUser(new User());
        $reclamation->setTexte('Ce texte est suffisamment long pour être valide.');
        $reclamation->setDateCreation(new \DateTime());
        $reclamation->setStatut(StatutReclamation::NON_TRAITEE);

        $manager = new ReclamationManager();

        $manager->validate($reclamation);
    }
}
