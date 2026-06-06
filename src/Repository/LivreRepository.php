<?php

namespace App\Repository;

use App\Entity\Livre;
use Doctrine\Bundle\DoctrineBundle\Repository\ServiceEntityRepository;
use Doctrine\Persistence\ManagerRegistry;
use Doctrine\ORM\Tools\Pagination\Paginator;

/**
 * @extends ServiceEntityRepository<Livre>
 */
class LivreRepository extends ServiceEntityRepository
{
    public function __construct(ManagerRegistry $registry)
    {
        parent::__construct($registry, Livre::class);
    }

    /**
     * Search and sort livres for the admin list with simple pagination.
     *
     * - $q can be numeric id for exact match, or text used with LIKE on titre and categorie
     * - $sort accepts: 'price_asc','price_desc','date_asc','date_desc'
     *
     * Returns an array with keys: 'items' => array of Livre, 'total' => int
     */
    public function findForAdmin(?string $q, ?string $sort, int $page = 1, int $limit = 20): array
    {
        $qb = $this->createQueryBuilder('l')
            ->leftJoin('l.collection', 'c')
            ->addSelect('c');

        if ($q !== null && $q !== '') {
            $trim = trim($q);
            if (ctype_digit($trim)) {
                $qb->andWhere('l.id = :id')->setParameter('id', (int) $trim);
            } else {
                $like = '%' . str_replace('%', '\\%', $trim) . '%';
                $qb->andWhere('l.titre LIKE :like OR l.categorie LIKE :like')
                   ->setParameter('like', $like);
            }
        }

        // apply sorting
        switch ($sort) {
            case 'price_asc':
                $qb->orderBy('l.prix_location', 'ASC');
                break;
            case 'price_desc':
                $qb->orderBy('l.prix_location', 'DESC');
                break;
            case 'date_asc':
                $qb->orderBy('l.date_creation', 'ASC');
                break;
            case 'date_desc':
            default:
                // default to newest first if sort not provided
                $qb->orderBy('l.date_creation', 'DESC');
                break;
        }

        $page = max(1, $page);
        $first = ($page - 1) * $limit;

        $query = $qb->getQuery()
            ->setFirstResult($first)
            ->setMaxResults($limit);

        $paginator = new Paginator($query, true);
        $total = count($paginator);
        $items = iterator_to_array($paginator);

        return [
            'items' => $items,
            'total' => $total,
        ];
    }
}
