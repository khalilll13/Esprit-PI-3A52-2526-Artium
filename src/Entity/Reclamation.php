<?php

namespace App\Entity;

use App\Enum\StatutReclamation;
use App\Enum\TypeReclamation;
use App\Repository\ReclamationRepository;
use Doctrine\Common\Collections\ArrayCollection;
use Doctrine\Common\Collections\Collection;
use Doctrine\DBAL\Types\Types;
use Doctrine\ORM\Mapping as ORM;
use Symfony\Component\Validator\Constraints as Assert;
use Vich\UploaderBundle\Mapping\Annotation as Vich;
use Symfony\Component\HttpFoundation\File\File;

#[ORM\Entity(repositoryClass: ReclamationRepository::class)]
#[Vich\Uploadable]
class Reclamation
{
    #[ORM\Id]
    #[ORM\GeneratedValue]
    #[ORM\Column]
    private ?int $id = null;

    #[ORM\ManyToOne(inversedBy: 'reclamations')]
    #[ORM\JoinColumn(nullable: false)]
    private ?User $user = null;

    #[ORM\Column(type: Types::TEXT)]
    #[Assert\NotBlank(message: "Le texte de la reclamation est obligatoire", groups: ['Default', 'edit'])]
    #[Assert\Length(
        min: 10,
        max: 2000,
        minMessage: "Le texte doit contenir au minimum {{ limit }} caracteres",
        maxMessage: "Le texte ne peut pas depasser {{ limit }} caracteres",
        groups: ['Default', 'edit']
    )]
    private ?string $texte = null;

    #[ORM\Column(type: Types::DATE_MUTABLE)]
    private ?\DateTime $date_creation = null;

    #[ORM\Column(enumType: StatutReclamation::class)]
    private ?StatutReclamation $statut = null;

    #[ORM\Column(enumType: TypeReclamation::class)]
    #[Assert\NotBlank(message: "Le type de reclamation est obligatoire", groups: ['Default', 'edit'])]
    private ?TypeReclamation $type = null;

    /**
     * @var Collection<int, Reponse>
     */
    #[ORM\OneToMany(targetEntity: Reponse::class, mappedBy: 'reclamation', cascade: ['persist', 'remove'], orphanRemoval: true)]
    private Collection $reponses;

    // Champ pour stocker le nom du fichier en base
    #[ORM\Column(name: 'file_name', type: Types::STRING, length: 255, nullable: true)]
    private ?string $fileName = null;

    // Champ pour manipuler le fichier (non persistant)
    #[Vich\UploadableField(mapping: 'reclamation_file', fileNameProperty: 'fileName')]
    private ?File $file = null;

    #[ORM\Column(name: 'updated_at', type: Types::DATETIME_MUTABLE)]
    private ?\DateTimeInterface $updatedAt = null;

    #[ORM\Column(nullable: false)]
    private ?bool $isArchived = false;

    public function __construct()
    {
        $this->reponses = new ArrayCollection();
        $this->updatedAt = new \DateTime();
    }

    public function getId(): ?int
    {
        return $this->id;
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

    public function getTexte(): ?string
    {
        return $this->texte;
    }

    public function setTexte(string $texte): static
    {
        $this->texte = $texte;

        return $this;
    }

    public function getDateCreation(): ?\DateTime
    {
        return $this->date_creation;
    }

    public function setDateCreation(\DateTime $date_creation): static
    {
        $this->date_creation = $date_creation;

        return $this;
    }

    public function getStatut(): ?StatutReclamation
    {
        return $this->statut;
    }

    public function setStatut(StatutReclamation $statut): static
    {
        $this->statut = $statut;

        return $this;
    }

    public function getType(): ?TypeReclamation
    {
        return $this->type;
    }

    public function setType(TypeReclamation $type): static
    {
        $this->type = $type;

        return $this;
    }

    /**
     * @return Collection<int, Reponse>
     */
    public function getReponses(): Collection
    {
        return $this->reponses;
    }

    public function addReponse(Reponse $reponse): static
    {
        if (!$this->reponses->contains($reponse)) {
            $this->reponses->add($reponse);
            $reponse->setReclamation($this);
        }

        return $this;
    }

    public function removeReponse(Reponse $reponse): static
    {
        if ($this->reponses->removeElement($reponse)) {
            // set the owning side to null (unless already changed)
            if ($reponse->getReclamation() === $this) {
                $reponse->setReclamation(null);
            }
        }

        return $this;
    }

    public function setFile(?File $file = null): void
    {
        $this->file = $file;

        if ($file) {
            $this->updatedAt = new \DateTime();
        }
    }

    public function getFile(): ?File
    {
        return $this->file;
    }

    public function getFileName(): ?string
    {
        return $this->fileName;
    }

    public function setFileName(?string $fileName): self
    {
        $this->fileName = $fileName;
        return $this;
    }

    public function getUpdatedAt(): ?\DateTimeInterface
    {
        return $this->updatedAt;
    }

    public function setUpdatedAt(\DateTimeInterface $updatedAt): self
    {
        $this->updatedAt = $updatedAt;
        return $this;
    }

    public function isIsArchived(): ?bool
    {
        return $this->isArchived;
    }

    public function setIsArchived(?bool $isArchived): self
    {
        $this->isArchived = $isArchived;

        return $this;
    }
}
