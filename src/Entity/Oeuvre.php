<?php

namespace App\Entity;

use App\Enum\TypeOeuvre;
use App\Repository\OeuvreRepository;
use Doctrine\Common\Collections\ArrayCollection;
use Doctrine\Common\Collections\Collection;
use Doctrine\DBAL\Types\Types;
use Doctrine\ORM\Mapping as ORM;
use Symfony\Component\Validator\Constraints as Assert;
use Symfony\Component\Serializer\Annotation\Ignore;

#[ORM\Entity(repositoryClass: OeuvreRepository::class)]
#[ORM\InheritanceType("JOINED")]
#[ORM\DiscriminatorColumn(name: "classe", type: "string")]
#[ORM\DiscriminatorMap([
    "oeuvre" => Oeuvre::class,
    "livre" => Livre::class,
    "musique" => Musique::class
])]
class Oeuvre
{
    #[ORM\Id]
    #[ORM\GeneratedValue]
    #[ORM\Column]
    private ?int $id = null;

    #[ORM\Column(length: 255)]
    #[Assert\NotBlank(message: 'Le titre ne peut pas etre vide')]
    private ?string $titre = null;


    #[ORM\Column(type: Types::TEXT)]
    #[Assert\NotBlank(message: 'La description ne peut pas etre vide')]
    private ?string $description = null;


    #[ORM\Column(type: Types::DATE_MUTABLE)]
    #[Assert\NotNull(message: 'La date d\'ajout ne peut pas être vide', groups: ['edit'])]
    #[Assert\LessThanOrEqual('today', message: 'La date d\'ajout ne peut pas être dans le futur', groups: ['edit'])]
    private ?\DateTime $date_creation = null;

    #[ORM\Column(length: 2048, nullable: false)]
    #[Ignore]
    private ?string $image = null;

    #[ORM\Column(nullable: false)]
    private ?bool $isPublic = false;

    
    #[ORM\Column(type: 'string', enumType: TypeOeuvre::class)]
    private ?TypeOeuvre $type = null;



    #[ORM\ManyToOne(inversedBy: 'oeuvres')]
    #[ORM\JoinColumn(nullable: false)]
    #[Assert\NotNull(message: 'Veuillez choisir une collection')]
    private ?Collections $collection = null;

    /**
     * @var Collection<int, Commentaire>
     */
    #[ORM\OneToMany(targetEntity: Commentaire::class, mappedBy: 'oeuvre', cascade: ['persist', 'remove'], orphanRemoval: true)]
    #[Ignore]
    private Collection $commentaires;

    /**
     * @var Collection<int, User>
     */
    #[ORM\ManyToMany(targetEntity: User::class, inversedBy: 'fav_user')]
    #[Ignore]
    private Collection $user_fav;

    /**
     * @var Collection<int, Like>
     */
    #[ORM\OneToMany(targetEntity: Like::class, mappedBy: 'oeuvre')]
    #[Ignore]
    private Collection $likes;

    #[ORM\Column(type: 'json', nullable: true)]
    private ?array $embedding = null;

    #[ORM\Column(type: 'json', nullable: true)]
    private ?array $imageEmbedding = null;


    public function __construct()
    {
        $this->commentaires = new ArrayCollection();
        $this->user_fav = new ArrayCollection();
        $this->likes = new ArrayCollection();
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

    public function getDateCreation(): ?\DateTime
    {
        return $this->date_creation;
    }

    public function setDateCreation(?\DateTime $date_creation): static
    {
        $this->date_creation = $date_creation;

        return $this;
    }

    public function getImage(): ?string
    {
        return $this->image;
    }

    public function setImage(?string $image): static
    {
        $this->image = $image;

        return $this;
    }

    public function isIsPublic(): ?bool
    {
        return $this->isPublic;
    }

    public function setIsPublic(?bool $isPublic): static
    {
        $this->isPublic = $isPublic;

        return $this;
    }

    public function getType(): ?TypeOeuvre
    {
        return $this->type;
    }

    public function setType(TypeOeuvre $type): static
    {
        $this->type = $type;

        return $this;
    }

    public function getCollection(): ?Collections
    {
        return $this->collection;
    }

    public function setCollection(?Collections $collection): static
    {
        $this->collection = $collection;

        return $this;
    }

    /**
     * @return Collection<int, Commentaire>
     */
    #[Ignore]
    public function getCommentaires(): Collection
    {
        return $this->commentaires;
    }

    public function addCommentaire(Commentaire $commentaire): static
    {
        if (!$this->commentaires->contains($commentaire)) {
            $this->commentaires->add($commentaire);
            $commentaire->setOeuvre($this);
        }

        return $this;
    }

    public function removeCommentaire(Commentaire $commentaire): static
    {
        if ($this->commentaires->removeElement($commentaire)) {
            // set the owning side to null (unless already changed)
            if ($commentaire->getOeuvre() === $this) {
                $commentaire->setOeuvre(null);
            }
        }

        return $this;
    }

    /**
     * @return Collection<int, User>
     */
    #[Ignore]
    public function getUserFav(): Collection
    {
        return $this->user_fav;
    }

    public function addUserFav(User $userFav): static
    {
        if (!$this->user_fav->contains($userFav)) {
            $this->user_fav->add($userFav);
        }

        return $this;
    }

    public function removeUserFav(User $userFav): static
    {
        $this->user_fav->removeElement($userFav);

        return $this;
    }

    /**
     * @return Collection<int, Like>
     */
    #[Ignore]
    public function getLikes(): Collection
    {
        return $this->likes;
    }

    public function addLike(Like $like): static
    {
        if (!$this->likes->contains($like)) {
            $this->likes->add($like);
            $like->setOeuvre($this);
        }

        return $this;
    }

    public function removeLike(Like $like): static
    {
        if ($this->likes->removeElement($like)) {
            // set the owning side to null (unless already changed)
            if ($like->getOeuvre() === $this) {
                $like->setOeuvre(null);
            }
        }

        return $this;
    }

    public function isIndexableInSearch(): bool
    {
        return !($this instanceof Livre || $this instanceof Musique);
    }

    public function getEmbedding(): ?array
    {
    return $this->embedding;
    }

    public function setEmbedding(array $embedding): self
    {
    $this->embedding = $embedding;
    return $this;
    }

    public function getImageEmbedding(): ?array
    {
    return $this->imageEmbedding;
    }

    public function setImageEmbedding(?array $imageEmbedding): self
    {
    $this->imageEmbedding = $imageEmbedding;

    return $this;
    }
}
