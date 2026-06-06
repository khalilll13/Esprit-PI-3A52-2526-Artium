<?php

namespace App\Controller;



use App\Entity\User;
use App\Entity\Commentaire;
use App\Service\RecommendationServiceoeuvre;
use App\Service\FileStorageService;
use App\Repository\UserRepository;
use Doctrine\ORM\EntityManagerInterface;
use App\Entity\Collections;
use App\Entity\Oeuvre;
use App\Enum\TypeOeuvre;
use App\Form\OeuvreType;
use App\Form\UserType;
use App\Repository\CommentaireRepository;
use App\Repository\CollectionsRepository;
use App\Repository\OeuvreRepository;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\HttpFoundation\Request;
use App\Security\Password\Pbkdf2Sha256PasswordHasher;
use Symfony\Component\PasswordHasher\Hasher\UserPasswordHasherInterface;
use Symfony\Component\Routing\Attribute\Route;

final class FeedController extends AbstractController
{
    /**
     * @param Oeuvre[] $oeuvres
     * @return array<int, Commentaire[]>
     */
    private function buildCommentsByOeuvre(array $oeuvres, CommentaireRepository $commentaireRepository): array
    {
        $oeuvreIds = [];
        foreach ($oeuvres as $oeuvre) {
            if ($oeuvre instanceof Oeuvre && $oeuvre->getId() !== null) {
                $oeuvreIds[] = $oeuvre->getId();
            }
        }

        if ($oeuvreIds === []) {
            return [];
        }

        return $commentaireRepository->findGroupedByOeuvreIdsWithUser(array_values(array_unique($oeuvreIds)));
    }

    private function getInitialDisplayCount(array $oeuvres, Request $request): int
    {
        $defaultCount = 3;
        $focusOeuvreId = (int) $request->query->get('focus_oeuvre', 0);

        if ($focusOeuvreId <= 0) {
            return $defaultCount;
        }

        foreach ($oeuvres as $index => $oeuvre) {
            if ($oeuvre instanceof Oeuvre && $oeuvre->getId() === $focusOeuvreId) {
                return max($defaultCount, $index + 1);
            }
        }

        return $defaultCount;
    }

    private function buildRedirectWithFocus(string $redirectPath, int $oeuvreId): string
    {
        if ($redirectPath === '' || !str_starts_with($redirectPath, '/')) {
            $redirectPath = '/feed';
        }

        $parts = parse_url($redirectPath);
        $path = $parts['path'] ?? '/feed';
        $query = [];

        if (!empty($parts['query'])) {
            parse_str($parts['query'], $query);
        }

        if ($oeuvreId > 0) {
            $query['focus_oeuvre'] = $oeuvreId;
        }

        $queryString = http_build_query($query);
        $base = $path.($queryString !== '' ? '?'.$queryString : '');
        $anchor = $oeuvreId > 0 ? '#oeuvre-'.$oeuvreId : '';

        return $base.$anchor;
    }

    #[Route('/feed', name: 'app_feed')]
    public function index(OeuvreRepository $oeuvreRepository, CollectionsRepository $collectionsRepository, CommentaireRepository $commentaireRepository, Request $request, EntityManagerInterface $em, FileStorageService $fileStorageService): Response
    {
        $currentUser = $this->getUser();

        // Création du formulaire de profil
        $form = null;
        if ($currentUser) {
            $form = $this->createForm(UserType::class, $currentUser, ['is_edit' => true]);
            $form->handleRequest($request);

            if ($form->isSubmitted() && $form->isValid()) {
                $photoFile = $form->get('photoProfil')->getData();
                if ($photoFile) {
                    $newFilename = $fileStorageService->uploadImage($photoFile, 'profile_');
                    $currentUser->setPhotoProfil($fileStorageService->getImageUrl($newFilename));
                }
                $em->flush();
                $this->addFlash('success', 'Profil mis à jour !');
                return $this->redirectToRoute('app_feed');
            }
        }

        $peintures = $oeuvreRepository->findBy(['type' => TypeOeuvre::PEINTURE]);
        $sculptures = $oeuvreRepository->findBy(['type' => TypeOeuvre::SCULPTURE]);
        $photos = $oeuvreRepository->findBy(['type' => TypeOeuvre::PHOTOGRAPHIE]);
        $all = array_merge($peintures, $sculptures, $photos);
        $commentsByOeuvre = $this->buildCommentsByOeuvre($all, $commentaireRepository);
        $initialDisplayCount = $this->getInitialDisplayCount($all, $request);

        return $this->render('Front Office/feed/feed.html.twig', [
            'controller_name' => 'FeedController',
            'currentUser' => $currentUser,
            'profile_form' => $form?->createView(),
            'profile_form_action' => $this->generateUrl('app_feed'),
            'oeuvres' => $all,
            'commentsByOeuvre' => $commentsByOeuvre,
            'initialDisplayCount' => $initialDisplayCount,
            'typeOeuvres' => TypeOeuvre::cases(),
            'collections' => $collectionsRepository->findBy([], ['id' => 'DESC'], 50),
        ]);
    }

    #[Route('/profil/changer-mot-de-passe', name: 'app_changer_mot_de_passe', methods: ['POST'])]
    public function changePassword(Request $request, Pbkdf2Sha256PasswordHasher $customHasher, UserPasswordHasherInterface $passwordHasher, EntityManagerInterface $em): Response
    {
        $user = $this->getUser();
        if (!$user instanceof User) {
            return $this->redirectToRoute('app_signin');
        }

        $submittedToken = (string) $request->request->get('_token');
        if (!$this->isCsrfTokenValid('change_password', $submittedToken)) {
            $this->addFlash('error', 'Requête invalide.');
            return $this->redirectToRoute('app_feed');
        }

        $currentPassword = (string) $request->request->get('currentPassword', '');
        $newPassword = (string) $request->request->get('newPassword', '');
        $confirmPassword = (string) $request->request->get('confirmPassword', '');

        if ($currentPassword === '' || $newPassword === '' || $confirmPassword === '') {
            $this->addFlash('error', 'Tous les champs mot de passe sont obligatoires.');
            return $this->redirectToRoute('app_feed');
        }

        if (!$passwordHasher->isPasswordValid($user, $currentPassword)) {
            $this->addFlash('error', 'Mot de passe actuel incorrect.');
            return $this->redirectToRoute('app_feed');
        }

        if ($newPassword !== $confirmPassword) {
            $this->addFlash('error', 'La confirmation du mot de passe ne correspond pas.');
            return $this->redirectToRoute('app_feed');
        }

        if (mb_strlen($newPassword) < 8) {
            $this->addFlash('error', 'Le nouveau mot de passe doit contenir au moins 8 caractères.');
            return $this->redirectToRoute('app_feed');
        }

        $user->setMdp($customHasher->hash($newPassword));
        $em->flush();

        $this->addFlash('success', 'Mot de passe mis à jour avec succès.');

        return $this->redirectToRoute('app_feed');
    }

    #[Route('/feed_recommandations', name: 'app_feed_recommandations')]
    public function indexRecommandations(OeuvreRepository $oeuvreRepository, CollectionsRepository $collectionsRepository, CommentaireRepository $commentaireRepository, Request $request, RecommendationServiceoeuvre $recommendationService): Response
    {
        $user = $this->getUser();
        if (!$user) {
            throw $this->createNotFoundException('User not found');
        }

        #$recommendedOeuvres = $recommendationService->getRecommendedOeuvres($user);
        $recommendedOeuvres = $recommendationService->getRecommendedOeuvresHybrid($user);
        $commentsByOeuvre = $this->buildCommentsByOeuvre($recommendedOeuvres, $commentaireRepository);

        $initialDisplayCount = $this->getInitialDisplayCount($recommendedOeuvres, $request);

        return $this->render('Front Office/feed/feed.html.twig', [
            'controller_name' => 'FeedController',
            'oeuvres' => $recommendedOeuvres,
            'commentsByOeuvre' => $commentsByOeuvre,
            'initialDisplayCount' => $initialDisplayCount,
            'typeOeuvres' => TypeOeuvre::cases(),
            'collections' => $collectionsRepository->findBy([], ['id' => 'DESC'], 50),
        ]);
    }

    
    #[Route('/feed_peintures', name: 'app_feed_peintures')]
    public function indexPeintures(OeuvreRepository $oeuvreRepository, CollectionsRepository $collectionsRepository, CommentaireRepository $commentaireRepository, Request $request): Response
    {
        $peintures = $oeuvreRepository->findBy(['type' => TypeOeuvre::PEINTURE]);
        $commentsByOeuvre = $this->buildCommentsByOeuvre($peintures, $commentaireRepository);
        $initialDisplayCount = $this->getInitialDisplayCount($peintures, $request);

        return $this->render('Front Office/feed/feed.html.twig', [
            'controller_name' => 'FeedController',
            'oeuvres' => $peintures,
            'commentsByOeuvre' => $commentsByOeuvre,
            'initialDisplayCount' => $initialDisplayCount,
            'typeOeuvres' => TypeOeuvre::cases(),
            'collections' => $collectionsRepository->findBy([], ['id' => 'DESC'], 50),
        ]);
    }


    #[Route('/feed_sculptures', name: 'app_feed_sculptures')]
    public function indexSculptures(OeuvreRepository $oeuvreRepository, CollectionsRepository $collectionsRepository, CommentaireRepository $commentaireRepository, Request $request): Response
    {     
         
        $sculptures = $oeuvreRepository->findBy(['type' => TypeOeuvre::SCULPTURE]);
        $commentsByOeuvre = $this->buildCommentsByOeuvre($sculptures, $commentaireRepository);
        $initialDisplayCount = $this->getInitialDisplayCount($sculptures, $request);

        return $this->render('Front Office/feed/feed.html.twig', [
            'controller_name' => 'FeedController',
            'oeuvres' => $sculptures,
            'commentsByOeuvre' => $commentsByOeuvre,
            'initialDisplayCount' => $initialDisplayCount,
            'typeOeuvres' => TypeOeuvre::cases(),
            'collections' => $collectionsRepository->findBy([], ['id' => 'DESC'], 50),
        ]);
    }
    #[Route('/feed_photos', name: 'app_feed_photos')]
    public function indexPhotos(OeuvreRepository $oeuvreRepository, CollectionsRepository $collectionsRepository, CommentaireRepository $commentaireRepository, Request $request): Response
    {
        $photos = $oeuvreRepository->findBy(['type' => TypeOeuvre::PHOTOGRAPHIE]);
        $commentsByOeuvre = $this->buildCommentsByOeuvre($photos, $commentaireRepository);
        $initialDisplayCount = $this->getInitialDisplayCount($photos, $request);

        return $this->render('Front Office/feed/feed.html.twig', [
            'controller_name' => 'FeedController',
            'oeuvres' => $photos,
            'commentsByOeuvre' => $commentsByOeuvre,
            'initialDisplayCount' => $initialDisplayCount,
            'typeOeuvres' => TypeOeuvre::cases(),
            'collections' => $collectionsRepository->findBy([], ['id' => 'DESC'], 50),
        ]);
    }



    #[Route('/oeuvre/{id}/favorite', name: 'oeuvre_favorite')]
    public function favorite(Oeuvre $oeuvre, EntityManagerInterface $em, UserRepository $userRepository): Response
    {
    $user = $this->getUser();
    if (!$user) {
        throw $this->createNotFoundException('User not found');
    }

    // Toggle favorite
    if ($user->getFavUser()->contains($oeuvre)) {
        $user->removeFavUser($oeuvre);
    } else {
        $user->addFavUser($oeuvre);
    }

    $em->persist($user);
    $em->flush();

    return $this->redirectToRoute('app_feed'); // or wherever you came from
    }

    #[Route('/oeuvre/{id}/favorite-ajax', name: 'oeuvre_favorite_ajax', methods: ['POST'])]
    public function favoriteAjax(Oeuvre $oeuvre, EntityManagerInterface $em, Request $request): Response
    {
        if (!$request->isXmlHttpRequest()) {
            return $this->json(['success' => false, 'message' => 'Invalid request'], 400);
        }

        $user = $this->getUser();
        if (!$user) {
            return $this->json(['success' => false, 'message' => 'User not logged in'], 401);
        }

        // Toggle favorite
        $isFavorited = $user->getFavUser()->contains($oeuvre);
        if ($isFavorited) {
            $user->removeFavUser($oeuvre);
        } else {
            $user->addFavUser($oeuvre);
        }

        $em->persist($user);
        $em->flush();

        return $this->json([
            'success' => true,
            'favorited' => !$isFavorited,
            'favoriteCount' => $oeuvre->getUserFav()->count()
        ]);
    }

    #[Route('/oeuvre/{id}/like-ajax', name: 'oeuvre_like_ajax', methods: ['POST'])]
    public function likeAjax(Oeuvre $oeuvre, EntityManagerInterface $em, Request $request): Response
    {
        if (!$request->isXmlHttpRequest()) {
            return $this->json(['success' => false, 'message' => 'Invalid request'], 400);
        }

        $user = $this->getUser();
        if (!$user) {
            return $this->json(['success' => false, 'message' => 'User not logged in'], 401);
        }
        // Use repository lookup to find an existing Like for this user+oeuvre
        $likeRepo = $em->getRepository(\App\Entity\Like::class);
        $existingLike = $likeRepo->findOneBy(['oeuvre' => $oeuvre, 'user' => $user]);

        try {
            // Debug logging for troubleshooting persistent like issues
            try {
                $uid = method_exists($user, 'getId') ? $user->getId() : 'unknown';
                $eid = method_exists($oeuvre, 'getId') ? $oeuvre->getId() : 'unknown';
                $logLine = sprintf("[%s] likeAjax start: user=%s, oeuvre=%s, existingLike=%s\n", (new \DateTime())->format('c'), $uid, $eid, $existingLike ? 'yes' : 'no');
                @file_put_contents($this->getParameter('kernel.project_dir') . '/var/log/likes_debug.log', $logLine, FILE_APPEND | LOCK_EX);
            } catch (\Throwable $inner) {
                // ignore logging failures
            }

            if ($existingLike) {
                // Unlike - remove the like
                $oeuvre->removeLike($existingLike);
                $em->remove($existingLike);
                $isLiked = false;
            } else {
                // Like - create new like
                $like = new \App\Entity\Like();
                $like->setUser($user);
                $like->setOeuvre($oeuvre);
                $like->setLiked(true);
                $oeuvre->addLike($like);  // keep inverse side in sync
                $em->persist($like);
                $isLiked = true;
            }

            $em->flush();

            try {
                $logLine2 = sprintf("[%s] likeAjax flush succeeded: user=%s, oeuvre=%s, liked=%s\n", (new \DateTime())->format('c'), $uid, $eid, $isLiked ? 'true' : 'false');
                @file_put_contents($this->getParameter('kernel.project_dir') . '/var/log/likes_debug.log', $logLine2, FILE_APPEND | LOCK_EX);
            } catch (\Throwable) {}

            // Refresh the oeuvre to reflect the latest likes count
            try {
                $em->refresh($oeuvre);
            } catch (\Throwable $e) {
                // refresh may fail for detached entities; ignore safely
            }

            return $this->json([
                'success' => true,
                'liked' => $isLiked,
                'likeCount' => $oeuvre->getLikes()->count()
            ]);
        } catch (\Throwable $e) {
            // Log and return error so client doesn't assume success
            try {
                $errLine = sprintf("[%s] likeAjax error: %s\n", (new \DateTime())->format('c'), $e->getMessage());
                @file_put_contents($this->getParameter('kernel.project_dir') . '/var/log/likes_debug.log', $errLine, FILE_APPEND | LOCK_EX);
            } catch (\Throwable) {}
            return $this->json(['success' => false, 'message' => 'Could not toggle like'], 500);
        }
    }

    #[Route('/oeuvre/{id}/commentaire', name: 'oeuvre_commentaire', methods: ['POST'])]
    public function addCommentaire(Oeuvre $oeuvre, Request $request, EntityManagerInterface $em): Response
    {
        $contenu = trim((string) $request->request->get('contenu'));
        $isTurboFrame = $request->headers->has('Turbo-Frame');
        
        if ($contenu === '') {
            if ($isTurboFrame) {
                return $this->render('Front Office/feed/list.html.twig', [
                    'oeuvre' => $oeuvre
                ]);
            }
            return $this->redirect($this->buildRedirectWithFocus('/feed', $oeuvre->getId()));
        }

        $user = $this->getUser();
        if (!$user) {
            throw $this->createNotFoundException('User not found');
        }

        $commentaire = new Commentaire();
        $commentaire->setTexte($contenu);
        $commentaire->setUser($user);
        $oeuvre->addCommentaire($commentaire);
        $commentaire->setDateCommentaire(new \DateTime());
        $em->persist($commentaire);
        $em->flush();

        if ($isTurboFrame) {
            return $this->render('Front Office/feed/list.html.twig', [
                'oeuvre' => $oeuvre
            ]);
        }

        return $this->redirect($this->buildRedirectWithFocus('/feed', $oeuvre->getId()));
    }

    #[Route('/commentaire/{id}/delete', name: 'commentaire_delete', methods: ['POST'])]
    public function deleteCommentaire(int $id, Request $request, CommentaireRepository $commentaireRepository, OeuvreRepository $oeuvreRepository): Response
    {
        $user = $this->getUser();
        if (!$user) {
            return $this->redirectToRoute('app_feed');
        }

        $commentData = $commentaireRepository->findOwnershipDataById($id);

        if (!$commentData) {
            return $this->redirectToRoute('app_feed');
        }

        if ($commentData['userId'] !== $user->getId()) {
            $this->addFlash('error', 'Vous ne pouvez pas supprimer ce commentaire.');
            return $this->redirectToRoute('app_feed');
        }

        $submittedToken = (string) $request->request->get('_token');
        if (!$this->isCsrfTokenValid('delete_comment_'.$id, $submittedToken)) {
            $this->addFlash('error', 'Requête invalide.');
            return $this->redirectToRoute('app_feed');
        }

        $oeuvreId = $commentData['oeuvreId'];

        $commentaireRepository->deleteById($id);

        if ($request->headers->has('Turbo-Frame')) {
            $oeuvre = $oeuvreRepository->find($oeuvreId);
            if ($oeuvre) {
                return $this->render('Front Office/feed/list.html.twig', [
                    'oeuvre' => $oeuvre
                ]);
            }
        }

        return $this->redirect($this->buildRedirectWithFocus('/feed', (int) $oeuvreId));
    }

    
    #[Route('/feed/commentaire/{id}/edit', name: 'commentaire_edit', methods: ['POST'])]
    public function editCommentaire(int $id, Request $request, CommentaireRepository $commentaireRepository, OeuvreRepository $oeuvreRepository): Response
    {
        $user = $this->getUser();
        if (!$user) {
            return $this->redirectToRoute('app_feed');
        }

        $submittedToken = (string) $request->request->get('_token');
        if (!$this->isCsrfTokenValid('edit_comment_'.$id, $submittedToken)) {
            $this->addFlash('error', 'Requête invalide.');
            return $this->redirectToRoute('app_feed');
        }

        $contenu = trim((string) $request->request->get('contenu'));
        if ($contenu === '') {
            $this->addFlash('error', 'Le commentaire ne peut pas être vide.');
            return $this->redirectToRoute('app_feed');
        }

        $updatedRows = $commentaireRepository->updateTextIfOwnedByUser($id, $user->getId(), $contenu);

        if ($updatedRows === 0) {
            $this->addFlash('error', 'Vous ne pouvez pas modifier ce commentaire.');
            return $this->redirectToRoute('app_feed');
        }

        $oeuvreId = (int) $request->request->get('oeuvre_id', 0);

        if ($request->headers->has('Turbo-Frame') && $oeuvreId > 0) {
            $oeuvre = $oeuvreRepository->find($oeuvreId);
            if ($oeuvre) {
                return $this->render('Front Office/feed/list.html.twig', [
                    'oeuvre' => $oeuvre
                ]);
            }
        }

        return $this->redirect($this->buildRedirectWithFocus('/feed', $oeuvreId));
    }

    #[Route('/oeuvre/{id}/image', name: 'oeuvre_image', methods: ['GET'])]
    public function oeuvreImage(Oeuvre $oeuvre): Response
    {
        $imageData = $oeuvre->getImage();

        if (!$imageData) {
            throw $this->createNotFoundException('Image not found');
        }

        // Legacy BLOB/resource handling
        if (is_resource($imageData)) {
            try { rewind($imageData); } catch (\Throwable) {}
            $imageData = stream_get_contents($imageData);
            $finfo = new \finfo(FILEINFO_MIME_TYPE);
            $mimeType = $finfo->buffer($imageData) ?: 'image/jpeg';
            return new Response($imageData, 200, ['Content-Type' => $mimeType]);
        }

        // If image is a string: could be URL, absolute path or uploaded filename
        if (is_string($imageData)) {
            if (preg_match('#^https?://#i', $imageData)) {
                return $this->redirect($imageData);
            }

            $publicDir = $this->getParameter('kernel.project_dir') . '/public/';
            $candidate = $imageData;
            if (!file_exists($candidate)) {
                $candidate = $publicDir . ltrim($imageData, '/');
            }
            if (file_exists($candidate)) {
                $response = new \Symfony\Component\HttpFoundation\BinaryFileResponse($candidate);
                $response->headers->set('Content-Type', mime_content_type($candidate) ?: 'image/jpeg');
                $response->setContentDisposition(\Symfony\Component\HttpFoundation\ResponseHeaderBag::DISPOSITION_INLINE);
                return $response;
            }

            // Try uploads folder by filename
            $candidate = $publicDir . 'uploads/' . ltrim($imageData, '/');
            if (file_exists($candidate)) {
                $response = new \Symfony\Component\HttpFoundation\BinaryFileResponse($candidate);
                $response->headers->set('Content-Type', mime_content_type($candidate) ?: 'image/jpeg');
                $response->setContentDisposition(\Symfony\Component\HttpFoundation\ResponseHeaderBag::DISPOSITION_INLINE);
                return $response;
            }

            // Fallback: redirect to uploads path (may be served by webserver)
            return $this->redirect('/uploads/' . ltrim($imageData, '/'));
        }

        throw $this->createNotFoundException('Image not found');
    }

    #[Route('/user/{id}/photo', name: 'user_photo', methods: ['GET'])]
    public function userPhoto(User $user): Response
    {
        $filename = $user->getPhotoProfil();
        if (!$filename) {
            throw $this->createNotFoundException('Photo not found');
        }

        // Redirect to the external img folder
        return $this->redirect('http://127.0.0.1/img/' . $filename);
    }

    }
