<?php

namespace App\Enum;

enum Role: string
{
    case ADMIN = 'Admin';
    case ARTISTE = 'Artiste';
    case AMATEUR = 'Amateur';
}