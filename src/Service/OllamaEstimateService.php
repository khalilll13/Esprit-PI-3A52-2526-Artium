<?php

namespace App\Service;

use Symfony\Component\HttpClient\HttpClient;
use Symfony\Contracts\HttpClient\Exception\ExceptionInterface;
use Symfony\Contracts\HttpClient\HttpClientInterface;

class OllamaEstimateService
{
    private HttpClientInterface $httpClient;
    private string $baseUrl;
    private string $model;

    public function __construct(?HttpClientInterface $httpClient = null, ?string $baseUrl = null, ?string $model = null)
    {
        $this->httpClient = $httpClient ?? HttpClient::create();
        $this->baseUrl = rtrim($baseUrl ?: (getenv('OLLAMA_BASE_URL') ?: 'http://localhost:11434'), '/');
        $this->model = $model ?: (getenv('OLLAMA_CHAT_MODEL') ?: 'llama3.2:3b');
    }

    /**
     * @param array<string, mixed> $payload
     * @return array{estimate:int, confidence:string}|null
     */
    public function estimateTickets(array $payload): ?array
    {
        $prompt = $this->buildPrompt($payload);

        try {
            $response = $this->httpClient->request('POST', $this->baseUrl . '/api/generate', [
                'json' => [
                    'model' => $this->model,
                    'prompt' => $prompt,
                    'stream' => false,
                    'keep_alive' => '5m',
                ],
                'timeout' => 120,
            ]);

            if ($response->getStatusCode() !== 200) {
                return null;
            }

            $data = $response->toArray(false);
            if (!isset($data['response']) || !is_string($data['response'])) {
                return null;
            }

            $json = $this->extractJson($data['response']);
            if ($json === null) {
                return null;
            }

            $decoded = json_decode($json, true);
            if (!is_array($decoded)) {
                return null;
            }

            $estimate = isset($decoded['estimate']) ? (int) $decoded['estimate'] : null;
            $confidence = isset($decoded['confidence']) ? (string) $decoded['confidence'] : 'medium';

            if ($estimate === null) {
                return null;
            }

            $confidence = in_array($confidence, ['low', 'medium', 'high'], true) ? $confidence : 'medium';

            return [
                'estimate' => max(0, $estimate),
                'confidence' => $confidence,
            ];
        } catch (ExceptionInterface $exception) {
            return null;
        }
    }

    /**
     * @param array<string, mixed> $payload
     */
    private function buildPrompt(array $payload): string
    {
        $title = trim((string) ($payload['titre'] ?? ''));
        $description = trim((string) ($payload['description'] ?? ''));
        $type = trim((string) ($payload['type'] ?? ''));
        $capacity = (int) ($payload['capacite_max'] ?? 0);
        $price = (float) ($payload['prix_ticket'] ?? 0);
        $dateDebut = trim((string) ($payload['date_debut'] ?? ''));
        $dateFin = trim((string) ($payload['date_fin'] ?? ''));
        $galerie = trim((string) ($payload['galerie'] ?? ''));

        return "You estimate expected tickets sold for an event. "
            . "Return ONLY valid JSON like {\"estimate\": 120, \"confidence\": \"medium\"}. "
            . "Estimate should be between 0 and capacity if capacity is provided. "
            . "If information is missing, assume a conservative estimate.\n\n"
            . "Event title: {$title}\n"
            . "Type: {$type}\n"
            . "Description: {$description}\n"
            . "Capacity: {$capacity}\n"
            . "Price: {$price}\n"
            . "Start date: {$dateDebut}\n"
            . "End date: {$dateFin}\n"
            . "Gallery: {$galerie}\n";
    }

    private function extractJson(string $text): ?string
    {
        if (preg_match('/\{[\s\S]*\}/', $text, $matches)) {
            return $matches[0];
        }

        return null;
    }
}
