<?php

namespace App\Controller;

use App\Entity\Galerie;
use App\Form\GalerieAddType;
use App\Form\GalerieEditType;
use App\Repository\GalerieRepository;
use Doctrine\ORM\EntityManagerInterface;
use Doctrine\ORM\Tools\Pagination\Paginator;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\Form\FormFactoryInterface;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Attribute\Route;

/**
 * GalerieController
 * 
 * Ce contrôleur gère tous les opérations CRUD (Créer, Lire, Modifier, Supprimer) pour les galeries.
 * Il utilise un système d'offcanvas (panneaux latéraux) pour l'ajout et la modification des données
 * sans quitter la page liste principale. La pagination est implémentée pour afficher 10 galeries par page.
 */
#[Route('/galerie')]
final class GalerieController extends AbstractController
{
    /**
     * INDEX - Affiche la liste des galeries avec formulaires d'ajout et d'édition
     * 
     * BLOC 1 : RÉCUPÉRATION DE LA PAGE ET PAGINATION
     *   - Récupère le numéro de page depuis l'URL (paramètre ?page=X), défaut = page 1
     *   - Définit le nombre d'éléments par page à 10
     */
    #[Route(name: 'galeries', methods: ['GET', 'POST'])]
    public function index(GalerieRepository $galerieRepository, FormFactoryInterface $formFactory, Request $request, EntityManagerInterface $entityManager): Response
    {
        $page = max(1, $request->query->getInt('page', 1));
        $limit = 10;
        $filterField = (string) $request->query->get('filter_field', 'nom');
        $filterValue = trim((string) $request->query->get('filter_value', ''));
        $sort = (string) $request->query->get('sort', 'id_desc');
        $allowedFilterFields = ['id', 'nom', 'capacite'];
        if (!in_array($filterField, $allowedFilterFields, true)) {
            $filterField = 'nom';
        }

        $allowedSorts = [
            'id_asc' => ['g.id', 'ASC'],
            'id_desc' => ['g.id', 'DESC'],
            'nom_asc' => ['g.nom', 'ASC'],
            'nom_desc' => ['g.nom', 'DESC'],
            'capacite_asc' => ['g.capaciteMax', 'ASC'],
            'capacite_desc' => ['g.capaciteMax', 'DESC'],
        ];
        if (!array_key_exists($sort, $allowedSorts)) {
            $sort = 'id_desc';
        }

        $filterParams = array_filter([
            'filter_field' => $filterField,
            'filter_value' => $filterValue,
            'sort' => $sort,
        ], static fn ($value) => $value !== '');
        
        /**
         * BLOC 2 : REQUÊTE PAGINÉE À LA BASE DE DONNÉES
         *   - Crée une requête qui récupère les galeries par ordre décroissant (derniers en premier)
         *   - Applique l'offset (SKIP) et le LIMIT pour la pagination
         *   - Exemple : page 2 skip 10 items, affiche 10 items suivants (11-20)
         */
        $queryBuilder = $galerieRepository->createQueryBuilder('g');

        [$sortField, $sortDirection] = $allowedSorts[$sort];
        $queryBuilder->orderBy($sortField, $sortDirection);

        if ($filterValue !== '') {
            if ($filterField === 'id') {
                $queryBuilder
                    ->andWhere('g.id = :filterId')
                    ->setParameter('filterId', (int) $filterValue);
            } elseif ($filterField === 'capacite') {
                $queryBuilder
                    ->andWhere('g.capaciteMax = :filterCapacite')
                    ->setParameter('filterCapacite', (int) $filterValue);
            } else {
                $queryBuilder
                    ->andWhere('LOWER(g.nom) LIKE :filterNom')
                    ->setParameter('filterNom', '%' . mb_strtolower($filterValue) . '%');
            }
        }
        $query = $queryBuilder->getQuery()
            ->setFirstResult(($page - 1) * $limit)
            ->setMaxResults($limit);
        
        /**
         * BLOC 3 : CALCUL DU NOMBRE TOTAL DE PAGES
         *   - Utilise Doctrine Paginator pour compter le total d'éléments
         *   - Calcule le nombre de pages (ex: 25 total items / 10 par page = 3 pages)
         *   - Convertit le paginator en tableau PHP pour itération
         */
        $paginator = new Paginator($query, true);
        $total = count($paginator);
        $totalPages = (int) ceil($total / $limit);
        $galeries = iterator_to_array($paginator->getIterator());

        /**
         * BLOC 4 : CRÉATION DU FORMULAIRE D'AJOUT (Offcanvas Add)
         *   - Crée une nouvelle galerie vierge
         *   - Construit le formulaire nommé 'galerie_new' avec l'action pointant à la liste
         *   - L'action inclut la page actuelle (?page=X) pour rester sur la même page après submit
         */
        $newGalerie = new Galerie();
        $newForm = $formFactory->createNamed('galerie_new', GalerieAddType::class, $newGalerie, [
            'action' => $this->generateUrl('galeries', array_merge($filterParams, ['page' => $page])),
            'method' => 'POST',
        ]);
        
        /**
         * BLOC 5 : TRAITEMENT DU FORMULAIRE D'AJOUT
         *   - handleRequest() : vérifie si une soumission POST correspond à ce formulaire
         *   - Si formulaire soumis ET valide :
         *       → Persiste la nouvelle galerie en mémoire
         *       → Flush : enregistre en base de données
         *       → Redirige vers la liste (gardant la page actuelle)
         */
        $newForm->handleRequest($request);
        $showAddForm = false; // Flag pour afficher l'offcanvas d'ajout en cas d'erreur

        if ($newForm->isSubmitted() && $newForm->isValid()) {
            $entityManager->persist($newGalerie);
            $entityManager->flush();

            return $this->redirectToRoute('galeries', [], Response::HTTP_SEE_OTHER);
        }
        
        // Si le formulaire est soumis mais contient des erreurs, on garde l'offcanvas ouvert
        if ($newForm->isSubmitted() && !$newForm->isValid()) {
            $showAddForm = true;
        }

        /**
         * BLOC 6 : CRÉATION DES FORMULAIRES D'ÉDITION (Offcanvas Edit)
         *   - Boucle sur chaque galerie récupérée
         *   - Pour chaque galerie : crée un formulaire nommé 'galerie_edit_ID' pré-rempli avec ses données
         *   - L'action pointe à la liste avec la page actuelle pour rester sur la même page
         *   - Traite les soumissions : si valide, persiste les changements et redirige
         *   - Stocke chaque formulaire vue pour utilisation en template
         */
        $editForms = [];
        $showEditForms = []; // Flags pour afficher les offcanvas d'édition en cas d'erreur
        foreach ($galeries as $galerie) {
            $formName = 'galerie_edit_' . $galerie->getId();
            $editForm = $formFactory->createNamed(
                $formName,
                GalerieEditType::class,
                $galerie,
                [
                    'action' => $this->generateUrl('galeries', array_merge($filterParams, ['page' => $page])),
                    'method' => 'POST',
                ]
            );
            $editForm->handleRequest($request);

            // Vérifier si ce formulaire spécifique a été soumis
            $isFormSubmitted = $request->isMethod('POST') && $request->request->has($formName);

            if ($isFormSubmitted && $editForm->isValid()) {
                $entityManager->flush();

                return $this->redirectToRoute('galeries', [], Response::HTTP_SEE_OTHER);
            }
            
            // Si le formulaire est soumis mais contient des erreurs, on garde l'offcanvas ouvert
            if ($isFormSubmitted && !$editForm->isValid()) {
                $showEditForms[$galerie->getId()] = true;
            }

            $editForms[$galerie->getId()] = $editForm->createView();
        }

        /**
         * BLOC 7 : RENDU DU TEMPLATE
         *   - Passe la liste paginée des galeries
         *   - Passe le formulaire d'ajout
         *   - Passe les formulaires d'édition (un par galerie)
         *   - Passe les info de pagination (page actuelle, nombre total de pages)
         *   - Le template utilise ces données pour afficher la table et les formulaires
         */
        return $this->render('galerie/galeries.html.twig', [
            'galeries' => $galeries,
            'new_form' => $newForm->createView(),
            'edit_forms' => $editForms,
            'show_add_form' => $showAddForm,
            'show_edit_forms' => $showEditForms,
            'current_page' => $page,
            'total_pages' => $totalPages,
            'filter_field' => $filterField,
            'filter_value' => $filterValue,
            'sort' => $sort,
            'filter_params' => $filterParams,
        ]);
    }

    /**
     * NEW - Route legacy (non utilisée actuellement)
     * Conservée pour compatibilité, n'est pas appelée par les offcanvas
     */
    #[Route('/new', name: 'app_galerie_new', methods: ['GET', 'POST'])]
    public function new(Request $request, EntityManagerInterface $entityManager): Response
    {
        $galerie = new Galerie();
        $form = $this->createForm(GalerieType::class, $galerie);
        $form->handleRequest($request);

        if ($form->isSubmitted() && $form->isValid()) {
            $entityManager->persist($galerie);
            $entityManager->flush();

            return $this->redirectToRoute('galeries', [], Response::HTTP_SEE_OTHER);
        }

        return $this->render('galerie/new.html.twig', [
            'galerie' => $galerie,
            'form' => $form,
        ]);
    }

    /**
     * SHOW - Affiche les détails d'une galerie
     */
    #[Route('/{id}', name: 'app_galerie_show', methods: ['GET'])]
    public function show(Galerie $galerie): Response
    {
        return $this->render('galerie/show.html.twig', [
            'galerie' => $galerie,
        ]);
    }

    /**
     * EDIT - Route legacy (non utilisée actuellement)
     * Conservée pour compatibilité, l'édition se fait via l'offcanvas sur la liste
     */
    #[Route('/{id}/edit', name: 'app_galerie_edit', methods: ['GET', 'POST'])]
    public function edit(Request $request, Galerie $galerie, EntityManagerInterface $entityManager): Response
    {
        $form = $this->createForm(GalerieType::class, $galerie);
        $form->handleRequest($request);

        if ($form->isSubmitted() && $form->isValid()) {
            $entityManager->flush();

            return $this->redirectToRoute('galeries', [], Response::HTTP_SEE_OTHER);
        }

        return $this->render('galerie/edit.html.twig', [
            'galerie' => $galerie,
            'form' => $form,
        ]);
    }

    /**
     * DELETE - Supprime une galerie
     * 
     * BLOC 1 : RÉCUPÉRATION DE LA PAGE ACTUELLE
     *   - Récupère le paramètre page de l'URL pour rester sur la même page après suppression
     */
    #[Route('/{id}', name: 'app_galerie_delete', methods: ['POST'])]
    public function delete(Request $request, Galerie $galerie, EntityManagerInterface $entityManager): Response
    {
        $page = max(1, $request->query->getInt('page', 1));

        /**
         * BLOC 2 : VÉRIFICATION ET SUPPRESSION
         *   - Valide le token CSRF pour la sécurité (anti-attaque forgery)
         *   - Si token valide :
         *       → Remove : marque la galerie pour suppression en mémoire
         *       → Flush : supprime effectivement de la base de données
         */
        if ($this->isCsrfTokenValid('delete'.$galerie->getId(), $request->getPayload()->getString('_token'))) {
            $entityManager->remove($galerie);
            $entityManager->flush();
        }

        /**
         * BLOC 3 : REDIRECTION
         *   - Redirige vers la liste en conservant le numéro de page actuelle
         *   - HTTP 303 (SEE_OTHER) : redirige en GET (POST->GET) après suppression
         */
        return $this->redirectToRoute('galeries', ['page' => $page], Response::HTTP_SEE_OTHER);
    }
}

/**
 * ═══════════════════════════════════════════════════════════════════════════════════════════
 * LOGIQUE COMPLÈTE DU SYSTÈME DE GESTION DES GALERIES (GalerieController)
 * ═══════════════════════════════════════════════════════════════════════════════════════════
 * 
 * 1. PAGE D'AFFICHAGE (Route / - GET/POST)
 *    ├─ Récupère le numéro de page (?page=X)
 *    ├─ Pagine la requête BD : 10 galeries par page, trié par ID décroissant
 *    ├─ Calcule le nombre total de pages
 *    │
 *    ├─ FORMULAIRE D'AJOUT (POST)
 *    │  ├─ Si nouveau submit détecté + valide
 *    │  ├─ Persiste la nouvelle galerie
 *    │  ├─ Flush en BD
 *    │  └─ Redirige vers /galerie?page=X (conservation page)
 *    │
 *    ├─ FORMULAIRES D'ÉDITION (POST) - Un par galerie affichée
 *    │  ├─ Pour chaque galerie
 *    │  ├─ Crée un formulaire pré-rempli galerie_edit_ID
 *    │  ├─ Si submit détecté + valide
 *    │  ├─ Flush les changements en BD (pas de persist, objet déjà géré)
 *    │  └─ Redirige vers /galerie?page=X (conservation page)
 *    │
 *    └─ RENDU TEMPLATE avec :
 *       ├─ Tableau HTML avec toutes les galeries de la page
 *       ├─ Offcanvas "Ajouter" contenant le formulaire new_form
 *       ├─ Offcanvas "Modifier" par galerie (edit_forms[galerie.id])
 *       └─ Pagination dynamique : affiche les boutons page 1,2,3... etc
 *          (Pagination hidden si < 2 pages)
 *
 * 2. ACTION DE SUPPRESSION (Route /{id} - POST)
 *    ├─ Récupère la galerie par ID (ParamConverter auto)
 *    ├─ Récupère le numéro de page pour redirection
 *    ├─ Valide le token CSRF (formulaire de suppression)
 *    ├─ Si valide :
 *    │  ├─ Remove la galerie
 *    │  ├─ Flush en BD
 *    │  └─ Redirige vers /galerie?page=X (conservation page)
 *    └─ Si invalide : redirige quand même (sécurité)
 *
 * 3. FLUX DE DONNÉES
 *    ├─ BD → Repository → QueryBuilder (paginated)
 *    │   ↓
 *    ├─ Doctrine Paginator (calcul total + slice)
 *    │   ↓
 *    ├─ GalerieController (crée formulaires)
 *    │   ↓
 *    ├─ Template Twig (affiche tableau + formulaires + pagination)
 *    │   ↓
 *    ├─ Utilisateur (ajoute/modifie/supprime via formulaires/boutons)
 *    │   ↓
 *    ├─ POST soumis → handleRequest() valide & persiste
 *    │   ↓
 *    ├─ EntityManager→flush() enregistre en BD
 *    │   ↓
 *    └─ Redirect vers la même route/page (stay on same page)
 *
 * 4. SÉCURITÉ
 *    ├─ CSRF Token : requis pour suppression (protection anti-CSRF)
 *    ├─ FormFactoryInterface : génère des noms de formulaires uniques (galerie_new, galerie_edit_1, etc)
 *    ├─ ParamConverter : Symfony injecte la Galerie objet directement (sinon 404)
 *    └─ AbstractController : héritage qui fournit generateUrl, redirectToRoute, etc.
 *
 * 5. PAGINATION
 *    ├─ 10 items par page (const $limit = 10)
 *    ├─ Offset = (page - 1) × limit
 *       Exemple : page 2 → offset 10 (skip 10), prend 10 suivants (11-20)
 *    ├─ Utilise Doctrine Paginator (optimisé, pas COUNT séparé)
 *    ├─ Template affiche boutons page uniquement si total_pages > 1
 *    ├─ Boutons page conservent ?page=X dans les redirects
 *    └─ Previous/Next desactivés aux limites (page 1 ou dernière page)
 *
 * ═══════════════════════════════════════════════════════════════════════════════════════════
 */
