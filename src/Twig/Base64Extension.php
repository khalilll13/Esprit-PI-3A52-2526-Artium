<?php

namespace App\Twig;

use Twig\Extension\AbstractExtension;
use Twig\TwigFilter;

class Base64Extension extends AbstractExtension
{
    public function getFilters(): array
    {
        return [
            new TwigFilter('base64_encode', [$this, 'base64Encode']),
        ];
    }

    public function base64Encode($data): string
    {
        if (is_resource($data)) {
            $data = stream_get_contents($data);
        }
        
        return base64_encode($data);
    }
}
