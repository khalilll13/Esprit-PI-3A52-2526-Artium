<?php

namespace App\Service;

use App\Entity\Reclamation;

/**
 * Service principal pour générer des suggestions de réponses IA
 */
class AIResponseService
{
    public function __construct(
        private HuggingFaceAIService $huggingFaceService
    ) {
    }

    /**
     * Génère une suggestion de réponse pour une réclamation
     */
    public function generateSuggestion(Reclamation $reclamation): string
    {
        $type = $reclamation->getType() ? $reclamation->getType()->value : 'générale';
        $texte = $reclamation->getTexte();
        $userName = $reclamation->getUser() ? $reclamation->getUser()->getPrenom() : 'Cher client';
        $reclamationId = $reclamation->getId() ?? 0;

        // Essayer d'obtenir une réponse de Hugging Face
        $aiResponse = $this->huggingFaceService->generateResponse($texte, $type, $userName, $reclamationId);
        
        if ($aiResponse && strlen($aiResponse) > 50) {
            return $aiResponse;
        }

        // Fallback: Générer une réponse contextuelle locale
        return $this->generateLocalResponse($texte, $type, $userName, $reclamationId);
    }

    /**
     * Génère une suggestion rapide et non bloquante pour affichage en liste.
     */
    public function generateSuggestionForList(Reclamation $reclamation): string
    {
        $type = $reclamation->getType() ? $reclamation->getType()->value : 'générale';
        $texte = $reclamation->getTexte();
        $userName = $reclamation->getUser() ? $reclamation->getUser()->getPrenom() : 'Cher client';
        $reclamationId = $reclamation->getId() ?? 0;

        return $this->generateLocalResponse($texte, $type, $userName, $reclamationId);
    }

    /**
     * Génère une réponse locale si l'API ne fonctionne pas
     */
    private function generateLocalResponse(string $texte, string $type, string $userName, int $reclamationId = 0): string
    {
        // Extraire des mots-clés du texte
        $keywords = $this->extractKeywords($texte);
        
        // Utiliser l'ID pour choisir un template unique
        $templateIndex = abs($reclamationId) % 5;
        
        return match($templateIndex) {
            0 => $this->buildResponse0($userName, $type, $keywords),
            1 => $this->buildResponse1($userName, $type, $keywords),
            2 => $this->buildResponse2($userName, $type, $keywords),
            3 => $this->buildResponse3($userName, $type, $keywords),
            default => $this->buildResponse4($userName, $type, $keywords),
        };
    }

    private function buildResponse0(string $userName, string $type, array $keywords): string
    {
        $response = "Bonjour $userName,\n\n";
        
        if (in_array('urgent', $keywords) || in_array('rapidement', $keywords)) {
            $response .= "Nous avons noté l'urgence de votre situation. ";
        }
        
        if (in_array('remboursement', $keywords) || in_array('argent', $keywords)) {
            $response .= "Concernant votre demande de remboursement, notre service comptabilité va examiner votre dossier en priorité. Vous recevrez un retour sous 48 heures maximum.\n\n";
        } elseif (in_array('problème', $keywords) || in_array('erreur', $keywords)) {
            $response .= "Nous prenons votre réclamation très au sérieux. Notre équipe technique va analyser ce problème et vous proposer une solution adaptée dans les plus brefs délais.\n\n";
        } else {
            $response .= "Nous avons bien pris en compte votre message concernant $type. Notre équipe va examiner votre situation et vous apporter une réponse personnalisée rapidement.\n\n";
        }
        
        $response .= "Nous vous tiendrons informé(e) de l'avancement de votre dossier.\n\nCordialement,\nL'équipe ARTIUM";
        return $response;
    }

    private function buildResponse1(string $userName, string $type, array $keywords): string
    {
        $response = "Cher(e) $userName,\n\n";
        $response .= "Merci de nous avoir signalé cette situation. ";
        
        if (in_array('remboursement', $keywords) || in_array('argent', $keywords)) {
            $response .= "Notre service financier va examiner votre demande de remboursement en détail et vous contactera sous 48 heures.\n\n";
        } elseif (in_array('problème', $keywords) || in_array('erreur', $keywords)) {
            $response .= "Nos équipes techniques vont l'investiguer immédiatement pour vous proposer un correctif adapté.\n\n";
        } else {
            $response .= "Nos collaborateurs examinent votre situation sur $type avec attention.\n\n";
        }
        
        if (in_array('urgent', $keywords) || in_array('rapidement', $keywords)) {
            $response .= "Étant donné le caractère urgent de votre demande, elle bénéficiera d'une prise en charge accélérée.\n\n";
        }
        
        $response .= "Nous vous recontacterons très rapidement.\nCordialement,\nL'équipe ARTIUM";
        return $response;
    }

    private function buildResponse2(string $userName, string $type, array $keywords): string
    {
        $response = "Bonjour $userName,\n\n";
        $response .= "Nous vous remercions de nous avoir signalé cette situation. ";
        
        if (in_array('remboursement', $keywords) || in_array('argent', $keywords)) {
            $response .= "Votre demande de remboursement est en cours d'analyse par notre département financier. Vous recevrez une réponse définitive dans les 2 jours ouvrables.\n\n";
        } elseif (in_array('problème', $keywords) || in_array('erreur', $keywords)) {
            $response .= "Notre équipe support va immédiatement vérifier ce problème et vous proposer une résolution dans les meilleurs délais.\n\n";
        } else {
            $response .= "Nous allons examiner votre réclamation relative à $type et vous adresser une réponse personnalisée.\n\n";
        }
        
        $response .= "Nous apprécions votre patience et restons à votre disposition.\nCordialement,\nL'équipe ARTIUM";
        return $response;
    }

    private function buildResponse3(string $userName, string $type, array $keywords): string
    {
        $response = "Cher(e) $userName,\n\n";
        
        if (in_array('urgent', $keywords) || in_array('rapidement', $keywords)) {
            $response .= "Compte tenu de l'urgence que vous exprimez, nous accélérons le traitement de votre dossier. ";
        }
        
        if (in_array('remboursement', $keywords) || in_array('argent', $keywords)) {
            $response .= "Nous avons bien enregistré votre demande de remboursement. Après vérification par nos services, vous recevrez une confirmation dans un délai de 48 heures.\n\n";
        } elseif (in_array('problème', $keywords) || in_array('erreur', $keywords)) {
            $response .= "Nous prenons votre signalement très au sérieux. Notre équipe technique procèdera à une analyse complète et vous proposera une solution appropriée rapidement.\n\n";
        } else {
            $response .= "Votre préoccupation concernant $type a été enregistrée. Nos équipes vont l'étudier attentivement et vous répondre sous peu.\n\n";
        }
        
        $response .= "Merci de votre confiance.\nCordialement,\nL'équipe ARTIUM";
        return $response;
    }

    private function buildResponse4(string $userName, string $type, array $keywords): string
    {
        $response = "Bonjour $userName,\n\n";
        $response .= "Nous avons reçu votre message et le traitons immédiatement. ";
        
        if (in_array('remboursement', $keywords) || in_array('argent', $keywords)) {
            $response .= "En ce qui concerne le remboursement, notre comptabilité va vérifier tous les éléments de votre dossier. Une décision sera communiquée dans les 48 heures suivantes.\n\n";
        } elseif (in_array('problème', $keywords) || in_array('erreur', $keywords)) {
            $response .= "Concernant le problème que vous décrivez, notre équipe technique est mobilisée pour l'étudier et vous apporter une solution.\n\n";
        } else {
            $response .= "Concernant votre réclamation sur $type, nous vous confirmons que nous l'examinons en détail.\n\n";
        }
        
        $response .= "Nous vous tiendrons informé(e) de chaque étape du processus.\nCordialement,\nL'équipe ARTIUM";
        return $response;
    }

    private function extractKeywords(string $text): array
    {
        $text = mb_strtolower($text);
        $keywords = [];
        
        $patterns = [
            'urgent', 'rapidement', 'vite', 'immédiatement',
            'remboursement', 'rembourser', 'argent', 'paiement',
            'problème', 'erreur', 'bug', 'défaut',
            'attente', 'délai', 'retard',
        ];
        
        foreach ($patterns as $pattern) {
            if (str_contains($text, $pattern)) {
                $keywords[] = $pattern;
            }
        }
        
        return $keywords;
    }
}
