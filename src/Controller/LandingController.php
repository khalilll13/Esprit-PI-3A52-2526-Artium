<?php

namespace App\Controller;

use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Attribute\Route;

final class LandingController extends AbstractController
{
    #[Route('/accueil', name: 'landing')]
    public function index(): Response
    {
        return $this->render('Front Office/landing/landing.html.twig', [
            'controller_name' => 'LandingController',
        ]);
    }
}
