<?php
namespace App\Security;

use Symfony\Component\Routing\Generator\UrlGeneratorInterface;
use Symfony\Component\Security\Http\Authenticator\AuthenticatorInterface;
use Symfony\Component\Security\Http\Authenticator\Passport\Passport;
use Symfony\Component\Security\Http\Authenticator\Passport\SelfValidatingPassport;
use Symfony\Component\Security\Http\Authenticator\Passport\Badge\UserBadge;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\Security\Core\User\UserInterface;
use Symfony\Component\Security\Core\Authentication\Token\TokenInterface;
use Symfony\Component\Security\Http\Authenticator\AuthenticatorManager;
use Symfony\Component\Security\Http\Authenticator\AbstractAuthenticator;

class FaceAuthenticator extends AbstractAuthenticator
{
    private UrlGeneratorInterface $urlGenerator;

    public function __construct(UrlGeneratorInterface $urlGenerator)
    {
        $this->urlGenerator = $urlGenerator;
    }
    public function supports(Request $request): bool
    {
        // Supporte uniquement la route de connexion faciale
        return $request->getPathInfo() === '/face-signin' && $request->isMethod('POST');
    }

    public function authenticate(Request $request): Passport
    {
        $email = $request->request->get('email');
        return new SelfValidatingPassport(new UserBadge($email));
    }


    public function onAuthenticationSuccess(Request $request, TokenInterface $token, string $firewallName): ?\Symfony\Component\HttpFoundation\Response
    {
        $user = $token->getUser();
        if ($user instanceof \App\Entity\User) {
            $roles = $user->getRoles();
            if (in_array('ROLE_ADMIN', $roles, true)) {
                return new \Symfony\Component\HttpFoundation\RedirectResponse($this->urlGenerator->generate('app_admin'));
            }
            if (in_array('ROLE_ARTISTE', $roles, true)) {
                return new \Symfony\Component\HttpFoundation\RedirectResponse($this->urlGenerator->generate('app_oeuvre_front'));
            }
            if (in_array('ROLE_AMATEUR', $roles, true)) {
                return new \Symfony\Component\HttpFoundation\RedirectResponse($this->urlGenerator->generate('app_feed'));
            }
        }
        return new \Symfony\Component\HttpFoundation\RedirectResponse($this->urlGenerator->generate('app_signin'));
    }

    public function onAuthenticationFailure(Request $request, \Symfony\Component\Security\Core\Exception\AuthenticationException $exception): ?\Symfony\Component\HttpFoundation\Response
    {
        return new \Symfony\Component\HttpFoundation\Response('Erreur de connexion faciale', 401);
    }
}
