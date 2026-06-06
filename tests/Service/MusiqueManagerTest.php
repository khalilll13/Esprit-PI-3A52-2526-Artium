<?php

namespace App\Tests\Service;

use App\Entity\Musique;
use App\Entity\Collections;
use App\Enum\TypeOeuvre;
use App\Enum\GenreMusique;
use App\Service\MusiqueManager;
use PHPUnit\Framework\TestCase;

class MusiqueManagerTest extends TestCase
{
    private function createValidMusique(): Musique
    {
        $musique = new Musique();
        $musique->setTitre('Musique test');
        $musique->setDescription('Description test pour musique');
        $musique->setDateCreation(new \DateTime());
        $musique->setType(TypeOeuvre::MUSIQUE);
        $musique->setCollection(new Collections());
        $musique->setGenre(GenreMusique::ROCK);
        $musique->setImage('fake_image_data');
        $musique->setAudio('fake_audio.mp3');

        return $musique;
    }

    public function testValidMusique()
    {
        $musique = $this->createValidMusique();

        $manager = new MusiqueManager();

        $this->assertTrue($manager->validate($musique));
    }

    public function testMusiqueWithoutTitre()
    {
        $this->expectException(\InvalidArgumentException::class);
        $this->expectExceptionMessage('Le titre est obligatoire');

        $musique = $this->createValidMusique();
        $musique->setTitre(null);

        $manager = new MusiqueManager();

        $manager->validate($musique);
    }

    public function testMusiqueWithoutDescription()
    {
        $this->expectException(\InvalidArgumentException::class);
        $this->expectExceptionMessage('La description est obligatoire');

        $musique = $this->createValidMusique();
        $musique->setDescription(null);

        $manager = new MusiqueManager();

        $manager->validate($musique);
    }

    public function testMusiqueWithFutureDate()
    {
        $this->expectException(\InvalidArgumentException::class);
        $this->expectExceptionMessage('La date ne peut pas être dans le futur');

        $musique = $this->createValidMusique();

        $futureDate = new \DateTime();
        $futureDate->modify('+1 day');

        $musique->setDateCreation($futureDate);

        $manager = new MusiqueManager();

        $manager->validate($musique);
    }

    public function testMusiqueWithoutCollection()
    {
        $this->expectException(\InvalidArgumentException::class);
        $this->expectExceptionMessage('La collection est obligatoire');

        $musique = $this->createValidMusique();
        $musique->setCollection(null);

        $manager = new MusiqueManager();

        $manager->validate($musique);
    }

    public function testMusiqueWithoutType()
    {
        $this->expectException(\InvalidArgumentException::class);
        $this->expectExceptionMessage('Le type est obligatoire');

        $musique = new Musique();
        $musique->setTitre('Musique test');
        $musique->setDescription('Description test pour musique');
        $musique->setDateCreation(new \DateTime());
        $musique->setCollection(new Collections());
        $musique->setGenre(GenreMusique::JAZZ);

        $manager = new MusiqueManager();

        $manager->validate($musique);
    }

    public function testMusiqueWithShortDescription()
    {
        $this->expectException(\InvalidArgumentException::class);
        $this->expectExceptionMessage('La description doit contenir au moins 10 caracteres');

        $musique = $this->createValidMusique();
        $musique->setDescription('court');

        $manager = new MusiqueManager();

        $manager->validate($musique);
    }

    public function testMusiqueWithoutGenre()
    {
        $this->expectException(\InvalidArgumentException::class);
        $this->expectExceptionMessage('Le genre est obligatoire');

        $musique = new Musique();
        $musique->setTitre('Musique test');
        $musique->setDescription('Description test pour musique');
        $musique->setDateCreation(new \DateTime());
        $musique->setType(TypeOeuvre::MUSIQUE);
        $musique->setCollection(new Collections());

        $manager = new MusiqueManager();

        $manager->validate($musique);
    }

    public function testMusiqueWithoutImage()
    {
        $this->expectException(\InvalidArgumentException::class);
        $this->expectExceptionMessage('L\'image de couverture est obligatoire');

        $musique = $this->createValidMusique();
        $musique->setImage(null);

        $manager = new MusiqueManager();

        $manager->validate($musique);
    }

    public function testMusiqueWithoutAudio()
    {
        $this->expectException(\InvalidArgumentException::class);
        $this->expectExceptionMessage('Le fichier audio est obligatoire');

        $musique = $this->createValidMusique();
        $musique->setAudio(null);

        $manager = new MusiqueManager();

        $manager->validate($musique);
    }
}
