<?php

namespace App\Entity;

use App\Enum\StatutEvenement;
use App\Enum\TypeEvenement;
use App\Repository\EvenementRepository;
use Doctrine\Common\Collections\ArrayCollection;
use Doctrine\Common\Collections\Collection;
use Doctrine\DBAL\Types\Types;
use Doctrine\ORM\Mapping as ORM;
use Symfony\Component\Validator\Constraints as Assert;

#[ORM\Entity(repositoryClass: EvenementRepository::class)]
class Evenement
{
    #[ORM\Id]
    #[ORM\GeneratedValue]
    #[ORM\Column]
    private ?int $id = null;

    #[ORM\Column(length: 255)]
    #[Assert\NotBlank(message: "Le titre de l'événement est obligatoire", groups: ['Default'])]
    #[Assert\Length(
        min: 3,
        max: 255,
        minMessage: "Le titre doit contenir au minimum {{ limit }} caractères",
        maxMessage: "Le titre ne peut pas dépasser {{ limit }} caractères",
        groups: ['Default', 'edit']
    )]
    private ?string $titre = null;

    #[ORM\Column(type: Types::TEXT)]
    #[Assert\NotBlank(message: "La description est obligatoire", groups: ['Default'])]
    #[Assert\Length(
        min: 10,
        minMessage: "La description doit contenir au minimum {{ limit }} caractères",
        groups: ['Default', 'edit']
    )]
    private ?string $description = null;

    #[ORM\Column]
    #[Assert\NotBlank(message: "La date de début est obligatoire", groups: ['Default'])]
    private ?\DateTime $date_debut = null;

    #[ORM\Column]
    #[Assert\NotBlank(message: "La date de fin est obligatoire", groups: ['Default'])]
    #[Assert\GreaterThan(
        propertyPath: "date_debut",
        message: "La date de fin doit être après la date de début",
        groups: ['Default', 'edit']
    )]
    private ?\DateTime $date_fin = null;

    #[ORM\Column(type: Types::DATE_MUTABLE)]
    private ?\DateTime $date_creation = null;

    #[ORM\Column(enumType: TypeEvenement::class)]
    #[Assert\NotBlank(message: "Le type d'événement est obligatoire", groups: ['Default'])]
    private ?TypeEvenement $type = null;

    #[ORM\Column(length: 2048, nullable: false)]
    private ?string $image_couverture = null;

    #[ORM\Column(enumType: StatutEvenement::class)]
    #[Assert\NotBlank(message: "Le statut est obligatoire", groups: ['admin'])]
    private ?StatutEvenement $statut = null;

    #[ORM\Column]
    #[Assert\NotBlank(message: "La capacité maximale est obligatoire", groups: ['Default'])]
    #[Assert\Positive(message: "La capacité doit être un nombre positif", groups: ['Default', 'edit'])]
    #[Assert\Range(
        min: 1,
        max: 100000,
        notInRangeMessage: "La capacité doit être entre {{ min }} et {{ max }}",
        groups: ['Default', 'edit']
    )]
    private ?int $capacite_max = null;

    #[ORM\ManyToOne(inversedBy: 'evenements')]
    #[ORM\JoinColumn(nullable: false)]
    #[Assert\NotBlank(message: "La galerie est obligatoire", groups: ['Default'])]
    private ?Galerie $galerie = null;

    #[ORM\ManyToOne(inversedBy: 'evenements')]
    #[ORM\JoinColumn(nullable: false)]
    #[Assert\NotBlank(message: "L'artiste est obligatoire", groups: ['admin'])]
    private ?User $artiste = null;

    #[ORM\Column]
    #[Assert\NotBlank(message: "Le prix du ticket est obligatoire", groups: ['Default'])]
    #[Assert\Positive(message: "Le prix doit être un nombre positif", groups: ['Default', 'edit'])]
    #[Assert\Range(
        min: 0.01,
        max: 10000,
        notInRangeMessage: "Le prix doit être entre {{ min }}€ et {{ max }}€",
        groups: ['Default', 'edit']
    )]
    private ?float $prix_ticket = null;

    /**
     * @var Collection<int, Ticket>
     */
    #[ORM\OneToMany(targetEntity: Ticket::class, mappedBy: 'evenement', cascade: ['persist', 'remove'], orphanRemoval: true)]
    private Collection $tickets;

    public function __construct()
    {
        $this->tickets = new ArrayCollection();
    }

    public function getId(): ?int
    {
        return $this->id;
    }

    public function getTitre(): ?string
    {
        return $this->titre;
    }

    public function setTitre(?string $titre): static
    {
        $this->titre = $titre;

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

    public function getDateDebut(): ?\DateTime
    {
        return $this->date_debut;
    }

    public function setDateDebut(?\DateTime $date_debut): static
    {
        $this->date_debut = $date_debut;

        return $this;
    }

    public function getDateFin(): ?\DateTime
    {
        return $this->date_fin;
    }

    public function setDateFin(?\DateTime $date_fin): static
    {
        $this->date_fin = $date_fin;

        return $this;
    }

    public function getDateCreation(): ?\DateTime
    {
        return $this->date_creation;
    }

    public function setDateCreation(?\DateTime $date_creation): static
    {
        $this->date_creation = $date_creation;

        return $this;
    }

    public function getType(): ?TypeEvenement
    {
        return $this->type;
    }

    public function setType(?TypeEvenement $type): static
    {
        $this->type = $type;

        return $this;
    }

    public function getImageCouverture(): ?string
    {
        return $this->image_couverture;
    }

    public function setImageCouverture(?string $image_couverture): static
    {
        $this->image_couverture = $image_couverture;

        return $this;
    }

    public function getStatut(): ?StatutEvenement
    {
        return $this->statut;
    }

    public function setStatut(?StatutEvenement $statut): static
    {
        $this->statut = $statut;

        return $this;
    }

    public function getCapaciteMax(): ?int
    {
        return $this->capacite_max;
    }

    public function setCapaciteMax(?int $capacite_max): static
    {
        $this->capacite_max = $capacite_max;

        return $this;
    }

    public function getGalerie(): ?Galerie
    {
        return $this->galerie;
    }

    public function setGalerie(?Galerie $galerie): static
    {
        $this->galerie = $galerie;

        return $this;
    }

    public function getArtiste(): ?User
    {
        return $this->artiste;
    }

    public function setArtiste(?User $artiste): static
    {
        $this->artiste = $artiste;

        return $this;
    }

    public function getPrixTicket(): ?float
    {
        return $this->prix_ticket;
    }

    public function setPrixTicket(?float $prix_ticket): static
    {
        $this->prix_ticket = $prix_ticket;

        return $this;
    }

    /**
     * @return Collection<int, Ticket>
     */
    public function getTickets(): Collection
    {
        return $this->tickets;
    }

    public function addTicket(Ticket $ticket): static
    {
        if (!$this->tickets->contains($ticket)) {
            $this->tickets->add($ticket);
            $ticket->setEvenement($this);
        }

        return $this;
    }

    public function removeTicket(Ticket $ticket): static
    {
        if ($this->tickets->removeElement($ticket)) {
            // set the owning side to null (unless already changed)
            if ($ticket->getEvenement() === $this) {
                $ticket->setEvenement(null);
            }
        }

        return $this;
    }
}
