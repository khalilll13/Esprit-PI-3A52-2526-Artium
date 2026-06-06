<?php

namespace App\Repository;

use App\Entity\LocationLivre;
use App\Entity\Livre;
use App\Enum\EtatLocation;
use Doctrine\Bundle\DoctrineBundle\Repository\ServiceEntityRepository;
use Doctrine\Persistence\ManagerRegistry;

/**
 * @extends ServiceEntityRepository<LocationLivre>
 */
class LocationLivreRepository extends ServiceEntityRepository
{
    public function __construct(ManagerRegistry $registry)
    {
        parent::__construct($registry, LocationLivre::class);
    }

    public function findCurrentActiveForLivre(Livre $livre): ?LocationLivre
    {
        $locations = $this->createQueryBuilder('loc')
            ->andWhere('loc.livre = :livre')
            ->andWhere('loc.etat = :etat')
            ->setParameter('livre', $livre)
            ->setParameter('etat', EtatLocation::ACTIVE)
            ->orderBy('loc.date_debut', 'DESC')
            ->getQuery()
            ->getResult();

        $now = new \DateTime();

        foreach ($locations as $location) {
            $start = $location->getDateDebut();
            if (!$start) {
                continue;
            }

            $days = $location->getNombreDeJours() ?? 1;
            $expiration = (clone $start)->modify('+' . max(1, $days) . ' days');

            if ($expiration > $now) {
                return $location;
            }
        }

        return null;
    }

    public function findLatestActiveForLivre(Livre $livre): ?LocationLivre
    {
        return $this->createQueryBuilder('loc')
            ->andWhere('loc.livre = :livre')
            ->andWhere('loc.etat = :etat')
            ->setParameter('livre', $livre)
            ->setParameter('etat', EtatLocation::ACTIVE)
            ->orderBy('loc.date_debut', 'DESC')
            ->setMaxResults(1)
            ->getQuery()
            ->getOneOrNullResult();
    }

    //    /**
    //     * @return LocationLivre[] Returns an array of LocationLivre objects
    //     */
    //    public function findByExampleField($value): array
    //    {
    //        return $this->createQueryBuilder('l')
    //            ->andWhere('l.exampleField = :val')
    //            ->setParameter('val', $value)
    //            ->orderBy('l.id', 'ASC')
    //            ->setMaxResults(10)
    //            ->getQuery()
    //            ->getResult()
    //        ;
    //    }

    //    public function findOneBySomeField($value): ?LocationLivre
    //    {
    //        return $this->createQueryBuilder('l')
    //            ->andWhere('l.exampleField = :val')
    //            ->setParameter('val', $value)
    //            ->getQuery()
    //            ->getOneOrNullResult()
    //        ;
    //    }
}
