<?php

namespace App\Tests\Controller;

use App\Entity\Oeuvre;
use Doctrine\ORM\EntityManagerInterface;
use Doctrine\ORM\EntityRepository;
use Symfony\Bundle\FrameworkBundle\KernelBrowser;
use Symfony\Bundle\FrameworkBundle\Test\WebTestCase;

final class OeuvreControllerTest extends WebTestCase
{
    private KernelBrowser $client;
    private EntityManagerInterface $manager;
    private EntityRepository $oeuvreRepository;
    private string $path = '/oeuvre/';

    protected function setUp(): void
    {
        $this->client = static::createClient();
        $this->manager = static::getContainer()->get('doctrine')->getManager();
        $this->oeuvreRepository = $this->manager->getRepository(Oeuvre::class);

        foreach ($this->oeuvreRepository->findAll() as $object) {
            $this->manager->remove($object);
        }

        $this->manager->flush();
    }

    public function testIndex(): void
    {
        $this->client->followRedirects();
        $crawler = $this->client->request('GET', $this->path);

        self::assertResponseStatusCodeSame(200);
        self::assertPageTitleContains('Oeuvre index');

        // Use the $crawler to perform additional assertions e.g.
        // self::assertSame('Some text on the page', $crawler->filter('.p')->first()->text());
    }

    public function testNew(): void
    {
        $this->markTestIncomplete();
        $this->client->request('GET', sprintf('%snew', $this->path));

        self::assertResponseStatusCodeSame(200);

        $this->client->submitForm('Save', [
            'oeuvre[titre]' => 'Testing',
            'oeuvre[description]' => 'Testing',
            'oeuvre[date_creation]' => 'Testing',
            'oeuvre[image]' => 'Testing',
            'oeuvre[type]' => 'Testing',
            'oeuvre[collection]' => 'Testing',
            'oeuvre[user_fav]' => 'Testing',
        ]);

        self::assertResponseRedirects($this->path);

        self::assertSame(1, $this->oeuvreRepository->count([]));
    }

    public function testShow(): void
    {
        $this->markTestIncomplete();
        $fixture = new Oeuvre();
        $fixture->setTitre('My Title');
        $fixture->setDescription('My Title');
        $fixture->setDate_creation('My Title');
        $fixture->setImage('My Title');
        $fixture->setType('My Title');
        $fixture->setCollection('My Title');
        $fixture->setUser_fav('My Title');

        $this->manager->persist($fixture);
        $this->manager->flush();

        $this->client->request('GET', sprintf('%s%s', $this->path, $fixture->getId()));

        self::assertResponseStatusCodeSame(200);
        self::assertPageTitleContains('Oeuvre');

        // Use assertions to check that the properties are properly displayed.
    }

    public function testEdit(): void
    {
        $this->markTestIncomplete();
        $fixture = new Oeuvre();
        $fixture->setTitre('Value');
        $fixture->setDescription('Value');
        $fixture->setDate_creation('Value');
        $fixture->setImage('Value');
        $fixture->setType('Value');
        $fixture->setCollection('Value');
        $fixture->setUser_fav('Value');

        $this->manager->persist($fixture);
        $this->manager->flush();

        $this->client->request('GET', sprintf('%s%s/edit', $this->path, $fixture->getId()));

        $this->client->submitForm('Update', [
            'oeuvre[titre]' => 'Something New',
            'oeuvre[description]' => 'Something New',
            'oeuvre[date_creation]' => 'Something New',
            'oeuvre[image]' => 'Something New',
            'oeuvre[type]' => 'Something New',
            'oeuvre[collection]' => 'Something New',
            'oeuvre[user_fav]' => 'Something New',
        ]);

        self::assertResponseRedirects('/oeuvre/');

        $fixture = $this->oeuvreRepository->findAll();

        self::assertSame('Something New', $fixture[0]->getTitre());
        self::assertSame('Something New', $fixture[0]->getDescription());
        self::assertSame('Something New', $fixture[0]->getDate_creation());
        self::assertSame('Something New', $fixture[0]->getImage());
        self::assertSame('Something New', $fixture[0]->getType());
        self::assertSame('Something New', $fixture[0]->getCollection());
        self::assertSame('Something New', $fixture[0]->getUser_fav());
    }

    public function testRemove(): void
    {
        $this->markTestIncomplete();
        $fixture = new Oeuvre();
        $fixture->setTitre('Value');
        $fixture->setDescription('Value');
        $fixture->setDate_creation('Value');
        $fixture->setImage('Value');
        $fixture->setType('Value');
        $fixture->setCollection('Value');
        $fixture->setUser_fav('Value');

        $this->manager->persist($fixture);
        $this->manager->flush();

        $this->client->request('GET', sprintf('%s%s', $this->path, $fixture->getId()));
        $this->client->submitForm('Delete');

        self::assertResponseRedirects('/oeuvre/');
        self::assertSame(0, $this->oeuvreRepository->count([]));
    }
}
