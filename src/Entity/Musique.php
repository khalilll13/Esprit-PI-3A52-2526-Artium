<?php

namespace App\Entity;

use App\Enum\GenreMusique;
use App\Repository\MusiqueRepository;
use Doctrine\Common\Collections\ArrayCollection;
use Doctrine\Common\Collections\Collection;
use Doctrine\ORM\Mapping as ORM;
use Symfony\Component\Serializer\Annotation\Ignore;
use Symfony\Component\HttpFoundation\File\File;
use Symfony\Component\Validator\Constraints as Assert;
use Vich\UploaderBundle\Mapping\Annotation as Vich;

#[ORM\Entity(repositoryClass: MusiqueRepository::class)]
#[Vich\Uploadable]
class Musique extends Oeuvre
{
    #[ORM\Column(enumType: GenreMusique::class)]
    #[Assert\NotBlank(message: 'Genre is required')]
    private ?GenreMusique $genre = null;

    #[ORM\Column(length: 255, nullable: true)]
    #[Ignore]
    private ?string $audio = null;

    #[Vich\UploadableField(mapping: 'music_audio', fileNameProperty: 'audio')]
    private ?File $audioFile = null;

    #[ORM\Column(nullable: true)]
    private ?\DateTimeImmutable $updatedAt = null;

    /**
     * @var Collection<int, Playlist>
     */
    #[ORM\ManyToMany(targetEntity: Playlist::class, mappedBy: 'musique')]
    #[Ignore]
    private Collection $playlists;

    public function __construct()
    {
        parent::__construct();
        $this->playlists = new ArrayCollection();
    }

    public function getGenre(): ?GenreMusique
    {
        return $this->genre;
    }

    public function setGenre(GenreMusique $genre): static
    {
        $this->genre = $genre;

        return $this;
    }

    public function getAudio(): ?string
    {
        return $this->audio;
    }

    public function setAudio(?string $audio): static
    {
        $this->audio = $audio;

        return $this;
    }

    public function setAudioFile(?File $audioFile = null): void
    {
        $this->audioFile = $audioFile;

        if ($audioFile !== null) {
            $this->updatedAt = new \DateTimeImmutable();
        }
    }

    public function getAudioFile(): ?File
    {
        return $this->audioFile;
    }

    public function getUpdatedAt(): ?\DateTimeImmutable
    {
        return $this->updatedAt;
    }

    public function setUpdatedAt(?\DateTimeImmutable $updatedAt): static
    {
        $this->updatedAt = $updatedAt;

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
            $playlist->addMusique($this);
        }

        return $this;
    }

    public function removePlaylist(Playlist $playlist): static
    {
        if ($this->playlists->removeElement($playlist)) {
            $playlist->removeMusique($this);
        }

        return $this;
    }
}
