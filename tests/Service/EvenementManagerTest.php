<?php

namespace App\Tests\Service;

use App\Entity\Evenement;
use App\Entity\Galerie;
use App\Entity\User;
use App\Enum\StatutEvenement;
use App\Enum\TypeEvenement;
use App\Service\EvenementManager;
use PHPUnit\Framework\TestCase;

class EvenementManagerTest extends TestCase
{
    private function createValidEvenement(): Evenement
    {
        $evenement = new Evenement();
        $evenement->setTitre('Concert test');
        $evenement->setDescription('Description suffisamment longue');

        $dateDebut = new \DateTime('+1 day');
        $dateFin = new \DateTime('+2 days');

        $evenement->setDateDebut($dateDebut);
        $evenement->setDateFin($dateFin);
        $evenement->setDateCreation(new \DateTime());
        $evenement->setType(TypeEvenement::CONCERT);
        $evenement->setImageCouverture('fake_image_data');
        $evenement->setStatut(StatutEvenement::A_VENIR);
        $evenement->setCapaciteMax(100);
        $evenement->setGalerie(new Galerie());
        $evenement->setArtiste(new User());
        $evenement->setPrixTicket(25.5);

        return $evenement;
    }

    public function testValidEvenement()
    {
        $evenement = $this->createValidEvenement();

        $manager = new EvenementManager();

        $this->assertTrue($manager->validate($evenement));
    }

    public function testEvenementWithoutTitre()
    {
        $this->expectException(\InvalidArgumentException::class);
        $this->expectExceptionMessage('Le titre de l\'événement est obligatoire');

        $evenement = $this->createValidEvenement();
        $evenement->setTitre(null);

        $manager = new EvenementManager();

        $manager->validate($evenement);
    }

    public function testEvenementWithShortTitre()
    {
        $this->expectException(\InvalidArgumentException::class);
        $this->expectExceptionMessage('Le titre doit contenir au minimum 3 caractères');

        $evenement = $this->createValidEvenement();
        $evenement->setTitre('ab');

        $manager = new EvenementManager();

        $manager->validate($evenement);
    }

    public function testEvenementWithoutDescription()
    {
        $this->expectException(\InvalidArgumentException::class);
        $this->expectExceptionMessage('La description est obligatoire');

        $evenement = $this->createValidEvenement();
        $evenement->setDescription('');

        $manager = new EvenementManager();

        $manager->validate($evenement);
    }

    public function testEvenementWithShortDescription()
    {
        $this->expectException(\InvalidArgumentException::class);
        $this->expectExceptionMessage('La description doit contenir au minimum 10 caractères');

        $evenement = $this->createValidEvenement();
        $evenement->setDescription('court');

        $manager = new EvenementManager();

        $manager->validate($evenement);
    }

    public function testEvenementWithDateFinBeforeDateDebut()
    {
        $this->expectException(\InvalidArgumentException::class);
        $this->expectExceptionMessage('La date de fin doit être après la date de début');

        $evenement = $this->createValidEvenement();
        $evenement->setDateDebut(new \DateTime('+2 days'));
        $evenement->setDateFin(new \DateTime('+1 day'));

        $manager = new EvenementManager();

        $manager->validate($evenement);
    }

    public function testEvenementWithoutType()
    {
        $this->expectException(\InvalidArgumentException::class);
        $this->expectExceptionMessage('Le type d\'événement est obligatoire');

        $evenement = $this->createValidEvenement();
        $evenement->setType(null);

        $manager = new EvenementManager();

        $manager->validate($evenement);
    }

    public function testEvenementWithoutImage()
    {
        $this->expectException(\InvalidArgumentException::class);
        $this->expectExceptionMessage('L\'image de couverture est obligatoire');

        $evenement = $this->createValidEvenement();
        $evenement->setImageCouverture(null);

        $manager = new EvenementManager();

        $manager->validate($evenement);
    }

    public function testEvenementWithoutStatut()
    {
        $this->expectException(\InvalidArgumentException::class);
        $this->expectExceptionMessage('Le statut est obligatoire');

        $evenement = $this->createValidEvenement();
        $evenement->setStatut(null);

        $manager = new EvenementManager();

        $manager->validate($evenement);
    }

    public function testEvenementWithInvalidCapacite()
    {
        $this->expectException(\InvalidArgumentException::class);
        $this->expectExceptionMessage('La capacité doit être entre 1 et 100000');

        $evenement = $this->createValidEvenement();
        $evenement->setCapaciteMax(0);

        $manager = new EvenementManager();

        $manager->validate($evenement);
    }

    public function testEvenementWithoutGalerie()
    {
        $this->expectException(\InvalidArgumentException::class);
        $this->expectExceptionMessage('La galerie est obligatoire');

        $evenement = $this->createValidEvenement();
        $evenement->setGalerie(null);

        $manager = new EvenementManager();

        $manager->validate($evenement);
    }

    public function testEvenementWithoutArtiste()
    {
        $this->expectException(\InvalidArgumentException::class);
        $this->expectExceptionMessage('L\'artiste est obligatoire');

        $evenement = $this->createValidEvenement();
        $evenement->setArtiste(null);

        $manager = new EvenementManager();

        $manager->validate($evenement);
    }

    public function testEvenementWithInvalidPrixTicket()
    {
        $this->expectException(\InvalidArgumentException::class);
        $this->expectExceptionMessage('Le prix doit être entre 0.01€ et 10000€');

        $evenement = $this->createValidEvenement();
        $evenement->setPrixTicket(0.0);

        $manager = new EvenementManager();

        $manager->validate($evenement);
    }
}
