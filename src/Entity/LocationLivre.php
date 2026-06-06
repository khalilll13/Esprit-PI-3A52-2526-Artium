<?php

namespace App\Entity;

use App\Enum\EtatLocation;
use App\Repository\LocationLivreRepository;
use Doctrine\ORM\Mapping as ORM;

#[ORM\Entity(repositoryClass: LocationLivreRepository::class)]
class LocationLivre
{
    #[ORM\Id]
    #[ORM\GeneratedValue]
    #[ORM\Column]
    private ?int $id = null;

    #[ORM\Column]
    private ?\DateTime $date_debut = null;

    #[ORM\Column(enumType: EtatLocation::class)]
    private ?EtatLocation $etat = null;

    #[ORM\ManyToOne(inversedBy: 'locationLivres')]
    #[ORM\JoinColumn(nullable: false)]
    private ?User $user = null;

    #[ORM\ManyToOne(inversedBy: 'locationLivres')]
    #[ORM\JoinColumn(nullable: false)]
    private ?Livre $livre = null;

    #[ORM\Column(type: 'integer', nullable: true)]
    private ?int $nombreDeJours = null;

    public function getId(): ?int
    {
        return $this->id;
    }

    public function getDateDebut(): ?\DateTime
    {
        return $this->date_debut;
    }

    public function setDateDebut(\DateTime $date_debut): static
    {
        $this->date_debut = $date_debut;

        return $this;
    }

    public function getEtat(): ?EtatLocation
    {
        return $this->etat;
    }

    public function setEtat(EtatLocation $etat): static
    {
        $this->etat = $etat;

        return $this;
    }

    public function getUser(): ?User
    {
        return $this->user;
    }

    public function setUser(?User $user): static
    {
        $this->user = $user;

        return $this;
    }

    public function getLivre(): ?Livre
    {
        return $this->livre;
    }

    public function setLivre(?Livre $livre): static
    {
        $this->livre = $livre;

        return $this;
    }
    
    public function getNombreDeJours(): ?int
    {
        return $this->nombreDeJours;
    }

    public function setNombreDeJours(?int $nombreDeJours): static
    {
        $this->nombreDeJours = $nombreDeJours;
        return $this;
    }
}
