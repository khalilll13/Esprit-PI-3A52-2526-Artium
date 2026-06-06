<?php

namespace App\Service;

use App\Entity\User;
use App\Repository\OeuvreRepository;
use App\Enum\CentreInteret;
use App\Enum\TypeOeuvre;

class RecommendationServiceoeuvre
{
    private OeuvreRepository $oeuvreRepository;

    public function __construct(OeuvreRepository $oeuvreRepository)
    {
        $this->oeuvreRepository = $oeuvreRepository;
    }

    /**
     * Temporary implementation:
     * just returns user-interacted œuvres
     */
    public function getInteractedOeuvresForUser(User $user): array
    {
        return $this->oeuvreRepository->findRecommendedForUser($user);
    }
    public function getRecommendedOeuvres(User $user,int $topN = 3): array
    {
        // 1️⃣ Get user-interacted œuvres
        $interactedOeuvres = $this->oeuvreRepository->findRecommendedForUser($user);
        $validEmbeddings = [];
        foreach ($interactedOeuvres as $oeuvre) {
            $embedding = $oeuvre->getEmbedding(); // array of floats
                if (is_array($embedding) && count($embedding) > 0) {
                    $validEmbeddings[] = $embedding;
                }
        }
        //If no valid embeddings, fallback based on user interests
        if (count($validEmbeddings) === 0) {
            $centreInterets = $user->getCentreInteret() ?? [];
            $mappedTypes = [];
            foreach ($centreInterets as $ci) {
                $ciValue = $ci instanceof CentreInteret ? $ci->value : (string) $ci;
                switch ($ciValue) {
                case CentreInteret::PEINTURE->value:
                    $mappedTypes[] = TypeOeuvre::PEINTURE->value;
                    break;
                case CentreInteret::SCULPTURE->value:
                    $mappedTypes[] = TypeOeuvre::SCULPTURE->value;
                    break;
                case CentreInteret::PHOTOGRAPHIE->value:
                    $mappedTypes[] = TypeOeuvre::PHOTOGRAPHIE->value;
                    break;
                case CentreInteret::MUSIQUE->value:
                    $mappedTypes[] = TypeOeuvre::MUSIQUE->value;
                    break;
                case CentreInteret::LECTURE->value:
                    $mappedTypes[] = TypeOeuvre::LIVRE->value;
                    break;
                }
            }
            $mappedTypes = array_unique($mappedTypes);
            // return directement les œuvres filtrées par type
            return $this->oeuvreRepository->findByTypes($mappedTypes);
        }
        // 2️⃣ Compute user embedding if possible
        $userEmbedding = null;
        if (count($validEmbeddings) > 0) {
            $dimension = count($validEmbeddings[0]);
            $sumVector = array_fill(0, $dimension, 0.0);
            foreach ($validEmbeddings as $embedding) {
                if (count($embedding) !== $dimension) {
                    continue; // skip if dimension mismatch
                }
               for ($i = 0; $i < $dimension; $i++) {
                    $sumVector[$i] += $embedding[$i];
                }
           }
        $userEmbedding = array_map(fn($value) => $value / count($validEmbeddings), $sumVector);
        }
        // 3️⃣ Get candidate œuvres (all artworks except interacted)
        $allOeuvres = $this->oeuvreRepository->findAll(); // or filtered by type
        $candidates = array_filter($allOeuvres, fn($o) => !in_array($o, $interactedOeuvres, true));
        // 4️⃣ Compute similarity for each candidate
        $scores = [];
        foreach ($candidates as $oeuvre) {
            $embedding = $oeuvre->getEmbedding();
            if (!is_array($embedding) || count($embedding) !== $dimension) continue;
            $scores[] = [
                'oeuvre' => $oeuvre,
                'score' => $this->cosineSimilarity($userEmbedding, $embedding)
            ];
        }
        // 5️⃣ Sort by similarity descending
        usort($scores, fn($a, $b) => $b['score'] <=> $a['score']);

        // 6️⃣ Return top N recommended œuvres
        return array_map(fn($s) => $s['oeuvre'], array_slice($scores, 0, $topN));
    }

    public function getRecommendedOeuvresHybrid(User $user, int $topN = 3): array
    {
        $interactedOeuvres = $this->oeuvreRepository->findRecommendedForUser($user);

        $textEmbeddings = [];
        $imageEmbeddings = [];

        foreach ($interactedOeuvres as $oeuvre) {
            $textEmbedding = $oeuvre->getEmbedding();
            if (is_array($textEmbedding) && count($textEmbedding) > 0) {
                $textEmbeddings[] = $textEmbedding;
            }

            $imageEmbedding = $oeuvre->getImageEmbedding();
            if (is_array($imageEmbedding) && count($imageEmbedding) > 0) {
                $imageEmbeddings[] = $imageEmbedding;
            }
        }

        $userTextEmbedding = $this->averageEmbeddings($textEmbeddings);
        $userImageEmbedding = $this->averageEmbeddings($imageEmbeddings);

        if ($userTextEmbedding === null && $userImageEmbedding === null) {
            $mappedTypes = $this->mapUserInterestsToTypes($user);
            return $this->oeuvreRepository->findByTypes($mappedTypes);
        }

        $allOeuvres = $this->oeuvreRepository->findAll();
        $candidates = array_filter($allOeuvres, fn($o) => !in_array($o, $interactedOeuvres, true));

        $scores = [];
        foreach ($candidates as $oeuvre) {
            $candidateTextEmbedding = $oeuvre->getEmbedding();
            $candidateImageEmbedding = $oeuvre->getImageEmbedding();

            $textScore = null;
            if (
                $userTextEmbedding !== null &&
                is_array($candidateTextEmbedding) &&
                count($candidateTextEmbedding) === count($userTextEmbedding)
            ) {
                $textScore = $this->cosineSimilarity($userTextEmbedding, $candidateTextEmbedding);
            }

            $imageScore = null;
            if (
                $userImageEmbedding !== null &&
                is_array($candidateImageEmbedding) &&
                count($candidateImageEmbedding) === count($userImageEmbedding)
            ) {
                $imageScore = $this->cosineSimilarity($userImageEmbedding, $candidateImageEmbedding);
            }

            if ($textScore === null && $imageScore === null) {
                continue;
            }

            if ($textScore !== null && $imageScore !== null) {
                $finalScore = ($textScore + $imageScore) / 2;
            } else {
                $finalScore = $textScore ?? $imageScore;
            }

            $scores[] = [
                'oeuvre' => $oeuvre,
                'score' => $finalScore,
            ];
        }

        usort($scores, fn($a, $b) => $b['score'] <=> $a['score']);

        return array_map(fn($s) => $s['oeuvre'], array_slice($scores, 0, $topN));
    }

    private function averageEmbeddings(array $embeddings): ?array
    {
        if (count($embeddings) === 0) {
            return null;
        }

        $dimension = count($embeddings[0]);
        if ($dimension === 0) {
            return null;
        }

        $sumVector = array_fill(0, $dimension, 0.0);
        $validCount = 0;

        foreach ($embeddings as $embedding) {
            if (!is_array($embedding) || count($embedding) !== $dimension) {
                continue;
            }

            for ($i = 0; $i < $dimension; $i++) {
                $sumVector[$i] += (float) $embedding[$i];
            }

            $validCount++;
        }

        if ($validCount === 0) {
            return null;
        }

        return array_map(fn($value) => $value / $validCount, $sumVector);
    }

    private function mapUserInterestsToTypes(User $user): array
    {
        $centreInterets = $user->getCentreInteret() ?? [];
        $mappedTypes = [];

        foreach ($centreInterets as $ci) {
            $ciValue = $ci instanceof CentreInteret ? $ci->value : (string) $ci;

            switch ($ciValue) {
                case CentreInteret::PEINTURE->value:
                    $mappedTypes[] = TypeOeuvre::PEINTURE->value;
                    break;
                case CentreInteret::SCULPTURE->value:
                    $mappedTypes[] = TypeOeuvre::SCULPTURE->value;
                    break;
                case CentreInteret::PHOTOGRAPHIE->value:
                    $mappedTypes[] = TypeOeuvre::PHOTOGRAPHIE->value;
                    break;
                case CentreInteret::MUSIQUE->value:
                    $mappedTypes[] = TypeOeuvre::MUSIQUE->value;
                    break;
                case CentreInteret::LECTURE->value:
                    $mappedTypes[] = TypeOeuvre::LIVRE->value;
                    break;
            }
        }

        return array_values(array_unique($mappedTypes));
    }

    private function cosineSimilarity(array $a, array $b): float
    {
        $dot = 0.0;
        $normA = 0.0;
        $normB = 0.0;
        $dim = count($a);
        for ($i = 0; $i < $dim; $i++) {
            $dot += $a[$i] * $b[$i];
            $normA += $a[$i] * $a[$i];
            $normB += $b[$i] * $b[$i];
        }
        if ($normA == 0 || $normB == 0) return 0.0; // avoid division by zero
        return $dot / (sqrt($normA) * sqrt($normB));
    }
}