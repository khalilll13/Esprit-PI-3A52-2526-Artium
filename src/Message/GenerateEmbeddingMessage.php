<?php
namespace App\Message;

class GenerateEmbeddingMessage
{
    private int $oeuvreId;

    public function __construct(int $oeuvreId)
    {
        $this->oeuvreId = $oeuvreId;
    }

    public function getOeuvreId(): int
    {
        return $this->oeuvreId;
    }
}