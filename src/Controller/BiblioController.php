<?php

namespace App\Controller;

use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\HttpFoundation\BinaryFileResponse;
use Symfony\Component\HttpFoundation\ResponseHeaderBag;
use Symfony\Component\Routing\Attribute\Route;
use Doctrine\ORM\EntityManagerInterface;
use App\Entity\Livre;
use App\Form\LivreType;
use App\Repository\LivreRepository;
use App\Service\FileStorageService;
use App\Enum\TypeOeuvre;
use Knp\Component\Pager\PaginatorInterface;
use App\Repository\LocationLivreRepository;

final class BiblioController extends AbstractController
{
    #[Route('/bibliotheque', name: 'livres')]
    public function index(Request $request, LivreRepository $livreRepository, LocationLivreRepository $locationRepo, PaginatorInterface $paginator): Response
    {
        $form = $this->createForm(LivreType::class);
        // separate empty form instance to be used for editing (populated client-side)
        $editForm = $this->createForm(LivreType::class);
        // search / sort / pagination parameters (GET)
        $q = $request->query->get('q');
        $sort = $request->query->get('sort');
        $queryBuilder = $livreRepository->createQueryBuilder('l');

        if ($q) {
            $queryBuilder
            ->andWhere('l.titre LIKE :q')
            ->setParameter('q', '%' . $q . '%');
        }

        if ($sort === 'asc') {
            $queryBuilder->orderBy('l.titre', 'ASC');
        } elseif ($sort === 'desc') {
            $queryBuilder->orderBy('l.titre', 'DESC');
        } else {
            $queryBuilder->orderBy('l.id', 'DESC');
        }

        $livres = $paginator->paginate(
            $queryBuilder, 
            $request->query->getInt('page', 1),
            8 // 👈 LIMIT PER PAGE (as you requested)
        );

        $statusMap = [];

foreach ($livres as $livre) {

    $isActive = false;
    $activeLocation = null;
    $expiration = null;

    foreach ($livre->getLocationLivres() as $loc) {

        if (!$loc->getEtat() || $loc->getEtat()->value !== 'Active') {
            continue;
        }

        $start = $loc->getDateDebut();
        if (!$start) {
            continue;
        }

        $days = $loc->getNombreDeJours();

        if (!$days || $days <= 0) {
            continue; // ignore invalid rentals
        }

        $expiration = (clone $start)->modify("+$days days");
        $now = new \DateTime();

        if ($start <= $now && $expiration > $now) {
            $isActive = true;
            $activeLocation = $loc;
            break;
        }
    }

    $statusMap[$livre->getId()] = [
        'isActive' => $isActive,
        'locationId' => $activeLocation?->getId() ?? null,
        'startDate' => $activeLocation?->getDateDebut()?->format('Y-m-d H:i:s') ?? null,
        'expirationDate' => isset($expiration) ? $expiration->format('Y-m-d H:i:s') : null
    ];
}

        return $this->render('biblio/livres.html.twig', [
            'controller_name' => 'BiblioController',
            'livreForm' => $form->createView(),
            'livreEditForm' => $editForm->createView(),
            'livres' => $livres,
            'livreStatus' => $statusMap,
            'search_q' => $q,
            'search_sort' => $sort,
        ]);
    }

    #[Route('/bibliotheque/new', name: 'livre_create', methods: ['POST'])]
    public function create(Request $request, EntityManagerInterface $em, FileStorageService $fileStorageService): Response
    {
        $livre = new Livre();
        $form = $this->createForm(LivreType::class, $livre);
        $form->handleRequest($request);
        if ($form->isSubmitted()) {
            // Ensure mapped scalar fields are explicitly set on the entity (covers inherited fields)
            $titre = $form->get('titre')->getData();
            if ($titre !== null) {
                $livre->setTitre($titre);
            }

            $description = $form->get('description')->getData();
            if ($description !== null) {
                $livre->setDescription($description);
            }

            $categorie = $form->get('categorie')->getData();
            if ($categorie !== null) {
                $livre->setCategorie($categorie);
            }

            $prix = $form->get('prix_location')->getData();
            if ($prix !== null && $prix !== '') {
                $livre->setPrixLocation((float) $prix);
            }

            // Explicitly set the Collection relation (ManyToOne)
            if ($form->has('collection')) {
                $collection = $form->get('collection')->getData();
                if ($collection !== null) {
                    $livre->setCollection($collection);
                }
            }

            // set required Oeuvre fields if not already set
            if (null === $livre->getDateCreation()) {
                $livre->setDateCreation(new \DateTime());
            }
            if (null === $livre->getType()) {
                $livre->setType(TypeOeuvre::LIVRE);
            }

            // handle uploaded image (store as blob) safely
            try {
                $imageFile = $form->get('image')->getData();
                if ($imageFile) {
                    $newFilename = $fileStorageService->uploadImage($imageFile, 'livre_');
                    $livre->setImage($newFilename);
                }
            } catch (\Throwable $e) {
                // swallow file errors to avoid blocking persistence; validation should catch size/type
            }

            // handle uploaded pdf safely
            try {
                $pdfFile = $form->get('fichier_pdf')->getData();
                if ($pdfFile) {
                    $newFilename = $fileStorageService->uploadPdf($pdfFile, 'pdf_');
                    $livre->setFichierPdf($newFilename);
                }
            } catch (\Throwable $e) {
                // swallow file errors
            }

            // Only persist when the form is valid
            if ($form->isValid()) {
                $em->persist($livre);
                $em->flush();

                $this->addFlash('success', 'Livre créé avec succès.');

                return $this->redirectToRoute('livres');
            }
        }

        // If invalid, render the index with form errors
        return $this->render('biblio/livres.html.twig', [
            'controller_name' => 'BiblioController',
            'livreForm' => $form->createView(),
        ]);
    }

    #[Route('/bibliotheque/livre/{id}/edit', name: 'livre_edit', methods: ['POST'])]
    public function edit(Livre $livre, Request $request, EntityManagerInterface $em, FileStorageService $fileStorageService): Response
    {
        $form = $this->createForm(LivreType::class, $livre);
        $form->handleRequest($request);

        if ($form->isSubmitted()) {
            // scalar fields
            $titre = $form->get('titre')->getData();
            if ($titre !== null) {
                $livre->setTitre($titre);
            }

            $description = $form->get('description')->getData();
            if ($description !== null) {
                $livre->setDescription($description);
            }

            $categorie = $form->get('categorie')->getData();
            if ($categorie !== null) {
                $livre->setCategorie($categorie);
            }

            $prix = $form->get('prix_location')->getData();
            if ($prix !== null && $prix !== '') {
                $livre->setPrixLocation((float) $prix);
            }

            if ($form->has('collection')) {
                $collection = $form->get('collection')->getData();
                if ($collection !== null) {
                    $livre->setCollection($collection);
                }
            }

            // handle replacing image
            try {
                $imageFile = $form->get('image')->getData();
                if ($imageFile) {
                    $newFilename = $fileStorageService->uploadImage($imageFile, 'livre_');
                    $livre->setImage($newFilename);
                }
            } catch (\Throwable $e) {
                // ignore image errors
            }

            // handle replacing pdf
            try {
                $pdfFile = $form->get('fichier_pdf')->getData();
                if ($pdfFile) {
                    $newFilename = $fileStorageService->uploadPdf($pdfFile, 'pdf_');
                    $livre->setFichierPdf($newFilename);
                }
            } catch (\Throwable $e) {
                // ignore pdf errors
            }

            if ($form->isValid()) {
                $em->persist($livre);
                $em->flush();

                $this->addFlash('success', 'Livre mis à jour avec succès.');

                return $this->redirectToRoute('livres');
            }
        }

        $this->addFlash('error', 'Impossible de mettre à jour le livre.');
        return $this->redirectToRoute('livres');
    }

    #[Route('/bibliotheque/livre/{id}/delete', name: 'livre_delete', methods: ['POST'])]
    public function delete(Livre $livre, EntityManagerInterface $em): Response
    {
        $em->remove($livre);
        $em->flush();

        $this->addFlash('success', 'Livre supprimé avec succès.');

        return $this->redirectToRoute('livres');
    }

    #[Route('/bibliotheque/livre/{id}/pdf', name: 'livre_pdf', methods: ['GET'])]
    public function pdf(Livre $livre): Response
    {
        $filename = $livre->getFichierPdf();
        if (!$filename) {
            throw $this->createNotFoundException('PDF introuvable');
        }

        if (preg_match('#^https?://#i', $filename)) {
            return $this->redirect($filename);
        }

        if (str_starts_with($filename, '/pdf/')) {
            return $this->redirect('http://127.0.0.1' . $filename);
        }

        // Serve PDF from external folder
        $pdfPath = 'C:\\xampp\\htdocs\\pdf\\' . $filename;
        if (!file_exists($pdfPath)) {
            throw $this->createNotFoundException('Fichier PDF non trouvé');
        }

        $response = new BinaryFileResponse($pdfPath);
        $response->headers->set('Content-Type', 'application/pdf');
        $response->setContentDisposition(ResponseHeaderBag::DISPOSITION_INLINE, $filename);
        return $response;
    }

    #[Route('/bibliotheque/livre/{id}/image', name: 'livre_image', methods: ['GET'])]
    public function image(Livre $livre): Response
    {
        $imageData = $livre->getImage();
        if (!$imageData) {
            throw $this->createNotFoundException('Image introuvable');
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

            // Try external img folder first (new storage)
            $extImgPath = 'C:\\xampp\\htdocs\\img\\' . $imageData;
            if (file_exists($extImgPath)) {
                $response = new BinaryFileResponse($extImgPath);
                $response->headers->set('Content-Type', mime_content_type($extImgPath) ?: 'image/jpeg');
                $response->setContentDisposition(ResponseHeaderBag::DISPOSITION_INLINE);
                return $response;
            }

            // Try public folder (legacy)
            $publicDir = $this->getParameter('kernel.project_dir') . '/public/';
            $candidate = $imageData;
            if (!file_exists($candidate)) {
                $candidate = $publicDir . ltrim($imageData, '/');
            }
            if (file_exists($candidate)) {
                $response = new BinaryFileResponse($candidate);
                $response->headers->set('Content-Type', mime_content_type($candidate) ?: 'image/jpeg');
                $response->setContentDisposition(ResponseHeaderBag::DISPOSITION_INLINE);
                return $response;
            }

            // Try uploads folder by filename (legacy)
            $candidate = $publicDir . 'uploads/' . ltrim($imageData, '/');
            if (file_exists($candidate)) {
                $response = new BinaryFileResponse($candidate);
                $response->headers->set('Content-Type', mime_content_type($candidate) ?: 'image/jpeg');
                $response->setContentDisposition(ResponseHeaderBag::DISPOSITION_INLINE);
                return $response;
            }

            // Fallback: redirect to uploads path (may be served by webserver)
            return $this->redirect('/uploads/' . ltrim($imageData, '/'));
        }

        throw $this->createNotFoundException('Image introuvable');
    }
}
