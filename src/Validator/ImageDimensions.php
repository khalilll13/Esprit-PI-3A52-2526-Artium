<?php

namespace App\Validator;

use Symfony\Component\Validator\Constraint;

/**
 * @Annotation
 */
#[\Attribute(\Attribute::TARGET_PROPERTY | \Attribute::TARGET_METHOD)]
class ImageDimensions extends Constraint
{
    public int $minWidth = 100;
    public int $minHeight = 100;
    public int $maxWidth = 5000;
    public int $maxHeight = 5000;
    
    public string $minWidthMessage = 'Image width must be at least {{ min_width }}px (current: {{ width }}px)';
    public string $minHeightMessage = 'Image height must be at least {{ min_height }}px (current: {{ height }}px)';
    public string $maxWidthMessage = 'Image width must not exceed {{ max_width }}px (current: {{ width }}px)';
    public string $maxHeightMessage = 'Image height must not exceed {{ max_height }}px (current: {{ height }}px)';
}
