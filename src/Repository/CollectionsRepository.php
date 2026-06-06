<?php

namespace App\Repository;

use App\Entity\Collections;
use App\Entity\User;
use Doctrine\Bundle\DoctrineBundle\Repository\ServiceEntityRepository;
use Doctrine\Persistence\ManagerRegistry;

/**
 * @extends ServiceEntityRepository<Collections>
 */
class CollectionsRepository extends ServiceEntityRepository
{
    public function __construct(ManagerRegistry $registry)
    {
        parent::__construct($registry, Collections::class);
    }
    public function findAllWithSearchFirst(?string $search = null): array{
        $qb = $this->createQueryBuilder('c');
        if ($search) {
            // Exact match gets 2, partial match gets 1, no match gets 0
            $qb->addSelect("(CASE WHEN LOWER(c.titre) = LOWER(:exact) THEN 2 WHEN c.titre LIKE :search THEN 1 ELSE 0 END) AS HIDDEN searchMatch")
               ->setParameter('exact', $search)
               ->setParameter('search', '%'.$search.'%')
               ->orderBy('searchMatch', 'DESC') // exact matches first (2), then partial (1), then rest (0)
               ->addOrderBy('c.titre', 'ASC'); // then alphabetically
        } else {
            $qb->orderBy('c.titre', 'ASC');
        }
        return $qb->getQuery()->getResult();
    }

    public function findByArtisteWithSearchFirst(User $artiste, ?string $search = null): array
    {
        $qb = $this->createQueryBuilder('c')
            ->andWhere('c.artiste = :artiste')
            ->setParameter('artiste', $artiste);

        if ($search) {
            $qb->addSelect("(CASE WHEN LOWER(c.titre) = LOWER(:exact) THEN 2 WHEN c.titre LIKE :search THEN 1 ELSE 0 END) AS HIDDEN searchMatch")
               ->setParameter('exact', $search)
               ->setParameter('search', '%' . $search . '%')
               ->orderBy('searchMatch', 'DESC')
               ->addOrderBy('c.titre', 'ASC');
        } else {
            $qb->orderBy('c.titre', 'ASC');
        }

        return $qb->getQuery()->getResult();
    }

    public function findInteractionStatsByArtiste(User $artiste): array
    {
        return $this->createQueryBuilder('c')
            ->select('c.id AS collectionId')
            ->addSelect('c.titre AS collectionTitle')
            ->addSelect('COUNT(DISTINCT o.id) AS oeuvresCount')
            ->addSelect('COUNT(DISTINCT l.id) AS likesCount')
            ->addSelect('COUNT(DISTINCT uf.id) AS favorisCount')
            ->addSelect('COUNT(DISTINCT com.id) AS commentairesCount')
            ->addSelect('(COUNT(DISTINCT l.id) + COUNT(DISTINCT uf.id) + COUNT(DISTINCT com.id)) AS interactionsCount')
            ->leftJoin('c.oeuvres', 'o')
            ->leftJoin('o.likes', 'l', 'WITH', 'l.liked = true')
            ->leftJoin('o.user_fav', 'uf')
            ->leftJoin('o.commentaires', 'com')
            ->andWhere('c.artiste = :artiste')
            ->setParameter('artiste', $artiste)
            ->groupBy('c.id, c.titre')
            ->orderBy('interactionsCount', 'DESC')
            ->getQuery()
            ->getArrayResult();
    }


//    /**
//     * @return Collections[] Returns an array of Collections objects
//     */
//    public function findByExampleField($value): array
//    {
//        return $this->createQueryBuilder('c')
//            ->andWhere('c.exampleField = :val')
//            ->setParameter('val', $value)
//            ->orderBy('c.id', 'ASC')
//            ->setMaxResults(10)
//            ->getQuery()
//            ->getResult()
//        ;
//    }

//    public function findOneBySomeField($value): ?Collections
//    {
//        return $this->createQueryBuilder('c')
//            ->andWhere('c.exampleField = :val')
//            ->setParameter('val', $value)
//            ->getQuery()
//            ->getOneOrNullResult()
//        ;
//    }
}
