<?php

namespace App\Form;

use App\Entity\Reclamation;
use App\Enum\TypeReclamation;
use Symfony\Component\Form\Extension\Core\Type\EnumType;
use Symfony\Component\Form\AbstractType;
use Symfony\Component\Form\FormBuilderInterface;
use Symfony\Component\OptionsResolver\OptionsResolver;
use Vich\UploaderBundle\Form\Type\VichFileType;

class Reclamation1Type extends AbstractType
{
    public function buildForm(FormBuilderInterface $builder, array $options): void
    {
        $builder
            ->add('texte')
            ->add('type', EnumType::class, [
                'class' => TypeReclamation::class,
                'choice_label' => static fn (TypeReclamation $choice): string => $choice->value,
                'placeholder' => 'Selectionner une categorie',
            ])
            ->add('file', VichFileType::class, [
                'label' => 'Fichier joint (optionnel)',
                'required' => false,
                'allow_delete' => true,
                'download_uri' => true,
                'attr' => [
                    'accept' => '.pdf,.jpg,.jpeg,.png'
                ]
            ])
        ;
    }

    public function configureOptions(OptionsResolver $resolver): void
    {
        $resolver->setDefaults([
            'data_class' => Reclamation::class,
        ]);
    }
}
