<?php

namespace App\Controller;

use App\Entity\Evenement;
use App\Entity\Ticket;
use App\Entity\User;
use App\Repository\TicketRepository;
use App\Repository\UserRepository;
use Doctrine\ORM\EntityManagerInterface;
use Endroid\QrCode\QrCode;
use Endroid\QrCode\Writer\PngWriter;
use Endroid\QrCode\Writer\SvgWriter;
use Psr\Log\LoggerInterface;
use Symfony\Bridge\Twig\Mime\TemplatedEmail;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Mailer\MailerInterface;
use Symfony\Component\Mime\Address;
use Symfony\Component\Routing\Attribute\Route;

final class EventdetailsController extends AbstractController
{
    #[Route('/details-evenement/{id}', name: 'app_eventdetails', methods: ['GET', 'POST'])]
    public function index(
        Request $request,
        Evenement $evenement,
        TicketRepository $ticketRepository,
        UserRepository $userRepository,
        EntityManagerInterface $entityManager,
        MailerInterface $mailer,
        LoggerInterface $logger
    ): Response
    {
        $user = $this->getUser();
        if (!$user instanceof User) {
            $fallback = $userRepository->find(1);
            if ($fallback instanceof User) {
                $user = $fallback;
            } else {
                throw $this->createAccessDeniedException();
            }
        }

        if ($request->isMethod('POST') && $this->isCsrfTokenValid('buy_ticket_' . $evenement->getId(), $request->request->get('_token'))) {
            // Rediriger vers la route de paiement Stripe
            return $this->redirectToRoute('app_payment_checkout', [
                'id' => $evenement->getId(),
            ]);
        }

        $tickets = $ticketRepository->findBy(
            ['evenement' => $evenement, 'user' => $user],
            ['date_achat' => 'DESC']
        );

        // Fetch weather data if gallery has coordinates
        $weatherData = null;
        if ($evenement->getGalerie() && $evenement->getGalerie()->getLatitude() && $evenement->getGalerie()->getLongitude()) {
            $weatherData = $this->fetchWeatherData(
                $evenement->getGalerie()->getLatitude(),
                $evenement->getGalerie()->getLongitude(),
                $evenement->getDateDebut()
            );
        }

        return $this->render('Front Office/eventdetails/eventdetails.html.twig', [
            'evenement' => $evenement,
            'image' => $this->getImageDataUri($evenement->getImageCouverture()),
            'tickets' => $tickets,
            'weather' => $weatherData,
        ]);
    }

    #[Route('/ticket/{id}/qr', name: 'app_ticket_qr', methods: ['GET'])]
    public function ticketQr(Ticket $ticket, UserRepository $userRepository): Response
    {
        $user = $this->getUser();
        if (!$user instanceof User) {
            $fallback = $userRepository->find(1);
            if ($fallback instanceof User) {
                $user = $fallback;
            } else {
                throw $this->createAccessDeniedException();
            }
        }

        if ($ticket->getUser()?->getId() !== $user->getId()) {
            throw $this->createAccessDeniedException();
        }

        $payload = $this->extractTicketPayload($ticket);
        $qrCode = QrCode::create($payload)->setSize(240)->setMargin(10);
        $writer = new SvgWriter();
        $result = $writer->write($qrCode);

        return new Response($result->getString(), Response::HTTP_OK, [
            'Content-Type' => 'image/svg+xml',
        ]);
    }

    #[Route('/ticket/{id}/download', name: 'app_ticket_download', methods: ['GET'])]
    public function downloadTicket(
        Ticket $ticket,
        UserRepository $userRepository
    ): Response {
        $user = $this->getUser();
        if (!$user instanceof User) {
            $fallback = $userRepository->find(1);
            if ($fallback instanceof User) {
                $user = $fallback;
            } else {
                throw $this->createAccessDeniedException();
            }
        }

        if ($ticket->getUser()?->getId() !== $user->getId()) {
            throw $this->createAccessDeniedException();
        }

        $evenement = $ticket->getEvenement();
        if (!$evenement) {
            throw $this->createNotFoundException('Événement non trouvé');
        }

        // Générer le QR code
        $payload = $this->extractTicketPayload($ticket);
        $qrCode = QrCode::create($payload)->setSize(300)->setMargin(10);
        $qrCodePath = null;
        $qrCodeFormat = null;
        
        // Sauvegarder le QR code dans un fichier du projet
        $varDir = $this->getParameter('kernel.project_dir') . '/var/tmp';
        if (!is_dir($varDir)) {
            mkdir($varDir, 0777, true);
        }

        if (extension_loaded('gd')) {
            try {
                $writer = new PngWriter();
                $result = $writer->write($qrCode);
                $qrCodePath = $varDir . '/qr_' . $ticket->getId() . '_' . time() . '.png';
                file_put_contents($qrCodePath, $result->getString());
                $qrCodeFormat = 'PNG';
            } catch (\Throwable) {
                $qrCodePath = null;
                $qrCodeFormat = null;
            }
        }

        if ($qrCodePath === null) {
            try {
                $writer = new SvgWriter();
                $result = $writer->write($qrCode);
                $qrCodePath = $varDir . '/qr_' . $ticket->getId() . '_' . time() . '.svg';
                file_put_contents($qrCodePath, $result->getString());
                $qrCodeFormat = 'SVG';
            } catch (\Throwable) {
                $qrCodePath = null;
                $qrCodeFormat = null;
            }
        }

        // Préparer les données pour le template
        $twig = $this->container->get('twig');
        $templateData = [
            'ticket' => $ticket,
            'evenement' => $evenement,
            'user' => $ticket->getUser(),
            'image' => $this->getImageDataUri($evenement->getImageCouverture()),
            'logo' => $this->supportsPngRenderingInTcpdf() ? $this->getLogoDataUri() : null,
            'qrCodePath' => $qrCodePath,
        ];

        // Générer le PDF
        $html = $twig->render('pdf/ticket_pdf.html.twig', $templateData);
        
        $pdf = new \TCPDF('P', 'mm', 'A4', true, 'UTF-8', false);
        error_reporting(error_reporting() & ~E_WARNING);
        
        $pdf->SetMargins(0, 0, 0);
        $pdf->SetAutoPageBreak(false, 0);
        $pdf->AddPage();
        $pdf->writeHTML($html, true, false, true, false, '');
        
        // Ajouter le QR code directement
        if ($qrCodePath !== null && file_exists($qrCodePath)) {
            try {
                if ($qrCodeFormat === 'PNG') {
                    $pdf->Image($qrCodePath, 75, 220, 60, 60, 'PNG');
                } elseif ($qrCodeFormat === 'SVG' && method_exists($pdf, 'ImageSVG')) {
                    $svgContent = file_get_contents($qrCodePath);
                    if ($svgContent !== false) {
                        $pdf->ImageSVG('@' . $svgContent, 75, 220, 60, 60);
                    }
                }
            } catch (\Exception $e) {
                // Ignorer les erreurs d'image
            }
        }

        error_reporting(E_ALL);
        $pdfContent = $pdf->Output('', 'S');
        
        // Nettoyer le fichier temporaire
        if ($qrCodePath !== null && file_exists($qrCodePath)) {
            @unlink($qrCodePath);
        }

        // Retourner le fichier
        $response = new Response($pdfContent);
        $response->headers->set('Content-Type', 'application/pdf');
        $response->headers->set('Content-Disposition', 'attachment; filename="ticket-' . $ticket->getId() . '-' . time() . '.pdf"');

        return $response;
    }

    private function getLogoDataUri(): ?string
    {
        $logoPath = $this->getParameter('kernel.project_dir') . '/assets/assetsback/images/logo2.png';
        if (!file_exists($logoPath)) {
            return null;
        }
        
        $logoContent = file_get_contents($logoPath);
        if ($logoContent === false) {
            return null;
        }

        return 'data:image/png;base64,' . base64_encode($logoContent);
    }

    private function supportsPngRenderingInTcpdf(): bool
    {
        return extension_loaded('gd') || extension_loaded('imagick');
    }

    private function getImageDataUri(mixed $image): ?string
    {
        if ($image === null) {
            return null;
        }

        if (is_resource($image)) {
            $data = stream_get_contents($image);
            if ($data === false || $data === '') {
                return null;
            }
            return 'data:image/jpeg;base64,' . base64_encode($data);
        }

        if (is_string($image)) {
            if (str_starts_with($image, 'data:')) {
                return $image;
            }
            if (preg_match('#^https?://#i', $image)) {
                return $image;
            }
            if (str_starts_with($image, '/')) {
                return $image;
            }
            if (strpos($image, '/') !== false) {
                return '/' . ltrim($image, '/');
            }
            return '/uploads/' . ltrim($image, '/');
        }

        return null;
    }

    private function buildTicketPayload(Evenement $evenement, User $user): string
    {
        return json_encode([
            'evenement_id' => $evenement->getId(),
            'evenement' => $evenement->getTitre(),
            'user_id' => $user->getId(),
            'user' => trim($user->getNom() . ' ' . $user->getPrenom()),
            'issued_at' => (new \DateTime())->format('c'),
        ], JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES) ?: '';
    }

    private function extractTicketPayload(Ticket $ticket): string
    {
        $user = $ticket->getUser();
        $evenement = $ticket->getEvenement();

        return json_encode([
            'ticket_id' => $ticket->getId(),
            'user_id' => $user ? $user->getId() : null,
            'user_nom' => $user ? $user->getNom() : null,
            'user_prenom' => $user ? $user->getPrenom() : null,
            'evenement_id' => $evenement ? $evenement->getId() : null,
            'evenement' => $evenement ? $evenement->getTitre() : null,
            'issued_at' => (new \DateTime())->format('c'),
        ], JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
    }

    private function sendTicketEmail(
        MailerInterface $mailer,
        LoggerInterface $logger,
        User $user,
        Evenement $evenement,
        Ticket $ticket,
        string $payload
    ): void {
        $emailAddress = $user->getEmail();
        if ($emailAddress === null || $emailAddress === '') {
            $logger->warning('Ticket email not sent: user has no email', ['user_id' => $user->getId()]);
            return;
        }

        try {
            $qrCode = QrCode::create($payload)->setSize(240)->setMargin(10);
            $writer = new SvgWriter();
            $result = $writer->write($qrCode);
            $svg = $result->getString();
            $qrCodeDataUri = 'data:image/svg+xml;base64,' . base64_encode($svg);

            // Prepare logo attachment
            $logoPath = $this->getParameter('kernel.project_dir') . '/assets/assetsback/images/logo2.png';
            $logoContent = file_get_contents($logoPath);

            $email = (new TemplatedEmail())
                ->from(new Address('noreply@artium.tn', 'Artium'))
                ->to($emailAddress)
                ->subject('Votre ticket pour ' . ($evenement->getTitre() ?? 'evenement'))
                ->htmlTemplate('emails/ticket.html.twig')
                ->context([
                    'user' => $user,
                    'evenement' => $evenement,
                    'ticket' => $ticket,
                    'qrCodeDataUri' => $qrCodeDataUri,
                ])
                ->attach($logoContent, 'artium-logo', 'image/png')
                ->attach($svg, 'QR-Code-' . $ticket->getId() . '.svg', 'image/svg+xml');

            $mailer->send($email);
            $logger->info('Ticket email sent successfully', ['user_id' => $user->getId(), 'ticket_id' => $ticket->getId()]);
        } catch (\Exception $e) {
            $logger->error('Failed to send ticket email: ' . $e->getMessage(), [
                'user_id' => $user->getId(),
                'ticket_id' => $ticket->getId(),
            ]);
        }
    }

    /**
     * Fetch weather forecast from Open-Meteo API (free, no API key required)
     */
    private function fetchWeatherData(float $latitude, float $longitude, ?\DateTimeInterface $eventDate): ?array
    {
        if (!$eventDate) {
            return null;
        }

        try {
            $targetDateTime = $eventDate->format('Y-m-d\TH:00');
            
            // Open-Meteo API URL with hourly forecast
            $url = sprintf(
                'https://api.open-meteo.com/v1/forecast?latitude=%s&longitude=%s&hourly=temperature_2m,relative_humidity_2m,precipitation,weather_code,wind_speed_10m&timezone=auto&forecast_days=16',
                $latitude,
                $longitude
            );

            $context = stream_context_create([
                'http' => [
                    'timeout' => 10,
                    'ignore_errors' => true
                ]
            ]);

            $response = @file_get_contents($url, false, $context);
            if ($response === false) {
                return null;
            }

            $data = json_decode($response, true);
            if (!$data || !isset($data['hourly'])) {
                return null;
            }

            // Find the index for the event date and hour
            $index = array_search($targetDateTime, $data['hourly']['time'] ?? [], true);

            if ($index === false) {
                return null;
            }

            // Weather code descriptions (WMO Weather interpretation codes)
            $weatherDescriptions = [
                0 => 'Clear sky',
                1 => 'Mainly clear',
                2 => 'Partly cloudy',
                3 => 'Overcast',
                45 => 'Foggy',
                48 => 'Depositing rime fog',
                51 => 'Light drizzle',
                53 => 'Moderate drizzle',
                55 => 'Dense drizzle',
                61 => 'Slight rain',
                63 => 'Moderate rain',
                65 => 'Heavy rain',
                71 => 'Slight snow',
                73 => 'Moderate snow',
                75 => 'Heavy snow',
                77 => 'Snow grains',
                80 => 'Slight rain showers',
                81 => 'Moderate rain showers',
                82 => 'Violent rain showers',
                85 => 'Slight snow showers',
                86 => 'Heavy snow showers',
                95 => 'Thunderstorm',
                96 => 'Thunderstorm with slight hail',
                99 => 'Thunderstorm with heavy hail',
            ];

            $weatherCode = $data['hourly']['weather_code'][$index] ?? 0;

            return [
                'temperature' => $data['hourly']['temperature_2m'][$index] ?? null,
                'humidity' => $data['hourly']['relative_humidity_2m'][$index] ?? null,
                'precipitation' => $data['hourly']['precipitation'][$index] ?? null,
                'wind_speed' => $data['hourly']['wind_speed_10m'][$index] ?? null,
                'weather_code' => $weatherCode,
                'description' => $weatherDescriptions[$weatherCode] ?? 'Unknown',
            ];
        } catch (\Exception $e) {
            return null;
        }
    }
}
