<?php

namespace App\Twig;

use App\Entity\User;
use App\Repository\TicketRepository;
use App\Service\NotificationService;
use Symfony\Bundle\SecurityBundle\Security;
use Twig\Extension\AbstractExtension;
use Twig\TwigFunction;

class NotificationExtension extends AbstractExtension
{
    public function __construct(
        private TicketRepository $ticketRepository,
        private Security $security,
        private NotificationService $notificationService
    ) {
    }

    public function getFunctions(): array
    {
        return [
            new TwigFunction('canceled_event_notifications', [$this, 'getCanceledEventNotifications']),
            new TwigFunction('recent_reclamations_count', [$this, 'getRecentReclamationsCount']),
            new TwigFunction('recent_reclamations', [$this, 'getRecentReclamations']),
        ];
    }

    /**
     * @return array<int, array{evenement: \App\Entity\Evenement, tickets: string|int}>
     */
    public function getCanceledEventNotifications(): array
    {
        $user = $this->security->getUser();
        if (!$user instanceof User) {
            return [];
        }

        return $this->ticketRepository->findCanceledEventNotificationsForUser($user);
    }

    public function getRecentReclamationsCount(): int
    {
        return $this->notificationService->getRecentReclamationsCount();
    }

    public function getRecentReclamations(int $limit = 5): array
    {
        return $this->notificationService->getRecentReclamations($limit);
    }
}
