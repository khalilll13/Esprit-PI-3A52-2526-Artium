<?php
require 'vendor/autoload.php';

use App\Kernel;
use Symfony\Component\Dotenv\Dotenv;

(new Dotenv())->bootEnv('.env');

$kernel = new Kernel('dev', true);
$kernel->boot();

$container = $kernel->getContainer();
$em = $container->get('doctrine')->getManager();
$musiques = $em->getRepository(App\Entity\Musique::class)->findAll();

foreach ($musiques as $m) {
    echo $m->getId() . ' : ' . $m->getAudio() . "\n";
}
