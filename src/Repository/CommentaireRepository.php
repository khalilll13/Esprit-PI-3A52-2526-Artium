<?php

namespace App\Repository;

use App\Entity\Commentaire;
use App\Entity\User;
use Doctrine\DBAL\ParameterType;
use Doctrine\Bundle\DoctrineBundle\Repository\ServiceEntityRepository;
use Doctrine\Persistence\ManagerRegistry;

/**
 * @extends ServiceEntityRepository<Commentaire>
 */
class CommentaireRepository extends ServiceEntityRepository
{
    public function __construct(ManagerRegistry $registry)
    {
        parent::__construct($registry, Commentaire::class);
    }

    /**
     * @param int[] $oeuvreIds
     * @return array<int, Commentaire[]>
     */
    public function findGroupedByOeuvreIdsWithUser(array $oeuvreIds): array
    {
        if ($oeuvreIds === []) {
            return [];
        }

        $comments = $this->createQueryBuilder('c')
            ->leftJoin('c.user', 'u')
            ->addSelect('u')
            ->where('IDENTITY(c.oeuvre) IN (:oeuvreIds)')
            ->setParameter('oeuvreIds', $oeuvreIds)
            ->orderBy('c.date_commentaire', 'ASC')
            ->addOrderBy('c.id', 'ASC')
            ->getQuery()
            ->getResult();

        $grouped = [];
        foreach ($comments as $comment) {
            $oeuvre = $comment->getOeuvre();
            $oeuvreId = $oeuvre?->getId();
            if ($oeuvreId === null) {
                continue;
            }
            $grouped[$oeuvreId][] = $comment;
        }

        return $grouped;
    }

    public function findOwnershipDataById(int $id): ?array
    {
        return $this->createQueryBuilder('c')
            ->select('c.id AS id, IDENTITY(c.user) AS userId, IDENTITY(c.oeuvre) AS oeuvreId')
            ->andWhere('c.id = :id')
            ->setParameter('id', $id)
            ->getQuery()
            ->getOneOrNullResult();
    }

    public function deleteById(int $id): int
    {
        return $this->createQueryBuilder('c')
            ->delete()
            ->andWhere('c.id = :id')
            ->setParameter('id', $id)
            ->getQuery()
            ->execute();
    }

    public function updateTextIfOwnedByUser(int $id, int $userId, string $texte): int
    {
        return $this->getEntityManager()->getConnection()->executeStatement(
            'UPDATE commentaire SET texte = :texte WHERE id = :id AND user_id = :userId',
            [
                'texte' => $texte,
                'id' => $id,
                'userId' => $userId,
            ],
            [
                'id' => ParameterType::INTEGER,
                'userId' => ParameterType::INTEGER,
            ]
        );
    }

    public function countReceivedByArtistePerDayBetween(User $artiste, \DateTimeInterface $startDate, \DateTimeInterface $endDate): array
    {
        return $this->createQueryBuilder('com')
            ->select('com.date_commentaire AS commentDate')
            ->addSelect('COUNT(DISTINCT com.id) AS commentsCount')
            ->innerJoin('com.oeuvre', 'o')
            ->innerJoin('o.collection', 'c')
            ->andWhere('c.artiste = :artiste')
            ->andWhere('com.date_commentaire >= :startDate')
            ->andWhere('com.date_commentaire <= :endDate')
            ->setParameter('artiste', $artiste)
            ->setParameter('startDate', $startDate)
            ->setParameter('endDate', $endDate)
            ->groupBy('com.date_commentaire')
            ->orderBy('com.date_commentaire', 'ASC')
            ->getQuery()
            ->getArrayResult();
    }

    //    /**
    //     * @return Commentaire[] Returns an array of Commentaire objects
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

    //    public function findOneBySomeField($value): ?Commentaire
    //    {
    //        return $this->createQueryBuilder('c')
    //            ->andWhere('c.exampleField = :val')
    //            ->setParameter('val', $value)
    //            ->getQuery()
    //            ->getOneOrNullResult()
    //        ;
    //    }
    /**
     * Count comments for a given user (as artist, on their works)
     */
    public function countByArtist(\App\Entity\User $user): int
    {
        return $this->createQueryBuilder('c')
            ->select('COUNT(DISTINCT c.id)')
            ->join('c.oeuvre', 'o')
            ->join('o.collection', 'col')
            ->where('col.artiste = :user')
            ->setParameter('user', $user)
            ->getQuery()
            ->getSingleScalarResult();
    }
}
