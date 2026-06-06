<?php

namespace App\Form;

use App\Entity\Collections;
use App\Entity\Livre;
use App\Repository\CollectionsRepository;
use Symfony\Bridge\Doctrine\Form\Type\EntityType;
use Symfony\Component\Form\AbstractType;
use Symfony\Component\Form\Extension\Core\Type\FileType;
use Symfony\Component\Form\Extension\Core\Type\NumberType;
use Symfony\Component\Form\Extension\Core\Type\TextareaType;
use Symfony\Component\Form\Extension\Core\Type\TextType;
use Symfony\Component\Form\FormBuilderInterface;
use Symfony\Component\OptionsResolver\OptionsResolver;

class LivreType extends AbstractType
{
    public function buildForm(FormBuilderInterface $builder, array $options): void
    {
        $artist = $options['artist'];

        $builder
            ->add('titre', TextType::class, [
                'label' => 'Titre',
            ])
            ->add('description', TextareaType::class, [
                'label' => 'Description',
            ])
            ->add('image', FileType::class, [
                'label' => 'Image',
                'mapped' => false,
                'required' => false,
            ])
            ->add('categorie', TextType::class, [
                'label' => 'Catégorie',
            ])
            ->add('prix_location', NumberType::class, [
                'label' => 'Prix de location',
                'scale' => 2,
            ])
            ->add('fichier_pdf', FileType::class, [
                'label' => 'Fichier PDF',
                'mapped' => false,
                'required' => false,
            ])
            ->add('collection', EntityType::class, [
                'class' => Collections::class,
                'choice_label' => 'titre',
                'label' => 'Collection',
                'placeholder' => 'Choisir une collection',
                'required' => true,
                'query_builder' => function (CollectionsRepository $repo) use ($artist) {
                    $qb = $repo->createQueryBuilder('c')
                               ->orderBy('c.titre', 'ASC');

                    // If artist is provided → filter by artist
                    if ($artist !== null) {
                        $qb->where('c.artiste = :artist')
                           ->setParameter('artist', $artist);
                    }

                    return $qb;
                },
            ]);
    }

    public function configureOptions(OptionsResolver $resolver): void
    {
        $resolver->setDefaults([
            'data_class' => Livre::class,
            'artist' => null, // default = admin mode
        ]);
    }
}
