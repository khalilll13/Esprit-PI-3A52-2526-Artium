<?php

namespace App\Repository;

use App\Entity\Reponse;
use Doctrine\Bundle\DoctrineBundle\Repository\ServiceEntityRepository;
use Doctrine\Persistence\ManagerRegistry;

/**
 * @extends ServiceEntityRepository<Reponse>
 */
class ReponseRepository extends ServiceEntityRepository
{
    public function __construct(ManagerRegistry $registry)
    {
        parent::__construct($registry, Reponse::class);
    }

    /**
     * @param int[] $reclamationIds
     * @return array<int, int>
     */
    public function countByReclamationIds(array $reclamationIds): array
    {
        if ($reclamationIds === []) {
            return [];
        }

        $rows = $this->createQueryBuilder('rep')
            ->select('IDENTITY(rep.reclamation) AS reclamationId')
            ->addSelect('COUNT(rep.id) AS reponsesCount')
            ->where('IDENTITY(rep.reclamation) IN (:reclamationIds)')
            ->setParameter('reclamationIds', $reclamationIds)
            ->groupBy('rep.reclamation')
            ->getQuery()
            ->getArrayResult();

        $counts = [];
        foreach ($rows as $row) {
            $counts[(int) $row['reclamationId']] = (int) $row['reponsesCount'];
        }

        return $counts;
    }

    //    /**
    //     * @return Reponse[] Returns an array of Reponse objects
    //     */
    //    public function findByExampleField($value): array
    //    {
    //        return $this->createQueryBuilder('r')
    //            ->andWhere('r.exampleField = :val')
    //            ->setParameter('val', $value)
    //            ->orderBy('r.id', 'ASC')
    //            ->setMaxResults(10)
    //            ->getQuery()
    //            ->getResult()
    //        ;
    //    }

    //    public function findOneBySomeField($value): ?Reponse
    //    {
    //        return $this->createQueryBuilder('r')
    //            ->andWhere('r.exampleField = :val')
    //            ->setParameter('val', $value)
    //            ->getQuery()
    //            ->getOneOrNullResult()
    //        ;
    //    }
}
