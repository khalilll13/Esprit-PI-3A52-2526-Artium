<?php

namespace App\Controller;

use App\Entity\User;
use App\Repository\UserRepository;
use App\Enum\Role;
use Dompdf\Dompdf;
use Dompdf\Options;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Attribute\Route;
use Twig\Environment;

class ExportUserController extends AbstractController
{
    #[Route('/user/export/{type}', name: 'app_user_export_pdf', requirements: ['type' => 'artistes|amateurs'])]
    public function exportPdf(string $type, UserRepository $userRepository, Environment $twig): Response
    {
        $role = $type === 'artistes' ? Role::ARTISTE : Role::AMATEUR;
        $users = $userRepository->findBy(['role' => $role]);

        $html = $twig->render('user/export_pdf.html.twig', [
            'users' => $users,
            'type' => $type
        ]);

        $options = new Options();
        $options->set('defaultFont', 'DejaVu Sans');
        $dompdf = new Dompdf($options);
        $dompdf->loadHtml($html);
        $dompdf->setPaper('A4', 'landscape');
        $dompdf->render();

        $filename = 'liste_' . $type . '_' . date('Ymd_His') . '.pdf';
        return new Response(
            $dompdf->output(),
            200,
            [
                'Content-Type' => 'application/pdf',
                'Content-Disposition' => 'attachment; filename="' . $filename . '"'
            ]
        );
    }
}
