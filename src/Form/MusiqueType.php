<?php

namespace App\Form;

use App\Entity\Collections;
use App\Entity\Musique;
use App\Enum\GenreMusique;
use App\Validator\ImageDimensions;
use Symfony\Bridge\Doctrine\Form\Type\EntityType;
use Symfony\Component\Form\AbstractType;
use Symfony\Component\Form\Extension\Core\Type\EnumType;
use Symfony\Component\Form\Extension\Core\Type\TextareaType;
use Symfony\Component\Form\Extension\Core\Type\TextType;
use Symfony\Component\Form\FormBuilderInterface;
use Symfony\Component\OptionsResolver\OptionsResolver;
use Symfony\Component\Validator\Constraints as Assert;
use Symfony\UX\Dropzone\Form\DropzoneType;

class MusiqueType extends AbstractType
{
    public function buildForm(FormBuilderInterface $builder, array $options): void
    {
        // Detect if we're editing an existing entity
        $isEdit = $options['data']?->getId() !== null;
        
        $builder
            ->add('titre', TextType::class, [
                'label' => 'Titre',
                'attr' => [
                    'class' => 'form-control',
                    'placeholder' => 'entrez le titre de la musique',
                    'minlength' => 3,
                    'maxlength' => 255,
                    'required' => 'required'
                ],
                'constraints' => [
                    new Assert\NotBlank(['message' => 'Le titre est requis']),
                    new Assert\Length([
                        'min' => 3,
                        'minMessage' => 'Le titre doit comporter au moins 3 caractères',
                        'max' => 255,
                        'maxMessage' => 'Le titre ne peut pas dépasser 255 caractères'
                    ])
                ]
            ])
            ->add('description', TextareaType::class, [
                'label' => 'Description',
                'attr' => [
                    'class' => 'form-control',
                    'placeholder' => 'entrez la description de la musique',
                    'rows' => 3,
                    'minlength' => 10,
                    'maxlength' => 5000,
                    'required' => 'required'
                ],
                'constraints' => [
                    new Assert\NotBlank(['message' => 'La description est requise']),
                    new Assert\Length([
                        'min' => 10,
                        'minMessage' => 'La description doit comporter au moins 10 caractères long',
                        'max' => 5000,
                        'maxMessage' => 'Description cannot exceed 5000 characters'
                    ])
                ]
            ])
            ->add('genre', EnumType::class, [
                'class' => GenreMusique::class,
                'label' => 'Genre',
                'attr' => [
                    'class' => 'form-select',
                    'required' => 'required'
                ],
                'placeholder' => 'Sélectionnez un genre',
                'constraints' => [
                    new Assert\NotBlank(['message' => 'Le genre est requis'])
                ]
            ])
            ->add('collection', EntityType::class, [
                'class' => Collections::class,
                'choices' => $options['collection_choices'],
                'choice_label' => 'titre',
                'placeholder' => 'Sélectionnez une collection',
                'label' => 'Collection',
                'attr' => [
                    'class' => 'form-select',
                    'required' => 'required',
                ],
                'constraints' => [
                    new Assert\NotNull(['message' => 'La collection est requise'])
                ]
            ])
            ->add('imageFile', DropzoneType::class, [
                'label' => 'Image de Couverture',
                'mapped' => false,
                'required' => false,
                'attr' => [
                    'placeholder' => 'Glissez-déposez votre image ici ou cliquez pour parcourir',
                    'data-controller' => 'dropzone',
                ],
                'help' => 'Max 5MB (JPEG, PNG) • Recommandé: min 300x300px',
                'constraints' => [
                    new Assert\File([
                        'maxSize' => '5242880', // 5MB in bytes
                        'mimeTypes' => [
                            'image/jpeg',
                            'image/png',
                            'image/jpg',
                        ],
                        'mimeTypesMessage' => 'Veuillez télécharger un fichier image valide (JPEG ou PNG).',
                        'maxSizeMessage' => 'Le fichier image est trop volumineux. (max 5MB)',
                    ]),
                    new ImageDimensions([
                        'minWidth' => 300,
                        'minHeight' => 300,
                        'maxWidth' => 5000,
                        'maxHeight' => 5000,
                    ])
                ]
            ])
            ->add('audioFile', DropzoneType::class, [
                'label' => 'Fichier Audio',
                'mapped' => false,
                'required' => !$isEdit,
                'attr' => [
                    'placeholder' => 'Glissez-déposez votre fichier audio ici ou cliquez pour parcourir',
                    'data-controller' => 'dropzone',
                ],
                'help' => !$isEdit ? 'Requis. MP3, WAV, AAC, etc.' : 'Optionnel. Laissez vide pour conserver l\'actuel.',
                'constraints' => array_filter([
                    !$isEdit ? new Assert\NotBlank(['message' => 'Le fichier audio est requis']) : null,
                    new Assert\File([
                        'mimeTypes' => [
                            'audio/mpeg',
                            'audio/mp3',
                            'audio/wav',
                            'audio/x-wav',
                            'audio/aac',
                            'audio/aacp',
                            'audio/opus',
                            'audio/ogg',
                            'audio/webm',
                            'audio/flac',
                            'audio/x-m4a',
                            'audio/mp4',
                        ],
                        'mimeTypesMessage' => 'Veuillez télécharger un fichier audio valide',
                    ])
                ])
            ]);
    }

    public function configureOptions(OptionsResolver $resolver): void
    {
        $resolver->setDefaults([
            'data_class' => Musique::class,
            'collection_choices' => [],
            'attr' => [
                'class' => 'needs-validation',
                'novalidate' => true
            ]
        ]);

        $resolver->setAllowedTypes('collection_choices', 'array');
    }
}
