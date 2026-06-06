<?php

namespace App\Controller;
use App\Entity\Oeuvre;
use App\Entity\Commentaire;
use App\Repository\UserRepository; 
use App\Repository\CommentaireRepository;
use App\Repository\OeuvreRepository;
use App\Enum\TypeOeuvre;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\BinaryFileResponse;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\Routing\Attribute\Route;
use Doctrine\ORM\EntityManagerInterface;

final class FavorisController extends AbstractController
{
    /*#[Route('/favoris', name: 'app_favoris')]
    public function index(): Response
    {
        return $this->render('Front Office/favoris/favoris.html.twig', [
            'controller_name' => 'FavorisController',
        ]);
    }*/
    #[Route('/favoris', name: 'app_favoris')]
    public function userFavorites(UserRepository $userRepository): Response
    {
    $user = $this->getUser();

    if (!$user) {
        throw $this->createNotFoundException('User not found');
    }

    $favoriteOeuvres = $user->getFavUser()->toArray();

    $favoritesPeintures = [];
    $favoritesSculptures = [];
    $favoritesPhotographies = [];
    foreach ($favoriteOeuvres as $oeuvre) {
        $type = $oeuvre->getType();
        if ($type === TypeOeuvre::PEINTURE) {
            $favoritesPeintures[] = $oeuvre;
        } elseif ($type === TypeOeuvre::SCULPTURE) {
            $favoritesSculptures[] = $oeuvre;
        } elseif ($type === TypeOeuvre::PHOTOGRAPHIE) {
            $favoritesPhotographies[] = $oeuvre;
        }
    }

    return $this->render('Front Office/favoris/favoris.html.twig', [
        'user' => $user,
        'favorites' => $favoriteOeuvres,
        'favoritesPeintures' => $favoritesPeintures,
        'favoritesSculptures' => $favoritesSculptures,
        'favoritesPhotographies' => $favoritesPhotographies,
    ]);
    }

    #[Route('/favoris/oeuvre/{id}/image', name: 'favoris_oeuvre_image', methods: ['GET'])]
    public function oeuvreImage(Oeuvre $oeuvre): Response
    {
        $imageData = $oeuvre->getImage();

        if (!$imageData) {
            throw $this->createNotFoundException('Image not found');
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
                $response = new BinaryFileResponse($candidate);
                $response->headers->set('Content-Type', mime_content_type($candidate) ?: 'image/jpeg');
                $response->setContentDisposition(\Symfony\Component\HttpFoundation\ResponseHeaderBag::DISPOSITION_INLINE);
                return $response;
            }

            // Try uploads folder by filename
            $candidate = $publicDir . 'uploads/' . ltrim($imageData, '/');
            if (file_exists($candidate)) {
                $response = new BinaryFileResponse($candidate);
                $response->headers->set('Content-Type', mime_content_type($candidate) ?: 'image/jpeg');
                $response->setContentDisposition(\Symfony\Component\HttpFoundation\ResponseHeaderBag::DISPOSITION_INLINE);
                return $response;
            }

            // Fallback: redirect to uploads path (may be served by webserver)
            return $this->redirect('/uploads/' . ltrim($imageData, '/'));
        }

        // Legacy BLOB/resource handling
        if (is_resource($imageData)) {
            try { rewind($imageData); } catch (\Throwable) {}
            $imageData = stream_get_contents($imageData);
            $finfo = new \finfo(FILEINFO_MIME_TYPE);
            $mimeType = $finfo->buffer($imageData) ?: 'image/jpeg';
            return new Response($imageData, 200, ['Content-Type' => $mimeType]);
        }

        throw $this->createNotFoundException('Image not found');
    }

    #[Route('/favoris/oeuvre/{id}/commentaire', name: 'favoris_commentaire', methods: ['POST'])]
    public function addCommentaire(Oeuvre $oeuvre, Request $request, EntityManagerInterface $em): Response
    {
        $contenu = trim((string) $request->request->get('contenu'));
        $isTurboFrame = $request->headers->has('Turbo-Frame');
        
        if ($contenu === '') {
            if ($isTurboFrame) {
                return $this->render('Front Office/favoris/_favoris_comments_frame.html.twig', [
                    'oeuvre' => $oeuvre
                ]);
            }
            return $this->redirectToRoute('app_favoris', ['_fragment' => 'oeuvre-' . $oeuvre->getId()]);
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
            return $this->render('Front Office/favoris/_favoris_comments_frame.html.twig', [
                'oeuvre' => $oeuvre
            ]);
        }

        return $this->redirectToRoute('app_favoris', ['_fragment' => 'oeuvre-' . $oeuvre->getId()]);
    }

    #[Route('/favoris/commentaire/{id}/delete', name: 'favoris_commentaire_delete', methods: ['POST'])]
    public function deleteCommentaire(int $id, Request $request, CommentaireRepository $commentaireRepository, OeuvreRepository $oeuvreRepository): Response
    {
        $user = $this->getUser();
        if (!$user) {
            return $this->redirectToRoute('app_favoris');
        }

        $commentData = $commentaireRepository->findOwnershipDataById($id);

        if (!$commentData) {
            return $this->redirectToRoute('app_favoris');
        }

        if ($commentData['userId'] !== $user->getId()) {
            $this->addFlash('error', 'Vous ne pouvez pas supprimer ce commentaire.');
            return $this->redirectToRoute('app_favoris');
        }

        $submittedToken = (string) $request->request->get('_token');
        if (!$this->isCsrfTokenValid('delete_comment_'.$id, $submittedToken)) {
            $this->addFlash('error', 'Requête invalide.');
            return $this->redirectToRoute('app_favoris');
        }

        $oeuvreId = $commentData['oeuvreId'];

        $commentaireRepository->deleteById($id);

        if ($request->headers->has('Turbo-Frame')) {
            $oeuvre = $oeuvreRepository->find($oeuvreId);
            if ($oeuvre) {
                return $this->render('Front Office/favoris/_favoris_comments_frame.html.twig', [
                    'oeuvre' => $oeuvre
                ]);
            }
        }

        return $this->redirectToRoute('app_favoris', ['_fragment' => 'oeuvre-' . $oeuvreId]);
    }

    #[Route('/favoris/commentaire/{id}/edit', name: 'favoris_commentaire_edit', methods: ['POST'])]
    public function editCommentaire(int $id, Request $request, CommentaireRepository $commentaireRepository, OeuvreRepository $oeuvreRepository): Response
    {
        $user = $this->getUser();
        if (!$user) {
            return $this->redirectToRoute('app_favoris');
        }

        $submittedToken = (string) $request->request->get('_token');
        if (!$this->isCsrfTokenValid('edit_comment_'.$id, $submittedToken)) {
            $this->addFlash('error', 'Requête invalide.');
            return $this->redirectToRoute('app_favoris');
        }

        $contenu = trim((string) $request->request->get('contenu'));
        if ($contenu === '') {
            $this->addFlash('error', 'Le commentaire ne peut pas être vide.');
            return $this->redirectToRoute('app_favoris');
        }

        $updatedRows = $commentaireRepository->updateTextIfOwnedByUser($id, $user->getId(), $contenu);

        if ($updatedRows === 0) {
            $this->addFlash('error', 'Vous ne pouvez pas modifier ce commentaire.');
            return $this->redirectToRoute('app_favoris');
        }

        $oeuvreId = (int) $request->request->get('oeuvre_id', 0);

        if ($request->headers->has('Turbo-Frame') && $oeuvreId > 0) {
            $oeuvre = $oeuvreRepository->find($oeuvreId);
            if ($oeuvre) {
                return $this->render('Front Office/favoris/_favoris_comments_frame.html.twig', [
                    'oeuvre' => $oeuvre
                ]);
            }
        }

        return $this->redirectToRoute('app_favoris', ['_fragment' => 'oeuvre-' . $oeuvreId]);
    }

}
