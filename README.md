# HireSync — Plateforme Intelligente de Recrutement

> Projet de Fin d'Études — Cycle Ingénieur GSI — 2025/2026  
> **Réalisé par : Othmane Sadiky**

---

## Table des Matières

1. [Vue d'ensemble](#1-vue-densemble)
2. [Stack technologique](#2-stack-technologique)
3. [Architecture du projet](#3-architecture-du-projet)
4. [Fonctionnalités implémentées](#4-fonctionnalités-implémentées)
   - [4.1 Authentification JWT](#41-authentification-jwt)
   - [4.2 Upload & Analyse ATS du CV](#42-upload--analyse-ats-du-cv)
   - [4.3 Optimisation IA (OpenRouter + Gemma 4)](#43-optimisation-ia-openrouter--gemma-4)
   - [4.4 Pipeline RabbitMQ (traitement asynchrone)](#44-pipeline-rabbitmq-traitement-asynchrone)
   - [4.5 Notifications WebSocket (STOMP)](#45-notifications-websocket-stomp)
   - [4.6 Consultation de l'historique des optimisations](#46-consultation-de-lhistorique-des-optimisations)
   - [4.7 CV Studio — Template Designer & Export PDF](#47-cv-studio--template-designer--export-pdf)
   - [4.8 Frontend Angular — 10 pages](#48-frontend-angular--10-pages)
5. [Lancer le projet](#5-lancer-le-projet)
6. [Endpoints API](#6-endpoints-api)
7. [Variables de configuration](#7-variables-de-configuration)
8. [Structure des dossiers](#8-structure-des-dossiers)

---

## 1. Vue d'ensemble

HireSync est une plateforme web qui aide les candidats marocains à :

| Étape | Ce que fait HireSync |
|-------|----------------------|
| 1 | Uploader leur CV (PDF/Word) |
| 2 | Obtenir un **score ATS automatique** calculé par analyse du texte |
| 3 | Lancer une **optimisation IA** du CV pour une offre ciblée |
| 4 | Recevoir les **suggestions de l'IA** (Gemma 4 31B via OpenRouter) en ~15 secondes |
| 5 | Suivre ses **candidatures** dans un tableau Kanban |
| 6 | Recevoir des **notifications en temps réel** via WebSocket |

---

## 2. Stack technologique

### Backend (`/backend`)
| Couche | Technologie | Rôle |
|--------|-------------|------|
| Framework | **Spring Boot 3.5** (Java 21) | API REST, sécurité, logique métier |
| Sécurité | **Spring Security 6 + JJWT 0.12** | Authentification stateless JWT |
| Base de données | **PostgreSQL 16** (Docker) | Persistance utilisateurs, CVs, optimisations |
| Message broker | **RabbitMQ 3.13** (Docker) | File d'attente pour l'optimisation IA asynchrone |
| WebSocket | **Spring WebSocket + STOMP** | Push temps réel vers Angular |
| IA / LLM | **OpenRouter API** → `google/gemma-4-31b-it:free` | Optimisation du CV par l'IA |
| PDF parsing | **Apache PDFBox 3.0** | Extraction du texte depuis les CVs PDF |
| HTTP Client | **Spring WebFlux WebClient** | Appels HTTP vers OpenRouter |

### Frontend (`/hiresync`)
| Couche | Technologie | Rôle |
|--------|-------------|------|
| Framework | **Angular 19** (standalone components) | SPA moderne avec lazy loading |
| UI Components | **Angular Material 19** | Composants UI (formulaires, dialogs, snackbars) |
| CSS | **Tailwind CSS 3** | Utilitaires de style |
| État | **Angular Signals** | Gestion réactive de l'état |
| WebSocket | **STOMP.js + SockJS** | Connexion WebSocket côté Angular |
| HTTP | **Angular HttpClient** | Appels REST avec intercepteur JWT automatique |

### Infrastructure
| Service | Image Docker | Port |
|---------|-------------|------|
| PostgreSQL | `postgres:16-alpine` | 5432 |
| RabbitMQ | `rabbitmq:3.13-management-alpine` | 5672 (AMQP), 15672 (UI admin) |

---

## 3. Architecture du projet

```
HireSync/
├── backend/                    ← Spring Boot 3.5 (Java 21)
│   ├── src/main/java/ma/hiresync/
│   │   ├── auth/               ← Authentification JWT
│   │   ├── cv/                 ← Logique CV (upload, analyse, optimisation)
│   │   │   ├── controller/     ← Endpoints REST
│   │   │   ├── service/        ← Logique métier (AtsScorer, OpenRouterService, CvService)
│   │   │   ├── messaging/      ← RabbitMQ Producer + Consumer
│   │   │   ├── entity/         ← Entités JPA (Cv, CvOptimization)
│   │   │   ├── repository/     ← Spring Data JPA
│   │   │   └── dto/            ← Objets de transfert (request/response)
│   │   ├── notification/       ← WebSocket push (SimpMessagingTemplate)
│   │   └── config/             ← SecurityConfig, WebSocketConfig, RabbitMQConfig
│   ├── docker-compose.yml      ← PostgreSQL + RabbitMQ
│   └── src/main/resources/
│       └── application.yml     ← Configuration (DB, RabbitMQ, JWT, OpenRouter)
│
└── hiresync/                   ← Angular 19 SPA
    └── src/app/
        ├── core/
        │   ├── auth/           ← AuthService, JwtAuthFilter, authGuard, authInterceptor
        │   ├── models/         ← Interfaces TypeScript (User, Job, CV, Application)
        │   └── services/       ← Services Angular (CvService, JobService, etc.)
        ├── shared/
        │   └── components/     ← Sidebar, Topbar, StatusBadge, JobSelectorDialog
        ├── features/
        │   ├── landing/        ← Page d'accueil publique
        │   ├── auth/           ← Login + Register
        │   ├── dashboard/      ← Tableau de bord candidat
        │   ├── jobs/           ← Recherche d'offres + détail offre
        │   ├── cv/             ← CV Manager + CV Optimizer
        │   ├── applications/   ← Kanban des candidatures
        │   └── notifications/  ← Centre de notifications
        └── layout/             ← App Shell (sidebar + topbar)
```

---

## 4. Fonctionnalités implémentées

### 4.1 Authentification JWT

**Ce qui a été ajouté :**
- `User.java` — entité JPA avec `email`, `password` (bcrypt), `fullName`, `role`
- `JwtService.java` — génère et valide des tokens JWT signés HMAC-SHA384, 24h de validité
- `JwtAuthFilter.java` — intercepte chaque requête HTTP, extrait et valide le Bearer token
- `SecurityConfig.java` — configure Spring Security : stateless, routes publiques (`/api/auth/**`, `/ws/**`), CORS pour Angular
- `AuthController.java` — expose `POST /api/auth/register` et `POST /api/auth/login`

**Comment ça marche :**
```
Angular               Spring Boot            PostgreSQL
  │                       │                      │
  ├─ POST /api/auth/login ┤                      │
  │  {email, password}    │                      │
  │                       ├─ charger user ───────►│
  │                       ├─ bcrypt compare       │
  │                       ├─ générer JWT          │
  │◄── {token, userId} ───┤                      │
  │                       │                      │
  │  (stocké dans localStorage hs_token)         │
  │                       │                      │
  ├─ GET /api/cv/versions ┤                      │
  │  Authorization: Bearer <token>               │
  │                       ├─ JwtAuthFilter valide│
  │◄── [ liste des CVs ] ─┤                      │
```

**Côté Angular :**
- `authInterceptor` injecte automatiquement le token dans chaque requête HTTP
- Si le backend renvoie 401/403, l'intercepteur appelle `auth.logout()` et redirige vers `/login`
- `authGuard` protège toutes les routes après `/dashboard`

---

### 4.2 Upload & Analyse ATS du CV

**Ce qui a été ajouté :**
- `CvController.java` — `POST /api/cv/upload` (multipart/form-data)
- `AtsScorer.java` — extraction PDF + calcul du score ATS (0–100)
- `Cv.java` — entité JPA stockant `fileName`, `filePath`, `atsScore`, `extractedText`, `active`

**Comment ça marche — extraction PDF :**

```java
// AtsScorer.java — Apache PDFBox 3.x
public String extractText(MultipartFile file) throws IOException {
    try (PDDocument doc = Loader.loadPDF(file.getBytes())) {
        return new PDFTextStripper().getText(doc);
    }
}
```

Apache PDFBox lit le PDF, analyse chaque page et retourne le texte brut.

**Comment ça marche — calcul ATS :**

Le score (0–100) est calculé selon 5 critères :

| Critère | Points max | Logique |
|---------|-----------|---------|
| Sections CV présentes | 30 pts | détecte "expérience", "compétences", "formation", "contact"… |
| Verbes d'action | 20 pts | "développé", "géré", "créé", "implémenté", "optimisé"… |
| Mots-clés tech | 30 pts | "java", "angular", "docker", "sql", "git", "api"… |
| Longueur adéquate | 10 pts | idéal : 300–1500 mots |
| Coordonnées | 10 pts | présence d'un email + téléphone |

**Exemple avec votre CV réel :**
> PDFBox extrait le texte → détecte "FORMATION", "EXPERIENCE PROFESSIONNELLE", "COMPÉTENCES", "spring boot", "angular", "docker", "git"… → score calculé sur les critères → **affiché sur la jauge circulaire SVG** dans l'UI

**Le texte extrait est aussi parsé en sections :**
```java
// Détecte les titres de section (ligne courte en majuscules)
// Exemple : "COMPÉTENCES", "FORMATION", "EXPÉRIENCE PROFESSIONNELLE"
// → affiché dans la carte CV sous forme de tuiles
```

---

### 4.3 Optimisation IA (OpenRouter + Gemma 4)

**Ce qui a été ajouté :**
- `OpenRouterService.java` — appelle l'API OpenRouter via Spring WebClient
- Modèle principal : `google/gemma-4-31b-it:free`
- Fallback automatique : LLaMA 3.3 70B → Qwen3 → Nemotron 120B

**Comment ça marche :**

```
Angular                  Spring Boot              OpenRouter API
   │                         │                         │
   ├─ POST /api/cv/optimize ─┤                         │
   │  {cvId, jobId, jobTitle, jobDescription}           │
   │                         │                         │
   │                    Crée CvOptimization             │
   │                    (status=QUEUED)                 │
   │                    flush() vers DB                 │
   │                    afterCommit() →                 │
   │                    → publie RabbitMQ               │
   │◄── {optimizationId, status:"queued"} ──            │
   │                                                    │
   │  (Angular navigue vers /cv/optimize/:id)           │
   │  (polls GET /api/cv/optimize/:id toutes les 3s)    │
```

**Le prompt envoyé à Gemma :**
```
Analyze this CV against the job description and return a JSON array of improvements.
Each element must have:
- "type": keyword_added | section_rewritten | format_improved | skill_added
- "description": what to change (in French)
- "before": original text (optional)
- "after": improved text (optional)
Return maximum 6 suggestions.
```

**Chaîne de fallback :**
```java
// OpenRouterService.java
List<String> models = ["google/gemma-4-31b-it:free",
                       "meta-llama/llama-3.3-70b-instruct:free",
                       "qwen/qwen3-next-80b-a3b-instruct:free"]
for (model in models) {
    try { return callModel(model, prompt); }
    catch (RateLimitException) { continue; }  // essayer le suivant
}
```

**Résultat réel observé :**
> Score ATS : **55% → 78%** (+23 pts) en **12 secondes**  
> Suggestions : "Ajouter Kubernetes, Terraform, AWS", "Réécrire titre → Ingénieur DevOps/Full Stack", "Ajouter CI/CD Jenkins"…

---

### 4.4 Pipeline RabbitMQ (traitement asynchrone)

**Ce qui a été ajouté :**
- `RabbitMQConfig.java` — déclare l'exchange `cv.exchange`, la queue `cv.optimize.queue`, et une dead-letter queue `cv.optimize.dlq`
- `OptimizationProducer.java` — publie un message JSON après commit de transaction
- `OptimizationConsumer.java` — écoute la queue, appelle OpenRouter, met à jour la DB, pousse le WebSocket

**Pourquoi asynchrone ?**  
L'appel à OpenRouter prend 10–20 secondes. Si c'était synchrone, l'utilisateur attendrait avec une requête HTTP bloquée. Avec RabbitMQ :

```
Requête HTTP    →  répond immédiatement  (status: "queued")
                                              │
                                    RabbitMQ queue
                                              │
                                    Consumer (thread séparé)
                                         │
                                    OpenRouter API (~15s)
                                         │
                                    PostgreSQL (sauvegarde)
                                         │
                                    WebSocket push → Angular
```

**Fix important — race condition :**  
Sans précaution, RabbitMQ peut recevoir le message AVANT que PostgreSQL committe la transaction (le consumer ne trouve pas le record). Fix : publier le message dans `TransactionSynchronization.afterCommit()` — le message n'est envoyé à RabbitMQ qu'après que le `COMMIT SQL` soit confirmé.

**Dead Letter Queue :**  
Si le consumer échoue 3 fois (quota OpenRouter épuisé, timeout…), le message est automatiquement routé vers `cv.optimize.dlq` pour inspection manuelle (visible dans l'UI RabbitMQ : `http://localhost:15672`).

---

### 4.5 Notifications WebSocket (STOMP)

**Ce qui a été ajouté :**
- `WebSocketConfig.java` — configure Spring WebSocket avec STOMP, endpoint `/ws/notifications`, SockJS
- `NotificationService.java` — pousse des événements via `SimpMessagingTemplate.convertAndSendToUser()`
- `WebSocketService.ts` — service Angular qui connecte via SockJS + STOMP, souscrit aux topics

**Comment ça marche :**

```
Spring Boot (NotificationService)          Angular (WebSocketService)
      │                                           │
      │  convertAndSendToUser(                    │
      │    userId,                                │
      │    "/topic/cv-optimization",              │
      │    {optimizationId, status, message}      │
      │  )                                        │
      │                                           │
      │──── STOMP frame ─────────────────────────►│
                                                  │
                               Angular reçoit l'événement
                               si status === "completed"
                                  → arrête le polling
                                  → affiche le résultat
```

**Topics utilisés :**
| Topic | Émis quand |
|-------|-----------|
| `/user/topic/cv-optimization` | Optimisation en cours, terminée ou échouée |
| `/user/topic/notifications` | Réservé pour futures notifications (offres matchées…) |

**Fallback polling :**  
Si le WebSocket n'est pas connecté (réseau instable, firewall…), Angular interroge `GET /api/cv/optimize/{id}` toutes les **3 secondes** jusqu'à `status === "completed"`. Le polling s'arrête automatiquement quand le WebSocket envoie le résultat.

**Migration SockJS → WebSocket natif :**  
`sockjs-client` utilisait l'objet Node.js `global` qui n'existe pas dans les navigateurs, ce qui crashait silencieusement le routing Angular. Solution : remplacement par le WebSocket natif du navigateur via `brokerURL` dans `@stomp/stompjs`. Le backend Spring Boot expose désormais deux endpoints : un natif (`/ws/notifications`) et un SockJS pour compatibilité.

---

### 4.6 Consultation de l'historique des optimisations

**Ce qui a été ajouté :**
- Détection automatique du mode dans `CvOptimizerComponent` : nouvel appel IA vs consultation d'un résultat existant
- Méthode `_loadExistingResult()` — récupère directement le résultat sauvegardé en DB sans relancer l'IA
- Tableau d'historique enrichi dans le CV Manager : statuts `completed`/`failed`/`en cours`, noms de modèles réels

**Comment ça marche :**

Le composant `/cv/optimize/:id` fonctionne maintenant dans **deux modes** :

```
Mode 1 — Nouvelle optimisation (depuis "Optimiser IA")
  URL: /cv/optimize/:id?cvId=...&jobId=...&jobTitle=...
                │
  → isNewOptimization = true
  → _startProcessing() → animation IA → RabbitMQ → Gemma → WebSocket → résultat

Mode 2 — Consultation historique (depuis le tableau)
  URL: /cv/optimize/:id   (aucun query param)
                │
  → isNewOptimization = false
  → _loadExistingResult() → GET /api/cv/optimize/{id} → résultat immédiat
```

Le mode est détecté par la présence ou l'absence du query param `?cvId=` dans l'URL :
```typescript
ngOnInit() {
  const isNewOptimization = !!this.route.snapshot.queryParams['cvId'];
  if (isNewOptimization) this._startProcessing();
  else                   this._loadExistingResult(); // affiche le résultat sauvegardé
}
```

**Tableau d'historique :**
| Statut | Affichage | Lien |
|--------|-----------|------|
| `completed` | Score avant/après + gain en pts + nom du modèle | ✅ Cliquable → résultat complet |
| `failed` | Badge rouge "Échec" | ⚠️ Icône erreur (non cliquable) |
| `processing` | Badge jaune "En cours…" | ⏳ Icône hourglass (non cliquable) |

---

### 4.7 CV Studio — Template Designer & Export PDF

**Ce qui a été ajouté :**
- `cv-studio.component` — éditeur visuel de CV avec prévisualisation live côte-à-côte
- `cv-templates.ts` — 3 templates prédéfinis : **Modern** (bleu), **Classic** (sobre), **Creative** (violet)
- `CvPdfGenerator.java` — génération PDF vectoriel via Chrome headless (Playwright/CDP)
- `PdfRenderService.java` — orchestre le rendu HTML → PDF avec `POST /api/cv/render-pdf`
- `CvTextParser.java` — parse le texte extrait par PDFBox en sections structurées (JSON) pour préremplir le studio

**Comment ça marche :**

```
Angular (CV Studio)              Spring Boot              Chrome headless
       │                              │                         │
       ├─ GET /api/cv/{id} ──────────►│                         │
       │◄── {extractedText, sections}─┤                         │
       │                              │                         │
       │  Utilisateur choisit template│                         │
       │  + édite champs + photo      │                         │
       │                              │                         │
       ├─ POST /api/cv/render-pdf ────►│                         │
       │  {html, css, studioData}     ├─ lance Chrome ──────────►│
       │                              │  page.setContent(html)  │
       │                              │◄── PDF binaire ─────────┤
       │◄── blob PDF ─────────────────┤                         │
       │                              │                         │
       │  browser.saveAs("cv.pdf")    │                         │
```

**Templates disponibles :**

| Template | Style | Couleur accent |
|----------|-------|----------------|
| Modern | Barre latérale colorée, photo circulaire | Bleu (`#2563EB`) |
| Classic | Mise en page sobre, ligne de séparation | Gris anthracite |
| Creative | En-tête pleine largeur, typographie audacieuse | Violet (`#7C3AED`) |

**Rebuild IA des sections :**
Si le CV uploadé est mal parsé (texte brut non structuré), l'utilisateur peut cliquer "Rebuild with AI" — le studio envoie le texte brut à OpenRouter qui retourne un JSON structuré (`{name, email, experience[], skills[], education[]}`) pour préremplir automatiquement tous les champs.

---

### 4.8 Frontend Angular — 10 pages

| Page | Route | Données |
|------|-------|---------|
| Landing | `/` | Statique — présentation de la plateforme |
| Login | `/login` | `POST /api/auth/login` → JWT |
| Register | `/register` | `POST /api/auth/register` → JWT |
| Dashboard | `/dashboard` | Stats candidatures + notifications récentes (mock job/app data) |
| Recherche offres | `/jobs` | Mock data Maroc (OCP, Maroc Telecom, CIH Bank, Inwi…) |
| Détail offre | `/jobs/:id` | Score de compatibilité, bouton "Optimiser CV" |
| **Mon CV** | `/cv` | **RÉEL** — upload PDF, ATS réel, sections extraites, historique |
| **Optimizer** | `/cv/optimize/:id` | **RÉEL** — loading IA animé, résultat Gemma 4, avant/après |
| **CV Studio** | `/cv/studio/:id` | **RÉEL** — template designer, photo, live preview, export PDF vectoriel |
| Candidatures | `/applications` | Kanban (mock data) |
| Notifications | `/notifications` | Mock data |

> **Pages en gras = connectées au backend réel.**  
> Les autres pages utilisent des données mock — prêtes à être connectées au fur et à mesure que le backend est développé.

**Intercepteur JWT global :**  
Tous les appels HTTP Angular ont automatiquement le header `Authorization: Bearer <token>` grâce à `authInterceptor`. Si le serveur répond 401/403, l'intercepteur déconnecte automatiquement l'utilisateur et le redirige vers `/login`.

---

## 5. Lancer le projet

### Prérequis
- Java 21
- Docker Desktop (pour PostgreSQL + RabbitMQ)
- Node.js 20+ et npm

### Étape 1 — Configurer le backend

```bash
# Copier le template de configuration
cp backend/src/main/resources/application.yml.example \
   backend/src/main/resources/application.yml
```

Ouvrez `backend/src/main/resources/application.yml` et remplissez **ces deux valeurs** :

| Champ | Comment l'obtenir |
|-------|-------------------|
| `hiresync.openrouter.api-key` | Créez un compte gratuit sur [openrouter.ai/keys](https://openrouter.ai/keys) |
| `hiresync.jwt.secret` | N'importe quelle chaîne aléatoire de 64+ caractères — ex: `openssl rand -base64 64` |

Tout le reste (DB, RabbitMQ) est pré-rempli avec les valeurs du `docker-compose.yml` — **ne pas modifier**.

### Étape 2 — Lancer les services

```bash
# Terminal 1 — Infrastructure (PostgreSQL + RabbitMQ)
cd backend
docker compose up -d
# PostgreSQL  → localhost:5432
# RabbitMQ UI → http://localhost:15672  (hiresync / hiresync123)

# Terminal 2 — Backend Spring Boot
cd backend
./mvnw spring-boot:run          # Linux/macOS
.\mvnw.cmd spring-boot:run      # Windows
# → http://localhost:8080
# Hibernate crée les tables automatiquement au premier démarrage

# Terminal 3 — Frontend Angular
cd hiresync
npm install
npm start -- --port 4201 --no-open
# → http://localhost:4201
```

### Tester rapidement

```bash
# 1. Créer un compte
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"fullName":"Test User","email":"test@test.ma","password":"password123"}'

# 2. Se connecter
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@test.ma","password":"password123"}'
# → retourne {"token":"eyJ..."}

# 3. Lister les CVs
curl -H "Authorization: Bearer <token>" http://localhost:8080/api/cv/versions
```

---

## 6. Endpoints API

### Auth
| Méthode | URL | Body | Description |
|---------|-----|------|-------------|
| POST | `/api/auth/register` | `{fullName, email, password}` | Créer un compte |
| POST | `/api/auth/login` | `{email, password}` | Connexion → JWT |

### CV
| Méthode | URL | Description |
|---------|-----|-------------|
| GET | `/api/cv/versions` | Lister tous les CVs de l'utilisateur |
| POST | `/api/cv/upload` | Uploader un CV (multipart PDF/Word) → extraction PDFBox + score ATS |
| PATCH | `/api/cv/{id}/activate` | Définir ce CV comme CV actif |
| DELETE | `/api/cv/{id}` | Supprimer un CV |
| POST | `/api/cv/optimize` | Lancer l'optimisation IA (async RabbitMQ) → retourne `{optimizationId}` |
| GET | `/api/cv/optimize/{id}` | Consulter le résultat d'une optimisation (polling fallback WebSocket) |
| GET | `/api/cv/optimization-history` | Historique des optimisations |

### WebSocket
| Endpoint | Protocole | Description |
|----------|-----------|-------------|
| `/ws/notifications` | SockJS + STOMP | Point de connexion WebSocket |
| `/user/topic/cv-optimization` | STOMP subscription | Événements d'optimisation en temps réel |

---

## 7. Variables de configuration

La configuration du backend se fait dans un seul fichier : `backend/src/main/resources/application.yml`.

Ce fichier est dans `.gitignore` — vous devez le créer à partir du template (voir Étape 1 ci-dessus).

**Valeurs à remplir obligatoirement :**

| Champ dans `application.yml` | Description | Où l'obtenir |
|------------------------------|-------------|--------------|
| `hiresync.openrouter.api-key` | Clé API LLM | [openrouter.ai/keys](https://openrouter.ai/keys) — compte gratuit |
| `hiresync.jwt.secret` | Secret JWT (min. 64 chars) | N'importe quelle longue chaîne aléatoire |

**Tout le reste est pré-rempli** dans `application.yml.example` et fonctionne tel quel avec `docker compose up -d`.

> Le fichier `backend/.env.example` est une alternative pour les développeurs qui préfèrent injecter la config via des variables d'environnement système (CI/CD, production).

---

## 8. Structure des dossiers

```
backend/src/main/java/ma/hiresync/
│
├── HireSyncBackendApplication.java          Point d'entrée Spring Boot
│
├── auth/
│   ├── entity/User.java                     Entité JPA utilisateur (implémente UserDetails)
│   ├── UserRepository.java                  findByEmail, existsByEmail
│   ├── UserDetailsServiceImpl.java          Charge l'utilisateur depuis la DB (Spring Security)
│   ├── JwtService.java                      Génère/valide les JWT (JJWT 0.12)
│   ├── JwtAuthFilter.java                   Filtre HTTP — extrait et valide le Bearer token
│   ├── AuthService.java                     register() + login()
│   ├── AuthController.java                  POST /api/auth/register, /login
│   └── dto/                                 LoginRequest, RegisterRequest, AuthResponse
│
├── cv/
│   ├── entity/
│   │   ├── Cv.java                          CV uploadé (fileName, atsScore, extractedText…)
│   │   └── CvOptimization.java              Résultat d'optimisation IA (status, scores, JSON)
│   ├── repository/
│   │   ├── CvRepository.java                findByUserId, findByIdAndUserId, deactivateAll
│   │   └── CvOptimizationRepository.java    findByCvUserId, findByIdAndCvUserId
│   ├── dto/                                 CvResponse, OptimizeRequest, OptimizationResponse…
│   ├── service/
│   │   ├── AtsScorer.java                   PDFBox extraction + scoring ATS (0–100)
│   │   ├── OpenRouterService.java           Appel WebClient → OpenRouter (Gemma 4, fallbacks)
│   │   └── CvService.java                   Logique métier : upload, activate, delete, optimize
│   ├── messaging/
│   │   ├── OptimizationMessage.java         Record Java (optimizationId, cvId, userId, jobDesc)
│   │   ├── OptimizationProducer.java        Publie dans cv.exchange après commit transaction
│   │   └── OptimizationConsumer.java        @RabbitListener + @Transactional → appel OpenRouter
│   └── controller/
│       └── CvController.java                REST endpoints CV (upload, list, optimize, history)
│
├── notification/
│   └── NotificationService.java             convertAndSendToUser → Angular WebSocket
│
└── config/
    ├── SecurityConfig.java                  Spring Security (JWT filter, CORS, stateless)
    ├── WebSocketConfig.java                 STOMP sur SockJS (/ws/notifications)
    └── RabbitMQConfig.java                  Exchange + Queue + DLQ + JSON converter
```

---

*HireSync — PFE 2025/2026 — Othmane Sadiky*
