<?php
namespace App\EventListener;

use App\Entity\UserConnection;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Component\Security\Http\Event\InteractiveLoginEvent;
use Symfony\Component\HttpFoundation\RequestStack;

class UserConnectionListener
{
    private $em;
    private $requestStack;

    public function __construct(EntityManagerInterface $em, RequestStack $requestStack)
    {
        $this->em = $em;
        $this->requestStack = $requestStack;
    }

    public function onSecurityInteractiveLogin(InteractiveLoginEvent $event)
    {
        $user = $event->getAuthenticationToken()->getUser();
        if (!is_object($user)) {
            return;
        }
        $request = $this->requestStack->getCurrentRequest();
        $connection = new UserConnection();
        $connection->setUser($user);
        $connection->setConnectedAt(new \DateTime());
        $connection->setIpAddress($request ? $request->getClientIp() : '');
        $connection->setUserAgent($request ? $request->headers->get('User-Agent') : '');
        $this->em->persist($connection);
        $this->em->flush();
    }
}
