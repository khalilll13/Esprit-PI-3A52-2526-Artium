<?php

namespace App\Service;

use App\Repository\ReclamationRepository;
use App\Enum\StatutReclamation;

class NotificationService
{
    public function __construct(private ReclamationRepository $reclamationRepository) {}

    /**
     * Récupère le nombre de réclamations non traitées (dernières 24h)
     */
    public function getRecentReclamationsCount(): int
    {
        $recentReclamations = $this->reclamationRepository->createQueryBuilder('r')
            ->where('r.date_creation >= :date')
            ->andWhere('r.statut != :statut')
            ->setParameter('date', new \DateTime('-1 day'))
            ->setParameter('statut', StatutReclamation::TRAITEE)
            ->getQuery()
            ->getResult();

        return count($recentReclamations);
    }

    /**
     * Récupère les réclamations non traitées (dernières 24h)
     */
    public function getRecentReclamations(int $limit = 5): array
    {
        return $this->reclamationRepository->createQueryBuilder('r')
            ->where('r.date_creation >= :date')
            ->andWhere('r.statut != :statut')
            ->orderBy('r.date_creation', 'DESC')
            ->addOrderBy('r.id', 'DESC')
            ->setMaxResults($limit)
            ->setParameter('date', new \DateTime('-1 day'))
            ->setParameter('statut', StatutReclamation::TRAITEE)
            ->getQuery()
            ->getResult();
    }
}
