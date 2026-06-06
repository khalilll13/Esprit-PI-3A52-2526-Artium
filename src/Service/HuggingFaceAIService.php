<?php

namespace App\Service;

use Symfony\Contracts\HttpClient\HttpClientInterface;
use Psr\Log\LoggerInterface;
use Symfony\Contracts\HttpClient\Exception\DecodingExceptionInterface;
use Symfony\Contracts\HttpClient\Exception\TransportExceptionInterface;

class HuggingFaceAIService
{
    // Nouvelle API Chat Completions de Hugging Face
    private const API_URL = 'https://router.huggingface.co/v1/chat/completions';
    private const REQUEST_TIMEOUT = 30;
    private const MAX_RETRIES = 2;
    private const BASE_RETRY_DELAY_MS = 250;
    
    // Modèles à essayer en ordre de préférence
    private const MODELS = [
        'mistralai/Mistral-7B-Instruct-v0.2',
        'microsoft/phi-2',
        'google/flan-t5-large',
        'meta-llama/Llama-2-7b-chat-hf',
    ];
    
    public function __construct(
        private HttpClientInterface $httpClient,
        private LoggerInterface $logger,
        private string $huggingFaceApiKey
    ) {
    }

    /**
     * Génère une réponse IA pour une réclamation
     */
    public function generateResponse(string $reclamationText, string $type, string $userName, int $reclamationId = 0): ?string
    {
        // Essayer chaque modèle jusqu'à ce qu'un fonctionne
        foreach (self::MODELS as $model) {
            $this->logger->info('Tentative Hugging Face Chat API', ['model' => $model]);

            $content = $this->requestModelContentWithRetry($model, $reclamationText, $type, $userName, $reclamationId);
            if ($content !== null) {
                $this->logger->info('Hugging Face Chat API - Succès', [
                    'model' => $model,
                    'length' => strlen($content)
                ]);

                return $this->cleanResponse($content);
            }
        }
        
        // Si aucun modèle ne fonctionne, retourner null (le fallback prendra le relais)
        $this->logger->error('Tous les modèles Hugging Face ont échoué');
        return null;
    }

    private function requestModelContentWithRetry(string $model, string $reclamationText, string $type, string $userName, int $reclamationId): ?string
    {
        $payload = [
            'model' => $model,
            'messages' => [
                [
                    'role' => 'system',
                    'content' => 'Tu es un assistant administratif professionnel pour ARTIUM, une plateforme d\'art. Tu réponds aux réclamations de manière empathique et professionnelle en français.'
                ],
                [
                    'role' => 'user',
                    'content' => $this->buildUserPrompt($reclamationText, $type, $userName, $reclamationId)
                ]
            ],
            'max_tokens' => 300,
            'temperature' => 0.95,
            'top_p' => 0.95,
        ];

        for ($attempt = 1; $attempt <= self::MAX_RETRIES + 1; $attempt++) {
            try {
                $response = $this->httpClient->request('POST', self::API_URL, [
                    'headers' => [
                        'Authorization' => 'Bearer ' . $this->huggingFaceApiKey,
                        'Content-Type' => 'application/json',
                    ],
                    'json' => $payload,
                    'timeout' => self::REQUEST_TIMEOUT,
                ]);

                $statusCode = $response->getStatusCode();
                if ($statusCode !== 200) {
                    if ($this->isRetryableStatusCode($statusCode) && $attempt <= self::MAX_RETRIES) {
                        $this->logger->notice('Hugging Face transient HTTP status, retrying', [
                            'model' => $model,
                            'status' => $statusCode,
                            'attempt' => $attempt,
                            'max_attempts' => self::MAX_RETRIES + 1,
                        ]);
                        $this->sleepBeforeRetry($attempt);
                        continue;
                    }

                    $this->logger->warning('Hugging Face model failed', [
                        'model' => $model,
                        'status' => $statusCode,
                        'attempt' => $attempt,
                    ]);
                    return null;
                }

                $data = $response->toArray();
                if (isset($data['choices'][0]['message']['content'])) {
                    return trim((string) $data['choices'][0]['message']['content']);
                }

                $this->logger->warning('Hugging Face response format invalid', [
                    'model' => $model,
                    'attempt' => $attempt,
                ]);
                return null;
            } catch (TransportExceptionInterface $e) {
                if ($attempt <= self::MAX_RETRIES && $this->isRetryableTransportError($e->getMessage())) {
                    $this->logger->notice('Hugging Face transient transport error, retrying', [
                        'model' => $model,
                        'attempt' => $attempt,
                        'max_attempts' => self::MAX_RETRIES + 1,
                        'error' => $e->getMessage(),
                    ]);
                    $this->sleepBeforeRetry($attempt);
                    continue;
                }

                $this->logger->warning('Hugging Face model failed', [
                    'model' => $model,
                    'attempt' => $attempt,
                    'error' => $e->getMessage(),
                ]);
                return null;
            } catch (DecodingExceptionInterface|\Throwable $e) {
                $this->logger->warning('Hugging Face model failed', [
                    'model' => $model,
                    'attempt' => $attempt,
                    'error' => $e->getMessage(),
                ]);
                return null;
            }
        }

        return null;
    }

    private function isRetryableStatusCode(int $statusCode): bool
    {
        return in_array($statusCode, [408, 425, 429, 500, 502, 503, 504], true);
    }

    private function isRetryableTransportError(string $message): bool
    {
        $normalized = mb_strtolower($message);

        return str_contains($normalized, 'connection was reset')
            || str_contains($normalized, 'timed out')
            || str_contains($normalized, 'timeout')
            || str_contains($normalized, 'temporarily unavailable')
            || str_contains($normalized, 'connection refused')
            || str_contains($normalized, 'failed to connect')
            || str_contains($normalized, 'could not resolve host');
    }

    private function sleepBeforeRetry(int $attempt): void
    {
        $delayMs = self::BASE_RETRY_DELAY_MS * (2 ** ($attempt - 1));
        usleep($delayMs * 1000);
    }

    private function buildUserPrompt(string $text, string $type, string $userName, int $reclamationId = 0): string
    {
        // Créer une variation basée sur l'ID unique de la réclamation
        $tonIndex = abs($reclamationId) % 3;
        $tonVariable = match($tonIndex) {
            0 => 'avec empathie et rapidité',
            1 => 'avec professionnalisme et bienveillance',
            2 => 'avec attention particulière et assurance',
        };
        
        return <<<PROMPT
Réclamation de type "$type" de $userName (ID: $reclamationId):
"$text"

Rédige une réponse UNIQUE et personnalisée $tonVariable en français. La réponse doit:
- Commencer par "Bonjour $userName,"
- Reconnaître précisément le problème mentionné
- Proposer une solution concrète et adaptée
- Varier la structure et le langage (ne pas utiliser de template générique)
- Être concise (2-3 paragraphes maximum)
- Se terminer par "Cordialement, L'équipe ARTIUM"
IMPORTANT: Chaque réponse DOIT être unique, même pour des réclamations du même type.
PROMPT;
    }

    private function cleanResponse(string $response): string
    {
        $response = trim($response);
        
        // Supprimer les guillemets si présents
        if (str_starts_with($response, '"') && str_ends_with($response, '"')) {
            $response = substr($response, 1, -1);
        }
        
        // Limiter la longueur
        if (strlen($response) > 1000) {
            $response = substr($response, 0, 997) . '...';
        }
        
        return $response;
    }
}
