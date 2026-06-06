<?php

namespace App\Tests\Service;

use App\Entity\Collections;
use App\Entity\Livre;
use App\Enum\TypeOeuvre;
use App\Service\LivreManager;
use PHPUnit\Framework\TestCase;

class LivreManagerTest extends TestCase
{
    private function createValidLivre(): Livre
    {
        $livre = new Livre();
        $livre->setTitre('Livre test');
        $livre->setDescription('Description test pour livre');
        $livre->setDateCreation(new \DateTime());
        $livre->setType(TypeOeuvre::LIVRE);
        $livre->setCollection(new Collections());
        $livre->setImage('fake_image_data');
        $livre->setCategorie('Roman');
        $livre->setPrixLocation(15.5);
        $livre->setFichierPdf('fake_pdf_data');

        return $livre;
    }

    public function testValidLivre()
    {
        $livre = $this->createValidLivre();

        $manager = new LivreManager();

        $this->assertTrue($manager->validate($livre));
    }

    public function testLivreWithoutTitre()
    {
        $this->expectException(\InvalidArgumentException::class);
        $this->expectExceptionMessage('Le titre est obligatoire');

        $livre = $this->createValidLivre();
        $livre->setTitre(null);

        $manager = new LivreManager();

        $manager->validate($livre);
    }

    public function testLivreWithoutDescription()
    {
        $this->expectException(\InvalidArgumentException::class);
        $this->expectExceptionMessage('La description est obligatoire');

        $livre = $this->createValidLivre();
        $livre->setDescription(null);

        $manager = new LivreManager();

        $manager->validate($livre);
    }

    public function testLivreWithShortDescription()
    {
        $this->expectException(\InvalidArgumentException::class);
        $this->expectExceptionMessage('La description doit contenir au moins 10 caracteres');

        $livre = $this->createValidLivre();
        $livre->setDescription('court');

        $manager = new LivreManager();

        $manager->validate($livre);
    }

    public function testLivreWithFutureDate()
    {
        $this->expectException(\InvalidArgumentException::class);
        $this->expectExceptionMessage('La date ne peut pas être dans le futur');

        $livre = $this->createValidLivre();
        $futureDate = new \DateTime();
        $futureDate->modify('+1 day');
        $livre->setDateCreation($futureDate);

        $manager = new LivreManager();

        $manager->validate($livre);
    }

    public function testLivreWithoutType()
    {
        $this->expectException(\InvalidArgumentException::class);
        $this->expectExceptionMessage('Le type est obligatoire');

        $livre = new Livre();
        $livre->setTitre('Livre test');
        $livre->setDescription('Description test pour livre');
        $livre->setDateCreation(new \DateTime());
        $livre->setCollection(new Collections());
        $livre->setImage('fake_image_data');
        $livre->setCategorie('Roman');
        $livre->setPrixLocation(15.5);
        $livre->setFichierPdf('fake_pdf_data');

        $manager = new LivreManager();

        $manager->validate($livre);
    }

    public function testLivreWithoutCollection()
    {
        $this->expectException(\InvalidArgumentException::class);
        $this->expectExceptionMessage('La collection est obligatoire');

        $livre = $this->createValidLivre();
        $livre->setCollection(null);

        $manager = new LivreManager();

        $manager->validate($livre);
    }

    public function testLivreWithoutImage()
    {
        $this->expectException(\InvalidArgumentException::class);
        $this->expectExceptionMessage('L\'image de couverture est obligatoire');

        $livre = $this->createValidLivre();
        $livre->setImage(null);

        $manager = new LivreManager();

        $manager->validate($livre);
    }

    public function testLivreWithoutCategorie()
    {
        $this->expectException(\InvalidArgumentException::class);
        $this->expectExceptionMessage('La categorie est obligatoire');

        $livre = $this->createValidLivre();
        $livre->setCategorie('');

        $manager = new LivreManager();

        $manager->validate($livre);
    }

    public function testLivreWithCategorieTooShort()
    {
        $this->expectException(\InvalidArgumentException::class);
        $this->expectExceptionMessage('La categorie doit contenir au moins 2 caracteres');

        $livre = $this->createValidLivre();
        $livre->setCategorie('A');

        $manager = new LivreManager();

        $manager->validate($livre);
    }

    public function testLivreWithoutPrix()
    {
        $this->expectException(\InvalidArgumentException::class);
        $this->expectExceptionMessage('Le prix est obligatoire');

        $livre = new Livre();
        $livre->setTitre('Livre test');
        $livre->setDescription('Description test pour livre');
        $livre->setDateCreation(new \DateTime());
        $livre->setType(TypeOeuvre::LIVRE);
        $livre->setCollection(new Collections());
        $livre->setImage('fake_image_data');
        $livre->setCategorie('Roman');
        $livre->setFichierPdf('fake_pdf_data');

        $manager = new LivreManager();

        $manager->validate($livre);
    }

    public function testLivreWithNegativePrix()
    {
        $this->expectException(\InvalidArgumentException::class);
        $this->expectExceptionMessage('Le prix doit être supérieur à 0');

        $livre = $this->createValidLivre();
        $livre->setPrixLocation(-1);

        $manager = new LivreManager();

        $manager->validate($livre);
    }

    public function testLivreWithoutPdf()
    {
        $this->expectException(\InvalidArgumentException::class);
        $this->expectExceptionMessage('Le fichier PDF est obligatoire');

        $livre = $this->createValidLivre();
        $livre->setFichierPdf(null);

        $manager = new LivreManager();

        $manager->validate($livre);
    }
}
