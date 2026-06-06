<?php

namespace App\Security;

use App\Enum\Role;
use Symfony\Component\HttpFoundation\RedirectResponse;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\Routing\Generator\UrlGeneratorInterface;
use Symfony\Component\Security\Core\Authentication\Token\TokenInterface;
use Symfony\Component\Security\Http\Authentication\AuthenticationSuccessHandlerInterface;

final class LoginSuccessHandler implements AuthenticationSuccessHandlerInterface
{
    public function __construct(private UrlGeneratorInterface $urlGenerator)
    {
    }

    public function onAuthenticationSuccess(Request $request, TokenInterface $token): RedirectResponse
    {
        $user = $token->getUser();

        if ($user instanceof \App\Entity\User) {
            if ($user->getRole() === Role::ADMIN) {
                return new RedirectResponse($this->urlGenerator->generate('app_admin'));
            }

            if ($user->getRole() === Role::AMATEUR) {
                return new RedirectResponse($this->urlGenerator->generate('app_feed'));
            }

            if ($user->getRole() === Role::ARTISTE) {
                return new RedirectResponse($this->urlGenerator->generate('app_oeuvre_front'));
            }
        }

        return new RedirectResponse($this->urlGenerator->generate('app_signin'));
    }
}
