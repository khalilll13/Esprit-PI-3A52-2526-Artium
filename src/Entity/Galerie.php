<?php

namespace App\Entity;

use App\Repository\GalerieRepository;
use Doctrine\Common\Collections\ArrayCollection;
use Doctrine\Common\Collections\Collection;
use Doctrine\ORM\Mapping as ORM;
use Symfony\Component\Validator\Constraints as Assert;

#[ORM\Entity(repositoryClass: GalerieRepository::class)]
class Galerie
{
    #[ORM\Id]
    #[ORM\GeneratedValue]
    #[ORM\Column]
    private ?int $id = null;

    #[ORM\Column(length: 255)]
    #[Assert\NotBlank(message: "Le nom de la galerie est obligatoire")]
    #[Assert\Length(
        min: 3,
        max: 255,
        minMessage: "Le nom doit contenir au minimum {{ limit }} caractères",
        maxMessage: "Le nom ne peut pas dépasser {{ limit }} caractères"
    )]
    private ?string $nom = null;

    #[ORM\Column(length: 255)]
    #[Assert\NotBlank(message: "L'adresse est obligatoire")]
    #[Assert\Length(
        min: 5,
        max: 255,
        minMessage: "L'adresse doit contenir au minimum {{ limit }} caractères",
        maxMessage: "L'adresse ne peut pas dépasser {{ limit }} caractères"
    )]
    private ?string $adresse = null;

    #[ORM\Column(length: 255)]
    #[Assert\NotBlank(message: "La localisation est obligatoire")]
    #[Assert\Length(
        min: 3,
        max: 255,
        minMessage: "La localisation doit contenir au minimum {{ limit }} caractères",
        maxMessage: "La localisation ne peut pas dépasser {{ limit }} caractères"
    )]
    private ?string $localisation = null;

    #[ORM\Column(length: 255, nullable: true)]
    #[Assert\Length(
        max: 255,
        maxMessage: "La description ne peut pas dépasser {{ limit }} caractères"
    )]
    private ?string $description = null;

    #[ORM\Column]
    #[Assert\NotBlank(message: "La capacité maximale est obligatoire")]
    #[Assert\Positive(message: "La capacité doit être un nombre positif")]
    #[Assert\Range(
        min: 1,
        max: 5000,
        notInRangeMessage: "La capacité doit être entre {{ min }} et {{ max }}"
    )]
    private ?int $capacite_max = null;

    /**
     * @var Collection<int, Evenement>
     */
    #[ORM\OneToMany(targetEntity: Evenement::class, mappedBy: 'galerie')]
    private Collection $evenements;

    public function __construct()
    {
        $this->evenements = new ArrayCollection();
    }

    public function getId(): ?int
    {
        return $this->id;
    }

    public function getNom(): ?string
    {
        return $this->nom;
    }

    public function setNom(?string $nom): static
    {
        $this->nom = $nom;

        return $this;
    }

    public function getAdresse(): ?string
    {
        return $this->adresse;
    }

    public function setAdresse(?string $adresse): static
    {
        $this->adresse = $adresse;

        return $this;
    }

    public function getLocalisation(): ?string
    {
        return $this->localisation;
    }

    public function setLocalisation(?string $localisation): static
    {
        $this->localisation = $localisation;

        return $this;
    }

    public function getDescription(): ?string
    {
        return $this->description;
    }

    public function setDescription(?string $description): static
    {
        $this->description = $description;

        return $this;
    }

    public function getCapaciteMax(): ?int
    {
        return $this->capacite_max;
    }

    public function setCapaciteMax(int $capacite_max): static
    {
        $this->capacite_max = $capacite_max;

        return $this;
    }

    /**
     * @return Collection<int, Evenement>
     */
    public function getEvenements(): Collection
    {
        return $this->evenements;
    }

    public function addEvenement(Evenement $evenement): static
    {
        if (!$this->evenements->contains($evenement)) {
            $this->evenements->add($evenement);
            $evenement->setGalerie($this);
        }

        return $this;
    }

    public function removeEvenement(Evenement $evenement): static
    {
        if ($this->evenements->removeElement($evenement)) {
            // set the owning side to null (unless already changed)
            if ($evenement->getGalerie() === $this) {
                $evenement->setGalerie(null);
            }
        }

        return $this;
    }

    /**
     * Extract latitude from localisation field (format: "lat, lng")
     */
    public function getLatitude(): ?float
    {
        if (!$this->localisation) {
            return null;
        }

        $parts = array_map('trim', explode(',', $this->localisation));
        if (count($parts) >= 1 && is_numeric($parts[0])) {
            return (float) $parts[0];
        }

        return null;
    }

    /**
     * Extract longitude from localisation field (format: "lat, lng")
     */
    public function getLongitude(): ?float
    {
        if (!$this->localisation) {
            return null;
        }

        $parts = array_map('trim', explode(',', $this->localisation));
        if (count($parts) >= 2 && is_numeric($parts[1])) {
            return (float) $parts[1];
        }

        return null;
    }
}
