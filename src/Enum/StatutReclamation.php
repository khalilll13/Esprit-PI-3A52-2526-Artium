<?php

namespace App\Enum;

enum StatutReclamation: string
{
    case TRAITEE = 'Traitée';
    case NON_TRAITEE = 'Non traitée';
    case EN_COURS = 'En cours';
    case ARCHIVE = 'Archivée';
}