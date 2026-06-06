<?php

namespace App\Validator;

use Symfony\Component\Validator\Constraint;
use Symfony\Component\Validator\ConstraintValidator;
use Symfony\Component\Validator\Exception\UnexpectedTypeException;
use Symfony\Component\HttpFoundation\File\UploadedFile;

class ImageDimensionsValidator extends ConstraintValidator
{
    public function validate($value, Constraint $constraint): void
    {
        if (!$constraint instanceof ImageDimensions) {
            throw new UnexpectedTypeException($constraint, ImageDimensions::class);
        }

        if (null === $value || '' === $value) {
            return;
        }

        if (!$value instanceof UploadedFile) {
            throw new UnexpectedTypeException($value, UploadedFile::class);
        }

        // Only validate if it's an image file
        if (strpos($value->getMimeType(), 'image/') !== 0) {
            return;
        }

        // Get image dimensions
        $imageInfo = @getimagesize($value->getRealPath());
        if ($imageInfo === false) {
            $this->context->buildViolation('Unable to read image dimensions. File may be corrupted.')
                ->addViolation();
            return;
        }

        $width = $imageInfo[0];
        $height = $imageInfo[1];

        // Validate minimum width
        if ($width < $constraint->minWidth) {
            $this->context->buildViolation($constraint->minWidthMessage)
                ->setParameter('{{ min_width }}', (string)$constraint->minWidth)
                ->setParameter('{{ width }}', (string)$width)
                ->addViolation();
        }

        // Validate minimum height
        if ($height < $constraint->minHeight) {
            $this->context->buildViolation($constraint->minHeightMessage)
                ->setParameter('{{ min_height }}', (string)$constraint->minHeight)
                ->setParameter('{{ height }}', (string)$height)
                ->addViolation();
        }

        // Validate maximum width
        if ($width > $constraint->maxWidth) {
            $this->context->buildViolation($constraint->maxWidthMessage)
                ->setParameter('{{ max_width }}', (string)$constraint->maxWidth)
                ->setParameter('{{ width }}', (string)$width)
                ->addViolation();
        }

        // Validate maximum height
        if ($height > $constraint->maxHeight) {
            $this->context->buildViolation($constraint->maxHeightMessage)
                ->setParameter('{{ max_height }}', (string)$constraint->maxHeight)
                ->setParameter('{{ height }}', (string)$height)
                ->addViolation();
        }
    }
}
