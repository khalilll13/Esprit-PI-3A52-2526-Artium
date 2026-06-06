<?php

namespace App\Enum;

enum CentreInteret: string
{
    case PEINTURE = 'Peinture';
    case SCULPTURE = 'Sculpture';
    case PHOTOGRAPHIE = 'Photographie';
    case MUSIQUE = 'Musique';
    case LECTURE = 'Lecture';
}