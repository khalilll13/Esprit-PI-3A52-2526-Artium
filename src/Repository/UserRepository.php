<?php

namespace App\Repository;

use App\Enum\Role;
use App\Entity\User;
use Doctrine\Bundle\DoctrineBundle\Repository\ServiceEntityRepository;
use Doctrine\Persistence\ManagerRegistry;

/**
 * @extends ServiceEntityRepository<User>
 */
class UserRepository extends ServiceEntityRepository
{
    /**
     * Recherche les utilisateurs par nom (partiel) et trie par nom (ordre A-Z ou Z-A)
     *
     * @param string|null $nom
     * @param string $order 'ASC' ou 'DESC'
     * @return User[]
     */
    /**
     * Recherche les utilisateurs par nom et prénom combinés (partiel)
     *
     * @param string|null $nomPrenom
     * @param string $order 'ASC' ou 'DESC'
     * @return User[]
     */
    public function searchByNomPrenomQuery(?string $nomPrenom, string $order = 'ASC')
    {
        $qb = $this->createQueryBuilder('u');
        if ($nomPrenom && strpos(trim($nomPrenom), ' ') !== false) {
            $qb->andWhere("CONCAT(LOWER(u.nom), ' ', LOWER(u.prenom)) LIKE :nomPrenom")
               ->setParameter('nomPrenom', '%' . strtolower($nomPrenom) . '%');
        } elseif ($nomPrenom) {
            // Si nomPrenom ne contient pas d'espace, retourne aucun résultat
            $qb->andWhere('1=0');
        }
        // Si nomPrenom est null ou vide, ne filtre pas
        $qb->orderBy('u.nom', $order)
              ->addOrderBy('u.prenom', $order)
              ->setMaxResults(10);
        return $qb->getQuery();
    }
    public function __construct(ManagerRegistry $registry)
    {
        parent::__construct($registry, User::class);
    }

    public function findTopAmateurInteractorsForArtist(User $artist, int $limit = 3): array
    {
        return $this->createQueryBuilder('u')
            ->select('u.id AS userId')
            ->addSelect('u.nom AS nom')
            ->addSelect('u.prenom AS prenom')
            ->addSelect('COUNT(DISTINCT l.id) AS likesCount')
            ->addSelect('COUNT(DISTINCT com.id) AS commentsCount')
            ->addSelect('COUNT(DISTINCT fav.id) AS favorisCount')
            ->addSelect('(COUNT(DISTINCT l.id) + COUNT(DISTINCT com.id) + COUNT(DISTINCT fav.id)) AS interactionsCount')
            ->leftJoin('u.likes', 'l', 'WITH', 'l.liked = true')
            ->leftJoin('l.oeuvre', 'ol')
            ->leftJoin('ol.collection', 'olc')
            ->leftJoin('u.commentaires', 'com')
            ->leftJoin('com.oeuvre', 'oc')
            ->leftJoin('oc.collection', 'occ')
            ->leftJoin('u.fav_user', 'fav')
            ->leftJoin('fav.collection', 'fc')
            ->andWhere('u.role = :amateurRole')
            ->andWhere('(olc.artiste = :artist OR occ.artiste = :artist OR fc.artiste = :artist)')
            ->setParameter('amateurRole', Role::AMATEUR)
            ->setParameter('artist', $artist)
            ->groupBy('u.id, u.nom, u.prenom')
            ->having('interactionsCount > 0')
            ->orderBy('interactionsCount', 'DESC')
            ->addOrderBy('u.nom', 'ASC')
            ->setMaxResults($limit)
            ->getQuery()
            ->getArrayResult();
    }

    //    /**
    //     * @return User[] Returns an array of User objects
    //     */
    //    public function findByExampleField($value): array
    //    {
    //        return $this->createQueryBuilder('u')
    //            ->andWhere('u.exampleField = :val')
    //            ->setParameter('val', $value)
    //            ->orderBy('u.id', 'ASC')
    //            ->setMaxResults(10)
    //            ->getQuery()
    //            ->getResult()
    //        ;
    //    }

    //    public function findOneBySomeField($value): ?User
    //    {
    //        return $this->createQueryBuilder('u')
    //            ->andWhere('u.exampleField = :val')
    //            ->setParameter('val', $value)
    //            ->getQuery()
    //            ->getOneOrNullResult()
    //        ;
    //    }
}
