<?php

namespace App\Controller;

use App\Entity\Reclamation;
use App\Entity\User;
use App\Enum\StatutReclamation;
use App\Enum\TypeReclamation;
use App\Form\Reclamation1Type;
use App\Repository\ReclamationRepository;
use App\Repository\UserRepository;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Attribute\Route;

final class ReclamationArtisteController extends AbstractController
{
    #[Route('/artiste-reclamation', name: 'app_reclamationsartiste', methods: ['GET'])]
    public function index(
        Request $request,
        ReclamationRepository $reclamationRepository,
        UserRepository $userRepository,
        \App\Repository\EvenementRepository $evenementRepository,
        \App\Repository\CommentaireRepository $commentaireRepository,
        \App\Repository\LikeRepository $likeRepository
    ): Response
    {
        $user = $this->getUser();

        $search = trim((string) $request->query->get('q', ''));
        $statutValue = $request->query->get('statut');
        $statut = null;
        if (is_string($statutValue) && $statutValue !== '') {
            $statut = StatutReclamation::tryFrom($statutValue);
        }

        $typeValue = $request->query->get('type');
        $type = null;
        if (is_string($typeValue) && $typeValue !== '') {
            $type = TypeReclamation::tryFrom($typeValue);
        }

        $dateFrom = $request->query->get('date_from', '');

        $reclamations = [];
        if ($user instanceof User) {
            // Build query with all filters
            $qb = $reclamationRepository->createQueryBuilder('r')
                ->andWhere('r.user = :user')
                ->setParameter('user', $user)
                ->orderBy('r.date_creation', 'DESC');

            // Add search filter
            if ($search !== '') {
                $qb->andWhere('r.texte LIKE :search')
                    ->setParameter('search', '%' . $search . '%');
            }

            // Add status filter
            if ($statut !== null) {
                $qb->andWhere('r.statut = :statut')
                    ->setParameter('statut', $statut);
            }

            // Add type filter
            if ($type !== null) {
                $qb->andWhere('r.type = :type')
                    ->setParameter('type', $type);
            }

            // Add date filter
            if ($dateFrom !== '') {
                $dateFromObj = \DateTime::createFromFormat('Y-m-d', $dateFrom);
                if ($dateFromObj) {
                    $startOfDay = clone $dateFromObj;
                    $startOfDay->setTime(0, 0, 0);
                    $endOfDay = clone $dateFromObj;
                    $endOfDay->setTime(23, 59, 59);
                    $qb->andWhere('r.date_creation >= :startOfDay AND r.date_creation <= :endOfDay')
                        ->setParameter('startOfDay', $startOfDay)
                        ->setParameter('endOfDay', $endOfDay);
                }
            }

            $reclamations = $qb->getQuery()->getResult();
        }

        $form = $this->createForm(Reclamation1Type::class, new Reclamation(), [
            'action' => $this->generateUrl('app_reclamationartiste_new'),
            'method' => 'POST',
        ]);

        $editForms = [];
        foreach ($reclamations as $reclamation) {
            $editForms[$reclamation->getId()] = $this->createForm(Reclamation1Type::class, $reclamation, [
                'action' => $this->generateUrl('app_reclamationartiste_edit', ['id' => $reclamation->getId()]),
                'method' => 'POST',
            ])->createView();
        }

        // Statistiques dynamiques pour sidebar
        $oeuvres = [];
        if ($user && method_exists($user, 'getCollections')) {
            foreach ($user->getCollections() as $collectionItem) {
                if (method_exists($collectionItem, 'getOeuvres')) {
                    foreach ($collectionItem->getOeuvres() as $oeuvreItem) {
                        $oeuvres[] = $oeuvreItem;
                    }
                }
            }
        }
        $nbOeuvres = count($oeuvres);
        $nbReclamations = $user ? count($reclamationRepository->findByUserFilters($user, null, null, null)) : 0;
        $nbEvenements = $user ? $evenementRepository->count(['artiste' => $user]) : 0;
        $nbCommentaires = $user ? $commentaireRepository->countByArtist($user) : 0;
        $nbLikes = $user ? $likeRepository->countByArtist($user) : 0;
        return $this->render('Front Office/reclamationsartiste/reclamationsartiste.html.twig', [
            'reclamations' => $reclamations,
            'form' => $form->createView(),
            'edit_forms' => $editForms,
            'search_query' => $search ?? '',
            'selected_statut' => $statut?->value ?? '',
            'nbOeuvres' => $nbOeuvres,
            'nbReclamations' => $nbReclamations,
            'nbEvenements' => $nbEvenements,
            'nbCommentaires' => $nbCommentaires,
            'nbLikes' => $nbLikes,
            'selected_type' => $type?->value ?? '',
            'date_from' => $dateFrom,
        ]);
    }

    #[Route('/artiste-reclamation/new', name: 'app_reclamationartiste_new', methods: ['POST'])]
    public function new(Request $request, EntityManagerInterface $entityManager, UserRepository $userRepository, ReclamationRepository $reclamationRepository): Response
    {
        $reclamation = new Reclamation();
        $form = $this->createForm(Reclamation1Type::class, $reclamation, [
            'action' => $this->generateUrl('app_reclamationartiste_new'),
            'method' => 'POST',
        ]);
        $form->handleRequest($request);

        if ($form->isSubmitted() && $form->isValid()) {
            $user = $this->getUser();
            if (!$user instanceof User) {
                throw $this->createNotFoundException('Utilisateur par defaut introuvable.');
            }

            $reclamation->setUser($user);

            if (null === $reclamation->getDateCreation()) {
                $reclamation->setDateCreation(new \DateTime());
            }

            if (null === $reclamation->getStatut()) {
                $reclamation->setStatut(StatutReclamation::NON_TRAITEE);
            }

            $entityManager->persist($reclamation);
            $entityManager->flush();

            return $this->redirectToRoute('app_reclamationsartiste', [], Response::HTTP_SEE_OTHER);
        }

        if ($form->isSubmitted() && !$form->isValid()) {
            $user = $this->getUser();

            $reclamations = [];
            if ($user instanceof User) {
                $reclamations = $reclamationRepository->createQueryBuilder('r')
                    ->andWhere('r.user = :user')
                    ->setParameter('user', $user)
                    ->orderBy('r.date_creation', 'DESC')
                    ->getQuery()
                    ->getResult();
            }

            $editForms = [];
            foreach ($reclamations as $existingReclamation) {
                $editForms[$existingReclamation->getId()] = $this->createForm(Reclamation1Type::class, $existingReclamation, [
                    'action' => $this->generateUrl('app_reclamationartiste_edit', ['id' => $existingReclamation->getId()]),
                    'method' => 'POST',
                ])->createView();
            }

            return $this->render('Front Office/reclamationsartiste/reclamationsartiste.html.twig', [
                'reclamations' => $reclamations,
                'form' => $form->createView(),
                'edit_forms' => $editForms,
                'search_query' => '',
                'selected_statut' => '',
                'selected_type' => '',
                'date_from' => '',
            ]);
        }

        return $this->redirectToRoute('app_reclamationsartiste', [], Response::HTTP_SEE_OTHER);
    }

    #[Route('/artiste-reclamation/{id}/edit', name: 'app_reclamationartiste_edit', methods: ['POST'])]
    public function edit(Request $request, Reclamation $reclamation, EntityManagerInterface $entityManager, UserRepository $userRepository, ReclamationRepository $reclamationRepository): Response
    {
        $user = $this->getUser();
        if ($user instanceof User && $reclamation->getUser() !== $user) {
            throw $this->createAccessDeniedException('Vous ne pouvez pas modifier cette reclamation.');
        }

        $form = $this->createForm(Reclamation1Type::class, $reclamation, [
            'action' => $this->generateUrl('app_reclamationartiste_edit', ['id' => $reclamation->getId()]),
            'method' => 'POST',
        ]);
        $form->handleRequest($request);

        if ($form->isSubmitted() && $form->isValid()) {
            $entityManager->flush();

            return $this->redirectToRoute('app_reclamationsartiste', [], Response::HTTP_SEE_OTHER);
        }

        if ($form->isSubmitted() && !$form->isValid()) {
            $user = $this->getUser();

            $reclamations = [];
            if ($user instanceof User) {
                $reclamations = $reclamationRepository->findByUserFilters($user, null, null, null);
            }

            $editForms = [];
            foreach ($reclamations as $existingReclamation) {
                if ($existingReclamation->getId() === $reclamation->getId()) {
                    $editForms[$existingReclamation->getId()] = $form->createView();
                    continue;
                }

                $editForms[$existingReclamation->getId()] = $this->createForm(Reclamation1Type::class, $existingReclamation, [
                    'action' => $this->generateUrl('app_reclamationartiste_edit', ['id' => $existingReclamation->getId()]),
                    'method' => 'POST',
                ])->createView();
            }

            $createForm = $this->createForm(Reclamation1Type::class, new Reclamation(), [
                'action' => $this->generateUrl('app_reclamationartiste_new'),
                'method' => 'POST',
            ]);

            return $this->render('Front Office/reclamationsartiste/reclamationsartiste.html.twig', [
                'reclamations' => $reclamations,
                'form' => $createForm->createView(),
                'edit_forms' => $editForms,
                'search_query' => '',
                'selected_statut' => '',
                'selected_type' => '',
            ]);
        }

        return $this->redirectToRoute('app_reclamationsartiste', [], Response::HTTP_SEE_OTHER);
    }

    #[Route('/artiste-reclamation/{id}/delete', name: 'app_reclamationartiste_delete', methods: ['POST'])]
    public function delete(Request $request, Reclamation $reclamation, EntityManagerInterface $entityManager): Response
    {
        $user = $this->getUser();
        if ($user instanceof User && $reclamation->getUser() !== $user) {
            throw $this->createAccessDeniedException('Vous ne pouvez pas supprimer cette reclamation.');
        }

        if ($this->isCsrfTokenValid('delete'.$reclamation->getId(), $request->getPayload()->getString('_token'))) {
            $entityManager->remove($reclamation);
            $entityManager->flush();
        }

        return $this->redirectToRoute('app_reclamationsartiste', [], Response::HTTP_SEE_OTHER);
    }

    #[Route('/artiste-reclamation/{id}', name: 'app_reclamationartiste_show', methods: ['GET'])]
    public function show(Reclamation $reclamation): Response
    {
        $user = $this->getUser();
        if ($user instanceof User && $reclamation->getUser() !== $user) {
            throw $this->createAccessDeniedException('Vous ne pouvez pas consulter cette reclamation.');
        }

        return $this->render('Front Office/reclamationsartiste/show.html.twig', [
            'reclamation' => $reclamation,
        ]);
    }
}
