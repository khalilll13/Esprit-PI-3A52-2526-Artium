<?php

namespace App\Controller;

use App\Entity\Reclamation;
use App\Entity\Reponse;
use App\Entity\User;
use App\Enum\StatutReclamation;
use App\Enum\TypeReclamation;
use App\Form\Reclamation1Type;
use App\Form\ReponseType;
use App\Repository\ReclamationRepository;
use App\Repository\ReponseRepository;
use App\Repository\UserRepository;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Attribute\Route;

final class ReclamationController extends AbstractController
{
    #[Route('/user-reclamation', name: 'app_reclamationfront', methods: ['GET'])]
    public function index(Request $request, ReclamationRepository $reclamationRepository, ReponseRepository $reponseRepository, UserRepository $userRepository): Response
    {
        $user = $this->getUser();
        $isAdmin = $this->isGranted('ROLE_ADMIN');
        
        if (!$user instanceof User) {
            $user = $userRepository->find(1);
        }

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

        // Admin voit toutes les reclamations, user voit seulement les siennes
        if ($isAdmin) {
            if ($search !== '' || $statut !== null || $type !== null || $dateFrom !== '') {
                $qb = $reclamationRepository->createQueryBuilder('r')
                    ->orderBy('r.date_creation', 'DESC');
                    
                if ($search !== '') {
                    $qb->andWhere('r.texte LIKE :search')
                        ->setParameter('search', '%' . $search . '%');
                }
                
                if ($statut !== null) {
                    $qb->andWhere('r.statut = :statut')
                        ->setParameter('statut', $statut);
                }
                
                if ($type !== null) {
                    $qb->andWhere('r.type = :type')
                        ->setParameter('type', $type);
                }
                
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
            } else {
                $reclamations = $reclamationRepository->findBy([], ['date_creation' => 'DESC']);
            }
        } else {
            $reclamations = [];
            if ($user instanceof User) {
                $qb = $reclamationRepository->createQueryBuilder('r')
                    ->where('r.user = :user')
                    ->setParameter('user', $user)
                    ->orderBy('r.date_creation', 'DESC');
                
                if ($search !== '') {
                    $qb->andWhere('r.texte LIKE :search')
                        ->setParameter('search', '%' . $search . '%');
                }
                
                if ($statut !== null) {
                    $qb->andWhere('r.statut = :statut')
                        ->setParameter('statut', $statut);
                }
                
                if ($type !== null) {
                    $qb->andWhere('r.type = :type')
                        ->setParameter('type', $type);
                }
                
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
        }

        $editForms = [];
        $form = null;
        $responseCreateForms = [];
        $responseEditForms = [];
        $responseCounts = [];

        $reclamationIds = [];
        foreach ($reclamations as $reclamation) {
            $reclamationId = $reclamation->getId();
            if ($reclamationId !== null) {
                $reclamationIds[] = $reclamationId;
            }
        }
        $responseCounts = $reponseRepository->countByReclamationIds($reclamationIds);
        
        if ($isAdmin) {
            // Admin: formulaires pour répondre aux réclamations
            foreach ($reclamations as $reclamation) {
                $reponse = new Reponse();
                $reponse->setReclamation($reclamation);
                $createForm = $this->createForm(ReponseType::class, $reponse);
                $createForm->remove('reclamation');
                $createForm->remove('user_admin');
                $createForm->remove('date_reponse');
                $responseCreateForms[$reclamation->getId()] = $createForm->createView();

                foreach ($reclamation->getReponses() as $existingReponse) {
                    $editForm = $this->createForm(ReponseType::class, $existingReponse);
                    $editForm->remove('reclamation');
                    $editForm->remove('user_admin');
                    $editForm->remove('date_reponse');
                    $responseEditForms[$existingReponse->getId()] = $editForm->createView();
                }
            }
        } else {
            // User: formulaire pour créer et éditer ses réclamations
            $form = $this->createForm(Reclamation1Type::class, new Reclamation(), [
                'action' => $this->generateUrl('app_reclamation_new'),
                'method' => 'POST',
            ]);
            
            foreach ($reclamations as $reclamation) {
                $editForms[$reclamation->getId()] = $this->createForm(Reclamation1Type::class, $reclamation, [
                    'action' => $this->generateUrl('app_reclamation_edit', ['id' => $reclamation->getId()]),
                    'method' => 'POST',
                ])->createView();
            }
        }

        // Sélection du template selon le rôle
        $template = $isAdmin ? 'reclam/reclams.html.twig' : 'Front Office/reclamationfront/reclamationfront.html.twig';
        
        return $this->render($template, [
            'reclamations' => $reclamations,
            'response_counts' => $responseCounts,
            'form' => $form?->createView(),
            'edit_forms' => $editForms,
            'responseCreateForms' => $responseCreateForms,
            'responseEditForms' => $responseEditForms,
            'search_query' => $search,
            'selected_statut' => $statut?->value,
            'selected_type' => $type?->value,
            'date_from' => $dateFrom,
        ]);
    }

    #[Route('/reclamation/new', name: 'app_reclamation_new', methods: ['GET', 'POST'])]
    public function new(Request $request, EntityManagerInterface $entityManager, UserRepository $userRepository, ReclamationRepository $reclamationRepository): Response
    {
        $reclamation = new Reclamation();
        $form = $this->createForm(Reclamation1Type::class, $reclamation, [
            'action' => $this->generateUrl('app_reclamation_new'),
            'method' => 'POST',
        ]);
        $form->handleRequest($request);

        if ($form->isSubmitted() && $form->isValid()) {
            $user = $this->getUser();
            if (!$user instanceof User) {
                $user = $userRepository->find(1);
                if (!$user instanceof User) {
                    throw $this->createNotFoundException('Utilisateur par defaut introuvable.');
                }
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

            // If a file has been uploaded, convert stored filename to full XAMPP URL
            $fileName = $reclamation->getFileName();
            if ($fileName && !str_starts_with($fileName, 'http')) {
                $reclamation->setFileName('http://127.0.0.1/img/' . $fileName);
                $entityManager->flush();
            }

            return $this->redirectToRoute('app_reclamationfront', [], Response::HTTP_SEE_OTHER);
        }

        if ($form->isSubmitted() && !$form->isValid()) {
            $user = $this->getUser();
            if (!$user instanceof User) {
                $user = $userRepository->find(1);
            }

            $reclamations = [];
            if ($user instanceof User) {
                $reclamations = $reclamationRepository->findByUserFilters($user, null, null, null);
            }

            $editForms = [];
            foreach ($reclamations as $existingReclamation) {
                $editForms[$existingReclamation->getId()] = $this->createForm(Reclamation1Type::class, $existingReclamation, [
                    'action' => $this->generateUrl('app_reclamation_edit', ['id' => $existingReclamation->getId()]),
                    'method' => 'POST',
                ])->createView();
            }

            return $this->render('Front Office/reclamationfront/reclamationfront.html.twig', [
                'reclamations' => $reclamations,
                'form' => $form->createView(),
                'edit_forms' => $editForms,
                'search_query' => '',
                'selected_statut' => '',
            ]);
        }

        return $this->redirectToRoute('app_reclamationfront', [], Response::HTTP_SEE_OTHER);
    }

    #[Route('/reclamation/{id}', name: 'app_reclamation_show', methods: ['GET'])]
    public function show(Reclamation $reclamation): Response
    {
        return $this->render('reclamation/show.html.twig', [
            'reclamation' => $reclamation,
        ]);
    }

    #[Route('/reclamation/{id}/edit', name: 'app_reclamation_edit', methods: ['GET', 'POST'])]
    public function edit(Request $request, Reclamation $reclamation, EntityManagerInterface $entityManager, UserRepository $userRepository, ReclamationRepository $reclamationRepository): Response
    {
        $form = $this->createForm(Reclamation1Type::class, $reclamation, [
            'action' => $this->generateUrl('app_reclamation_edit', ['id' => $reclamation->getId()]),
            'method' => 'POST',
        ]);
        $form->handleRequest($request);

        if ($form->isSubmitted() && $form->isValid()) {
            $entityManager->flush();

            // If a file has been uploaded during edit, convert stored filename to full XAMPP URL
            $fileName = $reclamation->getFileName();
            if ($fileName && !str_starts_with($fileName, 'http')) {
                $reclamation->setFileName('http://127.0.0.1/img/' . $fileName);
                $entityManager->flush();
            }

            return $this->redirectToRoute('app_reclamationfront', [], Response::HTTP_SEE_OTHER);
        }

        if ($form->isSubmitted() && !$form->isValid()) {
            $user = $this->getUser();
            if (!$user instanceof User) {
                $user = $userRepository->find(1);
            }

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
                    'action' => $this->generateUrl('app_reclamation_edit', ['id' => $existingReclamation->getId()]),
                    'method' => 'POST',
                ])->createView();
            }

            $createForm = $this->createForm(Reclamation1Type::class, new Reclamation(), [
                'action' => $this->generateUrl('app_reclamation_new'),
                'method' => 'POST',
            ]);

            return $this->render('Front Office/reclamationfront/reclamationfront.html.twig', [
                'reclamations' => $reclamations,
                'form' => $createForm->createView(),
                'edit_forms' => $editForms,
                'search_query' => '',
                'selected_statut' => '',
            ]);
        }

        return $this->redirectToRoute('app_reclamationfront', [], Response::HTTP_SEE_OTHER);
    }

    #[Route('/reclamation/{id}/delete', name: 'app_reclamation_delete', methods: ['POST'])]
    public function delete(Request $request, Reclamation $reclamation, EntityManagerInterface $entityManager): Response
    {
        if ($this->isCsrfTokenValid('delete'.$reclamation->getId(), $request->getPayload()->getString('_token'))) {
            $entityManager->remove($reclamation);
            $entityManager->flush();
        }

        return $this->redirectToRoute('app_reclamationfront', [], Response::HTTP_SEE_OTHER);
    }
}
