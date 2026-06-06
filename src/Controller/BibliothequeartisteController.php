<?php

namespace App\Controller;

use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Attribute\Route;
use App\Repository\CollectionsRepository;
use App\Repository\LivreRepository;
use App\Repository\UserRepository;
use App\Repository\LocationLivreRepository;
use App\Entity\Livre;
use Symfony\Component\HttpFoundation\Request;
use Doctrine\ORM\EntityManagerInterface;
use App\Enum\TypeOeuvre;
use Symfony\Component\HttpFoundation\JsonResponse;
use App\Service\BookAiService;
use App\Service\FileStorageService;


final class BibliothequeartisteController extends AbstractController
{
    #[Route('/artiste-bibliotheque', name: 'app_bibliothequeartiste')]
    public function index(CollectionsRepository $collectionsRepository, LivreRepository $livreRepository, UserRepository $userRepository, LocationLivreRepository $locationLivreRepository): Response
    {
        
        /** @var \App\Entity\User|null $artist */
        $artist = $this->getUser();



        $collections = [];
        $livres = [];
        if ($artist) {
            $collections = $collectionsRepository->findBy(['artiste' => $artist]);

            // find livres that belong to this artist via collection relation
            $qb = $livreRepository->createQueryBuilder('l')
                ->join('l.collection', 'c')
                ->andWhere('c.artiste = :artist')
                ->setParameter('artist', $artist)
                ->orderBy('l.id', 'DESC');

            $livres = $qb->getQuery()->getResult();

            // compute basic stats per livre: count and total revenue (approx)
            $livreStats = [];
            foreach ($livres as $l) {
                $count = $locationLivreRepository->count(['livre' => $l]);
                $prix = $l->getPrixLocation() ?? 0;
                $total = $count * $prix;

                // find currently active rental (non-expired); fallback to latest active for history display
                $active = $locationLivreRepository->findCurrentActiveForLivre($l)
                    ?? $locationLivreRepository->findLatestActiveForLivre($l);

                $livreStats[$l->getId()] = [
                    'count' => $count,
                    'total' => $total,
                    'activeId' => $active ? $active->getId() : null,
                    'activeDate' => $active && $active->getDateDebut() ? $active->getDateDebut()->format('Y-m-d H:i:s') : null,
                    'activeDays' => $active && method_exists($active, 'getNombreDeJours') ? $active->getNombreDeJours() : null,
                ];
            }
        }

        return $this->render('Front Office/bibliothequeartiste/bibliothequeartiste.html.twig', [
            'controller_name' => 'BibliothequeartisteController',
            'collections' => $collections,
            'livres' => $livres,
            'livreStats' => $livreStats ?? [],
        ]);
    }

    #[Route('/artiste-bibliotheque/new', name: 'artiste_livre_create', methods: ['POST'])]
    public function create(Request $request, EntityManagerInterface $em, CollectionsRepository $collectionsRepository, UserRepository $userRepository, FileStorageService $fileStorageService): Response
    {
        // TODO: Replace test artist with $this->getUser() when authentication module is merged
        /** @var \App\Entity\User|null $artist */
        $artist = $this->getUser();
        if (!$artist) {
            $this->addFlash('error', 'Artiste de test introuvable.');
            return $this->redirectToRoute('app_bibliothequeartiste');
        }

        $livre = new Livre();

        // scalar fields
        $titre = $request->request->get('titre');
        if ($titre !== null) {
            $livre->setTitre($titre);
        }

        $description = $request->request->get('description');
        if ($description !== null) {
            $livre->setDescription($description);
        }

        $categorie = $request->request->get('categorie');
        if ($categorie !== null) {
            $livre->setCategorie($categorie);
        }

        $prix = $request->request->get('prix_location');
        if ($prix !== null && $prix !== '') {
            $livre->setPrixLocation((float) $prix);
        }

        // collection: ensure it belongs to the test artist (ID 1)
        $collectionId = $request->request->get('collection');
        $collection = null;
        if ($collectionId) {
            $collection = $collectionsRepository->find((int)$collectionId);
            if (!$collection || !$collection->getArtiste() || $collection->getArtiste()->getId() !== $artist->getId()) 
                {
                $this->addFlash('error', 'Collection invalide.');
                return $this->redirectToRoute('app_bibliothequeartiste');
            }
        } else {
            // if no collection provided, assign the first collection for this test artist
            $cols = $collectionsRepository->findBy(['artiste' => $artist]);
            if (count($cols) > 0) {
                $collection = $cols[0];
            }
        }

        if ($collection && method_exists($livre, 'setCollection')) {
            $livre->setCollection($collection);
        }

        // set required Oeuvre fields
        if (null === $livre->getDateCreation()) {
            $livre->setDateCreation(new \DateTime());
        }
        if (null === $livre->getType()) {
            $livre->setType(TypeOeuvre::LIVRE);
        }

        // handle uploaded image (stored in C:\xampp\htdocs\img as public URL)
        try {
            $imageFile = $request->files->get('image');
            if ($imageFile) {
                $newFilename = $fileStorageService->uploadImage($imageFile, 'livre_');
                $livre->setImage($fileStorageService->getImageUrl($newFilename));
            }
        } catch (\Throwable $e) {
            // ignore image errors
        }

        // handle uploaded pdf (stored in C:\xampp\htdocs\pdf as public URL)
        try {
            $pdfFile = $request->files->get('fichier_pdf');
            if ($pdfFile) {
                $newFilename = $fileStorageService->uploadPdf($pdfFile, 'pdf_');
                $livre->setFichierPdf($fileStorageService->getPdfUrl($newFilename));
            }
        } catch (\Throwable $e) {
            // ignore pdf errors
        }

        $em->persist($livre);
        $em->flush();

        $this->addFlash('success', 'Livre créé avec succès.');

        return $this->redirectToRoute('app_bibliothequeartiste');
    }

  #[Route('/artiste-bibliotheque/livre/{id}/edit', name: 'artiste_livre_edit', methods: ['POST'])]
public function edit(
    Livre $livre,
    Request $request,
    EntityManagerInterface $em,
    CollectionsRepository $collectionsRepository,
    LocationLivreRepository $locationLivreRepository,
    FileStorageService $fileStorageService
): Response {

    /** @var \App\Entity\User|null $artist */
    $artist = $this->getUser();

    if (!$artist) {
        $this->addFlash('error', 'Artiste introuvable.');
        return $this->redirectToRoute('app_bibliothequeartiste');
    }

    // 🔒 Ensure the book belongs to this artist
    if (
        !$livre->getCollection() ||
        !$livre->getCollection()->getArtiste() ||
        $livre->getCollection()->getArtiste()->getId() !== $artist->getId()
    ) {
        throw $this->createAccessDeniedException();
    }

    // 🔎 Check if book is currently rented
    $activeLocation = $locationLivreRepository->findCurrentActiveForLivre($livre);

    $isRented = $activeLocation ? true : false;

    // 📥 Get submitted data
    $all = $request->request->all();
    $data = (isset($all['livre']) && is_array($all['livre'])) ? $all['livre'] : [];

    $restrictedChangeAttempted = false;

    // =========================
    // ✅ ALWAYS ALLOWED
    // =========================

    if (isset($data['titre'])) {
        $livre->setTitre($data['titre']);
    }

    if (isset($data['description'])) {
        $livre->setDescription($data['description']);
    }

    // =========================
    // 🔒 IF RENTED → BLOCK STRUCTURAL CHANGES
    // =========================

    if ($isRented) {

        // Detect forbidden changes

        if (isset($data['categorie']) && $data['categorie'] !== $livre->getCategorie()) {
            $restrictedChangeAttempted = true;
        }

        if (isset($data['prix_location']) && 
            $data['prix_location'] !== '' &&
            (float)$data['prix_location'] !== (float)$livre->getPrixLocation()) {
            $restrictedChangeAttempted = true;
        }

        if (isset($data['collection']) && 
            (int)$data['collection'] !== (int)$livre->getCollection()?->getId()) {
            $restrictedChangeAttempted = true;
        }

        if ($restrictedChangeAttempted) {
            $this->addFlash(
                'warning',
                'Certaines modifications ne sont pas autorisées pendant une location active.'
            );
        } else {
            $this->addFlash('success', 'Livre mis à jour avec succès.');
        }

    } else {

        // =========================
        // ✅ FULL EDIT ALLOWED
        // =========================

        if (isset($data['categorie'])) {
            $livre->setCategorie($data['categorie']);
        }

        if (isset($data['prix_location']) && $data['prix_location'] !== '') {
            $livre->setPrixLocation((float)$data['prix_location']);
        }

        if (isset($data['collection'])) {

            $collection = $collectionsRepository->find((int)$data['collection']);

            if (
                !$collection ||
                !$collection->getArtiste() ||
                $collection->getArtiste()->getId() !== $artist->getId()
            ) {
                $this->addFlash('error', 'Collection invalide.');
                return $this->redirectToRoute('app_bibliothequeartiste');
            }

            $livre->setCollection($collection);
        }

        $this->addFlash('success', 'Livre mis à jour avec succès.');
    }

    // =========================
    // 🖼 IMAGE UPDATE (always allowed)
    // =========================

    try {
        $files = $request->files->get('livre', []);
        $imageFile = $files['image'] ?? null;

        if ($imageFile) {
            $newFilename = $fileStorageService->uploadImage($imageFile, 'livre_');
            $livre->setImage($fileStorageService->getImageUrl($newFilename));
        }
    } catch (\Throwable $e) {
        // ignore
    }

    // =========================
    // 📄 PDF UPDATE (always allowed)
    // =========================

    try {
        $files = $request->files->get('livre', []);
        $pdfFile = $files['fichier_pdf'] ?? null;

        if ($pdfFile) {
            $newFilename = $fileStorageService->uploadPdf($pdfFile, 'pdf_');
            $livre->setFichierPdf($fileStorageService->getPdfUrl($newFilename));
        }
    } catch (\Throwable $e) {
        // ignore
    }

    $em->persist($livre);
    $em->flush();

    return $this->redirectToRoute('app_bibliothequeartiste');
}

#[Route('/artiste-bibliotheque/livre/{id}/delete', name: 'artiste_livre_delete', methods: ['POST'])]
public function delete(
    Livre $livre,
    EntityManagerInterface $em,
    LocationLivreRepository $locationLivreRepository
): Response
{
    if (!$livre) {
        $this->addFlash('error', 'Livre introuvable.');
        return $this->redirectToRoute('app_bibliothequeartiste');
    }

    // Check if there is an ACTIVE rental
    $activeLocation = $locationLivreRepository->findCurrentActiveForLivre($livre);

    if ($activeLocation) {
        $this->addFlash(
            'error',
            "Ce livre est actuellement loué. Vous ne pouvez pas le supprimer avant l'expiration."
        );

        return $this->redirectToRoute('app_bibliothequeartiste');
    }

    $em->remove($livre);
    $em->flush();

    $this->addFlash('success', 'Livre supprimé avec succès.');

    return $this->redirectToRoute('app_bibliothequeartiste');
}


#[Route('/ai/generate-book', name: 'ai_generate_book', methods: ['POST'])]
public function generateBook(
    Request $request,
    BookAiService $bookAiService
): JsonResponse {

    $file = $request->files->get('fichier_pdf');

    if (!$file) {
        return $this->json([
            'error' => 'Veuillez ajouter un fichier PDF.'
        ], 400);
    }

    // Security: check mime type
    if ($file->getMimeType() !== 'application/pdf') {
        return $this->json([
            'error' => 'Le fichier doit être un PDF valide.'
        ], 400);
    }

    try {
        $data = $bookAiService->generateFromPdf($file->getPathname());

        return $this->json($data);

    } catch (\Exception $e) {
        return $this->json([
            'error' => 'Erreur lors de la génération automatique: ' . $e->getMessage()
        ], 500);
    }
}

}

   

