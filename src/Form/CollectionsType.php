<?php

namespace App\Form;

use App\Entity\Collections;
use App\Entity\User;
use Symfony\Component\Form\Extension\Core\Type\TextType;    
use Symfony\Component\Form\Extension\Core\Type\TextareaType;
use Symfony\Bridge\Doctrine\Form\Type\EntityType;
use Symfony\Component\Form\AbstractType;
use Symfony\Component\Form\FormBuilderInterface;
use Symfony\Component\OptionsResolver\OptionsResolver;

class CollectionsType extends AbstractType
{
    public function buildForm(FormBuilderInterface $builder, array $options): void
    {
        $builder
            ->add('titre', TextType::class, [
        'label' => 'Titre',
        'attr' => ['placeholder' => 'Entrez le titre', 'class' => 'form-control'],
    ])
    ->add('description', TextareaType::class, [
        'label' => 'Description',
        'attr' => ['placeholder' => 'Entrez la description', 'rows' => 4, 'class' => 'form-control'],
            ])
        ;
    }

    public function configureOptions(OptionsResolver $resolver): void
    {
        $resolver->setDefaults([
            'data_class' => Collections::class,
        ]);
    }
}
