<?php

namespace App\Service;

use App\Entity\User;
use App\Repository\LocationLivreRepository;
use App\Repository\LivreRepository;
use Symfony\Contracts\Cache\CacheInterface;
use Symfony\Contracts\Cache\ItemInterface;

class RecommendationService
{
    private $locationRepo;
    private $livreRepo;
    private $cache;

    public function __construct(
        LocationLivreRepository $locationRepo,
        LivreRepository $livreRepo,
        CacheInterface $cache
    )
    {
        $this->locationRepo = $locationRepo;
        $this->livreRepo = $livreRepo;
        $this->cache = $cache;
    }

    /**
     * Main recommendation logic
     */
public function getRecommendations(User $user, int $limit = 6): array
{
    return $this->cache->get(
        'recommendations_user_'.$user->getId(),
        function (ItemInterface $item) use ($user, $limit) {

            $item->expiresAfter(3600);

            $profile = $this->getUserProfile($user);
            if (!$profile) return [];

            $alreadyRentedIds = [];

            foreach ($this->locationRepo->findBy(['user'=>$user]) as $loc) {
                $alreadyRentedIds[] = $loc->getLivre()->getId();
            }

            $recommended = [];

            foreach ($profile as $cat => $percentage) {

                $qb = $this->livreRepo->createQueryBuilder('l')
                    ->where('l.categorie = :cat')
                    ->setParameter('cat', $cat)
                    ->setMaxResults(3);

                if (!empty($alreadyRentedIds)) {
                    $qb->andWhere('l.id NOT IN (:ids)')
                       ->setParameter('ids', $alreadyRentedIds);
                }

                $books = $qb->getQuery()->getResult();

                foreach ($books as $book) {
                    $recommended[] = $book;
                }

                if (count($recommended) >= $limit) break;
            }

            return array_slice($recommended, 0, $limit);
        }
    );
}

    /**
     * Stats for Chart.js
     */
    public function getUserCategoryStats(User $user): array
    {
        return $this->cache->get(
            'stats_user_'.$user->getId(),
            function (ItemInterface $item) use ($user) {

                $item->expiresAfter(3600);

                $locations = $this->locationRepo->findBy([
                    'user' => $user
                ]);

                $categoryScore = [];

                foreach ($locations as $loc) {

                    $book = $loc->getLivre();
                    $category = $book->getCategorie();
                    $weight = $loc->getNombreDeJours() ?? 1;

                    if (!isset($categoryScore[$category])) {
                        $categoryScore[$category] = 0;
                    }

                    $categoryScore[$category] += $weight;
                }

                return $categoryScore;
            }
        );
    }

    /**
     * Clear cache when user rents a new book
     */
    public function clearUserCache(User $user): void
    {
        $this->cache->delete('recommendations_user_'.$user->getId());
        $this->cache->delete('stats_user_'.$user->getId());
    }

public function getUserProfile(User $user): array{
    $stats = $this->getUserCategoryStats($user);

    if (!$stats) return [];

    $total = array_sum($stats);

    $profile = [];

    foreach ($stats as $cat => $value) {
        $profile[$cat] = round(($value / $total) * 100);
    }

    arsort($profile);

    return $profile;
}
}