<?php

namespace App\Controller;

use App\Entity\Reclamation;
use App\Entity\Reponse;
use App\Enum\StatutReclamation;
use App\Form\ReclamationType;
use App\Form\ReponseType;
use App\Repository\ReclamationRepository;
use App\Service\AIResponseService;
use App\Service\ReclamationArchiveService;
use Doctrine\ORM\EntityManagerInterface;
use Knp\Component\Pager\PaginatorInterface;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Attribute\Route;

#[Route('/reclamation/admin')]
final class ReclamationAdminController extends AbstractController
{
    #[Route(name: 'app_reclamation_admin_index', methods: ['GET'])]
    public function index(Request $request, ReclamationRepository $reclamationRepository, AIResponseService $aiService, ReclamationArchiveService $archiveService, PaginatorInterface $paginator): Response
    {
        // Archiver les réclamations éligibles au chargement de la page
        $archiveService->archiveEligibleReclamations();
        
        $responseCreateForms = [];
        $responseEditForms = [];
        $aiSuggestions = [];
        
        // Récupérer les paramètres de recherche et filtres
        $search = trim((string) $request->query->get('q', ''));
        $statut = $request->query->get('statut', '');
        $dateFrom = $request->query->get('date_from', '');
        
        // Construire la requête selon les filtres
        $queryBuilder = $reclamationRepository->createQueryBuilder('r')
            ->leftJoin('r.user', 'u');
        
        // Filtre recherche
        if ($search !== '') {
            $queryBuilder
                ->andWhere('(r.texte LIKE :search OR r.id LIKE :search OR u.prenom LIKE :search OR u.nom LIKE :search OR u.email LIKE :search)')
                ->setParameter('search', '%' . $search . '%');
        }
        
        // Filtre statut
        if ($statut !== '' && $statut !== null) {
            $queryBuilder
                ->andWhere('r.statut = :statut')
                ->setParameter('statut', $statut);
        }
        
        // Filtre date de création
        if ($dateFrom !== '') {
            $dateFromObj = \DateTime::createFromFormat('Y-m-d', $dateFrom);
            if ($dateFromObj) {
                // Début du jour
                $startOfDay = clone $dateFromObj;
                $startOfDay->setTime(0, 0, 0);
                
                // Fin du jour
                $endOfDay = clone $dateFromObj;
                $endOfDay->setTime(23, 59, 59);
                
                $queryBuilder
                    ->andWhere('r.date_creation >= :startOfDay AND r.date_creation <= :endOfDay')
                    ->setParameter('startOfDay', $startOfDay)
                    ->setParameter('endOfDay', $endOfDay);
            }
        }
        
        $queryBuilder->orderBy('r.date_creation', 'DESC');
        $query = $queryBuilder->getQuery();
        
        // Paginer les résultats (10 par page)
        $reclamations = $paginator->paginate(
            $query,
            $request->query->getInt('page', 1),
            10
        );

        foreach ($reclamations as $reclamation) {
            $reponse = new Reponse();
            $reponse->setReclamation($reclamation);
            $createForm = $this->createForm(ReponseType::class, $reponse);
            $createForm->remove('reclamation');
            $createForm->remove('user_admin');
            $createForm->remove('date_reponse');
            $responseCreateForms[$reclamation->getId()] = $createForm->createView();

            // Générer une suggestion rapide locale pour éviter de bloquer le rendu de la page
            $aiSuggestions[$reclamation->getId()] = $aiService->generateSuggestionForList($reclamation);

            foreach ($reclamation->getReponses() as $existingReponse) {
                $editForm = $this->createForm(ReponseType::class, $existingReponse);
                $editForm->remove('reclamation');
                $editForm->remove('user_admin');
                $editForm->remove('date_reponse');
                $responseEditForms[$existingReponse->getId()] = $editForm->createView();
            }
        }

        return $this->render('reclam/reclams.html.twig', [
            'reclamations' => $reclamations,
            'responseCreateForms' => $responseCreateForms,
            'responseEditForms' => $responseEditForms,
            'aiSuggestions' => $aiSuggestions,
            'search_query' => $search,
            'selected_statut' => $statut,
            'date_from' => $dateFrom,
            'current_page' => $reclamations->getCurrentPageNumber(),
            'total_pages' => ceil($reclamations->getTotalItemCount() / 10),
        ]);
    }

    #[Route('/new', name: 'app_reclamation_admin_new', methods: ['GET', 'POST'])]
    public function new(Request $request, EntityManagerInterface $entityManager): Response
    {
        $reclamation = new Reclamation();
        $form = $this->createForm(ReclamationType::class, $reclamation);
        $form->handleRequest($request);

        if ($form->isSubmitted() && $form->isValid()) {
            $entityManager->persist($reclamation);
            $entityManager->flush();

            return $this->redirectToRoute('app_reclamation_admin_index', [], Response::HTTP_SEE_OTHER);
        }

        return $this->render('reclamation_admin/new.html.twig', [
            'reclamation' => $reclamation,
            'form' => $form->createView(),
        ]);
    }

    #[Route('/{id}', name: 'app_reclamation_admin_show', methods: ['GET'])]
    public function show(Reclamation $reclamation): Response
    {
        return $this->render('reclamation_admin/show.html.twig', [
            'reclamation' => $reclamation,
        ]);
    }

    #[Route('/{id}/edit', name: 'app_reclamation_admin_edit', methods: ['GET', 'POST'])]
    public function edit(Request $request, Reclamation $reclamation, EntityManagerInterface $entityManager): Response
    {
        $form = $this->createForm(ReclamationType::class, $reclamation);
        $form->handleRequest($request);

        if ($form->isSubmitted() && $form->isValid()) {
            $entityManager->flush();

            return $this->redirectToRoute('app_reclamation_admin_index', [], Response::HTTP_SEE_OTHER);
        }

        return $this->render('reclamation_admin/edit.html.twig', [
            'reclamation' => $reclamation,
            'form' => $form->createView(),
        ]);
    }

    #[Route('/{id}', name: 'app_reclamation_admin_delete', methods: ['POST'])]
    public function delete(Request $request, Reclamation $reclamation, EntityManagerInterface $entityManager): Response
    {
        if ($this->isCsrfTokenValid('delete'.$reclamation->getId(), $request->getPayload()->getString('_token'))) {
            $entityManager->remove($reclamation);
            $entityManager->flush();
        }

        return $this->redirectToRoute('app_reclamation_admin_index', [], Response::HTTP_SEE_OTHER);
    }

    #[Route('/{id}/archive', name: 'app_reclamation_admin_archive', methods: ['POST'])]
    public function archive(Request $request, Reclamation $reclamation, EntityManagerInterface $entityManager): Response
    {
        if ($this->isCsrfTokenValid('archive'.$reclamation->getId(), $request->getPayload()->getString('_token'))) {
            $reclamation->setStatut(StatutReclamation::ARCHIVE);
            $entityManager->flush();
        }

        return $this->redirectToRoute('app_reclamation_admin_index', [], Response::HTTP_SEE_OTHER);
    }

    #[Route('/{id}/unarchive', name: 'app_reclamation_admin_unarchive', methods: ['POST'])]
    public function unarchive(Request $request, Reclamation $reclamation, EntityManagerInterface $entityManager): Response
    {
        if ($this->isCsrfTokenValid('unarchive'.$reclamation->getId(), $request->getPayload()->getString('_token'))) {
            $reclamation->setStatut(StatutReclamation::TRAITEE);
            $entityManager->flush();
        }

        return $this->redirectToRoute('app_reclamation_admin_index', [], Response::HTTP_SEE_OTHER);
    }
}
