<?php

namespace App\Service;

use Symfony\Contracts\HttpClient\HttpClientInterface;

class EmbeddingService 
{
    private HttpClientInterface $client;
    private string $apiKey;

    public function __construct(
        HttpClientInterface $client,
        string $apiKey
    ) {
        $this->client = $client;
        $this->apiKey = $apiKey;
    }

    /**
     * Generate an embedding for a given text
     */
    public function embed(string $text): array
    {
        $response = $this->client->request(
            'POST',
            'https://api.cohere.ai/v1/embed',
            [
                'headers' => [
                    'Authorization' => 'Bearer ' . $this->apiKey,
                    'Content-Type' => 'application/json',
                ],
                'json' => [
                    'model' => 'embed-v4.0',
                    'texts' => [$text],
                ],
            ]
        );

        $data = $response->toArray();

        return $data['embeddings'][0]; // vector (array of floats)
    }
    public function getApiKey(): string
    {
        return $this->apiKey;
    }
}