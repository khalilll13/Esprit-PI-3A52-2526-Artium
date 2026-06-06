<?php

namespace App\Controller;

use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Attribute\Route;
use Doctrine\ORM\EntityManagerInterface;
use App\Entity\Livre;
use App\Repository\CollectionsRepository;
use App\Repository\BookRepository; // Added BookRepository
use App\Repository\LivreRepository;
use App\Repository\UserRepository;
use App\Form\RentalFormType;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\HttpFoundation\BinaryFileResponse;
use Symfony\Component\HttpFoundation\ResponseHeaderBag;
use App\Enum\TypeOeuvre;
use App\Repository\LocationLivreRepository;
use App\Enum\EtatLocation;
use Stripe\Stripe;
use Stripe\PaymentIntent;
use Symfony\Component\RateLimiter\RateLimiterFactory;
use App\Service\RecommendationService;
use Symfony\UX\Chartjs\Builder\ChartBuilderInterface;
use Symfony\UX\Chartjs\Model\Chart;


final class BibliofrontController extends AbstractController
{
    #[Route('/user-bibliotheque', name: 'app_bibliofront')]
    public function index(Request $request, LivreRepository $livreRepository, LocationLivreRepository $locationLivreRepository, RecommendationService $recommendationService, ChartBuilderInterface $chartBuilder): Response
    {

        // search parameters from query string
        $q = trim((string) $request->query->get('q', ''));
        $category = trim((string) $request->query->get('category', ''));

        $qb = $livreRepository->createQueryBuilder('l')
            ->leftJoin('l.collection', 'c')
            ->addSelect('c')
            ->orderBy('l.date_creation', 'DESC');

        if ($q !== '') {
            $like = '%' . str_replace('%', '\\%', $q) . '%';
            $qb->andWhere('l.titre LIKE :like OR l.categorie LIKE :like')
               ->setParameter('like', $like);
        }

        if ($category !== '') {
            $qb->andWhere('l.categorie = :cat')->setParameter('cat', $category);
        }

        $livres = $qb->getQuery()->getResult();

        /** @var \App\Entity\User|null $currentUser */
        $currentUser = $this->getUser();
        $statusMap = [];
        $activeDateMap = [];
        $expirationMap = [];
        $rentalDaysMap = [];
        $remainingDaysMap = [];

        $now = new \DateTime();

        foreach ($livres as $livre) {

            $isActive = false;
            $activeLocation = null;
            $expirationDate = null;

            foreach ($livre->getLocationLivres() as $loc) {

                if ($loc->getEtat()->value !== 'Active') {
                    continue;
                }

                $start = $loc->getDateDebut();
                if (!$start) {
                    continue;
                }

                $days = $loc->getNombreDeJours() ?? 1;
                $expiration = (clone $start)->modify("+{$days} days");

                // Only count as active if NOT expired
                if ($expiration > $now) {
                    $isActive = true;
                    $activeLocation = $loc;
                    $expirationDate = $expiration->format('Y-m-d H:i:s');
                    break;
                }
            }

            $livreId = $livre->getId();
            
            // Check if the active rental belongs to the current user
            $rentedByCurrentUser = false;
            if ($isActive && $activeLocation && $currentUser) {
                $rentedByCurrentUser = ($activeLocation->getUser()?->getId() === $currentUser->getId());
            }
            
            $statusMap[$livreId] = [
                'isActive' => $isActive,
                'rentedByCurrentUser' => $rentedByCurrentUser,
                'locationId' => $activeLocation?->getId(),
                'startDate' => $activeLocation?->getDateDebut()?->format('Y-m-d H:i:s'),
                'expirationDate' => $expirationDate
            ];
            
            // Populate the individual maps that the template expects
            if ($isActive && $activeLocation) {
                $activeDateMap[$livreId] = $activeLocation->getDateDebut()->format('Y-m-d H:i:s');
                $expirationMap[$livreId] = $expirationDate;
                $rentalDaysMap[$livreId] = $activeLocation->getNombreDeJours() ?? 1;
                
                // Calculate remaining days
                $start = $activeLocation->getDateDebut();
                $days = $activeLocation->getNombreDeJours() ?? 1;
                $expiration = (clone $start)->modify("+{$days} days");
                $remainingDays = $expiration->diff($now)->days;
                $remainingDaysMap[$livreId] = max(0, $remainingDays);
            }
        }

        // fetch distinct categories for filter select
        $catRows = $livreRepository->createQueryBuilder('lc')->select('DISTINCT lc.categorie')->where('lc.categorie IS NOT NULL')->orderBy('lc.categorie', 'ASC')->getQuery()->getScalarResult();
        $categories = array_map(function($r){ return $r['categorie']; }, $catRows ?: []);

        $recommendations = [];
        $chart = null;

        /** @var \App\Entity\User|null $user */
        $user = $this->getUser();

        if ($user) {

            // Recommendations
            $recommendations = $recommendationService
                ->getRecommendations($user);

            // Stats for chart
            $stats = $recommendationService
                ->getUserCategoryStats($user);

            if ($stats) {

                $chart = $chartBuilder->createChart(
                    Chart::TYPE_DOUGHNUT
                );

                $chart->setData([
                    'labels' => array_keys($stats),
                    'datasets' => [[
                        'data' => array_values($stats),
                        'backgroundColor' => [
                            '#6366F1',
                            '#10B981',
                            '#F59E0B',
                            '#EF4444',
                            '#8B5CF6'
                        ]
                    ]]
                ]);

                $chart->setOptions([
                    'plugins' => [
                        'legend' => [
                            'position' => 'bottom'
                        ]
                    ]
                ]);
            }
        }

        $userProfile = [];
        $confidence = 0;


        return $this->render('Front Office/bibliofront/bibliofront.html.twig', [
            'controller_name' => 'BibliofrontController',
            'livres' => $livres,
            'categories' => $categories,
            'search_q' => $q,
            'search_category' => $category,
            'livreStatus' => $statusMap,
            'livreActiveDate' => $activeDateMap,
            'livreExpirationDate' => $expirationMap,
            'livreRentalDays' => $rentalDaysMap,
            'livreRemainingDays' => $remainingDaysMap,
            'stripe_public_key' => $_ENV['STRIPE_PUBLISHABLE_KEY'],
            'recommendations' => $recommendations,
            'chart' => $chart,
            'userProfile' => $userProfile,
            'confidence' => $confidence,
        ]);
    }


    #[Route('/user-bibliotheque/louer/{id}/form', name: 'app_biblio_rent_form', methods: ['GET','POST'])]
    public function rentForm(Livre $livre, Request $request): Response
    {
        $form = $this->createForm(RentalFormType::class);
        $form->handleRequest($request);

        if ($request->isMethod('POST')) {
            if ($form->isSubmitted() && $form->isValid()) {
                $data = $form->getData();
                $nombre = (int) ($data['nombre_jours'] ?? 1);
                $prix = $livre->getPrixLocation() ?? 0;
                return $this->render('Front Office/bibliofront/_rent_confirm.html.twig', [
                    'livre' => $livre,
                    'nombre_jours' => $nombre,
                    'prix_par_jour' => $prix,
                ]);
            }

            // invalid: re-render form with errors
            return $this->render('Front Office/bibliofront/_rent_form.html.twig', [
                'form' => $form->createView(),
                'livre' => $livre,
            ]);
        }
        return $this->render('Front Office/bibliofront/_rent_form.html.twig', [
            'form' => $form->createView(),
            'livre' => $livre,
         ]);

    }


    #[Route('/user-bibliotheque/louer/{id}/confirm', name: 'app_biblio_rent_confirm', methods: ['POST'])]
    public function rentConfirm(Livre $livre, Request $request, EntityManagerInterface $em, UserRepository $userRepository, RateLimiterFactory $rentBookLimiter, RecommendationService $recommendationService): JsonResponse
    {
        $nombre = (int) $request->request->get('nombre_jours', 0);

        // compute amount in cents (Stripe expects integer amount in smallest currency unit)
        $prixParJour = $livre->getPrixLocation() ?? 0;
        $amount = (int) round($prixParJour * max(1, $nombre) * 100);

        // If the frontend sent a payment_intent_id, verify its status and proceed only if payment succeeded
$paymentIntentId =$request->request->get('payment_intent_id');

if (!$paymentIntentId)
{
    return $this->json([
        'success'=>false,
        'message'=>'Paiement requis'
    ]);
}

Stripe::setApiKey(
    $_ENV['STRIPE_SECRET_KEY']
);

try {

    $pi =
        PaymentIntent::retrieve(
            $paymentIntentId
        );

    if ($pi->status !== 'succeeded')
    {
        return $this->json([
            'success'=>false,
            'message'=>'Paiement non validé'
        ]);
    }

}
catch (\Throwable $e)
{
    return $this->json([
        'success'=>false,
        'message'=>'Erreur Stripe: '.$e->getMessage()
    ]);
}

/** @var \App\Entity\User|null $user */
$user = $this->getUser();

$identifier = $user
    ? 'user_'.$user->getId()
    : $request->getClientIp();

$limiter = $rentBookLimiter->create($identifier);

$limit = $limiter->consume(1);

$remaining = $limit->getRemainingTokens();

if (!$limit->isAccepted())
{
    return $this->json([
        'success' => false,
        'message' => 'Limite atteinte : maximum 4 locations par heure.',
        'remaining' => 0
    ]);
}

        // check for existing active rental (allow expired rentals)
        foreach ($livre->getLocationLivres() as $loc) {
            $etat = $loc->getEtat();
            $etatVal = is_object($etat) && property_exists($etat, 'value') ? $etat->value : (string) $etat;
            if ($etatVal === 'Active') {

                $start = $loc->getDateDebut();

                $days = $loc->getNombreDeJours();

                if (!$days) {
                    $days = 1;
                }

                $expiration =
                    (clone $start)->modify('+' . $days . ' days');

                // block ONLY if still active
                if ($expiration > new \DateTime()) {

                    return $this->json([
                        'success'=>false,
                        'message'=>'Livre déjà loué'
                    ]);

                }

            }
        }

        // create a new LocationLivre and persist
        $location = new \App\Entity\LocationLivre();
        $location->setDateDebut(new \DateTime());
        $location->setEtat(\App\Enum\EtatLocation::ACTIVE);

        /** @var \App\Entity\User|null $user */
        $user = $this->getUser();
        if (!$user) {
            // fallback to test user id 1 if anonymous
            $user = $userRepository->find(1);
            if (!$user) {
                return $this->json(['success' => false, 'message' => 'Utilisateur introuvable']);
            }
        }

        $location->setUser($user);
        $location->setLivre($livre);

        // persist requested rental duration (nombre_de_jours)
        $location->setNombreDeJours(max(1, $nombre));

        $em->persist($location);
        $em->flush();

        // Clear recommendation cache for this user so recommendations update after a new rental
        $recommendationService->clearUserCache($user);

        $this->addFlash('success', 'Location confirmée.');

        $start = $location->getDateDebut();
        $expiration = (clone $start)->modify('+' . max(1, $nombre) . ' days');

        return $this->json([
            'success' => true,
            'nombre_jours' => $nombre,
            'start_date' => $start->format('Y-m-d H:i:s'),
            'expiration_date' => $expiration->format('Y-m-d H:i:s'),
            'remaining' => $remaining
        ]);
    } 

    #[Route(
    '/user-bibliotheque/payment-intent/{id}',
    name: 'create_payment_intent',
    methods: ['POST']
)]
public function createPaymentIntent(
    Livre $livre,
    Request $request
): JsonResponse
{
    $nombre =
        (int)$request->request->get('nombre_jours',1);

    $prix =
        $livre->getPrixLocation() ?? 0;

    $amount =
        (int)round($prix * $nombre * 100);

    Stripe::setApiKey(
        $_ENV['STRIPE_SECRET_KEY']
    );

    try {

        $intent =
            PaymentIntent::create([
                'amount'=>$amount,
                'currency'=>'eur',
                'automatic_payment_methods'=>[
                    'enabled'=>true
                ]
            ]);

        return $this->json([
            'clientSecret'=>$intent->client_secret
        ]);

    }
    catch(\Throwable $e){

        return $this->json([
            'success'=>false,
            'message'=>$e->getMessage()
        ]);

    }
}

#[Route('/book/{id}/read', name: 'book_read')]
public function read(Livre $livre, LocationLivreRepository $locationRepo): Response
{
    /** @var \App\Entity\User|null $user */
    $user = $this->getUser();

    if (!$user) {
        throw $this->createAccessDeniedException('You must be logged in.');
    }

    $now = new \DateTime();
    $hasAccess = false;

    foreach ($livre->getLocationLivres() as $loc) {

        if ($loc->getEtat()->value !== 'Active') {
            continue;
        }

        if ($loc->getUser()?->getId() !== $user->getId()) {
            continue;
        }

        $start = $loc->getDateDebut();
        $days = $loc->getNombreDeJours() ?? 1;
        $expiration = (clone $start)->modify("+{$days} days");

        if ($expiration > $now) {
            $hasAccess = true;
            break;
        }
    }

    if (!$hasAccess) {
        throw $this->createAccessDeniedException('Rental expired or not yours.');
    }

    return $this->render('book/read.html.twig', [
        'livre' => $livre
    ]);
}

#[Route('/livre/pdf/{id}', name: 'livre_pdf')]
public function livrePdf(Livre $livre): Response
{
        $pdf = $livre->getFichierPdf();

        if (!$pdf) {
            throw $this->createNotFoundException('PDF not found');
        }

        // If stored as a stream resource, read it
        if (is_resource($pdf)) {
            $pdf = stream_get_contents($pdf);
        }

        // If stored as a URL (e.g. http://127.0.0.1/pdf/foo.pdf), fetch its contents
        if (is_string($pdf)) {
            $pdfContent = false;

            // data URI (base64) handling
            if (str_starts_with($pdf, 'data:application/pdf;base64,')) {
                $pdfContent = base64_decode(substr($pdf, strlen('data:application/pdf;base64,')));
            }

            // absolute URL
            if ($pdfContent === false && filter_var($pdf, FILTER_VALIDATE_URL)) {
                $pdfContent = @file_get_contents($pdf);
            }

            // path under public/ (e.g. /pdf/foo.pdf or pdf/foo.pdf)
            if ($pdfContent === false && preg_match('#^/?pdf/#', $pdf)) {
                $projectDir = $this->getParameter('kernel.project_dir');
                $path = $projectDir . '/public' . (str_starts_with($pdf, '/') ? $pdf : '/' . $pdf);
                if (is_file($path) && is_readable($path)) {
                    $pdfContent = @file_get_contents($path);
                }
            }

            // If none of the above matched, assume the string contains raw PDF bytes
            if ($pdfContent === false) {
                // treat original string as binary content
                $pdfContent = $pdf;
            }

            $pdf = $pdfContent;
        }

        if ($pdf === false || $pdf === null || $pdf === '') {
            throw $this->createNotFoundException('PDF not found or could not be read');
        }

        return new Response(
            $pdf,
            200,
            [
                'Content-Type' => 'application/pdf',
                'Content-Disposition' => 'inline; filename="livre_' . $livre->getId() . '.pdf"',
            ]
        );
}
}  

