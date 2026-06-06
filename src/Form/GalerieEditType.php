<?php

namespace App\Form;

use App\Entity\Galerie;
use Symfony\Component\Form\AbstractType;
use Symfony\Component\Form\FormBuilderInterface;
use Symfony\Component\OptionsResolver\OptionsResolver;

/**
 * GalerieEditType
 * 
 * Formulaire pour la MODIFICATION d'une galerie existante.
 * Les champs sont optionnels : si un champ est vide, la valeur existante n'est pas modifiée.
 * Seules les contraintes Length, Range, Positive s'appliquent (pas NotBlank).
 * 
 * Validation group 'edit' : ignoré les contraintes NotBlank, applique les autres.
 */
class GalerieEditType extends AbstractType
{
    public function buildForm(FormBuilderInterface $builder, array $options): void
    {
        $builder
            ->add('nom')
            ->add('adresse')
            ->add('localisation')
            ->add('description')
            ->add('capacite_max')
        ;
    }

    public function configureOptions(OptionsResolver $resolver): void
    {
        $resolver->setDefaults([
            'data_class' => Galerie::class,
            // Les deux groupes : 'Default' pour NotBlank + autres pour Length, Positive, Range
            'validation_groups' => ['Default', 'edit'],
        ]);
    }
}
