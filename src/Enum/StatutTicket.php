<?php

namespace App\Enum;

enum StatutTicket: string
{
    case PAYE = 'Payé';
    case ANNUK = 'Non payé';
    case EN_COURS = 'En cours';
}