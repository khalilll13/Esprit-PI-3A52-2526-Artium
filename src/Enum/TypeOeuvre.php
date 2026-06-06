<?php

namespace App\Enum;

enum TypeOeuvre: string
{
    case PEINTURE = 'Peinture';
    case SCULPTURE = 'Sculpture';
    case PHOTOGRAPHIE = 'Photographie';
    case MUSIQUE = 'Musique';
    case LIVRE = 'Livre';

    public function getColor(): string
    {
        return match($this) {
            self::PEINTURE => 'primary',
            self::SCULPTURE => 'success',
            self::PHOTOGRAPHIE => 'info',
            self::MUSIQUE => 'warning',
            self::LIVRE => 'dark',
        };
    }
}