<?php

namespace App\Controller;

use App\Entity\Evenement;
use App\Entity\User;
use App\Form\EvenementArtisteType;
use App\Form\EvenementArtisteEditType;
use App\Repository\EvenementRepository;
use App\Repository\TicketRepository;
use App\Repository\UserRepository;
use App\Repository\ReclamationRepository;
use App\Repository\CommentaireRepository;
use App\Repository\LikeRepository;
use App\Enum\StatutEvenement;
use App\Service\FileStorageService;
use App\Service\OllamaEstimateService;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\Form\FormFactoryInterface;
use Symfony\Component\Form\FormInterface;
use Symfony\Component\HttpFoundation\File\UploadedFile;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Attribute\Route;

final class EventsartisteController extends AbstractController
{
    #[Route('/artiste-evenements', name: 'app_eventsartiste', methods: ['GET', 'POST'])]
    public function index(
        EvenementRepository $evenementRepository,
        TicketRepository $ticketRepository,
        UserRepository $userRepository,
        FormFactoryInterface $formFactory,
        Request $request,
        EntityManagerInterface $entityManager,
        FileStorageService $fileStorageService,
        ReclamationRepository $reclamationRepository,
        CommentaireRepository $commentaireRepository,
        LikeRepository $likeRepository
    ): Response
    {
        $artiste = $this->getArtisteOrDeny($userRepository);

        $evenements = $evenementRepository->findBy(
            ['artiste' => $this->getUser()],
            ['date_debut' => 'DESC']
        );

        // Calculate statistics for sidebar
        $nbOeuvres = 0;
        if (method_exists($artiste, 'getCollections')) {
            foreach ($artiste->getCollections() as $collection) {
                if (method_exists($collection, 'getOeuvres')) {
                    $nbOeuvres += $collection->getOeuvres()->count();
                }
            }
        }
        $nbReclamations = $artiste ? count($reclamationRepository->findByUserFilters($artiste, null, null, null)) : 0;
        $nbEvenements = $artiste ? $evenementRepository->count(['artiste' => $artiste]) : 0;
        $nbCommentaires = $artiste ? $commentaireRepository->countByArtist($artiste) : 0;
        $nbLikes = $artiste ? $likeRepository->countByArtist($artiste) : 0;
        $newEvenement = new Evenement();
        $newForm = $formFactory->createNamed('evenement_new', EvenementArtisteType::class, $newEvenement, [
            'action' => $this->generateUrl('app_eventsartiste'),
            'method' => 'POST',
        ]);
        $newForm->handleRequest($request);

        $showAddForm = false;
        if ($newForm->isSubmitted()) {
            $this->handleImageUpload($newForm, $newEvenement, $fileStorageService);
            
            if ($newForm->isValid()) {
                $newEvenement->setArtiste($this->getUser());
                $newEvenement->setDateCreation(new \DateTime());
                $newEvenement->setStatut($this->resolveStatut($newEvenement));

                $entityManager->persist($newEvenement);
                $entityManager->flush();

                return $this->redirectToRoute('app_eventsartiste');
            }

            $errors = [];
            foreach ($newForm->getErrors(true) as $error) {
                $errors[] = $error->getMessage();
            }
            $errors = array_values(array_unique(array_filter($errors)));
            $this->addFlash('error', 'Création impossible: '.($errors !== [] ? implode(' ', $errors) : 'vérifiez les champs du formulaire.'));
            
            $showAddForm = true;
        }

        $editForms = [];
        $showEditForms = [];
        $evenementRows = [];

        foreach ($evenements as $evenement) {
            if ($evenement->getArtiste()?->getId() !== $artiste->getId()) {
                continue;
            }

            $formName = 'evenement_edit_' . $evenement->getId();
            $editForm = $formFactory->createNamed($formName, EvenementArtisteEditType::class, $evenement, [
                'action' => $this->generateUrl('app_eventsartiste'),
                'method' => 'POST',
            ]);
            $editForm->handleRequest($request);

            $isFormSubmitted = $request->isMethod('POST') && $request->request->has($formName);
            if ($isFormSubmitted) {
                $this->handleImageUpload($editForm, $evenement, $fileStorageService);
                
                if ($editForm->isValid()) {
                    $evenement->setStatut($this->resolveStatut($evenement));
                    $entityManager->flush();

                    return $this->redirectToRoute('app_eventsartiste');
                }

                $showEditForms[$evenement->getId()] = true;
            }

            $editForms[$evenement->getId()] = $editForm->createView();
            $evenementRows[] = [
                'evenement' => $evenement,
                'image' => $this->getImageDataUri($evenement->getImageCouverture()),
                'tickets_sold' => $ticketRepository->count(['evenement' => $evenement]),
            ];
        }

        return $this->render('Front Office/eventsartiste/eventartiste.html.twig', [
            'evenement_rows' => $evenementRows,
            'new_form' => $newForm->createView(),
            'edit_forms' => $editForms,
            'show_add_form' => $showAddForm,
            'show_edit_forms' => $showEditForms,
            'nbOeuvres' => $nbOeuvres,
            'nbReclamations' => $nbReclamations,
            'nbEvenements' => $nbEvenements,
            'nbCommentaires' => $nbCommentaires,
            'nbLikes' => $nbLikes,
        ]);
    }

    #[Route('/artiste-evenements/{id}', name: 'app_eventsartiste_delete', methods: ['POST'], requirements: ['id' => '\\d+'])]
    public function delete(Request $request, Evenement $evenement, EntityManagerInterface $entityManager, UserRepository $userRepository): Response
    {
        $artiste = $this->getArtisteOrDeny($userRepository);
        if ($evenement->getArtiste()?->getId() !== $artiste->getId()) {
            throw $this->createAccessDeniedException();
        }

        if ($this->isCsrfTokenValid('delete_event_' . $evenement->getId(), $request->request->get('_token'))) {
            $entityManager->remove($evenement);
            $entityManager->flush();
        }

        return $this->redirectToRoute('app_eventsartiste');
    }

    #[Route('/artiste-evenements/{id}/cancel', name: 'app_eventsartiste_cancel', methods: ['POST'], requirements: ['id' => '\\d+'])]
    public function cancel(
        Request $request,
        Evenement $evenement,
        EntityManagerInterface $entityManager,
        UserRepository $userRepository
    ): Response
    {
        $artiste = $this->getArtisteOrDeny($userRepository);
        if ($evenement->getArtiste()?->getId() !== $artiste->getId()) {
            throw $this->createAccessDeniedException();
        }

        if ($this->isCsrfTokenValid('cancel_event_' . $evenement->getId(), $request->request->get('_token'))
            && $evenement->getStatut() === StatutEvenement::A_VENIR
        ) {
            $evenement->setStatut(StatutEvenement::ANNULE);
            $entityManager->flush();
        }

        return $this->redirectToRoute('app_eventsartiste');
    }

    #[Route('/artiste-evenements/estimate-tickets', name: 'app_eventsartiste_estimate', methods: ['POST'])]
    public function estimateTickets(
        Request $request,
        OllamaEstimateService $estimateService,
        UserRepository $userRepository
    ): JsonResponse {
        $this->getArtisteOrDeny($userRepository);

        $payload = json_decode($request->getContent(), true);
        if (!is_array($payload)) {
            return new JsonResponse(['ok' => false, 'message' => 'Invalid payload'], 400);
        }

        $estimate = $estimateService->estimateTickets($payload);
        if ($estimate === null) {
            return new JsonResponse(['ok' => false, 'message' => 'Ollama unavailable'], 200);
        }

        return new JsonResponse([
            'ok' => true,
            'estimate' => $estimate['estimate'],
            'confidence' => $estimate['confidence'],
        ]);
    }

    private function getArtisteOrDeny(UserRepository $userRepository): User
    {
        $user = $this->getUser();
        if (!$user instanceof User) {
            $fallback = $userRepository->find(1);
            if ($fallback instanceof User) {
                return $fallback;
            }

            throw $this->createAccessDeniedException();
        }

        return $user;
    }

    private function handleImageUpload(FormInterface $form, Evenement $evenement, FileStorageService $fileStorageService): void
    {
        $file = $form->get('imageFile')->getData();
        if ($file instanceof UploadedFile) {
            $newFilename = $fileStorageService->uploadImage($file, 'event_');
            $evenement->setImageCouverture($fileStorageService->getImageUrl($newFilename));
        }
    }

    private function resolveStatut(Evenement $evenement): StatutEvenement
    {
        $now = new \DateTimeImmutable();
        $dateFin = $evenement->getDateFin();

        if ($dateFin instanceof \DateTime && $dateFin < $now) {
            return StatutEvenement::TERMINE;
        }

        return StatutEvenement::A_VENIR;
    }

    private function getImageDataUri(mixed $image): ?string
    {
        if ($image === null) {
            return null;
        }

        if (is_resource($image)) {
            $data = stream_get_contents($image);
            if ($data === false || $data === '') {
                return null;
            }
            return 'data:image/jpeg;base64,' . base64_encode($data);
        }

        if (is_string($image)) {
            if (str_starts_with($image, 'data:')) {
                return $image;
            }
            if (preg_match('#^https?://#i', $image)) {
                return $image;
            }
            if (str_starts_with($image, 'img/')) {
                return 'http://127.0.0.1/' . ltrim($image, '/');
            }
            if (str_starts_with($image, '/')) {
                return $image;
            }
            // If only a filename is stored, treat it as XAMPP img storage
            if (!str_contains($image, '/')) {
                return 'http://127.0.0.1/img/' . ltrim($image, '/');
            }
            return '/' . ltrim($image, '/');
        }

        return null;
    }

}
