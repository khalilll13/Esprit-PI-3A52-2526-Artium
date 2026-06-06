<?php

namespace App\Controller;

use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Attribute\Route;

final class BookdetailsController extends AbstractController
{
    #[Route('/user-details-livre', name: 'app_bookdetails')]
    public function index(): Response
    {
        return $this->render('Front Office/bookdetails/bookdetails.html.twig', [
            'controller_name' => 'BookdetailsController',
        ]);
    }
}
