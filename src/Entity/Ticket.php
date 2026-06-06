<?php

namespace App\Entity;

use App\Repository\TicketRepository;
use Doctrine\DBAL\Types\Types;
use Doctrine\ORM\Mapping as ORM;

#[ORM\Entity(repositoryClass: TicketRepository::class)]
class Ticket
{
    #[ORM\Id]
    #[ORM\GeneratedValue]
    #[ORM\Column]
    private ?int $id = null;

    #[ORM\Column(length: 255, unique: true)]
    private ?string $code_qr = null;

    #[ORM\Column(type: Types::DATE_MUTABLE)]
    private ?\DateTime $date_achat = null;

    #[ORM\Column(nullable: true)]
    private ?bool $isUsed = false;

    #[ORM\Column(nullable: true)]
    private ?\DateTime $usedAt = null;

    #[ORM\Column(nullable: true)]
    private ?int $scanCount = 0;

    #[ORM\ManyToOne(inversedBy: 'tickets')]
    #[ORM\JoinColumn(nullable: false)]
    private ?Evenement $evenement = null;

    #[ORM\ManyToOne(inversedBy: 'tickets')]
    #[ORM\JoinColumn(nullable: false)]
    private ?User $user = null;

    public function getId(): ?int
    {
        return $this->id;
    }

    public function getCodeQr(): string
    {
        return $this->code_qr;
    }

    public function setCodeQr(string $code_qr): static
    {
        $this->code_qr = $code_qr;

        return $this;
    }

    public function getDateAchat(): ?\DateTime
    {
        return $this->date_achat;
    }

    public function setDateAchat(\DateTime $date_achat): static
    {
        $this->date_achat = $date_achat;

        return $this;
    }

    public function isIsUsed(): ?bool
    {
        return $this->isUsed;
    }
    public function setIsUsed(?bool $isUsed): static
    {
        $this->isUsed = $isUsed;

        return $this;
    }
    public function getUsedAt(): ?\DateTime
    {
        return $this->usedAt;
    }
    public function setUsedAt(?\DateTime $usedAt): static
    {
        $this->usedAt = $usedAt;

        return $this;
    }
    public function getScanCount(): ?int
    {
        return $this->scanCount;
    }
    public function setScanCount(?int $scanCount): static
    {
        $this->scanCount = $scanCount;

        return $this;
    }

    public function getEvenement(): ?Evenement
    {
        return $this->evenement;
    }

    public function setEvenement(?Evenement $evenement): static
    {
        $this->evenement = $evenement;

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
}
