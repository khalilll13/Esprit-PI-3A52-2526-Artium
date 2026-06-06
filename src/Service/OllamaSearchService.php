<?php

namespace App\Service;

use App\Entity\Evenement;
use Symfony\Component\HttpClient\HttpClient;
use Symfony\Contracts\HttpClient\Exception\ExceptionInterface;
use Symfony\Contracts\HttpClient\HttpClientInterface;

class OllamaSearchService
{
    private HttpClientInterface $httpClient;
    private string $baseUrl;
    private string $model;

    /** @var array<string, array> */
    private array $embeddingCache = [];

    public function __construct(?HttpClientInterface $httpClient = null, ?string $baseUrl = null, ?string $model = null)
    {
        $this->httpClient = $httpClient ?? HttpClient::create();
        $this->baseUrl = rtrim($baseUrl ?: (getenv('OLLAMA_BASE_URL') ?: 'http://localhost:11434'), '/');
        $this->model = $model ?: (getenv('OLLAMA_EMBED_MODEL') ?: 'nomic-embed-text');
    }

    /**
     * @param array<Evenement> $evenements
     * @return array<array{evenement: Evenement, score: float, similarity: float}>
     */
    public function searchAndRankEvents(string $query, array $evenements): array
    {
        if (trim($query) === '' || empty($evenements)) {
            return [];
        }

        $queryEmbedding = $this->getEmbedding($query);
        if ($queryEmbedding === null) {
            return [];
        }

        $ranked = [];
        foreach ($evenements as $evenement) {
            $eventText = $this->buildEventText($evenement);
            if ($eventText === '') {
                continue;
            }

            $eventEmbedding = $this->getEmbedding($eventText);
            if ($eventEmbedding === null) {
                continue;
            }

            $similarity = $this->cosineSimilarity($queryEmbedding, $eventEmbedding);
            $score = max(0.0, min(10.0, $similarity * 10.0));

            $ranked[] = [
                'evenement' => $evenement,
                'score' => round($score, 1),
                'similarity' => $similarity,
            ];
        }

        usort($ranked, static function (array $a, array $b): int {
            return $b['score'] <=> $a['score'];
        });

        return $ranked;
    }

    private function getEmbedding(string $text): ?array
    {
        $key = md5($text);
        if (isset($this->embeddingCache[$key])) {
            return $this->embeddingCache[$key];
        }

        try {
            $response = $this->httpClient->request('POST', $this->baseUrl . '/api/embeddings', [
                'json' => [
                    'model' => $this->model,
                    'prompt' => $text,
                ],
                'timeout' => 30,
            ]);

            if ($response->getStatusCode() !== 200) {
                return null;
            }

            $data = $response->toArray(false);
            if (!isset($data['embedding']) || !is_array($data['embedding'])) {
                return null;
            }

            $this->embeddingCache[$key] = $data['embedding'];
            return $data['embedding'];
        } catch (ExceptionInterface $exception) {
            return null;
        }
    }

    private function cosineSimilarity(array $a, array $b): float
    {
        $length = min(count($a), count($b));
        if ($length === 0) {
            return 0.0;
        }

        $dot = 0.0;
        $normA = 0.0;
        $normB = 0.0;

        for ($i = 0; $i < $length; $i++) {
            $dot += $a[$i] * $b[$i];
            $normA += $a[$i] ** 2;
            $normB += $b[$i] ** 2;
        }

        if ($normA == 0.0 || $normB == 0.0) {
            return 0.0;
        }

        return $dot / (sqrt($normA) * sqrt($normB));
    }

    private function buildEventText(Evenement $evenement): string
    {
        $parts = [];

        $title = trim((string) $evenement->getTitre());
        if ($title !== '') {
            $parts[] = $title;
            $parts[] = $title;
            $parts[] = $title;
        }

        $description = trim((string) $evenement->getDescription());
        if ($description !== '') {
            $parts[] = $description;
        }

        $type = $evenement->getType()?->value;
        if ($type) {
            $parts[] = $type;
            $parts[] = $type;
        }

        return trim(implode(' ', $parts));
    }
}
