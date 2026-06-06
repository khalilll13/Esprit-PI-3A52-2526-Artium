<?php

namespace App\Service;

use App\Entity\Reclamation;
use App\Enum\StatutReclamation;
use Doctrine\ORM\EntityManagerInterface;

class ReclamationArchiveService
{
    public function __construct(private EntityManagerInterface $entityManager) {}

    public function archiveEligibleReclamations(): int
    {
        // Récupérer les réclamations traitées avec une réponse datant de plus de 30 jours
        // ET qui n'ont pas été modifiées (désarchivées) depuis moins de 7 jours
        $reclamations = $this->entityManager->getRepository(Reclamation::class)
            ->createQueryBuilder('r')
            ->leftJoin('r.reponses', 'rep')
            ->where('r.statut = :statut')
            ->andWhere('rep.date_reponse IS NOT NULL')
            ->andWhere('rep.date_reponse <= :date30')
            ->andWhere('r.updatedAt <= :date7')
            ->groupBy('r.id')
            ->having('MAX(rep.date_reponse) <= :date30')
            ->setParameter('statut', StatutReclamation::TRAITEE)
            ->setParameter('date30', new \DateTime('-30 days'))
            ->setParameter('date7', new \DateTime('-7 days'))
            ->getQuery()
            ->getResult();

        $count = 0;
        
        // Archiver les réclamations éligibles
        foreach ($reclamations as $reclamation) {
            if ($reclamation->getStatut() !== StatutReclamation::ARCHIVE) {
                $reclamation->setStatut(StatutReclamation::ARCHIVE);
                $this->entityManager->persist($reclamation);
                $count++;
            }
        }

        // Flush les modifications
        if ($count > 0) {
            $this->entityManager->flush();
        }

        return $count;
    }
}
