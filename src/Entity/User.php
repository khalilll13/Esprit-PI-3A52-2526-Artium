<?php

namespace App\Entity;

use App\Enum\CentreInteret;
use App\Enum\Role;
use App\Enum\Specialite;
use App\Enum\Statut;
use App\Repository\UserRepository;
use Doctrine\Common\Collections\ArrayCollection;
use Doctrine\Common\Collections\Collection;
use Doctrine\DBAL\Types\Types;
use Doctrine\ORM\Mapping as ORM;
use Symfony\Component\Serializer\Annotation\Ignore;
use Symfony\Component\Security\Core\User\PasswordAuthenticatedUserInterface;
use Symfony\Component\Security\Core\User\UserInterface;
use Symfony\Component\Validator\Constraints as Assert;
use Symfony\Bridge\Doctrine\Validator\Constraints\UniqueEntity;
use SensitiveParameter;

#[ORM\Entity(repositoryClass: UserRepository::class)]
#[UniqueEntity(
    fields: ['email'],
    message: 'Cet email est déjà utilisé par un autre utilisateur'
)]
class User implements PasswordAuthenticatedUserInterface, UserInterface
{
    /**
     * Nom du fichier de la photo de profil
     */
    #[ORM\Column(type: 'string', length: 255, nullable: true)]
    private ?string $photoProfil = null;

    public function getPhotoProfil(): ?string
    {
        return $this->photoProfil;
    }

    public function getPhotoProfilUrl(): ?string
    {
        if (!$this->photoProfil) {
            return null;
        }

        if (filter_var($this->photoProfil, FILTER_VALIDATE_URL)) {
            return $this->photoProfil;
        }

        if (str_starts_with($this->photoProfil, '/')) {
            return $this->photoProfil;
        }

        if (str_starts_with($this->photoProfil, 'uploads/')) {
            return '/' . $this->photoProfil;
        }

        return '/uploads/' . $this->photoProfil;
    }

    public function setPhotoProfil(?string $photoProfil): self
    {
        $this->photoProfil = $photoProfil;
        return $this;
    }
    /**
     * Jeton de réinitialisation du mot de passe
     */
    #[ORM\Column(type: 'string', length: 255, nullable: true)]
    #[Ignore]
    private ?string $resetToken = null;

    /**
     * Date d'expiration du jeton de réinitialisation
     */
    #[ORM\Column(type: 'datetime', nullable: true)]
    #[Ignore]
    private ?\DateTimeInterface $resetTokenExpires = null;

    public function getResetToken(): ?string
    {
        return $this->resetToken;
    }

    public function setResetToken(#[SensitiveParameter] ?string $resetToken): self
    {
        $this->resetToken = $resetToken;
        return $this;
    }

    public function getResetTokenExpires(): ?\DateTimeInterface
    {
        return $this->resetTokenExpires;
    }

    public function setResetTokenExpires(#[SensitiveParameter] ?\DateTimeInterface $resetTokenExpires): self
    {
        $this->resetTokenExpires = $resetTokenExpires;
        return $this;
    }
    #[ORM\Id]
    #[ORM\GeneratedValue]
    #[ORM\Column]
    private ?int $id = null;

    #[ORM\Column(length: 255)]
    #[Assert\NotBlank(message: 'Le nom est obligatoire')]
    #[Assert\Length(
        min: 2,
        max: 255,
        minMessage: 'Le nom doit contenir au moins {{ limit }} caracteres',
        maxMessage: 'Le nom ne peut pas depasser {{ limit }} caracteres'
    )]
    #[Assert\Regex(
        pattern: '/^[a-zA-ZÀ-ÿ\s\'-]+$/u',
        message: 'Le nom ne doit contenir que des lettres, espaces, apostrophes et tirets'
    )]
    private ?string $nom = null;

    #[ORM\Column(length: 255)]
    #[Assert\NotBlank(message: 'Le prenom est obligatoire')]
    #[Assert\Length(
        min: 2,
        max: 255,
        minMessage: 'Le prenom doit contenir au moins {{ limit }} caracteres',
        maxMessage: 'Le prenom ne peut pas depasser {{ limit }} caracteres'
    )]
    #[Assert\Regex(
        pattern: '/^[a-zA-ZÀ-ÿ\s\'-]+$/u',
        message: 'Le prenom ne doit contenir que des lettres, espaces, apostrophes et tirets'
    )]
    private ?string $prenom = null;

    #[ORM\Column(type: Types::DATE_MUTABLE)]
    #[Assert\NotNull(message: 'La date de naissance ne peut pas etre vide')]
    #[Assert\LessThan(
        value: 'today',
        message: 'La date de naissance doit etre dans le passe'
    )]
    #[Assert\GreaterThan(
        value: '-120 years',
        message: 'La date de naissance n\'est pas valide'
    )]
    private ?\DateTime $date_naissance = null;

    #[ORM\Column(length: 255)]
    #[Assert\NotBlank(message: "L'email est obligatoire")]
    #[Assert\Email(message: "L'email n'est pas valide")]
    #[Assert\Length(max: 255, maxMessage: "L'email ne peut pas depasser {{ limit }} caracteres")]
    private ?string $email = null;

    #[ORM\Column(length: 255)]
    private ?string $mdp = null;

    #[Assert\NotBlank(message: 'Le mot de passe est obligatoire', groups: ['create'])]
    #[Assert\Length(
        min: 6,
        minMessage: 'Le mot de passe doit contenir au moins {{ limit }} caracteres',
        groups: ['create']
    )]
    private ?string $plainPassword = null;

    #[ORM\Column(enumType: Role::class)]
    #[Assert\NotNull(message: 'Le role est obligatoire')]
    private ?Role $role = null;

    #[ORM\Column(enumType: Statut::class)]
    #[Assert\NotNull(message: 'Le statut est obligatoire')]
    private ?Statut $statut = null;

    #[ORM\Column(type: Types::DATE_MUTABLE)]
    private ?\DateTime $date_inscription = null;

    #[ORM\Column(length: 255)]
    #[Assert\NotBlank(message: 'Le numero de telephone ne peut pas etre vide')]
    #[Assert\Regex(
        pattern: '/^[2459]\d{7}$/',
        message: 'Le numero de telephone doit contenir 8 chiffres et commencer par 2, 4, 5 ou 9'
    )]
    #[Assert\Length(
        min: 8,
        max: 8,
        exactMessage: 'Le numero de telephone doit contenir exactement {{ limit }} chiffres'
    )]
    
    private ?string $num_tel = null;

    #[ORM\Column(length: 255)]
    #[Assert\NotBlank(message: 'La ville ne peut pas etre vide')]
    #[Assert\Length(
        min: 2,
        max: 255,
        minMessage: 'La ville doit contenir au moins {{ limit }} caracteres',
        maxMessage: 'La ville ne peut pas depasser {{ limit }} caracteres'
    )]
    
    private ?string $ville = null;

    #[ORM\Column(type: Types::TEXT, nullable: true)]
    #[Assert\Length(
        max: 1000,
        maxMessage: 'La biographie ne peut pas depasser {{ limit }} caracteres'
    )]
    
    private ?string $biographie = null;

    #[ORM\Column(nullable: true, enumType: Specialite::class)]
    #[Assert\Choice(callback: [Specialite::class, 'cases'], message: 'La specialite selectionnee est invalide')]
    
    private ?Specialite $specialite = null;

    #[ORM\Column(type: Types::SIMPLE_ARRAY, nullable: true, enumType: CentreInteret::class)]
    #[Assert\All([
        new Assert\Choice(callback: [CentreInteret::class, 'cases'], message: 'Un centre d\'interet selectionne est invalide')
    ])]
    
    private ?array $centre_interet = null;

    /**
     * Chemin de la photo de référence pour la reconnaissance faciale
     */
    #[ORM\Column(type: 'string', length: 255, nullable: true)]
    private ?string $photoReferencePath = null;

    public function getPhotoReferencePath(): ?string
    {
        return $this->photoReferencePath;
    }

    public function setPhotoReferencePath(?string $photoReferencePath): self
    {
        $this->photoReferencePath = $photoReferencePath;
        return $this;
    }

    public static function getSpecialiteChoices(): array
    {
        return array_map(static fn (Specialite $specialite) => $specialite->value, Specialite::cases());
    }

    public static function getCentreInteretChoices(): array
    {
        return array_map(static fn (CentreInteret $centreInteret) => $centreInteret->value, CentreInteret::cases());
    }

    /**
     * @var Collection<int, Collections>
     */
    #[ORM\OneToMany(targetEntity: Collections::class, mappedBy: 'artiste', cascade: ['persist', 'remove'], orphanRemoval: true)]
    #[Ignore]
    private Collection $collections;

    /**
     * @var Collection<int, Commentaire>
     */
    #[ORM\OneToMany(targetEntity: Commentaire::class, mappedBy: 'user', cascade: ['persist', 'remove'], orphanRemoval: true)]
    #[Ignore]
    private Collection $commentaires;

    /**
     * @var Collection<int, Playlist>
     */
    #[ORM\OneToMany(targetEntity: Playlist::class, mappedBy: 'user', cascade: ['persist', 'remove'], orphanRemoval: true)]
    #[Ignore]
    private Collection $playlists;

    /**
     * @var Collection<int, Oeuvre>
     */
    #[ORM\ManyToMany(targetEntity: Oeuvre::class, mappedBy: 'user_fav')]
    #[Ignore]
    private Collection $fav_user;

    /**
     * @var Collection<int, Reclamation>
     */
    #[ORM\OneToMany(targetEntity: Reclamation::class, mappedBy: 'user', cascade: ['persist', 'remove'], orphanRemoval: true)]
    #[Ignore]
    private Collection $reclamations;

    /**
     * @var Collection<int, Reponse>
     */
    #[ORM\OneToMany(targetEntity: Reponse::class, mappedBy: 'user_admin', cascade: ['persist', 'remove'], orphanRemoval: true)]
    #[Ignore]
    private Collection $reponses;

    /**
     * @var Collection<int, Evenement>
     */
    #[ORM\OneToMany(targetEntity: Evenement::class, mappedBy: 'artiste', cascade: ['persist', 'remove'], orphanRemoval: true)]
    #[Ignore]
    private Collection $evenements;

    /**
     * @var Collection<int, Ticket>
     */
    #[ORM\OneToMany(targetEntity: Ticket::class, mappedBy: 'user', cascade: ['persist', 'remove'], orphanRemoval: true)]
    #[Ignore]
    private Collection $tickets;

    /**
     * @var Collection<int, LocationLivre>
     */
    #[ORM\OneToMany(targetEntity: LocationLivre::class, mappedBy: 'user', cascade: ['persist', 'remove'], orphanRemoval: true)]
    #[Ignore]
    private Collection $locationLivres;

    /**
     * @var Collection<int, Like>
     */
    #[ORM\OneToMany(targetEntity: Like::class, mappedBy: 'user', cascade: ['persist', 'remove'], orphanRemoval: true)]
    #[Ignore]
    private Collection $likes;

    public function __construct()
    {
        $this->collections = new ArrayCollection();
        $this->commentaires = new ArrayCollection();
        $this->playlists = new ArrayCollection();
        $this->fav_user = new ArrayCollection();
        $this->reclamations = new ArrayCollection();
        $this->reponses = new ArrayCollection();
        $this->evenements = new ArrayCollection();
        $this->tickets = new ArrayCollection();
        $this->locationLivres = new ArrayCollection();
        $this->likes = new ArrayCollection();
        $this->statut = Statut::ACTIVE;
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

    public function getPrenom(): ?string
    {
        return $this->prenom;
    }

    public function setPrenom(?string $prenom): static
    {
        $this->prenom = $prenom;

        return $this;
    }

    public function getDateNaissance(): ?\DateTime
    {
        return $this->date_naissance;
    }

    public function setDateNaissance(?\DateTime $date_naissance): static
    {
        $this->date_naissance = $date_naissance;

        return $this;
    }

    /**
     * Calcule l'âge de l'utilisateur
     */
    public function getAge(): ?int
    {
        if ($this->date_naissance === null) {
            return null;
        }

        $now = new \DateTime();
        $interval = $this->date_naissance->diff($now);
        
        return $interval->y;
    }

    public function getEmail(): ?string
    {
        return $this->email;
    }

    public function setEmail(?string $email): static
    {
        $this->email = $email;

        return $this;
    }

    public function getMdp(): ?string
    {
        return $this->mdp;
    }

    public function setMdp(string $mdp): static
    {
        $this->mdp = $mdp;

        return $this;
    }

    public function getPlainPassword(): ?string
    {
        return $this->plainPassword;
    }

    public function setPlainPassword(?string $plainPassword): static
    {
        $this->plainPassword = $plainPassword;

        return $this;
    }

    public function getRole(): ?Role
    {
        return $this->role;
    }

    public function setRole(?Role $role): static
    {
        $this->role = $role;

        return $this;
    }

    public function getStatut(): ?Statut
    {
        return $this->statut;
    }

    public function setStatut(?Statut $statut): static
    {
        $this->statut = $statut;

        return $this;
    }

    public function getDateInscription(): ?\DateTime
    {
        return $this->date_inscription;
    }

    public function setDateInscription(\DateTime $date_inscription): static
    {
        $this->date_inscription = $date_inscription;

        return $this;
    }

    public function getNumTel(): ?string
    {
        return $this->num_tel;
    }

    public function setNumTel(?string $num_tel): static
    {
        $this->num_tel = $num_tel;

        return $this;
    }

    public function getVille(): ?string
    {
        return $this->ville;
    }

    public function setVille(?string $ville): static
    {
        $this->ville = $ville;

        return $this;
    }

    public function getBiographie(): ?string
    {
        return $this->biographie;
    }

    public function setBiographie(?string $biographie): static
    {
        $this->biographie = $biographie;

        return $this;
    }

    public function getSpecialite(): ?Specialite
    {
        return $this->specialite;
    }

    public function setSpecialite(?Specialite $specialite): static
    {
        $this->specialite = $specialite;

        return $this;
    }

    /**
     * @return CentreInteret[]|null
     */
    public function getCentreInteret(): ?array
    {
        return $this->centre_interet;
    }

    public function setCentreInteret(?array $centre_interet): static
    {
        $this->centre_interet = $centre_interet;

        return $this;
    }

    /**
     * @return Collection<int, Collections>
     */
    #[Ignore]
    public function getCollections(): Collection
    {
        return $this->collections;
    }

    public function addCollection(Collections $collection): static
    {
        if (!$this->collections->contains($collection)) {
            $this->collections->add($collection);
            $collection->setArtiste($this);
        }

        return $this;
    }

    public function removeCollection(Collections $collection): static
    {
        if ($this->collections->removeElement($collection)) {
            if ($collection->getArtiste() === $this) {
                $collection->setArtiste(null);
            }
        }

        return $this;
    }

    /**
     * @return Collection<int, Commentaire>
     */
    public function getCommentaires(): Collection
    {
        return $this->commentaires;
    }

    public function addCommentaire(Commentaire $commentaire): static
    {
        if (!$this->commentaires->contains($commentaire)) {
            $this->commentaires->add($commentaire);
            $commentaire->setUser($this);
        }

        return $this;
    }

    public function removeCommentaire(Commentaire $commentaire): static
    {
        if ($this->commentaires->removeElement($commentaire)) {
            if ($commentaire->getUser() === $this) {
                $commentaire->setUser(null);
            }
        }

        return $this;
    }

    /**
     * @return Collection<int, Playlist>
     */
    public function getPlaylists(): Collection
    {
        return $this->playlists;
    }

    public function addPlaylist(Playlist $playlist): static
    {
        if (!$this->playlists->contains($playlist)) {
            $this->playlists->add($playlist);
            $playlist->setUser($this);
        }

        return $this;
    }

    public function removePlaylist(Playlist $playlist): static
    {
        if ($this->playlists->removeElement($playlist)) {
            if ($playlist->getUser() === $this) {
                $playlist->setUser(null);
            }
        }

        return $this;
    }

    /**
     * @return Collection<int, Oeuvre>
     */
    #[Ignore]
    public function getFavUser(): Collection
    {
        return $this->fav_user;
    }

    public function addFavUser(Oeuvre $favUser): static
    {
        if (!$this->fav_user->contains($favUser)) {
            $this->fav_user->add($favUser);
            $favUser->addUserFav($this);
        }

        return $this;
    }

    public function removeFavUser(Oeuvre $favUser): static
    {
        if ($this->fav_user->removeElement($favUser)) {
            $favUser->removeUserFav($this);
        }

        return $this;
    }

    /**
     * @return Collection<int, Reclamation>
     */
    public function getReclamations(): Collection
    {
        return $this->reclamations;
    }

    public function addReclamation(Reclamation $reclamation): static
    {
        if (!$this->reclamations->contains($reclamation)) {
            $this->reclamations->add($reclamation);
            $reclamation->setUser($this);
        }

        return $this;
    }

    public function removeReclamation(Reclamation $reclamation): static
    {
        if ($this->reclamations->removeElement($reclamation)) {
            if ($reclamation->getUser() === $this) {
                $reclamation->setUser(null);
            }
        }

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
            $reponse->setUserAdmin($this);
        }

        return $this;
    }

    public function removeReponse(Reponse $reponse): static
    {
        if ($this->reponses->removeElement($reponse)) {
            if ($reponse->getUserAdmin() === $this) {
                $reponse->setUserAdmin(null);
            }
        }

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
            $evenement->setArtiste($this);
        }

        return $this;
    }

    public function removeEvenement(Evenement $evenement): static
    {
        if ($this->evenements->removeElement($evenement)) {
            if ($evenement->getArtiste() === $this) {
                $evenement->setArtiste(null);
            }
        }

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
            $ticket->setUser($this);
        }

        return $this;
    }

    public function removeTicket(Ticket $ticket): static
    {
        if ($this->tickets->removeElement($ticket)) {
            if ($ticket->getUser() === $this) {
                $ticket->setUser(null);
            }
        }

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
            $locationLivre->setUser($this);
        }

        return $this;
    }

    public function removeLocationLivre(LocationLivre $locationLivre): static
    {
        if ($this->locationLivres->removeElement($locationLivre)) {
            if ($locationLivre->getUser() === $this) {
                $locationLivre->setUser(null);
            }
        }

        return $this;
    }

    /**
     * @return Collection<int, Like>
     */
    public function getLikes(): Collection
    {
        return $this->likes;
    }

    public function addLike(Like $like): static
    {
        if (!$this->likes->contains($like)) {
            $this->likes->add($like);
            $like->setUser($this);
        }

        return $this;
    }

    public function removeLike(Like $like): static
    {
        if ($this->likes->removeElement($like)) {
            if ($like->getUser() === $this) {
                $like->setUser(null);
            }
        }

        return $this;
    }

    public function getPassword(): string
    {
        return $this->mdp ?? '';
    }

    /**
     * Identifiant unique pour l'authentification
     */
    public function getUserIdentifier(): string
    {
        return (string) $this->email;
    }

    /**
     * Rôles de l'utilisateur
     */
    public function getRoles(): array
    {
        $roles = ['ROLE_USER'];

        if ($this->role === Role::ADMIN) {
            $roles[] = 'ROLE_ADMIN';
        } elseif ($this->role === Role::ARTISTE) {
            $roles[] = 'ROLE_ARTISTE';
        } elseif ($this->role === Role::AMATEUR) {
            $roles[] = 'ROLE_AMATEUR';
        }

        return array_unique($roles);
    }

    public function eraseCredentials(): void
    {
        $this->plainPassword = null;
    }
}