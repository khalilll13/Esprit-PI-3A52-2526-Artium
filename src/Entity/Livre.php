<?php

namespace App\Entity;

use App\Repository\LivreRepository;
use Doctrine\Common\Collections\ArrayCollection;
use Doctrine\Common\Collections\Collection;
use Doctrine\ORM\Mapping as ORM;
use Symfony\Component\Validator\Constraints as Assert;
use Symfony\Component\Serializer\Annotation\Ignore;

#[ORM\Entity(repositoryClass: LivreRepository::class)]
class Livre extends Oeuvre
{
    #[ORM\Column(length: 255)]
    #[Assert\NotBlank(message: "La catégorie est obligatoire.")]
    #[Assert\Length(
    min: 2,
    max: 255,
    minMessage: "La catégorie doit contenir au moins {{ limit }} caractères.",
    maxMessage: "La catégorie ne peut pas dépasser {{ limit }} caractères."
    )]
    private ?string $categorie = null;

    #[ORM\Column]
    #[Assert\NotBlank(message: "Le prix est obligatoire.")]
    #[Assert\Type(
    type: "numeric",
    message: "Le prix doit être un nombre."
    )]
    #[Assert\Positive(message: "Le prix doit être supérieur à 0.")]
    private ?float $prix_location = null;

    #[ORM\Column(length: 255)]
    #[Ignore]
    private ?string $fichier_pdf = null;

    /**
     * @var Collection<int, LocationLivre>
     */
    #[ORM\OneToMany(targetEntity: LocationLivre::class, mappedBy: 'livre')]
    #[Ignore]
    private Collection $locationLivres;

    public function __construct()
    {
        parent::__construct();
        $this->locationLivres = new ArrayCollection();
    }

    public function getCategorie(): ?string
    {
        return $this->categorie;
    }

    public function setCategorie(string $categorie): static
    {
        $this->categorie = $categorie;

        return $this;
    }

    public function getPrixLocation(): ?float
    {
        return $this->prix_location;
    }

    public function setPrixLocation(float $prix_location): static
    {
        $this->prix_location = $prix_location;

        return $this;
    }

    public function getFichierPdf(): ?string
    {
        return $this->fichier_pdf;
    }

    public function setFichierPdf(string $fichier_pdf): static
    {
        $this->fichier_pdf = $fichier_pdf;

        return $this;
    }

    /**
     * @return Collection<int, LocationLivre>
     */
    public function getLocationLivres(): Collection
    {
        return $this->locationLivres;
    }

    public function addLocationLivre(LocationLivre $locationLivre): static
    {
        if (!$this->locationLivres->contains($locationLivre)) {
            $this->locationLivres->add($locationLivre);
            $locationLivre->setLivre($this);
        }

        return $this;
    }

    public function removeLocationLivre(LocationLivre $locationLivre): static
    {
        if ($this->locationLivres->removeElement($locationLivre)) {
            // set the owning side to null (unless already changed)
            if ($locationLivre->getLivre() === $this) {
                $locationLivre->setLivre(null);
            }
        }

        return $this;
    }
}
