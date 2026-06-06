<?php

namespace App\Form;

use App\Entity\User;
use App\Enum\Role;
use App\Enum\Specialite;
use App\Enum\CentreInteret;
use Symfony\Component\Form\AbstractType;
use Symfony\Component\Form\Extension\Core\Type\EmailType;
use Symfony\Component\Form\Extension\Core\Type\PasswordType;
use Symfony\Component\Form\Extension\Core\Type\EnumType;
use Symfony\Component\Form\Extension\Core\Type\TextType;
use Symfony\Component\Form\Extension\Core\Type\TelType;
use Symfony\Component\Form\FormBuilderInterface;
use Symfony\Component\OptionsResolver\OptionsResolver;

class SignupType extends AbstractType
{
    public function buildForm(FormBuilderInterface $builder, array $options): void
    {
        //Informations personnelles
        $builder->add('nom', TextType::class, [
            'label' => 'Nom',
            'attr' => ['data-step' => 1, 'placeholder' => 'Entrez le nom', 'class' => 'form-control']
        ]);
        $builder->add('prenom', TextType::class, [
            'label' => 'Prénom',
            'attr' => ['data-step' => 1, 'placeholder' => 'Entrez le prénom', 'class' => 'form-control']
        ]);
        $builder->add('dateNaissance', \Symfony\Component\Form\Extension\Core\Type\DateType::class, [
            'label' => 'Date de naissance',
            'widget' => 'single_text',
            'attr' => ['data-step' => 1, 'class' => 'form-control']
        ]);
        $builder->add('numTel', TelType::class, [
            'label' => 'Numéro de téléphone',
            'attr' => ['data-step' => 1, 'placeholder' => '+216 XX XXX XXX', 'class' => 'form-control']
        ]);
        $builder->add('ville', TextType::class, [
            'label' => 'Ville',
            'attr' => ['data-step' => 1, 'placeholder' => 'Entrez la ville', 'class' => 'form-control']
        ]);

        //Connexion
        $builder->add('email', EmailType::class, [
            'label' => 'Adresse e-mail',
            'attr' => ['data-step' => 2, 'placeholder' => 'Votre adresse e-mail', 'class' => 'form-control']
        ]);
        $builder->add('plainPassword', PasswordType::class, [
            'label' => 'Mot de passe',
            'attr' => ['data-step' => 2, 'placeholder' => '**************', 'class' => 'form-control'],
            'required' => true
        ]);

        //Rôle et champs spécifiques
        $builder->add('role', EnumType::class, [
            'class' => Role::class,
            'label' => 'Rôle',
            'placeholder' => 'Choisir un rôle',
            'attr' => ['data-step' => 3, 'id' => 'signup_role', 'class' => 'form-select']
        ]);
        $builder->add('biographie', \Symfony\Component\Form\Extension\Core\Type\TextareaType::class, [
            'label' => 'Biographie',
            'required' => false,
            'attr' => ['data-step' => 3, 'placeholder' => 'Parlez-nous de vous...', 'rows' => 4, 'class' => 'form-control']
        ]);
        $builder->add('specialite', EnumType::class, [
            'class' => Specialite::class,
            'label' => 'Spécialité',
            'required' => false,
            'attr' => ['data-step' => 3, 'id' => 'signup_specialite', 'class' => 'form-select']
        ]);
        $builder->add('centreInteret', EnumType::class, [
            'class' => CentreInteret::class,
            'label' => "Centre d'intérêt",
            'required' => false,
            'multiple' => true,
            'attr' => ['data-step' => 3, 'id' => 'signup_centreInteret', 'class' => 'form-select']
        ]);
        // Photo de référence pour la reconnaissance faciale
        $builder->add('photo', \Symfony\Component\Form\Extension\Core\Type\FileType::class, [
            'label' => 'Photo de référence',
            'mapped' => false,
            'required' => false,
            'attr' => ['data-step' => 4, 'class' => 'form-control']
        ]);
    }

    public function configureOptions(OptionsResolver $resolver): void
    {
        $resolver->setDefaults([
            'data_class' => User::class,
        ]);
    }
}
