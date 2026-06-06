<?php

namespace App\Form;

use App\Entity\Evenement;
use App\Entity\Galerie;
use App\Enum\StatutEvenement;
use App\Enum\TypeEvenement;
use Symfony\Bridge\Doctrine\Form\Type\EntityType;
use Symfony\Component\Form\AbstractType;
use Symfony\Component\Form\Extension\Core\Type\DateTimeType;
use Symfony\Component\Form\Extension\Core\Type\EnumType;
use Symfony\Component\Form\Extension\Core\Type\FileType;
use Symfony\Component\Form\FormBuilderInterface;
use Symfony\Component\OptionsResolver\OptionsResolver;
use Symfony\Component\Validator\Constraints as Assert;

/**
 * EvenementArtisteEditType
 * 
 * Formulaire pour la modification d'événements par les artistes.
 * L'image est optionnelle lors de l'édition (car l'événement a déjà une image).
 */
class EvenementArtisteEditType extends AbstractType
{
    public function buildForm(FormBuilderInterface $builder, array $options): void
    {
        $builder
            ->add('titre')
            ->add('description')
            ->add('date_debut', DateTimeType::class, [
                'widget' => 'single_text',
            ])
            ->add('date_fin', DateTimeType::class, [
                'widget' => 'single_text',
            ])
            ->add('type', EnumType::class, [
                'class' => TypeEvenement::class,
                'choice_label' => static fn (TypeEvenement $choice) => $choice->value,
            ])
            ->add('capacite_max')
            ->add('prix_ticket')
            ->add('galerie', EntityType::class, [
                'class' => Galerie::class,
                'choice_label' => 'nom',
            ])
            ->add('imageFile', FileType::class, [
                'label' => 'Image de couverture',
                'mapped' => false,
                'required' => false,
                'constraints' => [
                    new Assert\File([
                        'maxSize' => '2M',
                        'mimeTypes' => ['image/jpeg', 'image/png', 'image/jpg'],
                        'mimeTypesMessage' => 'Veuillez télécharger une image valide (JPG, PNG)',
                    ])
                ],
            ])
        ;
    }

    public function configureOptions(OptionsResolver $resolver): void
    {
        $resolver->setDefaults([
            'data_class' => Evenement::class,
        ]);
    }
}
