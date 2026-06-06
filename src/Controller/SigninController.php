<?php

namespace App\Controller;

use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Attribute\Route;
use Symfony\Component\Security\Http\Authentication\AuthenticationUtils;
use App\Service\FaceRecognitionService;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\Security\Http\Authentication\UserAuthenticatorInterface;
use Symfony\Component\Security\Http\Authenticator\Passport\SelfValidatingPassport;
use Symfony\Component\Security\Http\Authenticator\Passport\Badge\UserBadge;
use App\Entity\UserConnection;
use Doctrine\ORM\EntityManagerInterface;

final class SigninController extends AbstractController
{
    #[Route('/signin', name: 'app_signin')]
    public function index(AuthenticationUtils $authenticationUtils): Response
    {
        // Si déjà connecté, rediriger
        if ($this->getUser()) {
            return $this->redirectToRoute('app_admin');
        }

        // Récupérer l'erreur de connexion
        $error = $authenticationUtils->getLastAuthenticationError();
        
        // Dernier email saisi
        $lastUsername = $authenticationUtils->getLastUsername();

        return $this->render('signin/signin.html.twig', [
            'last_username' => $lastUsername,
            'error' => $error,
        ]);
    }

    #[Route('/logout', name: 'app_logout')]
    public function logout(): void
    {
        
    }

    #[Route('/face-signin', name: 'app_face_signin', methods: ['POST'])]
    public function faceSignin(
        Request $request,
        FaceRecognitionService $faceService,
        UserAuthenticatorInterface $userAuthenticator,
        \App\Security\FaceAuthenticator $faceAuthenticator,
        \Doctrine\ORM\EntityManagerInterface $em
    ): Response {
        $uploadedImage = $request->files->get('photo');
        $email = $request->request->get('email');
        if (!$uploadedImage || !$email) {
            return new Response("Utilisateur ou photo manquant", 400);
        }
        // Récupérer l'utilisateur par email
        $user = $em->getRepository(\App\Entity\User::class)->findOneBy(['email' => $email]);
        if (!$user) {
            return new Response("Utilisateur non trouvé", 404);
        }
        $photoReferenceFilename = $user->getPhotoReferencePath();
        if (!$photoReferenceFilename) {
            return new Response("Aucune photo de référence", 400);
        }
        $photoDir = $this->getParameter('user_photos_directory');
        $referenceFile = $photoDir . '/' . $photoReferenceFilename;
        if (!file_exists($referenceFile)) {
            return new Response("La photo de référence n'existe pas", 500);
        }
        $result = $faceService->compare(
            $uploadedImage->getPathname(),
            $referenceFile
        );
        if ($result) {
            // Enregistrement de la connexion
            $connection = new UserConnection();
            $connection->setUser($user);
            $connection->setConnectedAt(new \DateTime());
            $connection->setIpAddress($request->getClientIp());
            $connection->setUserAgent($request->headers->get('User-Agent'));
            $em->persist($connection);
            $em->flush();
            // Connexion automatique
            return $userAuthenticator->authenticateUser($user, $faceAuthenticator, $request);
        }
        return new Response("Face Not Recognized", 401);
    }

    #[Route('/admin/user/{id}/connections', name: 'admin_user_connections')]
    public function userConnections(int $id, EntityManagerInterface $em): Response
    {
        $user = $em->getRepository(\App\Entity\User::class)->find($id);
        if (!$user) {
            throw $this->createNotFoundException('Utilisateur non trouvé');
        }
        $connections = $em->getRepository(\App\Entity\UserConnection::class)
            ->findBy(['user' => $user], ['connectedAt' => 'DESC']);
        return $this->render('admin/user_connection_history.html.twig', [
            'connections' => $connections,
            'user' => $user,
        ]);
    }

    #[Route('/admin/user/{id}/connections/popup', name: 'admin_user_connections_popup')]
    public function userConnectionsPopup(int $id, EntityManagerInterface $em, \Symfony\Component\HttpFoundation\Request $request): Response
    {
        $user = $em->getRepository(\App\Entity\User::class)->find($id);
        if (!$user) {
            throw $this->createNotFoundException('Utilisateur non trouvé');
        }
        $repo = $em->getRepository(\App\Entity\UserConnection::class);
        // Supprimer toutes les connexions de tous les utilisateurs qui ne sont pas d'aujourd'hui
        $start = (new \DateTimeImmutable('today'))->setTime(0,0,0);
        $end = (new \DateTimeImmutable('today'))->setTime(23,59,59);
        $qbDelete = $repo->createQueryBuilder('c')
            ->delete()
            ->where('c.connectedAt < :start OR c.connectedAt > :end')
            ->setParameter('start', $start)
            ->setParameter('end', $end);
        $qbDelete->getQuery()->execute();
        // Récupérer uniquement les connexions du jour
        $qb = $repo->createQueryBuilder('c')
            ->where('c.user = :user')
            ->andWhere('c.connectedAt BETWEEN :start AND :end')
            ->setParameter('user', $user)
            ->setParameter('start', $start)
            ->setParameter('end', $end)
            ->orderBy('c.connectedAt', 'DESC');
        $connections = $qb->getQuery()->getResult();

        // Parse User-Agent for each connection
        $uaParser = new \App\Service\UserAgentParser();
        $parsedConnections = [];
        foreach ($connections as $conn) {
            $parsed = $uaParser->parse($conn->getUserAgent());
            $parsedConnections[] = [
                'connectedAt' => $conn->getConnectedAt(),
                'ipAddress' => $conn->getIpAddress(),
                'browser' => $parsed['browser'],
                'os' => $parsed['os'],
                'device' => $parsed['device'],
            ];
        }

        return $this->render('admin/_user_connection_popup.html.twig', [
            'connections' => $parsedConnections,
            'user' => $user,
        ]);
    }
}