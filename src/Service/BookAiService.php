<?php

namespace App\Service;

use Symfony\Contracts\HttpClient\HttpClientInterface;
use Smalot\PdfParser\Parser;

class BookAiService
{
    private HttpClientInterface $client;
    private string $gapiKey;

    public function __construct(HttpClientInterface $client, string $gapiKey)
    {
        $this->client = $client;
        $this->gapiKey = $gapiKey;
    }

    /**
     * Generate book metadata from PDF using Gemini API
     *
     * @param string $pdfPath Path to the PDF file
     * @return array
     * @throws \Exception
     */
    public function generateFromPdf(string $pdfPath): array
    {
        // 1️⃣ Extract PDF text
        try {
            $parser = new Parser();
            $pdf = $parser->parseFile($pdfPath);
            $text = substr($pdf->getText(), 0, 4000); // limit first 4000 chars
        } catch (\Throwable $e) {
            throw new \Exception('PDF parse error: ' . $e->getMessage());
        }

        // 2️⃣ Prepare prompt for Gemini
        $prompt = <<<EOT
Extract book metadata from the following text and return STRICT JSON only:

{
  "titre": "...",
  "categorie": "...",
  "description": "...",
  "prix": number
}

Rules:
- description around 120-180 words
- prix between 5 and 30
- Only return JSON
- No explanation text

Text:
$text
EOT;

        // 3️⃣ Call Gemini API
        try {
            // Debug: Check if API key is loaded
            if (empty($this->gapiKey)) {
                throw new \Exception('GEMINI_API_KEY not configured in environment');
            }

            $response = $this->client->request(
                'POST',
                'https://generativelanguage.googleapis.com/v1beta/models/gemini-3-flash-preview:generateContent',
                [
                    'headers' => [
                        'x-goog-api-key' => $this->gapiKey,
                        'Content-Type' => 'application/json',
                    ],
                    'json' => [
                        'contents' => [
                            [
                                'parts' => [
                                    ['text' => $prompt]
                                ]
                            ]
                        ]
                    ]
                ]
            );

            $statusCode = $response->getStatusCode();
            $content = $response->getContent();
            
            // Debug: Show full response for non-200 status
            if ($statusCode !== 200) {
                throw new \Exception("Gemini API error: HTTP {$statusCode} returned. Response: {$content}");
            }
            
            $result = json_decode($content, true);

            if (
                !isset(
                    $result['candidates'][0]['content']['parts'][0]['text']
                )
            ) {
                throw new \Exception('Invalid Gemini response structure.');
            }

            $generatedText = $result['candidates'][0]['content']['parts'][0]['text'];

            // 4️⃣ Extract JSON block from AI response
            preg_match('/\{.*\}/s', $generatedText, $matches);
            $json = $matches[0] ?? '{}';
            $data = json_decode($json, true);

            if (!$data) {
                throw new \Exception('Invalid JSON returned by AI.');
            }

            return [
                'titre' => $data['titre'] ?? '',
                'description' => $data['description'] ?? '',
                'categorie' => $data['categorie'] ?? '',
                'prix' => $data['prix'] ?? 10,
            ];

        } catch (\Throwable $e) {
            throw new \Exception('Gemini API error: ' . $e->getMessage());
        }
    }
}