<?php

namespace App\Controller;

use App\Entity\Musique;
use App\Entity\User;
use App\Enum\TypeOeuvre;
use App\Form\MusiqueType;
use App\Repository\CollectionsRepository;
use App\Repository\MusiqueRepository;
use App\Service\FileStorageService;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\BinaryFileResponse;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\HttpFoundation\ResponseHeaderBag;
use Symfony\Component\Routing\Attribute\Route;
use Symfony\Contracts\HttpClient\HttpClientInterface;

final class MusiqueartisteController extends AbstractController
{
    #[Route('/musiqueartiste', name: 'app_musiqueartiste')]
    public function index(
        Request $request, 
        MusiqueRepository $musiqueRepository, 
        CollectionsRepository $collectionsRepository,
        EntityManagerInterface $entityManager,
        FileStorageService $fileStorageService
    ): Response
    {
        $currentUser = $this->getUser();
        if (!$currentUser instanceof User) {
            throw $this->createAccessDeniedException('You must be logged in as an artist.');
        }

        $artistCollections = $collectionsRepository->findBy(['artiste' => $currentUser], ['titre' => 'ASC']);

        // Create new Musique entity
        $musique = new Musique();
        
        // Create the form
        $form = $this->createForm(MusiqueType::class, $musique, [
            'collection_choices' => $artistCollections,
        ]);
        $form->handleRequest($request);

        if (empty($artistCollections)) {
            $this->addFlash('error', 'Aucune collection disponible. Créez d\'abord une collection.');
        }
        
        // Handle form submission
        if ($form->isSubmitted()) {
            // First check if form is valid
            if (!$form->isValid()) {
                // Collect all validation errors
                $errors = [];
                foreach ($form->getErrors(true) as $error) {
                    $errors[] = $error->getMessage();
                }
                
                // Add field-specific errors
                foreach ($form as $child) {
                    foreach ($child->getErrors() as $error) {
                        $errors[] = $error->getMessage();
                    }
                }
                
                if (!empty($errors)) {
                    $this->addFlash('error', 'Validation failed: ' . implode(', ', $errors));
                }
                
                // Fetch music to re-render the page with form errors
                $searchTerm = $request->query->get('search', '');
                $sortBy = $request->query->get('sort', 'date');
                $sortOrder = $request->query->get('order', 'DESC');
                
                if (!in_array($sortOrder, ['ASC', 'DESC'])) {
                    $sortOrder = 'DESC';
                }
                
                if ($searchTerm) {
                    $musiques = $musiqueRepository->searchAndFilter($searchTerm, $sortBy, $sortOrder, $currentUser->getId());
                } else {
                    $musiques = $musiqueRepository->searchAndFilter(null, $sortBy, $sortOrder, $currentUser->getId());
                }
                
                return $this->render('Front Office/musiqueartiste/musiqueartiste.html.twig', [
                    'controller_name' => 'MusiqueartisteController',
                    'musiques' => $musiques,
                    'form' => $form->createView(),
                    'searchTerm' => $searchTerm,
                    'sortBy' => $sortBy,
                    'sortOrder' => $sortOrder,
                ]);
            }
            
            // Form is valid, proceed with file upload
            try {
                // Handle image upload using FileStorageService
                $imageFile = $form->get('imageFile')->getData();
                if ($imageFile) {
                    if ($imageFile->getSize() > 5242880) { // 5MB
                        throw new \Exception('Image file exceeds maximum size of 5MB');
                    }
                    $newFilename = $fileStorageService->uploadImage($imageFile, 'music_');
                    $musique->setImage($fileStorageService->getImageUrl($newFilename));
                }
                
                // Handle audio upload
                $audioFile = $form->get('audioFile')->getData();
                if ($audioFile) {
                    $musique->setAudioFile($audioFile);
                }
                
                // Validate required fields are not empty after file processing
                if (!$musique->getTitre() || trim($musique->getTitre()) === '') {
                    throw new \Exception('Title cannot be empty');
                }
                if (!$musique->getDescription() || trim($musique->getDescription()) === '') {
                    throw new \Exception('Description cannot be empty');
                }
                if (!$musique->getGenre()) {
                    throw new \Exception('Genre must be selected');
                }
                if (!$audioFile) {
                    throw new \Exception('Audio file is required');
                }
                
                // Set creation date
                $musique->setDateCreation(new \DateTime());
                
                // Set type to MUSIQUE
                $musique->setType(TypeOeuvre::MUSIQUE);
                
                // Validate selected collection belongs to current artist
                $selectedCollection = $musique->getCollection();
                if (!$selectedCollection) {
                    throw new \Exception('Please select a collection.');
                }

                if (!$selectedCollection->getId()) {
                    throw new \Exception('Invalid collection selected.');
                }

                if ($selectedCollection->getArtiste()?->getId() !== $currentUser->getId()) {
                    throw new \Exception('You can only add music to your own collections.');
                }
                
                // Save to database
                $entityManager->persist($musique);
                $entityManager->flush();
                
                // Add success flash message
                $this->addFlash('success', 'Music added successfully!');
                
                // Redirect to avoid form resubmission
                return $this->redirectToRoute('app_musiqueartiste');
                
            } catch (\Exception $e) {
                // Handle database errors and file errors
                $errorMsg = 'An error occurred while uploading music.';
                
                if (strpos($e->getMessage(), 'server has gone away') !== false ||
                    strpos($e->getMessage(), 'max_allowed_packet') !== false) {
                    $errorMsg = 'The audio file is too large or the server upload limit was reached.';
                } elseif (strpos($e->getMessage(), 'Failed to read') !== false) {
                    $errorMsg = 'Failed to read file. Please try again.';
                } else {
                    $errorMsg = $e->getMessage();
                }
                
                $this->addFlash('error', $errorMsg);
            }
        }
        
        // Fetch all music pieces WITHOUT loading BLOB data
        // This prevents "MySQL server has gone away" errors with large audio files
        $searchTerm = $request->query->get('search', '');
        $sortBy = $request->query->get('sort', 'date');
        $sortOrder = $request->query->get('order', 'DESC');
        
        // Validate sort order
        if (!in_array($sortOrder, ['ASC', 'DESC'])) {
            $sortOrder = 'DESC';
        }
        
        // Use search/filter method or fallback to all
        if ($searchTerm) {
            $musiques = $musiqueRepository->searchAndFilter($searchTerm, $sortBy, $sortOrder, $currentUser->getId());
        } else {
            $musiques = $musiqueRepository->searchAndFilter(null, $sortBy, $sortOrder, $currentUser->getId());
        }
        
        return $this->render('Front Office/musiqueartiste/musiqueartiste.html.twig', [
            'controller_name' => 'MusiqueartisteController',
            'musiques' => $musiques,
            'form' => $form->createView(),
            'searchTerm' => $searchTerm,
            'sortBy' => $sortBy,
            'sortOrder' => $sortOrder,
        ]);
    }

    #[Route('/musiqueartiste/audio/{id}', name: 'app_musiqueartiste_audio')]
    public function getAudio(int $id, MusiqueRepository $musiqueRepository): Response
    {
        $musique = $musiqueRepository->find($id);
        
        if (!$musique || !$musique->getAudio()) {
            throw $this->createNotFoundException('Audio not found');
        }

        $audioReference = trim((string) $musique->getAudio());
        if (preg_match('#^https?://#i', $audioReference)) {
            return $this->redirect($audioReference);
        }

        $audioPath = $this->resolveAudioPath($musique);

        if ($audioPath === null) {
            throw $this->createNotFoundException('Audio file not found on disk');
        }

        $response = new BinaryFileResponse($audioPath);
        $response->headers->set('Content-Type', mime_content_type($audioPath) ?: 'audio/mpeg');
        $response->setContentDisposition(ResponseHeaderBag::DISPOSITION_INLINE, basename($audioPath));

        return $response;
    }

    private function resolveAudioPath(Musique $musique): ?string
    {
        $audioReference = trim((string) $musique->getAudio());
        $projectDir = $this->getParameter('kernel.project_dir');

        if ($audioReference === '') {
            return null;
        }

        if (preg_match('#^file:#i', $audioReference)) {
            $audioReference = urldecode(preg_replace('#^file:/{0,3}#i', '', $audioReference));
        }

        if (preg_match('#^https?://#i', $audioReference)) {
            return null;
        }

        $relativePath = ltrim($audioReference, '/\\');
        $candidatePaths = [
            $projectDir . '/public/uploads/music/' . basename($relativePath),
            $projectDir . '/public/' . $relativePath,
            $audioReference,
            'C:/xampp/htdocs/audio/' . basename($relativePath),
            'C:/xampp/htdocs/music/' . basename($relativePath),
            'C:/xampp/htdocs/uploads/music/' . basename($relativePath),
            'C:/xampp/htdocs/img/' . basename($relativePath)
        ];

        foreach ($candidatePaths as $candidatePath) {
            if (is_file($candidatePath)) {
                return $candidatePath;
            }
        }

        $searchTerms = array_filter([
            $musique->getTitre(),
            $musique->getCollection()?->getArtiste()?->getPrenom(),
            $musique->getCollection()?->getArtiste()?->getNom(),
        ]);

        $normalizedTerms = array_values(array_filter(array_map(static function (string $term): string {
            $ascii = iconv('UTF-8', 'ASCII//TRANSLIT//IGNORE', $term);
            if ($ascii === false) {
                $ascii = $term;
            }

            $slug = strtolower(preg_replace('/[^a-z0-9]+/i', '-', $ascii) ?? '');
            return trim($slug, '-');
        }, $searchTerms)));

        $musicDir = $projectDir . '/public/uploads/music';
        if (!is_dir($musicDir) || $normalizedTerms === []) {
            return null;
        }

        $matches = [];
        foreach ($normalizedTerms as $term) {
            foreach (glob($musicDir . '/*' . $term . '*') ?: [] as $file) {
                if (is_file($file)) {
                    $matches[] = $file;
                }
            }
        }

        if ($matches === []) {
            return null;
        }

        usort($matches, static function (string $left, string $right): int {
            return filemtime($right) <=> filemtime($left);
        });

        return $matches[0] ?? null;
    }

    #[Route('/musiqueartiste/image/{id}', name: 'app_musiqueartiste_image')]
    public function getImage(int $id, MusiqueRepository $musiqueRepository): Response
    {
        $musique = $musiqueRepository->find($id);
        
        if (!$musique || !$musique->getImage()) {
            throw $this->createNotFoundException('Image not found');
        }

        $imageData = $musique->getImage();
        if (is_resource($imageData)) {
            try { rewind($imageData); } catch (\Throwable) {}
            $imageData = stream_get_contents($imageData);
            return new Response($imageData, 200, ['Content-Type' => 'image/jpeg']);
        }

        if (is_string($imageData)) {
            if (preg_match('#^https?://#i', $imageData)) {
                return $this->redirect($imageData);
            }
            
            if (preg_match('#^file:#i', $imageData)) {
                $imageData = urldecode(preg_replace('#^file:/{0,3}#i', '', $imageData));
            }

            $public = $this->getParameter('kernel.project_dir') . '/public/';
            
            $candidatePaths = [
                $public . ltrim($imageData, '/'),
                $imageData,
                'C:/xampp/htdocs/img/' . basename($imageData),
            ];

            foreach ($candidatePaths as $path) {
                if (file_exists($path) && is_file($path)) {
                    $response = new BinaryFileResponse($path);
                    $response->headers->set('Content-Type', mime_content_type($path) ?: 'image/jpeg');
                    $response->setContentDisposition(ResponseHeaderBag::DISPOSITION_INLINE, basename($path));
                    return $response;
                }
            }
            return $this->redirect('/' . ltrim($imageData, '/'));
        }

        throw $this->createNotFoundException('Image not found');
    }

    #[Route('/musiqueartiste/lyrics/{id}', name: 'app_musiqueartiste_lyrics', methods: ['GET'])]
    public function getLyrics(
        int $id,
        MusiqueRepository $musiqueRepository,
        HttpClientInterface $httpClient
    ): JsonResponse
    {
        $currentUser = $this->getUser();
        if (!$currentUser instanceof User) {
            return $this->json([
                'success' => false,
                'message' => 'You must be logged in as an artist.'
            ], 403);
        }

        $musique = $musiqueRepository->find($id);

        if (!$musique) {
            return $this->json([
                'success' => false,
                'message' => 'Song not found.'
            ], 404);
        }

        if ($musique->getCollection()?->getArtiste()?->getId() !== $currentUser->getId()) {
            return $this->json([
                'success' => false,
                'message' => 'Song not found.'
            ], 404);
        }

        $trackName = trim((string) $musique->getTitre());
        $nom = trim((string) ($musique->getCollection()?->getArtiste()?->getNom() ?? ''));
        $prenom = trim((string) ($musique->getCollection()?->getArtiste()?->getPrenom() ?? ''));
        $artistName = trim($prenom . ' ' . $nom);

        if ($trackName === '') {
            return $this->json([
                'success' => false,
                'message' => 'Song title is missing.'
            ], 400);
        }

        $lyrics = null;
        $source = null;

        // Try lyrics.ovh first (simpler, more reliable API)
        if ($artistName !== '') {
            try {
                $lyricsOvhResponse = $httpClient->request('GET', sprintf(
                    'https://api.lyrics.ovh/v1/%s/%s',
                    rawurlencode($artistName),
                    rawurlencode($trackName)
                ), [
                    'timeout' => 10,
                    'max_duration' => 15,
                ]);

                if ($lyricsOvhResponse->getStatusCode() === 200) {
                    $lyricsOvhPayload = json_decode($lyricsOvhResponse->getContent(false), true);
                    if (is_array($lyricsOvhPayload) && !empty($lyricsOvhPayload['lyrics'])) {
                        $lyrics = trim($lyricsOvhPayload['lyrics']);
                        $source = 'lyrics.ovh';
                    }
                }
            } catch (\Throwable $e) {
                error_log('lyrics.ovh failed: ' . $e->getMessage());
            }
        }

        // Fallback to lrclib if lyrics.ovh didn't work
        if (!$lyrics) {
            try {
                $getParams = ['track_name' => $trackName];
                if ($artistName !== '') {
                    $getParams['artist_name'] = $artistName;
                }

                $getResponse = $httpClient->request('GET', 'https://lrclib.net/api/get', [
                    'query' => $getParams,
                    'timeout' => 10,
                    'max_duration' => 15,
                ]);

                if ($getResponse->getStatusCode() === 200) {
                    $payload = json_decode($getResponse->getContent(false), true);
                    if (is_array($payload)) {
                        $lyrics = $payload['plainLyrics'] ?? $payload['syncedLyrics'] ?? null;
                        if ($lyrics) {
                            $source = 'lrclib';
                        }
                    }
                }
            } catch (\Throwable $e) {
                error_log('lrclib.net /get failed: ' . $e->getMessage());
            }
        }

        if (!$lyrics) {
            return $this->json([
                'success' => false,
                'message' => 'Pas de paroles trouvées pour cette chanson.'
            ], 404);
        }

        return $this->json([
            'success' => true,
            'track' => $trackName,
            'artist' => $artistName,
            'lyrics' => $lyrics,
            'source' => $source
        ]);
    }

    #[Route('/musiqueartiste/edit/{id}', name: 'app_musiqueartiste_edit', methods: ['POST'])]
    public function edit(
        int $id,
        Request $request,
        MusiqueRepository $musiqueRepository,
        EntityManagerInterface $entityManager,
        FileStorageService $fileStorageService
    ): Response
    {
        $currentUser = $this->getUser();
        if (!$currentUser instanceof User) {
            throw $this->createAccessDeniedException('You must be logged in as an artist.');
        }

        $musique = $musiqueRepository->find($id);
        
        if (!$musique) {
            $this->addFlash('error', 'Music not found.');
            return $this->redirectToRoute('app_musiqueartiste');
        }

        if ($musique->getCollection()?->getArtiste()?->getId() !== $currentUser->getId()) {
            $this->addFlash('error', 'You can only edit your own songs.');
            return $this->redirectToRoute('app_musiqueartiste');
        }

        // Get and validate basic fields from POST data
        $titre = $request->request->get('titre');
        $description = $request->request->get('description');
        $genre = $request->request->get('genre');
        
        // Validation: Title
        if (empty($titre) || trim($titre) === '') {
            $this->addFlash('error', 'Title is required.');
            return $this->redirectToRoute('app_musiqueartiste');
        }
        
        if (strlen($titre) < 3) {
            $this->addFlash('error', 'Title must be at least 3 characters long.');
            return $this->redirectToRoute('app_musiqueartiste');
        }
        
        if (strlen($titre) > 255) {
            $this->addFlash('error', 'Title cannot exceed 255 characters.');
            return $this->redirectToRoute('app_musiqueartiste');
        }
        
        // Validation: Description
        if (empty($description) || trim($description) === '') {
            $this->addFlash('error', 'Description is required.');
            return $this->redirectToRoute('app_musiqueartiste');
        }
        
        if (strlen($description) < 10) {
            $this->addFlash('error', 'Description must be at least 10 characters long.');
            return $this->redirectToRoute('app_musiqueartiste');
        }
        
        if (strlen($description) > 5000) {
            $this->addFlash('error', 'Description cannot exceed 5000 characters.');
            return $this->redirectToRoute('app_musiqueartiste');
        }
        
        // Validation: Genre
        if (empty($genre)) {
            $this->addFlash('error', 'Genre is required.');
            return $this->redirectToRoute('app_musiqueartiste');
        }
        
        try {
            // Update fields
            $musique->setTitre(trim($titre));
            $musique->setDescription(trim($description));
            
            // Convert genre string to enum
            $musique->setGenre(\App\Enum\GenreMusique::from($genre));
        } catch (\ValueError $e) {
            $this->addFlash('error', 'Invalid genre selected.');
            return $this->redirectToRoute('app_musiqueartiste');
        }
        
        // Handle image upload (optional)
        $imageFile = $request->files->get('imageFile');
        if ($imageFile) {
            try {
                // Validate file size (5MB)
                if ($imageFile->getSize() > 5242880) {
                    throw new \Exception('Image file exceeds maximum size of 5MB');
                }
                
                // Validate MIME type
                $validMimes = ['image/jpeg', 'image/png', 'image/jpg'];
                if (!in_array($imageFile->getMimeType(), $validMimes)) {
                    throw new \Exception('Invalid image format. Please upload JPEG or PNG.');
                }
                
                // Upload to XAMPP using FileStorageService
                $newFilename = $fileStorageService->uploadImage($imageFile, 'music_');
                $musique->setImage($fileStorageService->getImageUrl($newFilename));
            } catch (\Exception $e) {
                $this->addFlash('error', 'Error uploading image: ' . $e->getMessage());
                return $this->redirectToRoute('app_musiqueartiste');
            }
        }
        
        try {
            $entityManager->flush();
            $this->addFlash('success', 'Music updated successfully!');
        } catch (\Exception $e) {
            $this->addFlash('error', 'An error occurred while updating: ' . $e->getMessage());
        }

        return $this->redirectToRoute('app_musiqueartiste');
    }

    #[Route('/musiqueartiste/delete/{id}', name: 'app_musiqueartiste_delete', methods: ['POST'])]
    public function delete(
        int $id, 
        Request $request,
        MusiqueRepository $musiqueRepository, 
        EntityManagerInterface $entityManager
    ): Response
    {
        $currentUser = $this->getUser();
        if (!$currentUser instanceof User) {
            if ($request->headers->get('X-Requested-With') === 'XMLHttpRequest') {
                return $this->json(['success' => false, 'message' => 'Access denied.'], 403);
            }
            throw $this->createAccessDeniedException('You must be logged in as an artist.');
        }

        // Check if this is an AJAX request
        $isAjax = $request->headers->get('X-Requested-With') === 'XMLHttpRequest';
        
        // Validate deletion confirmation
        $confirmDelete = $request->request->get('confirm_delete');
        
        if (!$confirmDelete || $confirmDelete !== '1') {
            if ($isAjax) {
                return $this->json(['success' => false, 'message' => 'Delete action must be confirmed.'], 400);
            }
            $this->addFlash('error', 'Delete action must be confirmed.');
            return $this->redirectToRoute('app_musiqueartiste');
        }
        
        $musique = $musiqueRepository->find($id);
        
        if (!$musique) {
            if ($isAjax) {
                return $this->json(['success' => false, 'message' => 'Music not found.'], 404);
            }
            $this->addFlash('error', 'Music not found.');
            return $this->redirectToRoute('app_musiqueartiste');
        }

        if ($musique->getCollection()?->getArtiste()?->getId() !== $currentUser->getId()) {
            if ($isAjax) {
                return $this->json(['success' => false, 'message' => 'You can only delete your own songs.'], 403);
            }
            $this->addFlash('error', 'You can only delete your own songs.');
            return $this->redirectToRoute('app_musiqueartiste');
        }
        
        // Store title for success message
        $musicTitle = $musique->getTitre();

        try {
            $entityManager->remove($musique);
            $entityManager->flush();
            
            if ($isAjax) {
                return $this->json([
                    'success' => true, 
                    'message' => "Music '{$musicTitle}' deleted successfully!"
                ], 200);
            }
            
            $this->addFlash('success', "Music '{$musicTitle}' deleted successfully!");
        } catch (\Exception $e) {
            if ($isAjax) {
                return $this->json([
                    'success' => false, 
                    'message' => 'Error deleting music: ' . $e->getMessage()
                ], 500);
            }
            $this->addFlash('error', 'Error deleting music: ' . $e->getMessage());
        }

        return $this->redirectToRoute('app_musiqueartiste');
    }
}
