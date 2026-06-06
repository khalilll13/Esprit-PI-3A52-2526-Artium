<?php

namespace App\Enum;

enum StatutEvenement: string
{
    case A_VENIR = 'À venir';
    case TERMINE = 'Terminé';
    case ANNULE = 'Annulé';
}