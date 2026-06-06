<?php

namespace App\Meili;

use Meilisearch\Bundle\SearchManagerInterface;
use Meilisearch\Bundle\Exception\NotSearchableException;
use Meilisearch\Bundle\Model\SearchResults;
use Meilisearch\Contracts\Task;
use Meilisearch\Bundle\Collection;
use Psr\Log\LoggerInterface;

/**
 * Decorator around Meilisearch manager to avoid hard failures when Meilisearch is down.
 */
final class SafeSearchManager implements SearchManagerInterface
{
    public function __construct(
        private readonly SearchManagerInterface $inner,
        private readonly LoggerInterface $logger
    ) {
    }

    public function isSearchable(object|string $className): bool
    {
        try {
            return $this->inner->isSearchable($className);
        } catch (\Throwable $e) {
            $this->logger->warning('Meilisearch unavailable in isSearchable: ' . $e->getMessage());
            return false;
        }
    }

    public function searchableAs(string $className): string
    {
        try {
            return $this->inner->searchableAs($className);
        } catch (\Throwable $e) {
            $this->logger->warning('Meilisearch searchableAs failed: ' . $e->getMessage());
            throw new NotSearchableException(sprintf('Class %s is not searchable at the moment', $className));
        }
    }

    public function getConfiguration(): Collection
    {
        try {
            return $this->inner->getConfiguration();
        } catch (\Throwable $e) {
            $this->logger->warning('Meilisearch getConfiguration failed: ' . $e->getMessage());
            return new Collection([]);
        }
    }

    public function index(object|array $searchable): array
    {
        try {
            return $this->inner->index($searchable);
        } catch (\Throwable $e) {
            $this->logger->warning('Meilisearch index failed, continuing without search indexing: ' . $e->getMessage());
            return [];
        }
    }

    public function remove(object|array $searchable): array
    {
        try {
            return $this->inner->remove($searchable);
        } catch (\Throwable $e) {
            $this->logger->warning('Meilisearch remove failed, continuing: ' . $e->getMessage());
            return [];
        }
    }

    public function clear(string $className): Task
    {
        try {
            return $this->inner->clear($className);
        } catch (\Throwable $e) {
            $this->logger->warning('Meilisearch clear failed: ' . $e->getMessage());
            throw $e;
        }
    }

    public function deleteByIndexName(string $indexName): Task
    {
        try {
            return $this->inner->deleteByIndexName($indexName);
        } catch (\Throwable $e) {
            $this->logger->warning('Meilisearch deleteByIndexName failed: ' . $e->getMessage());
            throw $e;
        }
    }

    public function delete(string $className): Task
    {
        try {
            return $this->inner->delete($className);
        } catch (\Throwable $e) {
            $this->logger->warning('Meilisearch delete failed: ' . $e->getMessage());
            throw $e;
        }
    }

    public function search(string $className, string $query = '', array $searchParams = []): SearchResults
    {
        try {
            return $this->inner->search($className, $query, $searchParams);
        } catch (\Throwable $e) {
            $this->logger->warning('Meilisearch search failed, returning empty results: ' . $e->getMessage());
            return new SearchResults([], $query, 0);
        }
    }

    public function rawSearch(string $className, string $query = '', array $searchParams = []): array
    {
        try {
            return $this->inner->rawSearch($className, $query, $searchParams);
        } catch (\Throwable $e) {
            $this->logger->warning('Meilisearch rawSearch failed: ' . $e->getMessage());
            return [self::RESULT_KEY_HITS => []];
        }
    }

    public function count(string $className, string $query = '', array $searchParams = []): int
    {
        try {
            return $this->inner->count($className, $query, $searchParams);
        } catch (\Throwable $e) {
            $this->logger->warning('Meilisearch count failed: ' . $e->getMessage());
            return 0;
        }
    }
}
