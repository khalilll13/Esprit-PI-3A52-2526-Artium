<?php

namespace App\Controller\Api;

use App\Entity\Ticket;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\Routing\Annotation\Route;
use App\Repository\TicketRepository;


class TicketScanController extends AbstractController
{
    #[Route('/api/ticket/scan', name: 'api_ticket_scan', methods: ['POST'])]
public function scan(Request $request, TicketRepository $ticketRepository, EntityManagerInterface $em): JsonResponse
{
    $data = json_decode($request->getContent(), true);

    if (!isset($data['qr'])) {
        return $this->json([
            'status' => 'error',
            'message' => 'QR code missing'
        ], 400);
    }

    $ticket = $ticketRepository->findOneBy([
        'code_qr' => $data['qr']
    ]);

    if (!$ticket) {
        return $this->json([
            'status' => 'invalid',
            'message' => 'Ticket non valide'
        ], 404);
    }

    // 🔴 ALREADY USED
    if ($ticket->isIsUsed()) {
        return $this->json([
            'status' => 'invalid',
            'reason' => 'already_used',
            'message' => 'Ticket déjà utilisé'
        ], 409);
    }

    // 🟢 FIRST SCAN → VALID
    $ticket->setIsUsed(true);
    $ticket->setUsedAt(new \DateTime());
    $ticket->setScanCount($ticket->getScanCount() + 1);

    $em->flush();

    return $this->json([
        'status' => 'valid',
        'ticket' => [
            'event' => $ticket->getEvenement()->getTitre(),
            'type' => $ticket->getEvenement()->getType()->value,
            'user' => $ticket->getUser()->getPrenom() . ' ' . $ticket->getUser()->getNom(),
            'price' => $ticket->getEvenement()->getPrixTicket(),
            'scan_count' => $ticket->getScanCount()
        ]
    ]);
}}