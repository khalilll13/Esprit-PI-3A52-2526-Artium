<?php

namespace App\Repository;

use App\Entity\Evenement;
use App\Entity\Ticket;
use App\Entity\User;
use App\Enum\StatutEvenement;
use Doctrine\Bundle\DoctrineBundle\Repository\ServiceEntityRepository;
use Doctrine\Persistence\ManagerRegistry;

/**
 * @extends ServiceEntityRepository<Ticket>
 */
class TicketRepository extends ServiceEntityRepository
{
    public function __construct(ManagerRegistry $registry)
    {
        parent::__construct($registry, Ticket::class);
    }

    /**
     * @return array<int, array{evenement: \App\Entity\Evenement, tickets: string|int}>
     */
    public function findCanceledEventNotificationsForUser(User $user): array
    {
        $dql = sprintf(
            'SELECT e AS evenement, '
            .'(SELECT COUNT(t2.id) FROM %s t2 WHERE t2.evenement = e AND t2.user = :user) AS tickets '
            .'FROM %s e '
            .'WHERE e.statut = :statut '
            .'AND EXISTS (SELECT t3.id FROM %s t3 WHERE t3.evenement = e AND t3.user = :user) '
            .'ORDER BY e.date_debut DESC',
            Ticket::class,
            Evenement::class,
            Ticket::class
        );

        return $this->getEntityManager()->createQuery($dql)
            ->setParameter('user', $user)
            ->setParameter('statut', StatutEvenement::ANNULE)
            ->getResult();
    }

//    /**
//     * @return Ticket[] Returns an array of Ticket objects
//     */
//    public function findByExampleField($value): array
//    {
//        return $this->createQueryBuilder('t')
//            ->andWhere('t.exampleField = :val')
//            ->setParameter('val', $value)
//            ->orderBy('t.id', 'ASC')
//            ->setMaxResults(10)
//            ->getQuery()
//            ->getResult()
//        ;
//    }

//    public function findOneBySomeField($value): ?Ticket
//    {
//        return $this->createQueryBuilder('t')
//            ->andWhere('t.exampleField = :val')
//            ->setParameter('val', $value)
//            ->getQuery()
//            ->getOneOrNullResult()
//        ;
//    }
}
