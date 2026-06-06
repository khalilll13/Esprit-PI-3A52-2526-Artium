<?php

namespace App\Tests\Service;

use App\Entity\Oeuvre;
use App\Entity\Collections;
use App\Enum\TypeOeuvre;
use App\Service\OeuvreManager;
use PHPUnit\Framework\TestCase;

class OeuvreManagerTest extends TestCase
{
    private function createValidOeuvre(): Oeuvre
    {
        $oeuvre = new Oeuvre();
        $oeuvre->setTitre('Oeuvre test');
        $oeuvre->setDescription('Description test');
        $oeuvre->setDateCreation(new \DateTime());
        $oeuvre->setType(TypeOeuvre::PEINTURE);
        $oeuvre->setCollection(new Collections());

        return $oeuvre;
    }

    public function testValidOeuvre()
    {
        $oeuvre = $this->createValidOeuvre();

        $manager = new OeuvreManager();

        $this->assertTrue($manager->validate($oeuvre));
    }


    public function testOeuvreWithoutTitre()
    {
        $this->expectException(\InvalidArgumentException::class);
        $this->expectExceptionMessage('Le titre est obligatoire');

        $oeuvre = $this->createValidOeuvre();
        $oeuvre->setTitre(null);

        $manager = new OeuvreManager();

        $manager->validate($oeuvre);
    }


    public function testOeuvreWithoutDescription()
    {
        $this->expectException(\InvalidArgumentException::class);
        $this->expectExceptionMessage('La description est obligatoire');

        $oeuvre = $this->createValidOeuvre();
        $oeuvre->setDescription(null);

        $manager = new OeuvreManager();

        $manager->validate($oeuvre);
    }


    public function testOeuvreWithFutureDate()
    {
        $this->expectException(\InvalidArgumentException::class);
        $this->expectExceptionMessage('La date ne peut pas être dans le futur');

        $oeuvre = $this->createValidOeuvre();

        $futureDate = new \DateTime();
        $futureDate->modify('+1 day');

        $oeuvre->setDateCreation($futureDate);

        $manager = new OeuvreManager();

        $manager->validate($oeuvre);
    }

    public function testOeuvreWithoutCollection()
    {
        $this->expectException(\InvalidArgumentException::class);
        $this->expectExceptionMessage('La collection est obligatoire');

        $oeuvre = $this->createValidOeuvre();
        $oeuvre->setCollection(null);

        $manager = new OeuvreManager();

        $manager->validate($oeuvre);
    }

    public function testOeuvreWithoutType()
    {
        $this->expectException(\InvalidArgumentException::class);
        $this->expectExceptionMessage('Le type est obligatoire');

        $oeuvre = new Oeuvre();
        $oeuvre->setTitre('Oeuvre test');
        $oeuvre->setDescription('Description test');
        $oeuvre->setDateCreation(new \DateTime());
        $oeuvre->setCollection(new Collections());

        $manager = new OeuvreManager();

        $manager->validate($oeuvre);
    }

    public function testOeuvreWithShortDescription()
    {
        $this->expectException(\InvalidArgumentException::class);
        $this->expectExceptionMessage('La description doit contenir au moins 10 caracteres');

        $oeuvre = $this->createValidOeuvre();
        $oeuvre->setDescription('court');

        $manager = new OeuvreManager();

        $manager->validate($oeuvre);
    }
}