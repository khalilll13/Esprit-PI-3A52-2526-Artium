<?php
namespace App\Service;

use Symfony\Contracts\HttpClient\HttpClientInterface;

class FaceRecognitionService
{
    private HttpClientInterface $client;

    public function __construct(HttpClientInterface $client)
    {
        $this->client = $client;
    }

    /**
     * Compare deux images en envoyant des fichiers au service Python
     * @param string $image1Path Chemin du premier fichier image
     * @param string $image2Path Chemin du second fichier image
     * @return bool TRUE si reconnu, FALSE sinon
     */
    public function compare(string $image1Path, string $image2Path): bool
    {
        $response = $this->client->request('POST', 'http://127.0.0.1:8002/compare', [
            'headers' => [
                'Accept' => 'application/json',
            ],
            'body' => [
                'file1' => fopen($image1Path, 'rb'),
                'file2' => fopen($image2Path, 'rb'),
            ],
        ]);

        // Gestion d'erreur HTTP ou JSON
        if ($response->getStatusCode() !== 200) {
            return false;
        }
        try {
            $data = $response->toArray(false);
        } catch (\Throwable $e) {
            return false;
        }
        // On accepte 'result' ou 'is_same' comme indicateur de reconnaissance
        if (isset($data['result'])) {
            return (bool)$data['result'];
        } elseif (isset($data['is_same'])) {
            return (bool)$data['is_same'];
        }
        return false;
    }
}