<?php

namespace App\Controller;

use App\Entity\Oeuvre;
use App\Enum\TypeOeuvre;
use App\Form\OeuvreType;
use App\Repository\CollectionsRepository;
use App\Repository\OeuvreRepository;
use App\Repository\CommentaireRepository;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\HttpFoundation\File\UploadedFile;
use Symfony\Component\Routing\Attribute\Route;
use Meilisearch\Bundle\SearchManagerInterface;
use Knp\Component\Pager\PaginatorInterface;

#[Route('/oeuvre')]
final class OeuvreController extends AbstractController
{

    #[Route(name: 'oeuvres')]
    public function indexx(OeuvreRepository $oeuvreRepository, CollectionsRepository $collectionsRepository, Request $request, SearchManagerInterface $searchManager, PaginatorInterface $paginator): Response
    {
        $query = $request->query->get('q', '');
        $sortBy = $request->query->get('sort', '');
        $sortOrder = $request->query->get('order', 'ASC');
        $activeTab = $request->query->get('tab', 'all-post');
        $searchResults = [];
        $noResultsMessage = '';
        $session = $request->hasSession() ? $request->getSession() : null;
        $searchNoticeSessionKey = 'oeuvre_search_last_notified_query';

        if ($session && $query === '') {
            $session->remove($searchNoticeSessionKey);
        }

        // If search query exists, find matching oeuvres with sorting
        if ($query) {
            $shouldNotifySearch = !$session || $session->get($searchNoticeSessionKey) !== $query;

            try {
                $hits = $searchManager->search(Oeuvre::class, $query, ['limit' => 100])->getHits();

                if (count($hits) === 0) {
                    if ($shouldNotifySearch) {
                        $this->addFlash('info', 'Aucune œuvre trouvée avec ce titre.');
                    }
                    $searchResults = [];
                } else {
                    // Extract IDs in Meilisearch relevance order
                    $ids = array_map(fn($oeuvre) => $oeuvre->getId(), $hits);

                    // Fetch results - apply sorting if selected, else keep Meilisearch relevance order
                    if ($sortBy && in_array($sortBy, ['likes', 'commentaires', 'favoris', 'titre'])) {
                        $searchResults = $oeuvreRepository->findByIdsWithSort($ids, $sortBy, $sortOrder);
                    } else {
                        // Keep Meilisearch relevance order
                        $searchResults = $oeuvreRepository->findByIdsWithSort($ids, '', 'ASC');
                    }

                    $searchResults = array_values(array_filter(
                        $searchResults,
                        static fn (Oeuvre $oeuvre): bool => in_array($oeuvre->getType(), [TypeOeuvre::PEINTURE, TypeOeuvre::SCULPTURE, TypeOeuvre::PHOTOGRAPHIE], true)
                    ));

                    if ($shouldNotifySearch) {
                        $this->addFlash('info', count($searchResults) . ' résultat(s) trouvé(s)');
                    }
                }
            } catch (\Throwable) {
                $searchResults = array_values(array_filter(
                    $oeuvreRepository->findByTitreWithSort($query, $sortBy ?: 'titre', $sortOrder),
                    static fn (Oeuvre $oeuvre): bool => in_array($oeuvre->getType(), [TypeOeuvre::PEINTURE, TypeOeuvre::SCULPTURE, TypeOeuvre::PHOTOGRAPHIE], true)
                ));

                if ($shouldNotifySearch) {
                    $this->addFlash('warning', 'Recherche Meilisearch indisponible, résultats affichés depuis la base de données.');
                }
            }

            if ($session && $shouldNotifySearch) {
                $session->set($searchNoticeSessionKey, $query);
            }
        }

        // Apply PHP sorting to search results if sortBy is specified
        if ($query && $searchResults && $sortBy) {
            $searchResults = $this->sortOeuvreArray($searchResults, $sortBy, $sortOrder);
        }

        // Paginate search results if search is active
        $searchResultsPaginated = null;
        if ($query && $searchResults) {
            $paginatorOptionsSearch = [
                'pageParameterName' => 'page',
                'sortFieldParameterName' => 'disable_sort',
                'sortDirectionParameterName' => 'disable_dir'
            ];
            $searchResultsPaginated = $paginator->paginate($searchResults, $request->query->getInt('page', 1), 6, $paginatorOptionsSearch);
        }

        // Get oeuvres by type - fetch without sorting first
        $peintures = $oeuvreRepository->findByTypeWithSort(TypeOeuvre::PEINTURE, 'titre', 'ASC');
        $sculptures = $oeuvreRepository->findByTypeWithSort(TypeOeuvre::SCULPTURE, 'titre', 'ASC');
        $photos = $oeuvreRepository->findByTypeWithSort(TypeOeuvre::PHOTOGRAPHIE, 'titre', 'ASC');
        $all = array_values(array_filter(
            $oeuvreRepository->findAllWithSort('titre', 'ASC'),
            static fn (Oeuvre $oeuvre): bool => in_array($oeuvre->getType(), [TypeOeuvre::PEINTURE, TypeOeuvre::SCULPTURE, TypeOeuvre::PHOTOGRAPHIE], true)
        ));
        
        // Sort in PHP based on sortBy parameter (so pagination works with sorting)
        if ($sortBy) {
            $all = $this->sortOeuvreArray($all, $sortBy, $sortOrder);
            $peintures = $this->sortOeuvreArray($peintures, $sortBy, $sortOrder);
            $sculptures = $this->sortOeuvreArray($sculptures, $sortBy, $sortOrder);
            $photos = $this->sortOeuvreArray($photos, $sortBy, $sortOrder);
        } else {
            // Default sort by title
            $all = $this->sortOeuvreArray($all, 'titre', $sortOrder);
            $peintures = $this->sortOeuvreArray($peintures, 'titre', $sortOrder);
            $sculptures = $this->sortOeuvreArray($sculptures, 'titre', $sortOrder);
            $photos = $this->sortOeuvreArray($photos, 'titre', $sortOrder);
        }
        
        // Paginate the sorted arrays - 6 items per page
        // IMPORTANT: Disable paginator's auto-sorting since we handle sorting in PHP
        $paginatorOptions = [
            'pageParameterName' => 'page',
            'sortFieldParameterName' => 'disable_sort',
            'sortDirectionParameterName' => 'disable_dir'
        ];
        $oeuvres = $paginator->paginate($all, $request->query->getInt('page', 1), 6, $paginatorOptions);
        
        $paginatorOptionsType = [
            'pageParameterName' => 'page_peinture',
            'sortFieldParameterName' => 'disable_sort',
            'sortDirectionParameterName' => 'disable_dir'
        ];
        $peinturesPaginated = $paginator->paginate($peintures, $request->query->getInt('page_peinture', 1), 6, $paginatorOptionsType);
        
        $paginatorOptionsType2 = [
            'pageParameterName' => 'page_sculpture',
            'sortFieldParameterName' => 'disable_sort',
            'sortDirectionParameterName' => 'disable_dir'
        ];
        $sculpturesPaginated = $paginator->paginate($sculptures, $request->query->getInt('page_sculpture', 1), 6, $paginatorOptionsType2);
        
        $paginatorOptionsType3 = [
            'pageParameterName' => 'page_photo',
            'sortFieldParameterName' => 'disable_sort',
            'sortDirectionParameterName' => 'disable_dir'
        ];
        $photosPaginated = $paginator->paginate($photos, $request->query->getInt('page_photo', 1), 6, $paginatorOptionsType3);
        
        return $this->render('oeuvre/oeuvres.html.twig', [
            'controller_name' => 'OeuvreController',
            'oeuvres' => $oeuvres,
            'peintures' => $peinturesPaginated,
            'sculptures' => $sculpturesPaginated,
            'photos' => $photosPaginated,
            'typeOeuvres' => TypeOeuvre::cases(),
            'collections' => $collectionsRepository->findAll(),
            'searchResults' => $searchResults,
            'searchResultsPaginated' => $searchResultsPaginated,
            'noResultsMessage' => $noResultsMessage,
            'isSearchActive' => (bool) $query,
            'sortBy' => $sortBy,
            'sortOrder' => $sortOrder,
            'currentQuery' => $query,
            'activeTab' => $activeTab,
        ]);
    }

    #[Route('/test',name: 'app_oeuvre_index', methods: ['GET'])]
    public function index(OeuvreRepository $oeuvreRepository): Response
    {
        return $this->render('oeuvre/index.html.twig', [
            'oeuvres' => $oeuvreRepository->findAll(),
        ]);
    }


    #[Route('/test/{id}', name: 'app_oeuvre_show', methods: ['GET'], requirements: ['id' => '\\d+'])]
    public function show(Oeuvre $oeuvre): Response
    {
        return $this->render('oeuvre/show.html.twig', [
            'oeuvre' => $oeuvre,
        ]);
    }

    #[Route('/{id}', name: 'app_oeuvre_details', methods: ['GET'], requirements: ['id' => '\\d+'])]
    public function details(Oeuvre $oeuvre): Response
    {
        return $this->render('oeuvre/oeuvre_details.html.twig', [
            'oeuvre' => $oeuvre,
        ]);
    }


    #[Route('/{id}/edit', name: 'app_oeuvre_edit', methods: ['GET', 'POST'], requirements: ['id' => '\\d+'])]
    public function edit(Request $request, Oeuvre $oeuvre, EntityManagerInterface $entityManager, OeuvreRepository $oeuvreRepository): Response
    {
        // Fetch oeuvre with collection and artist relations
        $oeuvre = $oeuvreRepository->findWithCollectionAndArtist($oeuvre->getId());
        
        if (!$oeuvre) {
            throw $this->createNotFoundException('Oeuvre not found');
        }
        
        // Get the artist who owns this oeuvre
        $oeuvreArtist = null;
        if ($oeuvre->getCollection() && $oeuvre->getCollection()->getArtiste()) {
            $oeuvreArtist = $oeuvre->getCollection()->getArtiste();
        }
        
        $session = $request->getSession();
        $tempImageName = $session->get('oeuvre_temp_image_' . $oeuvre->getId());
        
        $form = $this->createForm(OeuvreType::class, $oeuvre, [
            'user' => $oeuvreArtist,
            'image_required' => false,
            'include_type' => false,
            'temp_image_present' => $tempImageName !== null,
            'validation_groups' => ['Default', 'edit'],
        ]);
        $form->handleRequest($request);

        // Handle image file upload temporarily before validation
        $tempDir = $this->getParameter('kernel.project_dir') . '/var/tmp/oeuvre_uploads';
        $imageFile = $form->get('image')->getData();
        if ($imageFile instanceof UploadedFile) {
            if (!is_dir($tempDir)) {
                mkdir($tempDir, 0777, true);
            }
            $newName = bin2hex(random_bytes(16));
            $ext = $imageFile->guessExtension();
            if ($ext) {
                $newName .= '.' . $ext;
            }

            $imageFile->move($tempDir, $newName);

            if ($tempImageName) {
                $oldPath = $tempDir . '/' . $tempImageName;
                if (is_file($oldPath)) {
                    unlink($oldPath);
                }
            }

            $tempImageName = $newName;
            $session->set('oeuvre_temp_image_' . $oeuvre->getId(), $tempImageName);
        }

        if ($form->isSubmitted() && $form->isValid()) {
            // Handle the temp image on validation success
            $tempPath = $tempImageName ? $tempDir . '/' . $tempImageName : null;
            if ($tempPath && is_file($tempPath)) {
                $blobData = fopen($tempPath, 'rb');
                $oeuvre->setImage($blobData);
                unlink($tempPath);
                $session->remove('oeuvre_temp_image_' . $oeuvre->getId());
            } elseif ($imageFile instanceof UploadedFile) {
                // Fallback if temp handling didn't work
                $blobData = fopen($imageFile->getPathname(), 'rb');
                $oeuvre->setImage($blobData);
            }

            $entityManager->flush();

            // Return JSON for AJAX requests
            if ($request->isXmlHttpRequest()) {
                return $this->json(['success' => true, 'message' => 'Œuvre mise à jour avec succès']);
            }

            return $this->redirectToRoute('app_oeuvre_index', [], Response::HTTP_SEE_OTHER);
        }

        // For AJAX requests, return the form fields as HTML
        if ($request->isXmlHttpRequest()) {
            return $this->render('oeuvre/_form_fields.html.twig', [
                'form' => $form->createView(),
                'tempImagePresent' => $tempImageName !== null,
                'tempImageName' => $tempImageName,
            ]);
        }

        return $this->render('oeuvre/edit.html.twig', [
            'oeuvre' => $oeuvre,
            'form' => $form,
            'tempImagePresent' => $tempImageName !== null,
            'tempImageName' => $tempImageName,
        ]);
    }

    #[Route('/{id}', name: 'app_oeuvre_delete', methods: ['POST'], requirements: ['id' => '\\d+'])]
    public function delete(Request $request, Oeuvre $oeuvre, EntityManagerInterface $entityManager, OeuvreRepository $oeuvreRepository): Response
    {
        $oeuvreId = $oeuvre->getId();
        
        if ($this->isCsrfTokenValid('delete'.$oeuvreId, $request->getPayload()->getString('_token'))) {
            // Delete dependent records from repository
            $oeuvreRepository->deleteLikesByOeuvre($oeuvreId);
            $oeuvreRepository->deleteCommentairesByOeuvre($oeuvreId);
            
            // Delete the oeuvre itself
            $entityManager->remove($oeuvre);
            $entityManager->flush();
        }

        return $this->redirectToRoute('oeuvres', [], Response::HTTP_SEE_OTHER);
    }

    #[Route('/admin/commentaire/{id}/delete', name: 'app_oeuvre_comment_delete', methods: ['POST'])]
    public function deleteComment(int $id, Request $request, CommentaireRepository $commentaireRepository): Response
    {
        // Fetch comment ownership data from repository
        $commentData = $commentaireRepository->findOwnershipDataById($id);

        if (!$commentData) {
            return $this->redirectToRoute('oeuvres');
        }

        $submittedToken = (string) $request->getPayload()->getString('_token');
        if (!$this->isCsrfTokenValid('delete_comment'.$id, $submittedToken)) {
            return $this->redirectToRoute('oeuvres');
        }

        $oeuvreId = $commentData['oeuvreId'];

        // Delete comment via repository
        $commentaireRepository->deleteById($id);

        if ($oeuvreId !== null) {
            return $this->redirectToRoute('app_oeuvre_details', ['id' => $oeuvreId], Response::HTTP_SEE_OTHER);
        }

        return $this->redirectToRoute('oeuvres', [], Response::HTTP_SEE_OTHER);
    }

    #[Route('/bulk-delete', name: 'oeuvre_bulk_delete', methods: ['POST'])]
    public function bulkDelete(Request $request, OeuvreRepository $oeuvreRepository, EntityManagerInterface $entityManager): Response
    {
        if (!$this->isCsrfTokenValid('bulk_delete_oeuvre', $request->getPayload()->getString('_token'))) {
            return $this->redirectToRoute('oeuvres');
        }

        // Get IDs from form
        $ids = $request->getPayload()->all('ids');
        
        if (empty($ids)) {
            return $this->redirectToRoute('oeuvres');
        }

        // Delete likes and comments for each oeuvre
        foreach ($ids as $id) {
            $oeuvreRepository->deleteLikesByOeuvre((int)$id);
            $oeuvreRepository->deleteCommentairesByOeuvre((int)$id);
        }

        // Delete all oeuvres by IDs
        $entityManager->createQuery('DELETE FROM App\Entity\Oeuvre o WHERE o.id IN (:ids)')
            ->setParameter('ids', array_map('intval', $ids))
            ->execute();

        $this->addFlash('success', count($ids) . ' œuvre(s) supprimée(s) avec succès.');
        return $this->redirectToRoute('oeuvres', [], Response::HTTP_SEE_OTHER);
    }

    /**
     * Sort an array of Oeuvre objects by given criteria
        *
        * @param list<Oeuvre> $oeuvres
        * @return list<Oeuvre>
     */
    private function sortOeuvreArray(array $oeuvres, string $sortBy, string $sortOrder): array
    {
        // Validate sortOrder
        if ($sortOrder !== 'ASC' && $sortOrder !== 'DESC') {
            $sortOrder = 'ASC';
        }
        
        // Create array with counts for sorting
        $oeuvresWithCounts = [];
        foreach ($oeuvres as $oeuvre) {
            $count = 0;
            switch ($sortBy) {
                case 'likes':
                    $count = $oeuvre->getLikes()->filter(fn($like) => $like->isLiked())->count();
                    break;
                case 'commentaires':
                    $count = $oeuvre->getCommentaires()->count();
                    break;
                case 'favoris':
                    $count = $oeuvre->getUserFav()->count();
                    break;
                case 'titre':
                default:
                    $count = 0;
            }
            $oeuvresWithCounts[] = [
                'oeuvre' => $oeuvre,
                'count' => $count,
                'titre' => $oeuvre->getTitre() ?? ''
            ];
        }
        
        // Sort using usort
        usort($oeuvresWithCounts, function($a, $b) use ($sortBy, $sortOrder) {
            if ($sortBy === 'titre') {
                $cmp = strcmp($a['titre'], $b['titre']);
                return ($sortOrder === 'DESC') ? -$cmp : $cmp;
            }
            
            // For numeric sorts: likes, commentaires, favoris
            if ($sortOrder === 'ASC') {
                return $a['count'] <=> $b['count'];  // Small to large
            } else {
                return $b['count'] <=> $a['count'];  // Large to small
            }
        });
        
        // Extract sorted oeuvres
        $sorted = [];
        foreach ($oeuvresWithCounts as $item) {
            $sorted[] = $item['oeuvre'];
        }
        
        return $sorted;
    }

    
}
