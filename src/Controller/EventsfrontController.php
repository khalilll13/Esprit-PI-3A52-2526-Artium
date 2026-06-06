<?php

namespace App\Controller;

use App\Enum\TypeEvenement;
use App\Repository\EvenementRepository;
use App\Service\OllamaSearchService;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Attribute\Route;

final class EventsfrontController extends AbstractController
{
    #[Route('/user-evenements', name: 'app_eventsfront')]
    public function index(EvenementRepository $evenementRepository): Response
    {
        $evenements = $evenementRepository->findBy([], ['date_debut' => 'DESC']);
        $types = TypeEvenement::cases();

        $grouped = [];
        foreach ($types as $type) {
            $grouped[$type->value] = [];
        }

        $allRows = [];
        foreach ($evenements as $evenement) {
            $row = [
                'evenement' => $evenement,
                'image' => $this->getImageDataUri($evenement->getImageCouverture()),
            ];
            $allRows[] = $row;

            $typeValue = $evenement->getType()?->value;
            if ($typeValue && array_key_exists($typeValue, $grouped)) {
                $grouped[$typeValue][] = $row;
            }
        }

        return $this->render('Front Office/eventsfront/eventsfront.html.twig', [
            'types' => $types,
            'all_rows' => $allRows,
            'grouped_rows' => $grouped,
        ]);
    }

    private function getImageDataUri(mixed $image): ?string
    {
        if ($image === null) {
            return null;
        }

        // Legacy: image stored as a resource/blob
        if (is_resource($image)) {
            $data = stream_get_contents($image);
            if ($data === false || $data === '') {
                return null;
            }
            return 'data:image/jpeg;base64,' . base64_encode($data);
        }

        // If image is a string, it is now expected to be a URL or a path
        if (is_string($image)) {
            if (str_starts_with($image, 'data:')) {
                return $image;
            }

            if (preg_match('#^https?://#i', $image)) {
                return $image;
            }

            if (str_starts_with($image, '/')) {
                return $image;
            }

            if (strpos($image, '/') !== false) {
                return '/' . ltrim($image, '/');
            }

            return '/uploads/' . ltrim($image, '/');
        }

        return null;
    }

    #[Route('/search-events', name: 'app_eventsfrontkeyword', methods: ['GET'])]
    public function searchByKeyword(
        Request $request,
        EvenementRepository $evenementRepository,
        OllamaSearchService $searchService
    ): Response
    {
        $query = $request->query->get('query', '');
        
        if (empty($query)) {
            return $this->redirectToRoute('app_eventsfront');
        }

        $evenements = $evenementRepository->findAll();
        $types = TypeEvenement::cases();

        $grouped = [];
        foreach ($types as $type) {
            $grouped[$type->value] = [];
        }

        $rankedResults = $searchService->searchAndRankEvents($query, $evenements);

        $searchResults = [];
        if (!empty($rankedResults)) {
            foreach ($rankedResults as $result) {
                $evenement = $result['evenement'];
                $row = [
                    'evenement' => $evenement,
                    'image' => $this->getImageDataUri($evenement->getImageCouverture()),
                    'ai_score' => $result['score'],
                ];
                $searchResults[] = $row;

                $typeValue = $evenement->getType()?->value;
                if ($typeValue && array_key_exists($typeValue, $grouped)) {
                    $grouped[$typeValue][] = $row;
                }
            }
        } else {
            foreach ($evenements as $evenement) {
                $haystack = trim((string) $evenement->getTitre()) . ' ' . trim((string) $evenement->getDescription());
                if (stripos($haystack, $query) !== false) {
                    $row = [
                        'evenement' => $evenement,
                        'image' => $this->getImageDataUri($evenement->getImageCouverture()),
                    ];
                    $searchResults[] = $row;

                    $typeValue = $evenement->getType()?->value;
                    if ($typeValue && array_key_exists($typeValue, $grouped)) {
                        $grouped[$typeValue][] = $row;
                    }
                }
            }
        }

        return $this->render('Front Office/eventsfront/eventsfront.html.twig', [
            'types' => $types,
            'all_rows' => $searchResults,
            'grouped_rows' => $grouped,
            'search_query' => $query,
        ]);
    }
}
