<?php

namespace App\MessageHandler;

use App\Message\GenerateEmbeddingMessage;
use App\Repository\OeuvreRepository;
use App\Service\EmbeddingService;
use App\Service\ImageEmbeddingService;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Component\Messenger\Attribute\AsMessageHandler;

#[AsMessageHandler]
class GenerateEmbeddingHandler
{
    public function __construct(
        private OeuvreRepository $repository,
        private EntityManagerInterface $em,
        private EmbeddingService $textEmbeddingService,
        private ImageEmbeddingService $imageEmbeddingService
    ) {}

    public function __invoke(GenerateEmbeddingMessage $message): void
    {
        $oeuvre = $this->repository->find($message->getOeuvreId());

        if (!$oeuvre) {
            return;
        }

        /* =======================
         * TEXT EMBEDDING
         * ======================= */
        if ($oeuvre->getEmbedding() === null) {
            $text = trim(
                ($oeuvre->getTitre() ?? '') . ' ' .
                ($oeuvre->getDescription() ?? '')
            );

            if ($text !== '') {
                try {
                    $textEmbedding = $this->textEmbeddingService->embed($text);
                    $oeuvre->setEmbedding($textEmbedding);
                } catch (\Throwable) {
                }
            }
        }

        /* =======================
         * IMAGE EMBEDDING
         * ======================= */
        if (
            $oeuvre->getImage() !== null &&
            $oeuvre->getImageEmbedding() === null
        ) {
            try {
                $imageEmbedding = $this->imageEmbeddingService
                    ->getEmbeddingFromBlob(
                        $oeuvre->getImage(),
                        sprintf('oeuvre_%d_image.bin', $oeuvre->getId())
                    );

                $oeuvre->setImageEmbedding($imageEmbedding);
            } catch (\Throwable) {
            }
        }

        try {
            $this->em->flush();
        } catch (\Throwable) {
        }
    }
}