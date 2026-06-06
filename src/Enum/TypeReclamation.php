<?php

namespace App\Enum;

enum TypeReclamation: string
{
    case PAIEMENT = 'Paiement';
    case OEUVRE = 'Oeuvre';
    case EVENEMENT = 'Evènement';
    case COMPTE = 'Compte';
}