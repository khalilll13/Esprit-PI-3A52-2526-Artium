<?php

namespace App\Controller;


use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Attribute\Route;
use Doctrine\ORM\EntityManagerInterface;
use App\Repository\UserRepository;
use Symfony\Component\Uid\Uuid;
use Symfony\Component\Mailer\MailerInterface;
use Symfony\Bridge\Twig\Mime\TemplatedEmail;
use App\Security\Password\Pbkdf2Sha256PasswordHasher;
use Symfony\Component\Routing\Generator\UrlGeneratorInterface;

final class ForgotPWDController extends AbstractController
{
    #[Route('/forgotpwd', name: 'app_forgot_pwd', methods: ['GET', 'POST'])]
    #[Route('/forgotpwd/{token}', name: 'app_forgot_pwd_reset', methods: ['GET', 'POST'])]
    public function index(Request $request, UserRepository $userRepository, EntityManagerInterface $em, MailerInterface $mailer, Pbkdf2Sha256PasswordHasher $passwordHasher): Response
    {
        $error = null;
        $success = null;
        $email = $request->request->get('email');
        $token = $request->attributes->get('token') ?? $request->query->get('token');
        $showResetForm = false;
        $user = null;

        // Si accès via lien avec token, afficher le formulaire de nouveau mot de passe
        if ($token) {
            $user = $userRepository->findOneBy(['resetToken' => $token]);
            if (!$user || !$user->getResetTokenExpires() || $user->getResetTokenExpires() < new \DateTime()) {
                $error = "Lien de réinitialisation invalide ou expiré.";
            } else {
                $showResetForm = true;
                // Traitement du POST pour le nouveau mot de passe
                if ($request->isMethod('POST') && $request->request->get('new_password')) {
                    $newPassword = $request->request->get('new_password');
                    if (strlen($newPassword) < 6) {
                        $error = "Le mot de passe doit contenir au moins 6 caractères.";
                    } else {
                        // Hashage du mot de passe
                        $hashedPassword = $passwordHasher->hash($newPassword);
                        $user->setMdp($hashedPassword);
                        $user->setResetToken(null);
                        $user->setResetTokenExpires(null);
                        $em->persist($user);
                        $em->flush();
                        $success = "Votre mot de passe a été réinitialisé avec succès.";
                        $showResetForm = false;
                    }
                }
            }
        } else {
            // Demande de réinitialisation classique
            if ($request->isMethod('POST') && $email) {
                $user = $userRepository->findOneBy(['email' => $email]);
                if (!$user) {
                    $error = "Aucun utilisateur trouvé avec cet e-mail.";
                } else {
                    $token = Uuid::v4()->toRfc4122();
                    $user->setResetToken($token);
                    $user->setResetTokenExpires((new \DateTime('+1 hour')));
                    $em->persist($user);
                    $em->flush();

                    // Générer une URL absolue
                    $resetUrl = $this->generateUrl(
                        'app_forgot_pwd_reset',
                        ['token' => $token],
                        UrlGeneratorInterface::ABSOLUTE_URL
                    );

                    // Préparer le logo en pièce jointe inline (cid)
                    $logoPath = $this->getParameter('kernel.project_dir') . '/public/Assets/logo2.png';
                    $logoContent = null;
                    $logoCid = null;
                    if (file_exists($logoPath)) {
                        $logoContent = file_get_contents($logoPath);
                        $logoCid = 'logo2';
                    }

                    $emailMessage = (new TemplatedEmail())
                        ->from('no-reply@votreapp.com')
                        ->to($user->getEmail())
                        ->subject('Réinitialisation de votre mot de passe')
                        ->htmlTemplate('forgot_pwd/reset_email.html.twig')
                        ->context([
                            'user' => $user,
                            'resetUrl' => $resetUrl,
                            'logoCid' => $logoCid
                        ]);
                    if ($logoContent && $logoCid) {
                        $emailMessage->embed($logoContent, $logoCid, 'image/png');
                    }
                    $mailer->send($emailMessage);

                    $success = "Un email de réinitialisation a été envoyé si l'adresse existe.";
                }
            } elseif ($request->isMethod('POST')) {
                $error = "Veuillez saisir votre adresse e-mail.";
            }
        }

        return $this->render('forgot_pwd/forgotpwd.html.twig', [
            'email' => $email,
            'error' => $error,
            'success' => $success,
            'showResetForm' => $showResetForm,
            'token' => $token,
        ]);
    }
}
