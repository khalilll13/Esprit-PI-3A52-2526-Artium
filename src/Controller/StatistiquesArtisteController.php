<?php

namespace App\Controller;

use App\Entity\User;
use App\Repository\CommentaireRepository;
use App\Repository\CollectionsRepository;
use App\Repository\OeuvreRepository;
use App\Repository\UserRepository;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Attribute\Route;

final class StatistiquesArtisteController extends AbstractController
{
    #[Route('/artiste-statistiques', name: 'app_statistiquesartiste', methods: ['GET'])]
    public function index(Request $request, CollectionsRepository $collectionsRepository, CommentaireRepository $commentaireRepository, OeuvreRepository $oeuvreRepository, UserRepository $userRepository): Response
    {
        $user = $this->getUser();
        if (!$user instanceof User) {
            throw $this->createAccessDeniedException('Vous devez être connecté pour voir vos statistiques.');
        }

        $statsByCollection = $collectionsRepository->findInteractionStatsByArtiste($user);

        $metricOptions = [
            'interactions' => [
                'field' => 'interactionsCount',
                'label' => 'Interactions',
            ],
            'likes' => [
                'field' => 'likesCount',
                'label' => "J'aime",
            ],
            'favoris' => [
                'field' => 'favorisCount',
                'label' => 'Favoris',
            ],
            'commentaires' => [
                'field' => 'commentairesCount',
                'label' => 'Commentaires',
            ],
            'oeuvres' => [
                'field' => 'oeuvresCount',
                'label' => 'Oeuvres',
            ],
        ];

        $selectedMetric = (string) $request->query->get('metric', 'interactions');
        if (!isset($metricOptions[$selectedMetric])) {
            $selectedMetric = 'interactions';
        }

        $metricField = $metricOptions[$selectedMetric]['field'];
        $metricLabel = $metricOptions[$selectedMetric]['label'];

        $labels = array_map(static fn (array $row): string => (string) $row['collectionTitle'], $statsByCollection);
        $metricDataByMetric = [];
        foreach ($metricOptions as $metricKey => $metricConfig) {
            $field = $metricConfig['field'];
            $metricDataByMetric[$metricKey] = array_map(
                static fn (array $row): int => (int) ($row[$field] ?? 0),
                $statsByCollection
            );
        }

        $palette = [
            '#4f46e5', '#06b6d4', '#22c55e', '#f59e0b', '#ef4444', '#ec4899', '#a855f7', '#14b8a6',
            '#0ea5e9', '#84cc16', '#f97316', '#6366f1', '#10b981', '#d946ef', '#3b82f6', '#eab308',
        ];

        $backgroundColors = [];
        foreach ($statsByCollection as $index => $row) {
            $collectionId = (int) ($row['collectionId'] ?? 0);
            $colorIndex = ($collectionId > 0 ? $collectionId : $index) % count($palette);
            $backgroundColors[] = $palette[$colorIndex];
        }

        $totalCollections = count($statsByCollection);
        $totalOeuvres = array_sum(array_map(static fn (array $row): int => (int) $row['oeuvresCount'], $statsByCollection));
        $totalLikes = array_sum(array_map(static fn (array $row): int => (int) $row['likesCount'], $statsByCollection));
        $totalFavoris = array_sum(array_map(static fn (array $row): int => (int) $row['favorisCount'], $statsByCollection));
        $totalCommentaires = array_sum(array_map(static fn (array $row): int => (int) $row['commentairesCount'], $statsByCollection));
        $totalInteractions = array_sum(array_map(static fn (array $row): int => (int) $row['interactionsCount'], $statsByCollection));

        $topCollectionByMetric = [];
        if ($statsByCollection !== []) {
            foreach ($metricOptions as $metricKey => $metricConfig) {
                $field = $metricConfig['field'];
                $sorted = $statsByCollection;
                usort($sorted, static function (array $a, array $b) use ($field): int {
                    return ((int) ($b[$field] ?? 0)) <=> ((int) ($a[$field] ?? 0));
                });
                $top = $sorted[0] ?? null;
                if ($top !== null) {
                    $topCollectionByMetric[$metricKey] = [
                        'collectionTitle' => (string) ($top['collectionTitle'] ?? ''),
                        'value' => (int) ($top[$field] ?? 0),
                    ];
                }
            }
        }

        $availableMonths = [];
        $currentYear = (int) (new \DateTimeImmutable('now'))->format('Y');
        for ($monthNumber = 1; $monthNumber <= 12; $monthNumber++) {
            $month = new \DateTimeImmutable(sprintf('%d-%02d-01', $currentYear, $monthNumber));
            $availableMonths[$month->format('Y-m')] = $month->format('M Y');
        }

        $selectedMonth = (string) $request->query->get('month', (new \DateTimeImmutable('first day of this month'))->format('Y-m'));
        if (!isset($availableMonths[$selectedMonth])) {
            $selectedMonth = array_key_first($availableMonths);
        }

        $lineDataByMonth = [];
        foreach ($availableMonths as $monthKey => $monthLabel) {
            $monthStart = \DateTimeImmutable::createFromFormat('Y-m-d H:i:s', $monthKey . '-01 00:00:00');
            if (!$monthStart instanceof \DateTimeImmutable) {
                continue;
            }
            $monthEnd = $monthStart->modify('last day of this month')->setTime(23, 59, 59);

            $commentsByDayRaw = $commentaireRepository->countReceivedByArtistePerDayBetween($user, $monthStart, $monthEnd);
            $commentsByDayMap = [];
            foreach ($commentsByDayRaw as $row) {
                $dayKey = null;
                if (($row['commentDate'] ?? null) instanceof \DateTimeInterface) {
                    $dayKey = $row['commentDate']->format('Y-m-d');
                } elseif (is_string($row['commentDate'] ?? null)) {
                    $dayKey = (new \DateTimeImmutable($row['commentDate']))->format('Y-m-d');
                }

                if ($dayKey !== null) {
                    $commentsByDayMap[$dayKey] = (int) ($row['commentsCount'] ?? 0);
                }
            }

            $lineData = [];
            $cursor = $monthStart;
            while ($cursor <= $monthEnd) {
                $dayKey = $cursor->format('Y-m-d');
                $lineData[] = [
                    'x' => $dayKey,
                    'y' => $commentsByDayMap[$dayKey] ?? 0,
                ];
                $cursor = $cursor->modify('+1 day');
            }

            $lineDataByMonth[$monthKey] = $lineData;
        }

        $oeuvreMetricOptions = [
            'interactions' => [
                'field' => 'interactionsCount',
                'label' => 'Interactions',
            ],
            'likes' => [
                'field' => 'likesCount',
                'label' => "J'aime",
            ],
            'favoris' => [
                'field' => 'favorisCount',
                'label' => 'Favoris',
            ],
            'commentaires' => [
                'field' => 'commentairesCount',
                'label' => 'Commentaires',
            ],
        ];

        $selectedOeuvreMetric = (string) $request->query->get('oeuvre_metric', 'likes');
        if (!isset($oeuvreMetricOptions[$selectedOeuvreMetric])) {
            $selectedOeuvreMetric = 'likes';
        }

        $oeuvreStats = $oeuvreRepository->findInteractionStatsByArtiste($user);
        $topOeuvresByMetric = [];
        foreach ($oeuvreMetricOptions as $metricKey => $metricConfig) {
            $field = $metricConfig['field'];
            $sorted = $oeuvreStats;
            usort($sorted, static function (array $a, array $b) use ($field): int {
                $valueA = (int) ($a[$field] ?? 0);
                $valueB = (int) ($b[$field] ?? 0);
                if ($valueA === $valueB) {
                    return strcmp((string) ($a['oeuvreTitle'] ?? ''), (string) ($b['oeuvreTitle'] ?? ''));
                }

                return $valueB <=> $valueA;
            });

            $top = array_slice($sorted, 0, 3);
            $topOeuvresByMetric[$metricKey] = [
                'labels' => array_map(static fn (array $row): string => (string) ($row['oeuvreTitle'] ?? ''), $top),
                'values' => array_map(static fn (array $row): int => (int) ($row[$field] ?? 0), $top),
            ];
        }

        $topAmateurInteractors = $userRepository->findTopAmateurInteractorsForArtist($user, 3);

        return $this->render('Front Office/statistiquesartiste/statistiquesartiste.html.twig', [
            'stats_by_collection' => $statsByCollection,
            'chart_labels' => $labels,
            'chart_data_by_metric' => $metricDataByMetric,
            'chart_colors' => $backgroundColors,
            'selected_metric' => $selectedMetric,
            'metric_options' => array_map(static fn (array $item): string => $item['label'], $metricOptions),
            'metric_field' => $metricField,
            'metric_label' => $metricLabel,
            'total_collections' => $totalCollections,
            'total_oeuvres' => $totalOeuvres,
            'total_likes' => $totalLikes,
            'total_favoris' => $totalFavoris,
            'total_commentaires' => $totalCommentaires,
            'total_interactions' => $totalInteractions,
            'top_collection_by_metric' => $topCollectionByMetric,
            'line_data_by_month' => $lineDataByMonth,
            'selected_month' => $selectedMonth,
            'available_months' => $availableMonths,
            'top_oeuvres_by_metric' => $topOeuvresByMetric,
            'oeuvre_metric_options' => array_map(static fn (array $item): string => $item['label'], $oeuvreMetricOptions),
            'selected_oeuvre_metric' => $selectedOeuvreMetric,
            'top_amateur_interactors' => $topAmateurInteractors,
        ]);
    }
}
