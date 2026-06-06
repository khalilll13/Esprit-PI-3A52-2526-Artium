<?php

namespace App\Controller;

use App\Entity\Collections;
use App\Entity\Oeuvre;
use App\Entity\User;
use App\Form\CollectionsType;
use App\Repository\CollectionsRepository;
use App\Repository\UserRepository;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\BinaryFileResponse;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Attribute\Route;

#[Route('/artiste-collections')]
final class CollectionsController extends AbstractController
{
    #[Route(name: 'app_collections_front')]
    public function index(
        Request $request,
        CollectionsRepository $collectionsRepository,
        \App\Repository\ReclamationRepository $reclamationRepository,
        \App\Repository\EvenementRepository $evenementRepository,
        \App\Repository\CommentaireRepository $commentaireRepository,
        \App\Repository\LikeRepository $likeRepository
    ): Response
    {   
        $collections = $this->getUser()->getCollections();

        // Create empty form for modal display
        $form = $this->createForm(CollectionsType::class, new Collections());
        $formEdit = [];
        foreach ($collections as $collection) {
        $formEdit[$collection->getId()] = $this->createForm(CollectionsType::class, $collection)->createView();
        }

        // Statistiques dynamiques pour sidebar
        $user = $this->getUser();
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
        return $this->render('Front Office/collections_front/collectionsfront.html.twig', [
            'controller_name' => 'CollectionsController',
            'collections' => $this->getUser()->getCollections(),
            'form' => $form,
            'formEdit' => $formEdit,
            'nbOeuvres' => $nbOeuvres,
            'nbReclamations' => $nbReclamations,
            'nbEvenements' => $nbEvenements,
            'nbCommentaires' => $nbCommentaires,
            'nbLikes' => $nbLikes,
        ]);
    }

    #[Route('/store', name: 'app_collections_store', methods: ['POST'])]
    public function store(Request $request, EntityManagerInterface $entityManager, UserRepository $userRepository): Response
    {
        $collections = $this->getUser()->getCollections();
        $formEdit = [];
        foreach ($collections as $collection) {
        $formEdit[$collection->getId()] = $this->createForm(CollectionsType::class, $collection)->createView();
        }
        $collection = new Collections();
        $artiste = $this->getUser();
        $collection->setArtiste($artiste);
        $form = $this->createForm(CollectionsType::class, $collection);
        $form->handleRequest($request);

        if ($form->isSubmitted() && $form->isValid()) {
            $entityManager->persist($collection);
            $entityManager->flush();
            return $this->redirectToRoute('app_collections_front', [], Response::HTTP_SEE_OTHER);
        }

        return $this->render('Front Office/collections_front/collectionsfront.html.twig', [
        'form' => $form->createView(),
        'formEdit' => $formEdit, 
        'collections' => $this->getUser()->getCollections(),
    ]);
    }

    #[Route('/search', name: 'collection_search_first')]
    public function search_first(Request $request, CollectionsRepository $collectionsRepository): Response
    {
        $user = $this->getUser();
        if (!$user instanceof User) {
            throw $this->createAccessDeniedException('You must be logged in.');
        }

        $search = $request->query->get('q');
        $collections = $collectionsRepository->findByArtisteWithSearchFirst($user, $search);

        // Create empty form for modal display
        $form = $this->createForm(CollectionsType::class, new Collections());
        $formEdit = [];
        foreach ($collections as $collection) {
        $formEdit[$collection->getId()] = $this->createForm(CollectionsType::class, $collection)->createView();
        }

        return $this->render('Front Office/collections_front/collectionsfront.html.twig', [
            'collections' => $collections,
            'search' => $search,
            'form' => $form,
            'formEdit' => $formEdit, 
        ]);
    }

    #[Route('/test',name: 'app_collections_index', methods: ['GET'])]
    public function indexx(CollectionsRepository $collectionsRepository): Response
    {
        return $this->render('collections/index.html.twig', [
            'collections' => $collectionsRepository->findAll(),
        ]);
    }

    #[Route('/new', name: 'app_collections_new', methods: ['GET', 'POST'])]
    public function new(Request $request, EntityManagerInterface $entityManager): Response
    {
        $collection = new Collections();
        $form = $this->createForm(CollectionsType::class, $collection);
        $form->handleRequest($request);

        if ($form->isSubmitted() && $form->isValid()) {
            $entityManager->persist($collection);
            $entityManager->flush();

            return $this->redirectToRoute('app_collections_front', [], Response::HTTP_SEE_OTHER);
        }

        return $this->render('collections/new.html.twig', [
            'collection' => $collection,
            'form' => $form,
        ]);
    }

    #[Route('/oeuvre/{id}/image', name: 'app_collection_oeuvre_image', methods: ['GET'])]
    public function oeuvreImage(Oeuvre $oeuvre): Response
    {
        $user = $this->getUser();
        if (!$user instanceof User) {
            throw $this->createAccessDeniedException('You must be logged in.');
        }

        $oeuvreCollection = $oeuvre->getCollection();
        $oeuvreOwnerId = $oeuvreCollection?->getArtiste()?->getId();
        if ($oeuvreOwnerId !== $user->getId()) {
            throw $this->createAccessDeniedException('You are not allowed to access this image.');
        }

        $imageData = $oeuvre->getImage();
        if (!$imageData) {
            throw $this->createNotFoundException('Image not found');
        }

        // If a URL string was stored (e.g. http://127.0.0.1/img/...), redirect to it
        if (is_string($imageData)) {
            if (preg_match('/^https?:\/\//i', $imageData)) {
                return $this->redirect($imageData);
            }

            // Try to serve a local public file if the string looks like a path
            $projectDir = $this->getParameter('kernel.project_dir');
            $publicPath = $projectDir . '/public' . (str_starts_with($imageData, '/') ? $imageData : '/' . $imageData);
            if (is_file($publicPath)) {
                return new BinaryFileResponse($publicPath);
            }

            throw $this->createNotFoundException('Image not found');
        }

        // Legacy BLOB/resource handling
        if (is_resource($imageData)) {
            rewind($imageData);
            $imageData = stream_get_contents($imageData);
        }

        $finfo = new \finfo(FILEINFO_MIME_TYPE);
        $mimeType = $finfo->buffer($imageData) ?: 'image/jpeg';

        return new Response(
            $imageData,
            Response::HTTP_OK,
            ['Content-Type' => $mimeType]
        );
    }

    #[Route('/{id}', name: 'app_collections_show', methods: ['GET'])]
    public function show(Collections $collection): Response
    {
        return $this->render('collections/show.html.twig', [
            'collection' => $collection,
        ]);
    }

    #[Route('/{id}/edited', name: 'app_collections_edit', methods: ['GET', 'POST'])]
    public function edited(Request $request, Collections $collection, EntityManagerInterface $entityManager): Response
    {
        $form = $this->createForm(CollectionsType::class, $collection);
        $form->handleRequest($request);

        if ($form->isSubmitted() && $form->isValid()) {
            $entityManager->flush();

            return $this->redirectToRoute('app_collections_index', [], Response::HTTP_SEE_OTHER);
        }

        return $this->render('collections/edit.html.twig', [
            'collection' => $collection,
            'form' => $form,
        ]);
    }

    #[Route('/{id}', name: 'app_collections_delete', methods: ['POST'])]
    public function deleted(Request $request, Collections $collection, EntityManagerInterface $entityManager): Response
    {
        if ($this->isCsrfTokenValid('delete'.$collection->getId(), $request->getPayload()->getString('_token'))) {
            $entityManager->remove($collection);
            $entityManager->flush();
        }

        return $this->redirectToRoute('app_collections_index', [], Response::HTTP_SEE_OTHER);
    }

    #[Route('/{id}/edit', name: 'collection_edit', methods: ['GET', 'POST'])]
    public function edit(Collections $collection, Request $request, EntityManagerInterface $em,CollectionsRepository $collectionsRepository): Response
    {
        $collections = $this->getUser()->getCollections();
        $editForm = $this->createForm(CollectionsType::class, $collection);
        $editForm->handleRequest($request);

    if ($editForm->isSubmitted() && $editForm->isValid()) {
        $em->flush();
        return $this->redirectToRoute('app_collections_front', [], Response::HTTP_SEE_OTHER);
    }
    $form = $this->createForm(CollectionsType::class, new Collections());
    $formEdit = [];
    foreach ($collections as $artistCollection) {
        if ($artistCollection->getId() === $collection->getId() && $editForm->isSubmitted()) {
            $formEdit[$artistCollection->getId()] = $editForm->createView();
            continue;
        }

        $formEdit[$artistCollection->getId()] = $this->createForm(CollectionsType::class, $artistCollection)->createView();
    }

    return $this->render('Front Office/collections_front/collectionsfront.html.twig', [
        'form' => $form->createView(),
        'formEdit' => $formEdit,
        'collections' => $collections,
        'editErrorCollectionId' => $editForm->isSubmitted() && !$editForm->isValid() ? $collection->getId() : null,

    ]);
    }
    #[Route('{id}/delete', name: 'collection_delete', methods: ['POST'])]
    public function delete(Collections $collection, EntityManagerInterface $em): Response
    {
    if ($collection) {
        foreach ($collection->getOeuvres()->toArray() as $oeuvre) {
            foreach ($oeuvre->getLikes()->toArray() as $like) {
                $em->remove($like);
            }

            foreach ($oeuvre->getCommentaires()->toArray() as $commentaire) {
                $em->remove($commentaire);
            }

            foreach ($oeuvre->getUserFav()->toArray() as $user) {
                $oeuvre->removeUserFav($user);
            }

            $em->remove($oeuvre);
        }

        $em->remove($collection);
        $em->flush();
        $this->addFlash('success', 'Collection supprimée avec succès !');
    }

    return $this->redirectToRoute('app_collections_front');
   }



}
