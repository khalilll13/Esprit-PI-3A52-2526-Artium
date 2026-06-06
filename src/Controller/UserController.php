<?php

namespace App\Controller;

use App\Entity\User;
use App\Form\UserType;
use App\Repository\UserRepository;
use App\Service\FileStorageService;
use Knp\Component\Pager\PaginatorInterface;
use App\Enum\Role;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Attribute\Route;
use App\Security\Password\Pbkdf2Sha256PasswordHasher;


#[Route('/user')]
final class UserController extends AbstractController
{
    #[Route('/recherche', name: 'app_user_search', methods: ['GET'])]
    public function search(Request $request, UserRepository $userRepository, PaginatorInterface $paginator): Response
    {
        $nomPrenom = $request->query->get('nom');
        $order = strtoupper($request->query->get('order', 'ASC'));
        if (!in_array($order, ['ASC', 'DESC'])) {
            $order = 'ASC';
        }
        $query = $userRepository->searchByNomPrenomQuery($nomPrenom, $order);
        $page = $request->query->getInt('page', 1);
        $pagination = $paginator->paginate($query, $page, 10);
        return $this->render('user/index.html.twig', [
            'users' => $pagination,
            'page_title' => 'Recherche utilisateurs',
            'search_nom' => $nomPrenom,
            'search_order' => $order
        ]);
    }
    #[Route('/', name: 'app_user_index', methods: ['GET'])]
    public function index(Request $request, UserRepository $userRepository, PaginatorInterface $paginator): Response
    {
        $order = strtoupper($request->query->get('order', 'ASC'));
        if (!in_array($order, ['ASC', 'DESC'])) {
            $order = 'ASC';
        }
        $query = $userRepository->searchByNomPrenomQuery(null, $order);
        $page = $request->query->getInt('page', 1);
        $pagination = $paginator->paginate($query, $page, 10);
        return $this->render('user/index.html.twig', [
            'users' => $pagination,
            'page_title' => 'Tous les utilisateurs',
            'search_nom' => null,
            'search_order' => $order
        ]);
    }


    #[Route('/artistes', name: 'app_user_artistes', methods: ['GET'])]
    public function artistes(Request $request, UserRepository $userRepository, PaginatorInterface $paginator): Response
    {
        $nomPrenom = $request->query->get('nom');
        $order = strtoupper($request->query->get('order', 'ASC'));
        if (!in_array($order, ['ASC', 'DESC'])) {
            $order = 'ASC';
        }
        $qb = $userRepository->createQueryBuilder('u')
            ->andWhere('u.role = :role')
            ->setParameter('role', Role::ARTISTE);
        if ($nomPrenom && strpos(trim($nomPrenom), ' ') !== false) {
            $qb->andWhere("CONCAT(LOWER(u.nom), ' ', LOWER(u.prenom)) LIKE :nomPrenom")
               ->setParameter('nomPrenom', '%' . strtolower($nomPrenom) . '%');
        } elseif ($nomPrenom) {
            $qb->andWhere('1=0');
        }
        $qb->orderBy('u.nom', $order)
              ->addOrderBy('u.prenom', $order)
              ->setMaxResults(10);
        $page = $request->query->getInt('page', 1);
        $pagination = $paginator->paginate($qb, $page, 10);
        return $this->render('user/index.html.twig', [
            'users' => $pagination,
            'page_title' => 'Liste des Artistes',
            'current_filter' => 'ARTISTE',
            'search_nom' => $nomPrenom,
            'search_order' => $order
        ]);
    }


    #[Route('/amateurs', name: 'app_user_amateurs', methods: ['GET'])]
    public function amateurs(Request $request, UserRepository $userRepository, PaginatorInterface $paginator): Response
    {
        $nomPrenom = $request->query->get('nom');
        $order = strtoupper($request->query->get('order', 'ASC'));
        if (!in_array($order, ['ASC', 'DESC'])) {
            $order = 'ASC';
        }
        $qb = $userRepository->createQueryBuilder('u')
            ->andWhere('u.role = :role')
            ->setParameter('role', Role::AMATEUR);
        if ($nomPrenom && strpos(trim($nomPrenom), ' ') !== false) {
            $qb->andWhere("CONCAT(LOWER(u.nom), ' ', LOWER(u.prenom)) LIKE :nomPrenom")
               ->setParameter('nomPrenom', '%' . strtolower($nomPrenom) . '%');
        } elseif ($nomPrenom) {
            $qb->andWhere('1=0');
        }
        $qb->orderBy('u.nom', $order)
              ->addOrderBy('u.prenom', $order)
              ->setMaxResults(10);
        $page = $request->query->getInt('page', 1);
        $pagination = $paginator->paginate($qb, $page, 10);
        return $this->render('user/index.html.twig', [
            'users' => $pagination,
            'page_title' => 'Liste des Amateurs',
            'current_filter' => 'AMATEUR',
            'search_nom' => $nomPrenom,
            'search_order' => $order
        ]);
    }

    #[Route('/new', name: 'app_user_new', methods: ['GET', 'POST'])]
    public function new(Request $request, EntityManagerInterface $entityManager, Pbkdf2Sha256PasswordHasher $passwordHasher, FileStorageService $fileStorageService): Response
    {
        $user = new User();
        // Initialiser la date d'inscription à aujourd'hui (00:00:00)
        $today = new \DateTime('today');
        $user->setDateInscription($today);
        $form = $this->createForm(UserType::class, $user, ['is_edit' => false]);
        $form->handleRequest($request);

        if ($form->isSubmitted() && $form->isValid()) {
            // Gestion de l'upload de la photo de profil
            $photoFile = $form->get('photoProfil')->getData();
            if ($photoFile) {
                $newFilename = $fileStorageService->uploadImage($photoFile, 'profile_');
                $user->setPhotoProfil($newFilename);
            }
            // Hash the password
            $plainPassword = $user->getPlainPassword();
            if ($plainPassword) {
                $hashedPassword = $passwordHasher->hash($plainPassword);
                $user->setMdp($hashedPassword);
            }

            // Nettoyer les champs conditionnels
            if ($user->getRole() === Role::AMATEUR) {
                $user->setSpecialite(null);
            } elseif ($user->getRole() === Role::ARTISTE) {
                $user->setCentreInteret(null);
            } elseif ($user->getRole() === Role::ADMIN) {
                $user->setSpecialite(null);
                $user->setCentreInteret(null);
            }

            $entityManager->persist($user);
            $entityManager->flush();

            $this->addFlash('success', 'Utilisateur créé avec succès !');

            // Redirection vers la liste pour voir l'utilisateur ajouté
            return $this->redirectToRoute('app_user_index');
        }

        return $this->render('user/new.html.twig', [
            'form' => $form,
            'user' => $user,
        ]);
    }

    #[Route('/{id}', name: 'app_user_show', methods: ['GET'], requirements: ['id' => '\d+'])]
    public function show(User $user): Response
    {
        return $this->render('user/show.html.twig', [
            'user' => $user,
        ]);
    }

    #[Route('/{id}/edit', name: 'app_user_edit', methods: ['GET', 'POST'])]
    public function edit(Request $request, User $user, EntityManagerInterface $entityManager, FileStorageService $fileStorageService): Response
    {
        $form = $this->createForm(UserType::class, $user, ['is_edit' => true]);
        $form->handleRequest($request);

        if ($form->isSubmitted() && $form->isValid()) {
            // Gestion de l'upload de la photo de profil (édition)
            $photoFile = $form->get('photoProfil')->getData();
            if ($photoFile) {
                $newFilename = $fileStorageService->uploadImage($photoFile, 'profile_');
                $user->setPhotoProfil($newFilename);
            }

            $entityManager->flush();

            $this->addFlash('success', 'Utilisateur modifié avec succès !');

            return $this->redirectToRoute('app_user_index');
        }

        return $this->render('user/edit.html.twig', [
            'form' => $form,
            'user' => $user,
        ]);
    }

    #[Route('/{id}/delete', name: 'app_user_delete', methods: ['POST'])]
    public function delete(Request $request, User $user, EntityManagerInterface $entityManager): Response
    {
        // Suppression directe sans vérification CSRF pour tester
        try {
            $entityManager->remove($user);
            $entityManager->flush();
            $this->addFlash('success', 'Utilisateur supprimé avec succès !');
        } catch (\Exception $e) {
            $this->addFlash('error', 'Erreur lors de la suppression: ' . $e->getMessage());
        }

        return $this->redirectToRoute('app_user_index');
    }
}