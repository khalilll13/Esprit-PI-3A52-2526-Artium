<?php

namespace App\Controller;


use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Attribute\Route;
use App\Repository\UserRepository;
use App\Repository\OeuvreRepository;
use App\Repository\ReclamationRepository;
use App\Repository\EvenementRepository;

final class AdminController extends AbstractController
{
    #[Route('/dashboard', name: 'app_admin')]
    public function index(
        UserRepository $userRepository,
        OeuvreRepository $oeuvreRepository,
        ReclamationRepository $reclamationRepository,
        EvenementRepository $evenementRepository
    ): Response {
        // Statistiques globales
        $users_count = $userRepository->count([]);
        $artistes_count = $userRepository->count(['role' => 'Artiste']);
        $amateurs_count = $userRepository->count(['role' => 'Amateur']);
        $oeuvres_count = $oeuvreRepository->count([]);
        $reclamations_count = $reclamationRepository->count([]);
        $events_count = $evenementRepository->count([]);

        // Derniers inscrits (5)
        $last_users = $userRepository->findBy([], ['date_inscription' => 'DESC'], 5);
        // Dernières oeuvres (5)
        $last_oeuvres = $oeuvreRepository->findBy([], ['date_creation' => 'DESC'], 5);

        // Inscriptions récentes (dernières 24h)
        $since = (new \DateTime())->modify('-1 day');
        $recent_signups = $userRepository->createQueryBuilder('u')
            ->where('u.date_inscription >= :since')
            ->setParameter('since', $since)
            ->orderBy('u.date_inscription', 'DESC')
            ->getQuery()->getResult();


        // Réclamations récentes (dernières 24h)
        $yesterday = (new \DateTime())->modify('-1 day');
        $recent_reclamations = $reclamationRepository->createQueryBuilder('r')
            ->where('r.date_creation >= :since')
            ->andWhere('r.statut = :statut')
            ->setParameter('since', $yesterday)
            ->setParameter('statut', 'Non traitée')
            ->orderBy('r.date_creation', 'DESC')
            ->getQuery()->getResult();

        // Top artistes (par vrai nombre d'œuvres)
        $top_artistes = [];
        $artistes = $userRepository->findBy(['role' => 'Artiste']);
        foreach ($artistes as $a) {
            // On compte les collections de l'artiste, puis les oeuvres dans ces collections
            $nbOeuvres = 0;
            foreach ($a->getCollections() as $col) {
                $nbOeuvres += $col->getOeuvres()->count();
            }
            $top_artistes[] = [
                'nom' => $a->getNom(),
                'prenom' => $a->getPrenom(),
                'photoProfil' => $a->getPhotoProfil(),
                'nbOeuvres' => $nbOeuvres
            ];
        }
        usort($top_artistes, fn($a, $b) => $b['nbOeuvres'] <=> $a['nbOeuvres']);
        $top_artistes = array_slice($top_artistes, 0, 5);

        // Données pour les graphiques : activité (inscriptions par mois sur 6 mois)
        $activity_labels = [];
        $activity_data = [];
        $now = new \DateTime();
        $mois_fr = ['janv.', 'févr.', 'mars', 'avr.', 'mai', 'juin', 'juil.', 'août', 'sept.', 'oct.', 'nov.', 'déc.'];
        for ($i = 5; $i >= 0; $i--) {
            $month = (clone $now)->modify("-$i month");
            $label = $mois_fr[((int)$month->format('n')) - 1] . ' ' . $month->format('Y');
            $activity_labels[] = $label;
            $count = $userRepository->createQueryBuilder('u')
                ->select('COUNT(u.id)')
                ->where('u.date_inscription >= :start AND u.date_inscription < :end')
                ->setParameter('start', $month->format('Y-m-01 00:00:00'))
                ->setParameter('end', $month->modify('+1 month')->format('Y-m-01 00:00:00'))
                ->getQuery()->getSingleScalarResult();
            $activity_data[] = (int)$count;
        }

        $roles_data = [
            $userRepository->count(['role' => 'Admin']),
            $artistes_count,
            $amateurs_count
        ];

        // Données calendrier événements
        $events = $evenementRepository->findBy([], ['date_debut' => 'ASC']);
        $events_data = array_map(fn($e) => [
            'title' => $e->getTitre(),
            'start' => $e->getDateDebut()?->format('Y-m-d'),
            'end' => $e->getDateFin()?->format('Y-m-d'),
        ], $events);

        return $this->render('admin/dashboard.html.twig', [
            'users_count' => $users_count,
            'artistes_count' => $artistes_count,
            'amateurs_count' => $amateurs_count,
            'oeuvres_count' => $oeuvres_count,
            'reclamations_count' => $reclamations_count,
            'events_count' => $events_count,
            'last_users' => $last_users,
            'last_oeuvres' => $last_oeuvres,
            'recent_signups' => $recent_signups,
            'recent_reclamations' => $recent_reclamations,
            'top_artistes' => $top_artistes,
            'activity_labels' => $activity_labels,
            'activity_data' => $activity_data,
            'roles_data' => $roles_data,
            'events_data' => $events_data,
        ]);
    }
}
