<?php

namespace App\Controller;

use App\Entity\Evenement;
use App\Entity\Ticket;
use App\Entity\User;
use App\Repository\EvenementRepository;
use App\Repository\TicketRepository;
use App\Repository\UserRepository;
use Doctrine\ORM\EntityManagerInterface;
use Psr\Log\LoggerInterface;
use Stripe\Checkout\Session;
use Stripe\Exception\ApiErrorException;
use Stripe\Stripe;
use Stripe\Webhook;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Mailer\MailerInterface;
use Symfony\Component\Mime\Address;
use Symfony\Component\Mime\Email;
use Symfony\Component\Routing\Attribute\Route;
use Symfony\Component\Routing\Generator\UrlGeneratorInterface;

#[Route('/payment')]
class PaymentController extends AbstractController
{
    public function __construct(
        private readonly string $stripeSecretKey,
    ) {
    }

    /**
     * Créer une session de paiement Stripe pour l'achat d'un ticket
     */
    #[Route('/checkout/{id}', name: 'app_payment_checkout', methods: ['POST'])]
    public function checkout(
        Evenement $evenement,
        Request $request,
        UserRepository $userRepository,
        LoggerInterface $logger
    ): Response {
        $user = $this->getUser();
        if (!$user instanceof User) {
            $fallback = $userRepository->find(1);
            if ($fallback instanceof User) {
                $user = $fallback;
            } else {
                throw $this->createAccessDeniedException('Vous devez être connecté pour acheter un ticket');
            }
        }

        // Vérifier le token CSRF
        if (!$this->isCsrfTokenValid('buy_ticket_' . $evenement->getId(), $request->request->get('_token'))) {
            $this->addFlash('error', 'Token de sécurité invalide');
            return $this->redirectToRoute('app_eventdetails', ['id' => $evenement->getId()]);
        }

        $logger->info('Début processus de paiement Stripe', [
            'evenement_id' => $evenement->getId(),
            'evenement_titre' => $evenement->getTitre(),
            'prix' => $evenement->getPrixTicket(),
            'user_id' => $user->getId()
        ]);

        try {
            Stripe::setApiKey($this->stripeSecretKey);

            // Créer les URLs manuellement pour éviter l'encodage des placeholders
            $successUrl = $this->generateUrl('app_payment_success', [], UrlGeneratorInterface::ABSOLUTE_URL);
            $successUrl .= '?session_id={CHECKOUT_SESSION_ID}';
            
            $cancelUrl = $this->generateUrl('app_payment_cancel', [
                'evenement_id' => $evenement->getId()
            ], UrlGeneratorInterface::ABSOLUTE_URL);

            // Créer la session de checkout Stripe
            $session = Session::create([
                'payment_method_types' => ['card'],
                'line_items' => [[
                    'price_data' => [
                        'currency' => 'eur', // EUR car Stripe ne supporte pas TND en test
                        'product_data' => [
                            'name' => $evenement->getTitre(),
                            'description' => sprintf(
                                'Ticket pour %s le %s',
                                $evenement->getTitre(),
                                $evenement->getDateDebut()?->format('d/m/Y H:i') ?? 'Date à définir'
                            ),
                            'images' => [],
                        ],
                        'unit_amount' => (int) ($evenement->getPrixTicket() * 100), // Convertir en centimes
                    ],
                    'quantity' => 1,
                ]],
                'mode' => 'payment',
                'success_url' => $successUrl,
                'cancel_url' => $cancelUrl,
                'client_reference_id' => sprintf('%d|%d', $user->getId(), $evenement->getId()),
                'customer_email' => $user->getEmail(),
                'metadata' => [
                    'user_id' => (string) $user->getId(),
                    'evenement_id' => (string) $evenement->getId(),
                    'evenement_titre' => $evenement->getTitre(),
                ],
            ]);

            // Rediriger vers la page de paiement Stripe
            return $this->redirect($session->url, Response::HTTP_SEE_OTHER);

        } catch (ApiErrorException $e) {
            $logger->error('Erreur Stripe lors de la création du checkout', [
                'error' => $e->getMessage(),
                'evenement_id' => $evenement->getId(),
                'stripe_key' => substr($this->stripeSecretKey, 0, 10) . '...'
            ]);
            $this->addFlash('error', 'Erreur lors de la création du paiement: ' . $e->getMessage());
            return $this->redirectToRoute('app_eventdetails', ['id' => $evenement->getId()]);
        }
    }

    /**
     * Page de succès après paiement
     * CRÉE LE TICKET ICI au lieu d'attendre le webhook
     */
    #[Route('/success', name: 'app_payment_success', methods: ['GET'])]
    public function success(
        Request $request,
        EntityManagerInterface $entityManager,
        EvenementRepository $evenementRepository,
        UserRepository $userRepository,
        TicketRepository $ticketRepository,
        MailerInterface $mailer,
        LoggerInterface $logger
    ): Response {
        $sessionId = $request->query->get('session_id');
        
        if (!$sessionId) {
            $this->addFlash('warning', 'Session de paiement introuvable');
            return $this->redirectToRoute('app_eventsfront');
        }

        $ticket = null;
        $evenement = null;

        try {
            Stripe::setApiKey($this->stripeSecretKey);
            $session = Session::retrieve($sessionId);

            if ($session->payment_status === 'paid') {
                // Extraire les métadonnées
                $userId = (int) $session->metadata->user_id;
                $evenementId = (int) $session->metadata->evenement_id;

                $user = $userRepository->find($userId);
                $evenement = $evenementRepository->find($evenementId);

                if ($user && $evenement) {
                    // Vérifier si le ticket n'existe pas déjà (éviter les doublons)
                    // Chercher tous les tickets de cet utilisateur pour cet événement
                    $existingTickets = $ticketRepository->findBy([
                        'user' => $user,
                        'evenement' => $evenement,
                    ], ['date_achat' => 'DESC'], 1);

                    // Vérifier si le ticket le plus récent a été créé dans les 5 dernières minutes
                    $ticket = null;
                    if (!empty($existingTickets)) {
                        $lastTicket = $existingTickets[0];
                        $fiveMinutesAgo = new \DateTime('-5 minutes');
                        if ($lastTicket->getDateAchat() >= $fiveMinutesAgo) {
                            $ticket = $lastTicket;
                            $logger->info('Ticket existant trouvé (doublon évité)', ['ticket_id' => $ticket->getId()]);
                        }
                    }

                    if (!$ticket) {
                        // Créer le ticket
                        $payload = $this->buildTicketPayload($evenement, $user);
                        
                        $ticket = new Ticket();
                        $ticket->setEvenement($evenement);
                        $ticket->setUser($user);
                        $ticket->setDateAchat(new \DateTime());
                        $ticket->setCodeQr($payload);

                        $entityManager->persist($ticket);
                        $entityManager->flush();

                        // Envoyer l'email avec le ticket
                        $this->sendTicketEmail($mailer, $logger, $user, $evenement, $ticket, $payload);

                        $logger->info('Ticket créé sur page success', [
                            'ticket_id' => $ticket->getId(),
                            'user_id' => $userId,
                            'evenement_id' => $evenementId,
                            'session_id' => $sessionId,
                        ]);
                    }
                }

                $this->addFlash('success', 'Paiement réussi ! Votre ticket vous a été envoyé par email.');
            } else {
                $this->addFlash('warning', 'Le paiement est en cours de traitement...');
            }

        } catch (ApiErrorException $e) {
            $this->addFlash('error', 'Erreur lors de la vérification du paiement');
            $logger->error('Erreur récupération session Stripe', ['error' => $e->getMessage()]);
        }

        return $this->render('Front Office/payment/success.html.twig', [
            'session_id' => $sessionId,
            'ticket' => $ticket,
            'evenement' => $evenement,
        ]);
    }

    /**
     * Page d'annulation
     */
    #[Route('/cancel', name: 'app_payment_cancel', methods: ['GET'])]
    public function cancel(Request $request): Response
    {
        $evenementId = $request->query->get('evenement_id');
        
        $this->addFlash('warning', 'Paiement annulé. Vous pouvez réessayer quand vous voulez.');
        
        if ($evenementId) {
            return $this->redirectToRoute('app_eventdetails', ['id' => $evenementId]);
        }

        return $this->redirectToRoute('app_eventsfront');
    }

    /**
     * Webhook Stripe pour gérer les événements de paiement
     */
    #[Route('/webhook', name: 'app_payment_webhook', methods: ['POST'])]
    public function webhook(
        Request $request,
        EntityManagerInterface $entityManager,
        EvenementRepository $evenementRepository,
        UserRepository $userRepository,
        MailerInterface $mailer,
        LoggerInterface $logger
    ): Response {
        $payload = $request->getContent();
        $sigHeader = $request->headers->get('Stripe-Signature');
        $webhookSecret = $_ENV['STRIPE_WEBHOOK_SECRET'] ?? '';

        try {
            if ($webhookSecret) {
                $event = Webhook::constructEvent($payload, $sigHeader, $webhookSecret);
            } else {
                $event = json_decode($payload, false, 512, JSON_THROW_ON_ERROR);
            }

            // Gérer l'événement checkout.session.completed
            if ($event->type === 'checkout.session.completed') {
                $session = $event->data->object;

                if ($session->payment_status === 'paid') {
                    // Extraire les IDs depuis les métadonnées
                    $userId = (int) $session->metadata->user_id;
                    $evenementId = (int) $session->metadata->evenement_id;

                    $user = $userRepository->find($userId);
                    $evenement = $evenementRepository->find($evenementId);

                    if ($user && $evenement) {
                        // Créer le ticket
                        $payload = $this->buildTicketPayload($evenement, $user);
                        
                        $ticket = new Ticket();
                        $ticket->setEvenement($evenement);
                        $ticket->setUser($user);
                        $ticket->setDateAchat(new \DateTime());
                        $ticket->setCodeQr($payload);

                        $entityManager->persist($ticket);
                        $entityManager->flush();

                        // Envoyer l'email avec le ticket
                        $this->sendTicketEmail($mailer, $logger, $user, $evenement, $ticket, $payload);

                        $logger->info('Ticket créé après paiement', [
                            'ticket_id' => $ticket->getId(),
                            'user_id' => $userId,
                            'evenement_id' => $evenementId,
                            'session_id' => $session->id,
                        ]);
                    }
                }
            }

            return new Response('', Response::HTTP_OK);

        } catch (\Exception $e) {
            $logger->error('Erreur webhook Stripe', [
                'error' => $e->getMessage(),
                'trace' => $e->getTraceAsString(),
            ]);
            return new Response('Webhook error', Response::HTTP_BAD_REQUEST);
        }
    }

    /**
     * Construire le payload du ticket (code QR)
     */
    private function buildTicketPayload(Evenement $evenement, User $user): string
    {
        return json_encode([
            'evenement_id' => $evenement->getId(),
            'evenement_titre' => $evenement->getTitre(),
            'user_id' => $user->getId(),
            'user_nom' => $user->getNom(),
            'user_prenom' => $user->getPrenom(),
            'date_debut' => $evenement->getDateDebut()?->format('Y-m-d H:i:s'),
            'prix' => $evenement->getPrixTicket(),
            'timestamp' => time(),
        ], JSON_THROW_ON_ERROR);
    }

    /**
     * Envoyer l'email avec le ticket
     */
    private function sendTicketEmail(
        MailerInterface $mailer,
        LoggerInterface $logger,
        User $user,
        Evenement $evenement,
        Ticket $ticket,
        string $payload
    ): void {
        try {
            $email = (new Email())
                ->from(new Address('noreply@artium.tn', 'ARTIUM'))
                ->to($user->getEmail())
                ->subject('🎟️ Votre ticket ARTIUM - ' . $evenement->getTitre())
                ->html($this->renderView('emails/ticket.html.twig', [
                    'user' => $user,
                    'evenement' => $evenement,
                    'ticket' => $ticket,
                ]));

            $mailer->send($email);
            $logger->info('Email de ticket envoyé', ['user_id' => $user->getId(), 'ticket_id' => $ticket->getId()]);
        } catch (\Exception $e) {
            $logger->error('Erreur envoi email ticket', ['error' => $e->getMessage()]);
        }
    }
}
