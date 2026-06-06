<?php

namespace App\Entity;

use App\Repository\ReponseRepository;
use Doctrine\DBAL\Types\Types;
use Doctrine\ORM\Mapping as ORM;
use Symfony\Component\Validator\Constraints as Assert;

#[ORM\Entity(repositoryClass: ReponseRepository::class)]
class Reponse
{
    #[ORM\Id]
    #[ORM\GeneratedValue]
    #[ORM\Column]
    private ?int $id = null;

    #[ORM\Column(type: Types::TEXT)]
    #[Assert\NotBlank(message: "Le contenu de la reponse est obligatoire", groups: ['Default'])]
    #[Assert\Length(
        min: 10,
        max: 2000,
        minMessage: "La reponse doit contenir au minimum {{ limit }} caracteres",
        maxMessage: "La reponse ne peut pas depasser {{ limit }} caracteres",
        groups: ['Default', 'edit']
    )]
    private ?string $contenu = null;

    #[ORM\Column(type: Types::DATE_MUTABLE)]
    private ?\DateTime $date_reponse = null;

    #[ORM\ManyToOne(inversedBy: 'reponses')]
    #[ORM\JoinColumn(nullable: false)]
    #[Assert\NotBlank(message: "La reclamation associee est obligatoire", groups: ['Default'])]
    private ?Reclamation $reclamation = null;

    #[ORM\ManyToOne(inversedBy: 'reponses')]
    #[ORM\JoinColumn(nullable: true)]
    private ?User $user_admin = null;

    public function getId(): ?int
    {
        return $this->id;
    }

    public function getContenu(): ?string
    {
        return $this->contenu;
    }

    public function setContenu(string $contenu): static
    {
        $this->contenu = $contenu;

        return $this;
    }

    public function getDateReponse(): ?\DateTime
    {
        return $this->date_reponse;
    }

    public function setDateReponse(\DateTime $date_reponse): static
    {
        $this->date_reponse = $date_reponse;

        return $this;
    }

    public function getReclamation(): ?Reclamation
    {
        return $this->reclamation;
    }

    public function setReclamation(?Reclamation $reclamation): static
    {
        $this->reclamation = $reclamation;

        return $this;
    }

    public function getUserAdmin(): ?User
    {
        return $this->user_admin;
    }

    public function setUserAdmin(?User $user_admin): static
    {
        $this->user_admin = $user_admin;

        return $this;
    }
}
