<?php

namespace App\Controller;

use App\Entity\Evenement;
use App\Enum\StatutEvenement;
use App\Enum\TypeEvenement;
use App\Form\EvenementArtisteEditType;
use App\Form\EvenementArtisteType;
use App\Repository\EvenementRepository;
use App\Repository\TicketRepository;
use Doctrine\ORM\EntityManagerInterface;
use Doctrine\ORM\Tools\Pagination\Paginator;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Attribute\Route;

#[Route('/evenement')]
final class EvenementController extends AbstractController
{
    #[Route(name: 'evenements', methods: ['GET'])]
    public function index(
        Request $request,
        EvenementRepository $evenementRepository,
        TicketRepository $ticketRepository,
        EntityManagerInterface $entityManager
    ): Response
    {
        // Mise à jour automatique des statuts expirés
        $this->updateExpiredEventStatuses($evenementRepository, $entityManager);
        // Pagination setup
        $page = max(1, $request->query->getInt('page', 1));
        $limit = 10;
        $filterValue = trim((string) $request->query->get('filter_value', ''));
        $sort = (string) $request->query->get('sort', 'titre_asc');
        $statusFilter = (string) $request->query->get('status', '');
        
        // Nouveaux filtres
        $capaciteMin = $request->query->get('capacite_min', '');
        $capaciteMax = $request->query->get('capacite_max', '');
        $prixMin = $request->query->get('prix_min', '');
        $prixMax = $request->query->get('prix_max', '');

        $allowedSorts = [
            'titre_asc' => ['e.titre', 'ASC'],
            'titre_desc' => ['e.titre', 'DESC'],
            'date_asc' => ['e.date_debut', 'ASC'],
            'date_desc' => ['e.date_debut', 'DESC'],
        ];
        if (!array_key_exists($sort, $allowedSorts)) {
            $sort = 'titre_asc';
        }

        $filterParams = array_filter([
            'filter_value' => $filterValue,
            'sort' => $sort,
            'status' => $statusFilter,
            'capacite_min' => $capaciteMin,
            'capacite_max' => $capaciteMax,
            'prix_min' => $prixMin,
            'prix_max' => $prixMax,
        ], static fn ($value) => $value !== '');
        
        // Build query with filters and pagination
        $queryBuilder = $evenementRepository->createQueryBuilder('e');

        [$sortField, $sortDirection] = $allowedSorts[$sort];
        $queryBuilder->orderBy($sortField, $sortDirection);

        if ($filterValue !== '') {
            // Recherche par titre OU type
            $queryBuilder
                ->andWhere('LOWER(e.titre) LIKE :filterValue OR LOWER(e.type) LIKE :filterValue')
                ->setParameter('filterValue', '%' . mb_strtolower($filterValue) . '%');
        }
        
        // Filtre capacité (ignorer les valeurs par défaut)
        if ($capaciteMin !== '' && is_numeric($capaciteMin) && (int) $capaciteMin > 0) {
            $queryBuilder
                ->andWhere('e.capacite_max >= :capaciteMin')
                ->setParameter('capaciteMin', (int) $capaciteMin);
        }
        if ($capaciteMax !== '' && is_numeric($capaciteMax) && (int) $capaciteMax < 1000) {
            $queryBuilder
                ->andWhere('e.capacite_max <= :capaciteMax')
                ->setParameter('capaciteMax', (int) $capaciteMax);
        }
        
        // Filtre prix (ignorer les valeurs par défaut)
        if ($prixMin !== '' && is_numeric($prixMin) && (float) $prixMin > 0) {
            $queryBuilder
                ->andWhere('e.prix_ticket >= :prixMin')
                ->setParameter('prixMin', (float) $prixMin);
        }
        if ($prixMax !== '' && is_numeric($prixMax) && (float) $prixMax < 500) {
            $queryBuilder
                ->andWhere('e.prix_ticket <= :prixMax')
                ->setParameter('prixMax', (float) $prixMax);
        }

        if ($statusFilter !== '') {
            $statutMap = [
                'A_VENIR' => 'À venir',
                'TERMINE' => 'Terminé',
                'ANNULE' => 'Annulé',
            ];
            $statutKey = mb_strtoupper($statusFilter);
            if (isset($statutMap[$statutKey])) {
                $queryBuilder
                    ->andWhere('e.statut = :filterStatut')
                    ->setParameter('filterStatut', $statutMap[$statutKey]);
            }
        }

        
        $query = $queryBuilder->getQuery()
            ->setFirstResult(($page - 1) * $limit)
            ->setMaxResults($limit);
        
        // Calculate pagination
        $paginator = new Paginator($query, true);
        $total = count($paginator);
        $totalPages = (int) ceil($total / $limit);
        $evenements = iterator_to_array($paginator->getIterator());
        $ticketsSold = [];
        foreach ($evenements as $evenement) {
            $ticketsSold[$evenement->getId()] = $ticketRepository->count(['evenement' => $evenement]);
        }
        
        return $this->render('event/events.html.twig', [
            'evenements' => $evenements,
            'tickets_sold' => $ticketsSold,
            'filter_value' => $filterValue,
            'status_filter' => $statusFilter,
            'sort' => $sort,
            'filter_params' => $filterParams,
            'current_page' => $page,
            'total_pages' => $totalPages,
            'capacite_min' => $capaciteMin,
            'capacite_max' => $capaciteMax,
            'prix_min' => $prixMin,
            'prix_max' => $prixMax,
        ]);
    }

    #[Route('/new', name: 'app_evenement_new', methods: ['GET', 'POST'])]
    public function new(Request $request, EntityManagerInterface $entityManager): Response
    {
        $evenement = new Evenement();
        $form = $this->createForm(EvenementArtisteType::class, $evenement);
        $form->handleRequest($request);

        if ($form->isSubmitted() && $form->isValid()) {
            $entityManager->persist($evenement);
            $entityManager->flush();

            return $this->redirectToRoute('evenements', [], Response::HTTP_SEE_OTHER);
        }

        return $this->render('evenement/new.html.twig', [
            'evenement' => $evenement,
            'form' => $form,
        ]);
    }

    #[Route('/{id}', name: 'app_evenement_show', methods: ['GET'])]
    public function show(Evenement $evenement): Response
    {
        $imageDataUri = null;
        $image = $evenement->getImageCouverture();
        
        if ($image !== null) {
            if (is_resource($image)) {
                $data = stream_get_contents($image);
            } elseif (is_string($image)) {
                $data = $image;
            } else {
                $data = null;
            }
            
            if ($data !== false && $data !== '' && $data !== null) {
                $imageDataUri = 'data:image/jpeg;base64,' . base64_encode($data);
            }
        }
        
        return $this->render('evenement/show.html.twig', [
            'evenement' => $evenement,
            'image' => $imageDataUri,
        ]);
    }

    #[Route('/{id}/edit', name: 'app_evenement_edit', methods: ['GET', 'POST'])]
    public function edit(Request $request, Evenement $evenement, EntityManagerInterface $entityManager): Response
    {
        $form = $this->createForm(EvenementArtisteEditType::class, $evenement);
        $form->handleRequest($request);

        if ($form->isSubmitted() && $form->isValid()) {
            $entityManager->flush();

            return $this->redirectToRoute('evenements', [], Response::HTTP_SEE_OTHER);
        }

        return $this->render('evenement/edit.html.twig', [
            'evenement' => $evenement,
            'form' => $form,
        ]);
    }

    #[Route('/{id}', name: 'app_evenement_delete', methods: ['POST'])]
    public function delete(Request $request, Evenement $evenement, EntityManagerInterface $entityManager): Response
    {
        if ($this->isCsrfTokenValid('delete'.$evenement->getId(), $request->getPayload()->getString('_token'))) {
            $entityManager->remove($evenement);
            $entityManager->flush();
        }

        return $this->redirectToRoute('evenements', [], Response::HTTP_SEE_OTHER);
    }

    /**
     * Met à jour automatiquement le statut des événements dont la date de fin est passée
     */
    private function updateExpiredEventStatuses(
        EvenementRepository $evenementRepository,
        EntityManagerInterface $entityManager
    ): void
    {
        $now = new \DateTimeImmutable();
        
        // Récupérer tous les événements "À venir" dont la date de fin est passée
        $expiredEvents = $evenementRepository->createQueryBuilder('e')
            ->where('e.statut = :statut')
            ->andWhere('e.date_fin < :now')
            ->setParameter('statut', StatutEvenement::A_VENIR)
            ->setParameter('now', $now)
            ->getQuery()
            ->getResult();

        // Mettre à jour leur statut
        foreach ($expiredEvents as $event) {
            $event->setStatut(StatutEvenement::TERMINE);
        }

        // Sauvegarder les changements si nécessaire
        if (count($expiredEvents) > 0) {
            $entityManager->flush();
        }
    }
}
