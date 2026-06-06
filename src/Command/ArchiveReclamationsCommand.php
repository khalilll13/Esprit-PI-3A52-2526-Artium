<?php

namespace App\Command;

use App\Enum\StatutReclamation;
use App\Repository\ReclamationRepository;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Component\Console\Command\Command;
use Symfony\Component\Console\Input\InputInterface;
use Symfony\Component\Console\Output\OutputInterface;

class ArchiveReclamationsCommand extends Command
{
    protected static $defaultName = 'app:archive-reclamations';
    private $reclamationRepository;
    private $entityManager;

    public function __construct(ReclamationRepository $reclamationRepository, EntityManagerInterface $entityManager)
    {
        parent::__construct();
        $this->reclamationRepository = $reclamationRepository;
        $this->entityManager = $entityManager;
    }

    protected function configure(): void
    {
        $this->setDescription('Archive automatiquement les réclamations traitées depuis plus de 30 jours.');
    }

    protected function execute(InputInterface $input, OutputInterface $output): int
    {
        $reclamations = $this->reclamationRepository->createQueryBuilder('r')
            ->leftJoin('r.reponses', 'rep')
            ->where('r.statut = :statut')
            ->andWhere('rep.date_reponse IS NOT NULL')
            ->andWhere('rep.date_reponse <= :date')
            ->groupBy('r.id')
            ->having('MAX(rep.date_reponse) <= :date')
            ->setParameter('statut', StatutReclamation::TRAITEE)
            ->setParameter('date', new \DateTime('-30 days'))
            ->getQuery()
            ->getResult();

        foreach ($reclamations as $reclamation) {
            $reclamation->setStatut(StatutReclamation::ARCHIVE);
        }

        $this->entityManager->flush();

        $output->writeln(count($reclamations) . ' réclamation(s) archivée(s).');
        return Command::SUCCESS;
    }
}
