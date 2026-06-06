<?php

namespace App\Service;

use Symfony\Component\HttpFoundation\File\UploadedFile;

class FileStorageService
{
    /**
     * Directory for image files (C:\xampp\htdocs\img\)
     */
    private string $imgDir;

    /**
     * Directory for PDF files (C:\xampp\htdocs\pdf\)
     */
    private string $pdfDir;

    /**
     * Base URL for images (http://127.0.0.1/img/)
     */
    private string $imgBaseUrl;

    /**
     * Base URL for PDFs (http://127.0.0.1/pdf/)
     */
    private string $pdfBaseUrl;

    public function __construct(
        string $imgDir = 'C:\\xampp\\htdocs\\img\\',
        string $pdfDir = 'C:\\xampp\\htdocs\\pdf\\',
        string $imgBaseUrl = 'http://127.0.0.1/img/',
        string $pdfBaseUrl = 'http://127.0.0.1/pdf/'
    ) {
        $this->imgDir = $imgDir;
        $this->pdfDir = $pdfDir;
        $this->imgBaseUrl = $imgBaseUrl;
        $this->pdfBaseUrl = $pdfBaseUrl;
    }

    /**
     * Upload an image file and return its filename
     *
     * @param UploadedFile $file
     * @param string $prefix Optional prefix for the filename (e.g., 'playlist_', 'profile_')
     * @return string The filename (not the full URL)
     * @throws \Exception
     */
    public function uploadImage(UploadedFile $file, string $prefix = ''): string
    {
        return $this->uploadFileToDirectory($file, $this->imgDir, $prefix);
    }

    /**
     * Upload a PDF file and return its filename
     *
     * @param UploadedFile $file
     * @param string $prefix Optional prefix for the filename (e.g., 'pdf_')
     * @return string The filename (not the full URL)
     * @throws \Exception
     */
    public function uploadPdf(UploadedFile $file, string $prefix = ''): string
    {
        return $this->uploadFileToDirectory($file, $this->pdfDir, $prefix);
    }

    /**
     * Generic file upload to specified directory
     *
     * @param UploadedFile $file
     * @param string $uploadDir
     * @param string $prefix
     * @return string The filename (not the full URL)
     * @throws \Exception
     */
    private function uploadFileToDirectory(UploadedFile $file, string $uploadDir, string $prefix = ''): string
    {
        // Validate file
        if (!$file->isValid()) {
            throw new \Exception('File upload failed: ' . $file->getErrorMessage());
        }

        // Generate unique filename
        $extension = $file->guessExtension();
        $filename = $prefix . uniqid() . '.' . $extension;

        // Create directory if it doesn't exist
        if (!is_dir($uploadDir)) {
            if (!@mkdir($uploadDir, 0755, true)) {
                throw new \Exception('Failed to create upload directory: ' . $uploadDir);
            }
        }

        // Move file using UploadedFile->move which handles "test" files and
        // various wrappers more reliably than move_uploaded_file().
        try {
            $moved = $file->move($uploadDir, $filename);
            if (!is_file($moved->getPathname())) {
                throw new \Exception('Move did not produce expected file: ' . $uploadDir . $filename);
            }
        } catch (\Throwable $e) {
            throw new \Exception('Failed to move uploaded file to: ' . $uploadDir . $filename . ' - ' . $e->getMessage());
        }

        return $filename;
    }

    /**
     * Get the full URL for an image file
     *
     * @param string $filename
     * @return string
     */
    public function getImageUrl(string $filename): string
    {
        return $this->imgBaseUrl . $filename;
    }

    /**
     * Get the full URL for a PDF file
     *
     * @param string $filename
     * @return string
     */
    public function getPdfUrl(string $filename): string
    {
        return $this->pdfBaseUrl . $filename;
    }

    /**
     * Delete an image file if it exists
     *
     * @param string $filename
     * @return bool
     */
    public function deleteImage(string $filename): bool
    {
        return $this->deleteFileFromDirectory($filename, $this->imgDir);
    }

    /**
     * Delete a PDF file if it exists
     *
     * @param string $filename
     * @return bool
     */
    public function deletePdf(string $filename): bool
    {
        return $this->deleteFileFromDirectory($filename, $this->pdfDir);
    }

    /**
     * Generic file deletion from specified directory
     *
     * @param string $filename
     * @param string $uploadDir
     * @return bool
     */
    private function deleteFileFromDirectory(string $filename, string $uploadDir): bool
    {
        $fullPath = $uploadDir . $filename;
        if (file_exists($fullPath)) {
            return @unlink($fullPath);
        }
        return true;
    }

    /**
     * Check if an image file exists
     *
     * @param string $filename
     * @return bool
     */
    public function imageExists(string $filename): bool
    {
        return file_exists($this->imgDir . $filename);
    }

    /**
     * Check if a PDF file exists
     *
     * @param string $filename
     * @return bool
     */
    public function pdfExists(string $filename): bool
    {
        return file_exists($this->pdfDir . $filename);
    }
}
