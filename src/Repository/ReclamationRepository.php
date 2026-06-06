<?php

namespace App\Repository;

use App\Entity\Reclamation;
use App\Entity\User;
use App\Enum\StatutReclamation;
use App\Enum\TypeReclamation;
use Doctrine\Bundle\DoctrineBundle\Repository\ServiceEntityRepository;
use Doctrine\Persistence\ManagerRegistry;

/**
 * @extends ServiceEntityRepository<Reclamation>
 */
class ReclamationRepository extends ServiceEntityRepository
{
    public function __construct(ManagerRegistry $registry)
    {
        parent::__construct($registry, Reclamation::class);
    }

    /**
     * @return Reclamation[]
     */
    public function findByUserFilters(User $user, ?string $search, ?StatutReclamation $statut, ?TypeReclamation $type): array
    {
        $qb = $this->createQueryBuilder('r')
            ->andWhere('r.user = :user')
            ->setParameter('user', $user)
            ->orderBy('r.date_creation', 'DESC');

        if ($search !== null && $search !== '') {
            $qb->andWhere('r.texte LIKE :search')
                ->setParameter('search', '%' . $search . '%');
        }

        if ($statut !== null) {
            $qb->andWhere('r.statut = :statut')
                ->setParameter('statut', $statut);
        }

        if ($type !== null) {
            $qb->andWhere('r.type = :type')
                ->setParameter('type', $type);
        }

        return $qb->getQuery()->getResult();
    }

    //    /**
    //     * @return Reclamation[] Returns an array of Reclamation objects
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

    //    public function findOneBySomeField($value): ?Reclamation
    //    {
    //        return $this->createQueryBuilder('r')
    //            ->andWhere('r.exampleField = :val')
    //            ->setParameter('val', $value)
    //            ->getQuery()
    //            ->getOneOrNullResult()
    //        ;
    //    }
}
