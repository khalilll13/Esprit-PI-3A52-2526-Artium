<?php

namespace App\Controller;

use App\Entity\Playlist;
use App\Entity\User;
use App\Repository\MusiqueRepository;
use App\Repository\PlaylistRepository;
use App\Repository\UserRepository;
use App\Service\GroqPlaylistService;
use App\Service\FileStorageService;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Attribute\Route;
use Symfony\Contracts\HttpClient\HttpClientInterface;

final class MusicfrontController extends AbstractController
{
    #[Route('/user-musiques', name: 'app_musicfront')]
    public function index(
        Request $request,
        MusiqueRepository $musiqueRepository,
        PlaylistRepository $playlistRepository,
        UserRepository $userRepository
    ): Response
    {
        $searchTerm = trim((string) $request->query->get('search', ''));
        $sortBy = (string) $request->query->get('sort', 'date');
        $sortOrder = strtoupper((string) $request->query->get('order', 'DESC'));

        if (!in_array($sortOrder, ['ASC', 'DESC'], true)) {
            $sortOrder = 'DESC';
        }

        if (!in_array($sortBy, ['date', 'titre', 'genre'], true)) {
            $sortBy = 'date';
        }

        // Fetch all available songs
        $musiques = $musiqueRepository->findAll();

        if ($searchTerm !== '') {
            $musiques = array_values(array_filter($musiques, function ($musique) use ($searchTerm) {
                $titre = $musique->getTitre() ?? '';
                $description = $musique->getDescription() ?? '';
                $artistNom = $musique->getCollection()?->getArtiste()?->getNom() ?? '';
                $artistPrenom = $musique->getCollection()?->getArtiste()?->getPrenom() ?? '';
                $artistFullName = trim($artistPrenom . ' ' . $artistNom);

                return stripos($titre, $searchTerm) !== false
                    || stripos($description, $searchTerm) !== false
                    || stripos($artistNom, $searchTerm) !== false
                    || stripos($artistPrenom, $searchTerm) !== false
                    || stripos($artistFullName, $searchTerm) !== false;
            }));
        }

        usort($musiques, function ($a, $b) use ($sortBy, $sortOrder) {
            switch ($sortBy) {
                case 'titre':
                    $left = $a->getTitre() ?? '';
                    $right = $b->getTitre() ?? '';
                    $result = strcasecmp($left, $right);
                    break;
                case 'genre':
                    $left = $a->getGenre()->value;
                    $right = $b->getGenre()->value;
                    $result = strcasecmp($left, $right);
                    break;
                case 'date':
                default:
                    $left = $a->getDateCreation()?->getTimestamp() ?? 0;
                    $right = $b->getDateCreation()?->getTimestamp() ?? 0;
                    $result = $left <=> $right;
                    break;
            }

            return $sortOrder === 'DESC' ? -$result : $result;
        });

        $playlists = [];

        $user = $this->getUser();
        if ($user) {
            $playlists = $playlistRepository->findBy(
                ['user' => $user],
                ['date_creation' => 'DESC']
            );
        } else {
            // For guest users, fetch playlists for user ID 2
            $guestUser = $userRepository->find(2);
            if ($guestUser) {
                $playlists = $playlistRepository->findBy(
                    ['user' => $guestUser],
                    ['date_creation' => 'DESC']
                );
            }
        }
        
        return $this->render('Front Office/music/musicfront.html.twig', [
            'controller_name' => 'MusicfrontController',
            'musiques' => $musiques,
            'playlists' => $playlists,
            'searchTerm' => $searchTerm,
            'sortBy' => $sortBy,
            'sortOrder' => $sortOrder,
        ]);
    }

    #[Route('/user-playlists/create', name: 'app_playlist_create', methods: ['POST'])]
    public function createPlaylist(
        Request $request,
        PlaylistRepository $playlistRepository,
        EntityManagerInterface $entityManager,
        UserRepository $userRepository,
        FileStorageService $fileStorageService,
        \Symfony\Component\Validator\Validator\ValidatorInterface $validator
    ): Response {
        $user = $this->getUser();
        if (!$user instanceof User) {
            $user = $userRepository->find(2);
        }
        if (!$user instanceof User) {
            if ($request->headers->get('X-Requested-With') === 'XMLHttpRequest') {
                return new JsonResponse(['error' => 'Utilisateur par defaut introuvable.'], 400);
            }
            $this->addFlash('error', 'Utilisateur par defaut introuvable.');
            return $this->redirectToRoute('app_musicfront');
        }

        $token = $request->request->get('_token');
        if (!$this->isCsrfTokenValid('create_playlist', $token)) {
            if ($request->headers->get('X-Requested-With') === 'XMLHttpRequest') {
                return new JsonResponse(['error' => 'Jeton CSRF invalide.'], 400);
            }
            $this->addFlash('error', 'Jeton CSRF invalide. Veuillez reessayer.');
            return $this->redirectToRoute('app_musicfront');
        }

        $name = trim((string) $request->request->get('playlist_name', ''));
        $description = trim((string) $request->request->get('playlist_description', ''));

        $playlist = new Playlist();
        $playlist->setNom($name);
        $playlist->setDescription($description !== '' ? $description : null);
        $playlist->setDateCreation(new \DateTime());
        $playlist->setUser($user);

        // Validate entity
        $errors = $validator->validate($playlist);
        if (count($errors) > 0) {
            $errorMessage = $errors[0]->getMessage();
            if ($request->headers->get('X-Requested-With') === 'XMLHttpRequest') {
                return new JsonResponse(['error' => $errorMessage], 400);
            }
            $this->addFlash('error', $errorMessage);
            return $this->redirectToRoute('app_musicfront');
        }

        // Handle image upload
        $imageFile = $request->files->get('playlist_image');
        if ($imageFile && $imageFile->isValid()) {
            try {
                if ($imageFile->getSize() > 5242880) { // 5MB
                    throw new \Exception('Image file exceeds maximum size of 5MB');
                }
                
                // Validate image MIME type
                $allowedMimes = ['image/jpeg', 'image/png', 'image/gif', 'image/webp'];
                if (!in_array($imageFile->getMimeType(), $allowedMimes)) {
                    throw new \Exception('Invalid image format. Allowed formats: JPG, PNG, GIF, WebP');
                }
                
                // Upload file to C:\xampp\htdocs\img\
                $filename = $fileStorageService->uploadImage($imageFile, 'playlist_');
                $playlist->setImage($filename);
            } catch (\Exception $e) {
                if ($request->headers->get('X-Requested-With') === 'XMLHttpRequest') {
                    return new JsonResponse(['error' => 'Image upload failed: ' . $e->getMessage()], 400);
                }
                $this->addFlash('warning', 'Image upload failed: ' . $e->getMessage());
            }
        }

        $entityManager->persist($playlist);
        $entityManager->flush();

        if ($request->headers->get('X-Requested-With') === 'XMLHttpRequest') {
            return new JsonResponse(['success' => true]);
        }

        $this->addFlash('success', 'Playlist creee avec succes.');
        return $this->redirectToRoute('app_musicfront');
    }

    #[Route('/user-playlists/add-songs', name: 'app_playlist_add_multiple', methods: ['POST'])]
    public function addMultipleSongs(
        Request $request,
        PlaylistRepository $playlistRepository,
        MusiqueRepository $musiqueRepository,
        EntityManagerInterface $entityManager,
        UserRepository $userRepository
    ): Response {
        $user = $this->getUser();
        if (!$user) {
            $user = $userRepository->find(2);
        }
        if (!$user) {
            if ($request->headers->get('X-Requested-With') === 'XMLHttpRequest') {
                return new JsonResponse(['error' => 'Utilisateur par defaut introuvable.'], 400);
            }
            $this->addFlash('error', 'Utilisateur par defaut introuvable.');
            return $this->redirectToRoute('app_musicfront');
        }

        $token = $request->request->get('_token');
        if (!$this->isCsrfTokenValid('add_to_playlist', $token)) {
            if ($request->headers->get('X-Requested-With') === 'XMLHttpRequest') {
                return new JsonResponse(['error' => 'Jeton CSRF invalide.'], 400);
            }
            $this->addFlash('error', 'Jeton CSRF invalide. Veuillez reessayer.');
            return $this->redirectToRoute('app_musicfront');
        }

        $playlistId = (int) $request->request->get('playlist_id');
        $songIds = $request->request->all()['song_ids'] ?? [];

        if ($playlistId <= 0 || empty($songIds)) {
            if ($request->headers->get('X-Requested-With') === 'XMLHttpRequest') {
                return new JsonResponse(['error' => 'Veuillez selectionner une playlist et au moins une musique.'], 400);
            }
            $this->addFlash('error', 'Veuillez selectionner une playlist et au moins une musique.');
            return $this->redirectToRoute('app_musicfront');
        }

        $playlist = $playlistRepository->find($playlistId);
        if (!$playlist || $playlist->getUser() !== $user) {
            if ($request->headers->get('X-Requested-With') === 'XMLHttpRequest') {
                return new JsonResponse(['error' => 'Playlist introuvable.'], 404);
            }
            $this->addFlash('error', 'Playlist introuvable.');
            return $this->redirectToRoute('app_musicfront');
        }

        $addedCount = 0;
        foreach ($songIds as $musicId) {
            $musicId = (int) $musicId;
            if ($musicId <= 0) continue;

            $musique = $musiqueRepository->find($musicId);
            if (!$musique) continue;

            if (!$playlist->getMusique()->contains($musique)) {
                $playlist->addMusique($musique);
                $addedCount++;
            }
        }

        if ($addedCount > 0) {
            $entityManager->flush();
        }

        if ($request->headers->get('X-Requested-With') === 'XMLHttpRequest') {
            return new JsonResponse([
                'success' => true,
                'message' => $addedCount . ' musique(s) ajoutee(s) a la playlist.'
            ]);
        }

        $this->addFlash('success', $addedCount . ' musique(s) ajoutee(s) a la playlist.');
        return $this->redirectToRoute('app_musicfront');
    }

    #[Route('/user-playlists/ai-generate', name: 'app_playlist_ai_generate', methods: ['POST'])]
    public function generateAiPlaylist(
        Request $request,
        MusiqueRepository $musiqueRepository,
        EntityManagerInterface $entityManager,
        UserRepository $userRepository,
        \Symfony\Component\Validator\Validator\ValidatorInterface $validator,
        GroqPlaylistService $groqPlaylistService
    ): Response {
        $user = $this->getUser();
        if (!$user instanceof User) {
            $user = $userRepository->find(2);
        }

        if (!$user instanceof User) {
            return new JsonResponse(['success' => false, 'error' => 'Utilisateur par defaut introuvable.'], 400);
        }

        $token = $request->request->get('_token');
        if (!$this->isCsrfTokenValid('ai_generate_playlist', $token)) {
            return new JsonResponse(['success' => false, 'error' => 'Jeton CSRF invalide.'], 400);
        }

        $prompt = trim((string) $request->request->get('ai_prompt', ''));
        $count = (int) $request->request->get('ai_count', 10);
        $count = max(3, min(20, $count));

        if ($prompt === '') {
            return new JsonResponse(['success' => false, 'error' => 'Le prompt est requis.'], 400);
        }

        $allSongs = $musiqueRepository->findAll();
        if (empty($allSongs)) {
            return new JsonResponse(['success' => false, 'error' => 'Aucune musique disponible pour la génération.'], 400);
        }

        $catalog = [];
        foreach ($allSongs as $song) {
            $catalog[] = [
                'id' => (int) $song->getId(),
                'title' => (string) ($song->getTitre() ?? 'Titre inconnu'),
                'genre' => $song->getGenre()->value,
                'artist' => trim((string) (($song->getCollection()?->getArtiste()?->getPrenom() ?? '') . ' ' . ($song->getCollection()?->getArtiste()?->getNom() ?? ''))),
                'description' => mb_substr((string) ($song->getDescription() ?? ''), 0, 220),
            ];
        }

        try {
            $aiResult = $groqPlaylistService->generatePlaylist($prompt, $catalog, $count);
        } catch (\Throwable $e) {
            return new JsonResponse(['success' => false, 'error' => 'Erreur AI: ' . $e->getMessage()], 500);
        }

        $validSongMap = [];
        foreach ($allSongs as $song) {
            $validSongMap[(int) $song->getId()] = $song;
        }

        $selectedSongs = [];
        foreach ($aiResult['songIds'] as $id) {
            $songId = (int) $id;
            if ($songId > 0 && isset($validSongMap[$songId])) {
                $selectedSongs[$songId] = $validSongMap[$songId];
            }
        }

        if (empty($selectedSongs)) {
            return new JsonResponse(['success' => false, 'error' => 'AI n\'a sélectionné aucune musique valide.'], 400);
        }

        $playlist = new Playlist();
        $playlist->setNom(mb_substr((string) $aiResult['playlistName'], 0, 100));
        $playlist->setDescription(mb_substr((string) $aiResult['rationale'], 0, 500));
        $playlist->setDateCreation(new \DateTime());
        $playlist->setUser($user);

        foreach ($selectedSongs as $song) {
            $playlist->addMusique($song);
        }

        $errors = $validator->validate($playlist);
        if (count($errors) > 0) {
            return new JsonResponse(['success' => false, 'error' => $errors[0]->getMessage()], 400);
        }

        $entityManager->persist($playlist);
        $entityManager->flush();

        return new JsonResponse([
            'success' => true,
            'playlistId' => $playlist->getId(),
            'playlistName' => $playlist->getNom(),
            'addedCount' => count($selectedSongs),
        ]);
    }

    #[Route('/playlist/{id}/songs', name: 'app_playlist_songs', methods: ['GET'])]
    public function getPlaylistSongs(
        int $id,
        PlaylistRepository $playlistRepository,
        MusiqueRepository $musiqueRepository,
        UserRepository $userRepository
    ): Response
    {
        $playlist = $playlistRepository->find($id);
        
        if (!$playlist) {
            throw $this->createNotFoundException('Playlist not found');
        }

        $user = $this->getUser();
        if (!$user) {
            $guestUser = $userRepository->find(2);
            if (!$guestUser || $playlist->getUser()->getId() !== $guestUser->getId()) {
                return new JsonResponse(['error' => 'Playlist introuvable.'], 404);
            }
        } elseif ($playlist->getUser() !== $user) {
            return new JsonResponse(['error' => 'Playlist introuvable.'], 404);
        }

        $songs = [];
        $playlistSongIds = [];
        $playlistGenres = [];
        $playlistArtists = [];

        foreach ($playlist->getMusique() as $musique) {
            $genre = $musique->getGenre()->value;
            $artistName = trim((string) (($musique->getCollection()?->getArtiste()?->getPrenom() ?? '') . ' ' . ($musique->getCollection()?->getArtiste()?->getNom() ?? '')));

            $songs[] = [
                'id' => $musique->getId(),
                'titre' => $musique->getTitre(),
                'audioSrc' => $this->generateUrl('app_musiqueartiste_audio', ['id' => $musique->getId()]),
                'genre' => $genre,
                'artist' => $artistName !== '' ? $artistName : 'Artiste inconnu',
            ];

            $playlistSongIds[] = $musique->getId();
            $playlistGenres[] = mb_strtolower($genre);
            if ($artistName !== '') {
                $playlistArtists[] = mb_strtolower($artistName);
            }
        }

        $playlistSongIds = array_values(array_unique($playlistSongIds));
        $playlistGenres = array_values(array_unique($playlistGenres));
        $playlistArtists = array_values(array_unique($playlistArtists));

        $smartPool = [];
        if (!empty($playlistGenres) || !empty($playlistArtists)) {
            foreach ($musiqueRepository->findAll() as $candidate) {
                $candidateId = $candidate->getId();
                if (!$candidateId || in_array($candidateId, $playlistSongIds, true)) {
                    continue;
                }

                $candidateGenre = mb_strtolower($candidate->getGenre()->value);
                $candidateArtist = trim((string) (($candidate->getCollection()?->getArtiste()?->getPrenom() ?? '') . ' ' . ($candidate->getCollection()?->getArtiste()?->getNom() ?? '')));
                $candidateArtistLower = mb_strtolower($candidateArtist);

                $genreMatch = in_array($candidateGenre, $playlistGenres, true);
                $artistMatch = $candidateArtistLower !== '' && in_array($candidateArtistLower, $playlistArtists, true);

                if (!$genreMatch && !$artistMatch) {
                    continue;
                }

                $smartPool[] = [
                    'id' => $candidateId,
                    'titre' => $candidate->getTitre() ?? 'Titre inconnu',
                    'audioSrc' => $this->generateUrl('app_musiqueartiste_audio', ['id' => $candidateId]),
                    'genre' => $candidate->getGenre()->value,
                    'artist' => $candidateArtist !== '' ? $candidateArtist : 'Artiste inconnu',
                ];
            }
        }

        return new JsonResponse([
            'songs' => $songs,
            'smartPool' => $smartPool,
        ]);
    }

    #[Route('/playlist/{playlistId}/remove-song/{musicId}', name: 'app_playlist_remove_song', methods: ['POST'])]
    public function removeSongFromPlaylist(
        int $playlistId,
        int $musicId,
        Request $request,
        PlaylistRepository $playlistRepository,
        MusiqueRepository $musiqueRepository,
        EntityManagerInterface $entityManager
    ): Response {
        $token = $request->request->get('_token');
        if (!$this->isCsrfTokenValid('remove_song', $token)) {
            $this->addFlash('error', 'Jeton CSRF invalide.');
            return $this->redirectToRoute('app_musicfront');
        }

        $playlist = $playlistRepository->find($playlistId);
        $musique = $musiqueRepository->find($musicId);

        if (!$playlist || !$musique) {
            $this->addFlash('error', 'Playlist ou musique introuvable.');
            return $this->redirectToRoute('app_musicfront');
        }

        if ($playlist->getMusique()->contains($musique)) {
            $playlist->removeMusique($musique);
            $entityManager->flush();
            $this->addFlash('success', 'Musique supprimee de la playlist.');
        }

        return $this->redirectToRoute('app_musicfront');
    }

    #[Route('/playlist/{id}/delete', name: 'app_playlist_delete', methods: ['POST'])]
    public function deletePlaylist(
        int $id,
        Request $request,
        PlaylistRepository $playlistRepository,
        EntityManagerInterface $entityManager,
        UserRepository $userRepository
    ): Response {
        $token = $request->request->get('_token');
        if (!$this->isCsrfTokenValid('delete_playlist', $token)) {
            if ($request->headers->get('X-Requested-With') === 'XMLHttpRequest') {
                return new JsonResponse(['error' => 'Jeton CSRF invalide.'], 400);
            }
            $this->addFlash('error', 'Jeton CSRF invalide.');
            return $this->redirectToRoute('app_musicfront');
        }

        $playlist = $playlistRepository->find($id);
        
        if (!$playlist) {
            if ($request->headers->get('X-Requested-With') === 'XMLHttpRequest') {
                return new JsonResponse(['error' => 'Playlist introuvable.'], 404);
            }
            $this->addFlash('error', 'Playlist introuvable.');
            return $this->redirectToRoute('app_musicfront');
        }

        // Check if user owns this playlist
        $user = $this->getUser();
        if (!$user) {
            // For guest users, check if playlist belongs to user ID 2
            $guestUser = $userRepository->find(2);
            if (!$guestUser || $playlist->getUser()->getId() !== $guestUser->getId()) {
                if ($request->headers->get('X-Requested-With') === 'XMLHttpRequest') {
                    return new JsonResponse(['error' => 'Vous netes pas autorise a supprimer cette playlist.'], 403);
                }
                $this->addFlash('error', 'Vous netes pas autorise a supprimer cette playlist.');
                return $this->redirectToRoute('app_musicfront');
            }
        } else {
            // For logged-in users, verify they own the playlist
            if ($playlist->getUser() !== $user) {
                if ($request->headers->get('X-Requested-With') === 'XMLHttpRequest') {
                    return new JsonResponse(['error' => 'Vous netes pas autorise a supprimer cette playlist.'], 403);
                }
                $this->addFlash('error', 'Vous netes pas autorise a supprimer cette playlist.');
                return $this->redirectToRoute('app_musicfront');
            }
        }

        $playlistName = $playlist->getNom();
        $entityManager->remove($playlist);
        $entityManager->flush();

        if ($request->headers->get('X-Requested-With') === 'XMLHttpRequest') {
            return new JsonResponse(['success' => true]);
        }

        $this->addFlash('success', 'La playlist « ' . $playlistName . ' » a ete supprimee.');
        return $this->redirectToRoute('app_musicfront');
    }

    #[Route('/playlist/{id}/edit', name: 'app_playlist_edit', methods: ['POST'])]
    public function editPlaylist(
        int $id,
        Request $request,
        PlaylistRepository $playlistRepository,
        EntityManagerInterface $entityManager,
        UserRepository $userRepository,
        \Symfony\Component\Validator\Validator\ValidatorInterface $validator
    ): Response {
        $token = $request->request->get('_token');
        if (!$this->isCsrfTokenValid('edit_playlist', $token)) {
            if ($request->headers->get('X-Requested-With') === 'XMLHttpRequest') {
                return new JsonResponse(['error' => 'Jeton CSRF invalide.'], 400);
            }
            $this->addFlash('error', 'Jeton CSRF invalide.');
            return $this->redirectToRoute('app_musicfront');
        }

        $playlist = $playlistRepository->find($id);
        
        if (!$playlist) {
            if ($request->headers->get('X-Requested-With') === 'XMLHttpRequest') {
                return new JsonResponse(['error' => 'Playlist introuvable.'], 404);
            }
            $this->addFlash('error', 'Playlist introuvable.');
            return $this->redirectToRoute('app_musicfront');
        }

        // Check if user owns this playlist
        $user = $this->getUser();
        if (!$user) {
            // For guest users, check if playlist belongs to user ID 2
            $guestUser = $userRepository->find(2);
            if (!$guestUser || $playlist->getUser()->getId() !== $guestUser->getId()) {
                if ($request->headers->get('X-Requested-With') === 'XMLHttpRequest') {
                    return new JsonResponse(['error' => 'Vous netes pas autorise a modifier cette playlist.'], 403);
                }
                $this->addFlash('error', 'Vous netes pas autorise a modifier cette playlist.');
                return $this->redirectToRoute('app_musicfront');
            }
        } else {
            // For logged-in users, verify they own the playlist
            if ($playlist->getUser() !== $user) {
                if ($request->headers->get('X-Requested-With') === 'XMLHttpRequest') {
                    return new JsonResponse(['error' => 'Vous netes pas autorise a modifier cette playlist.'], 403);
                }
                $this->addFlash('error', 'Vous netes pas autorise a modifier cette playlist.');
                return $this->redirectToRoute('app_musicfront');
            }
        }

        $nom = trim((string) $request->request->get('nom', ''));
        $description = trim((string) $request->request->get('description', ''));

        $playlist->setNom($nom);
        $playlist->setDescription($description !== '' ? $description : null);

        // Validate entity
        $errors = $validator->validate($playlist);
        if (count($errors) > 0) {
            $errorMessage = $errors[0]->getMessage();
            if ($request->headers->get('X-Requested-With') === 'XMLHttpRequest') {
                return new JsonResponse(['error' => $errorMessage], 400);
            }
            $this->addFlash('error', $errorMessage);
            return $this->redirectToRoute('app_musicfront');
        }

        $entityManager->flush();

        if ($request->headers->get('X-Requested-With') === 'XMLHttpRequest') {
            return new JsonResponse(['success' => true]);
        }

        $this->addFlash('success', 'La playlist a ete modifiee avec succes.');
        return $this->redirectToRoute('app_musicfront');
    }

    #[Route('/playlist/image/{id}', name: 'app_playlist_image')]
    public function getPlaylistImage(int $id, PlaylistRepository $playlistRepository): Response
    {
        $playlist = $playlistRepository->find($id);
        
        if (!$playlist || !$playlist->getImage()) {
            throw $this->createNotFoundException('Playlist image not found');
        }

        $filename = $playlist->getImage();
        
        // Redirect to the external img folder
        return $this->redirect('http://127.0.0.1/img/' . $filename);
    }

    #[Route('/user-musiques/lyrics/{id}', name: 'app_musicfront_lyrics', methods: ['GET'])]
    public function getLyrics(
        int $id,
        MusiqueRepository $musiqueRepository,
        HttpClientInterface $httpClient
    ): JsonResponse
    {
        $musique = $musiqueRepository->find($id);

        if (!$musique) {
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
                'message' => 'pas de paroles trouvées pour cette chanson.'
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
}
