<?php

namespace App\Controller;

use App\Service\EmbeddingService;
use App\Service\ImageEmbeddingService;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\Routing\Annotation\Route;

class TestEmbeddingController extends AbstractController
{
    #[Route('/test-env', name: 'test_env')]
    public function testEnv(): JsonResponse
    {
        // get the env variable
        $key = $_ENV['COHERE_API_KEY'] ?? 'not found';

        return new JsonResponse([
            'cohere_api_key' => $key
        ]);
    }

    #[Route('/test-env-service', name: 'test_env_service')]
    public function testEnvService(EmbeddingService $embeddingService): JsonResponse
    {
        return new JsonResponse([
            'api_key_in_service' => $embeddingService->getApiKey()
        ]);
    }

    #[Route('/test-embedding', name: 'test_embedding')]
    public function testEmbedding(EmbeddingService $embeddingService): JsonResponse
    {
        $text = "Jeune fille au jardin. Une peinture douce représentant une jeune femme entourée de fleurs.";

        // Call the API via your service
        $embedding = $embeddingService->embed($text);

        return new JsonResponse([
            'vector_size' => count($embedding),
            'preview' => array_slice($embedding, 0, 5), // show first 5 values for check
        ]);
    }
    

}