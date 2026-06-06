<?php
// src/Service/ImageEmbeddingService.php
namespace App\Service;

use InvalidArgumentException;
use RuntimeException;
use Symfony\Component\HttpFoundation\File\UploadedFile;
use Symfony\Component\Mime\Part\DataPart;
use Symfony\Component\Mime\Part\Multipart\FormDataPart;
use Symfony\Contracts\HttpClient\HttpClientInterface;

class ImageEmbeddingService
{
    private HttpClientInterface $client;
    private string $apiUrl;

    public function __construct(HttpClientInterface $client, string $apiUrl = 'http://127.0.0.1:8001/embed')
    {
        $this->client = $client;
        $this->apiUrl = $apiUrl;
    }

    public function getEmbeddingFromBlob(mixed $imageInput, ?string $fileName = null): array
    {
        [$stream, $resolvedFileName, $temporaryPath] = $this->prepareFileStream($imageInput, $fileName);

        $formData = new FormDataPart([
            'file' => new DataPart($stream, $resolvedFileName),
        ]);

        $response = $this->client->request('POST', $this->apiUrl, [
            'headers' => $formData->getPreparedHeaders()->toArray(),
            'body' => $formData->bodyToIterable(),
        ]);

        try {
            $data = $response->toArray();

            if (!isset($data['embedding']) || !is_array($data['embedding'])) {
                throw new RuntimeException('Invalid embedding response payload.');
            }

            return $data['embedding'];
        } finally {
            if (is_resource($stream)) {
                fclose($stream);
            }

            if ($temporaryPath !== null && is_file($temporaryPath)) {
                @unlink($temporaryPath);
            }
        }
    }

    /**
     * @return array{0: resource, 1: string, 2: ?string}
     */
    private function prepareFileStream(mixed $imageInput, ?string $fileName): array
    {
        if ($imageInput instanceof UploadedFile) {
            $path = $imageInput->getPathname();
            $stream = fopen($path, 'rb');

            if ($stream === false) {
                throw new RuntimeException(sprintf('Unable to open uploaded file "%s".', $path));
            }

            $resolvedName = $fileName
                ?? $imageInput->getClientOriginalName()
                ?? basename($path);

            return [$stream, $resolvedName, null];
        }

        if (is_resource($imageInput)) {
            $meta = stream_get_meta_data($imageInput);
            if (($meta['seekable'] ?? false) === true) {
                rewind($imageInput);
            }

            $tempPath = tempnam(sys_get_temp_dir(), 'img_embed_');
            if ($tempPath === false) {
                throw new RuntimeException('Unable to create temporary file for image embedding.');
            }

            $tempWrite = fopen($tempPath, 'wb');
            if ($tempWrite === false) {
                throw new RuntimeException(sprintf('Unable to open temporary file "%s" for writing.', $tempPath));
            }

            stream_copy_to_stream($imageInput, $tempWrite);
            fclose($tempWrite);

            $stream = fopen($tempPath, 'rb');
            if ($stream === false) {
                throw new RuntimeException(sprintf('Unable to open temporary file "%s" for reading.', $tempPath));
            }

            return [$stream, $fileName ?? basename($tempPath), $tempPath];
        }

        if (is_string($imageInput) && $imageInput !== '') {
            $tempPath = tempnam(sys_get_temp_dir(), 'img_embed_');
            if ($tempPath === false) {
                throw new RuntimeException('Unable to create temporary file for image embedding.');
            }

            file_put_contents($tempPath, $imageInput);
            $stream = fopen($tempPath, 'rb');

            if ($stream === false) {
                throw new RuntimeException(sprintf('Unable to open temporary file "%s" for reading.', $tempPath));
            }

            return [$stream, $fileName ?? basename($tempPath), $tempPath];
        }

        throw new InvalidArgumentException('Image input must be an UploadedFile, blob resource, or non-empty binary string.');
    }
}