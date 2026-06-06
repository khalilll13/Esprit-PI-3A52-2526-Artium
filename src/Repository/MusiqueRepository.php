<?php

namespace App\Repository;

use App\Entity\Musique;
use Doctrine\Bundle\DoctrineBundle\Repository\ServiceEntityRepository;
use Doctrine\Persistence\ManagerRegistry;

/**
 * @extends ServiceEntityRepository<Musique>
 */
class MusiqueRepository extends ServiceEntityRepository
{
    public function __construct(ManagerRegistry $registry)
    {
        parent::__construct($registry, Musique::class);
    }

    //    /**
    //     * @return Musique[] Returns an array of Musique objects
    //     */
    //    public function findByExampleField($value): array
    //    {
    //        return $this->createQueryBuilder('m')
    //            ->andWhere('m.exampleField = :val')
    //            ->setParameter('val', $value)
    //            ->orderBy('m.id', 'ASC')
    //            ->setMaxResults(10)
    //            ->getQuery()
    //            ->getResult()
    //        ;
    //    }

    //    public function findOneBySomeField($value): ?Musique
    //    {
    //        return $this->createQueryBuilder('m')
    //            ->andWhere('m.exampleField = :val')
    //            ->setParameter('val', $value)
    //            ->getQuery()
    //            ->getOneOrNullResult()
    //        ;
    //    }

    /**
     * Find all music IDs and basic info only, without BLOBs
     * Returns lightweight data for listing pages
     * Use find($id) to get full entity with blobs when needed
        *
        * @return list<array<string, mixed>>
     */
    
    public function findAllLightweight(): array
    {
        $conn = $this->getEntityManager()->getConnection();
        
        $sql = '
            SELECT 
                m.id, 
                o.titre, 
                o.description, 
                o.date_creation,
                m.genre,
                u.nom AS artiste_nom,
                u.prenom AS artiste_prenom
            FROM musique m
            INNER JOIN oeuvre o ON m.id = o.id
            LEFT JOIN collections c ON o.collection_id = c.id
            LEFT JOIN `user` u ON c.artiste_id = u.id
            ORDER BY o.date_creation DESC
        ';
        
        $stmt = $conn->executeQuery($sql);
        return $stmt->fetchAllAssociative();
    }

    /**
     * Search and filter music with optional sorting
      * @param string|null $searchTerm Search in titre, description and artist name
     * @param string|null $sortBy Sort field: 'titre', 'date', 'genre'
     * @param string $sortOrder ASC or DESC
      * @return list<array<string, mixed>>
     */
    public function searchAndFilter(?string $searchTerm = null, ?string $sortBy = 'date', string $sortOrder = 'DESC', ?int $artistId = null): array
    {
        $conn = $this->getEntityManager()->getConnection();
        
        $sql = '
            SELECT 
                m.id, 
                o.titre, 
                o.description, 
                o.date_creation,
                m.genre,
                u.nom AS artiste_nom,
                u.prenom AS artiste_prenom
            FROM musique m
            INNER JOIN oeuvre o ON m.id = o.id
            LEFT JOIN collections c ON o.collection_id = c.id
            LEFT JOIN `user` u ON c.artiste_id = u.id
            WHERE 1=1
        ';
        
        $params = [];

        if ($artistId !== null) {
            $sql .= ' AND u.id = :artistId';
            $params['artistId'] = $artistId;
        }
        
        // Add search filter if provided
        if ($searchTerm) {
            $sql .= ' AND (
                o.titre LIKE :search
                OR o.description LIKE :search
                OR u.nom LIKE :search
                OR u.prenom LIKE :search
                OR CONCAT(COALESCE(u.prenom, \'\'), \' \', COALESCE(u.nom, \'\')) LIKE :search
                OR CONCAT(COALESCE(u.nom, \'\'), \' \', COALESCE(u.prenom, \'\')) LIKE :search
            )';
            $params['search'] = '%' . $searchTerm . '%';
        }
        
        // Add sorting
        $sortBy = match($sortBy) {
            'titre' => 'o.titre',
            'genre' => 'm.genre',
            'date' => 'o.date_creation',
            default => 'o.date_creation'
        };
        
        $sql .= ' ORDER BY ' . $sortBy . ' ' . strtoupper($sortOrder);
        
        $stmt = $conn->executeQuery($sql, $params);
        return $stmt->fetchAllAssociative();
    }
}
