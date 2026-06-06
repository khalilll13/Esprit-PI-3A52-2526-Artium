<?php

namespace App\Form;

use App\Entity\Galerie;
use Symfony\Component\Form\AbstractType;
use Symfony\Component\Form\FormBuilderInterface;
use Symfony\Component\OptionsResolver\OptionsResolver;

/**
 * GalerieAddType
 * 
 * Formulaire pour l'AJOUT d'une nouvelle galerie.
 * Tous les champs obligatoires doivent être remplis.
 * Les contraintes de validation de l'Entity s'appliquent complètement.
 */
class GalerieAddType extends AbstractType
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
            // Validation complète : toutes les contraintes de l'Entity s'appliquent
            'validation_groups' => ['Default'],
        ]);
    }
}
