<?php

namespace App\Enum;

enum TypeEvenement: string
{
    case EXPOSITION = 'Exposition';
    case CONCERT = 'Concert';
    case SPECTACLE = 'Spectacle';
    case CONFERENCE = 'Conférence';
}