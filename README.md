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
     - [4.3.1 Vérification de compatibilité profil ↔ offre](#431-vérification-de-compatibilité-profil--offre)
     - [4.3.2 Scoring ATS job-aware (mots-clés matched / missing)](#432-scoring-ats-job-aware-mots-clés-matched--missing)
     - [4.3.3 Backoff/retry sur erreur 429 OpenRouter](#433-backoffretry-sur-erreur-429-openrouter)
   - [4.4 Pipeline RabbitMQ (traitement asynchrone)](#44-pipeline-rabbitmq-traitement-asynchrone)
     - [4.4.1 Queue CV — optimisation IA](#441-queue-cv--optimisation-ia)
     - [4.4.2 Queues Scraping — 5 sources en parallèle + chaîne d'enrichissement](#442-queues-scraping--5-sources-en-parallèle--chaîne-denrichissement)
   - [4.5 Notifications WebSocket (STOMP)](#45-notifications-websocket-stomp)
   - [4.6 Consultation de l'historique des optimisations](#46-consultation-de-lhistorique-des-optimisations)
   - [4.7 CV Studio — Template Designer & Export PDF](#47-cv-studio--template-designer--export-pdf)
   - [4.8 Frontend Angular — 10 pages](#48-frontend-angular--10-pages)
   - [4.9 Scraping des offres d'emploi (multi-sources)](#49-scraping-des-offres-demploi-multi-sources)
     - [4.9.1 Vue d'ensemble & orchestration](#491-vue-densemble--orchestration)
     - [4.9.2 Source A — rekrute.com (HTML classique)](#492-source-a--rekrutecom-html-classique)
     - [4.9.3 Source B — emploi.ma (HTML classique)](#493-source-b--emploima-html-classique)
     - [4.9.4 Source C — Indeed Maroc (JSON embarqué)](#494-source-c--indeed-maroc-json-embarqué)
     - [4.9.5 Source D — LinkedIn (recherche invité)](#495-source-d--linkedin-recherche-invité)
     - [4.9.6 Source E — marocemploi.net (WordPress / FlareSolverr)](#496-source-e--marocemploinet-wordpress--flaresolverr)
   - [4.10 Enrichissement des descriptions (source-aware)](#410-enrichissement-des-descriptions-source-aware)
   - [4.11 Pagination côté serveur](#411-pagination-côté-serveur)
   - [4.12 Filtres dynamiques avec comptages live](#412-filtres-dynamiques-avec-comptages-live)
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
| Scraping HTML | **Jsoup 1.17** | Connexion HTTP + parsing DOM des job boards (rekrute, emploi.ma, indeed, marocemploi) |
| Parsing JSON | **Jackson `ObjectMapper`** | Décodage du JSON embarqué dans les pages Indeed et des réponses FlareSolverr |

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
| FlareSolverr | `ghcr.io/flaresolverr/flaresolverr:latest` | 8191 (API interne uniquement) |

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
│   │   ├── job/                ← Offres d'emploi (scraping multi-sources + enrichissement)
│   │   │   ├── controller/     ← JobController (recherche, détail, triggers admin)
│   │   │   ├── service/        ← JobService + 5 scrapers + JobEnrichmentService
│   │   │   ├── entity/         ← Job (+ @ElementCollection requirements)
│   │   │   ├── repository/     ← JobRepository (recherche paginée, dédup)
│   │   │   └── dto/            ← JobResponse, Scrape/EnrichTriggerResponse
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

#### 4.3.1 Vérification de compatibilité profil ↔ offre

Avant de lancer la réécriture du CV, le pipeline effectue un **contrôle de compatibilité** entre le profil détecté dans le CV et le poste ciblé. Ce contrôle utilise un second appel LLM dédié dans `OpenRouterService`.

**Pourquoi ce contrôle ?**  
Optimiser un CV de développeur junior pour un poste de CFO reviendrait à inventer une expérience fictive. Le contrôle permet d'éviter cela et de guider le candidat vers des offres réellement adaptées.

**Nouveau DTO — `CompatibilityVerdict.java` :**
```java
public record CompatibilityVerdict(
    boolean compatible,        // true → on continue ; false → état rejected
    int     compatibilityScore,// 0–100 — proximité profil/offre
    String  candidateProfile,  // résumé du profil détecté dans le CV ("Développeur Angular Junior")
    String  targetProfile,     // résumé du poste ("Chief Financial Officer")
    String  rejectionReason    // explication lisible si compatible = false
) {}
```

**Flux côté backend (`OptimizationConsumer.java`) :**
```
[Message RabbitMQ reçu]
        │
        ▼
OpenRouterService.checkCompatibility(cvText, jobDescription)
        │
        ├─ compatible = true  → continuer l'optimisation normale
        │
        └─ compatible = false → sauvegarder verdict + status = REJECTED
                                 → WebSocket push (status: "rejected")
```

**Côté Angular — état `rejected` :**

Le composant `/cv/optimize/:id` gère un quatrième état `rejected` (en plus de `processing`, `completed`, `failed`) :

| Élément affiché | Description |
|-----------------|-------------|
| Icône `block` rouge | Optimisation impossible |
| Bloc profil ↔ poste | Votre profil détecté à gauche → poste ciblé à droite |
| Barre de compatibilité | Pourcentage de proximité sur une barre de progression |
| Raison du rejet | Explication lisible fournie par le LLM |
| CTA 1 | "Trouver des offres adaptées" → `/jobs` |
| CTA 2 | "Retour au CV Manager" → `/cv` |

L'historique du CV Manager affiche les optimisations rejetées avec un badge orange `Profil incompatible`.

---

#### 4.3.2 Scoring ATS job-aware (mots-clés matched / missing)

Le score ATS initial de `AtsScorer.java` était calculé sur des critères génériques (sections présentes, verbes d'action, mots-clés tech). Il a été enrichi d'un **scoring contextuel** basé sur les mots-clés réels de l'offre.

**Comment ça marche — nouvelle méthode `scoreAgainstJob()` dans `AtsScorer.java` :**

1. `OptimizationConsumer` appelle `OpenRouterService.extractKeywords(jobDescription)` — le LLM retourne une liste de mots-clés techniques et compétences extraits de l'offre ciblée.
2. `AtsScorer.scoreAgainstJob(cvText, keywords)` calcule combien de ces mots-clés apparaissent dans le texte du CV optimisé.
3. Le score final (`optimizedScore`) pondère le score générique et le score job-aware.

**Nouveaux champs dans `OptimizationResponse.java` :**

| Champ | Type | Description |
|-------|------|-------------|
| `matchedKeywords` | `List<String>` | Mots-clés de l'offre couverts par le CV optimisé |
| `missingKeywords` | `List<String>` | Mots-clés de l'offre encore absents du CV |

**Côté Angular — section "Analyse ATS — mots-clés de l'offre" :**

Affichée uniquement si au moins un mot-clé est présent dans `matchedKeywords` ou `missingKeywords` :

```
┌─────────────────────────────────────────────────────────┐
│  🔍 Analyse ATS — mots-clés de l'offre    12/15 couverts│
│                                                         │
│  ✅ Couverts                                            │
│  [Angular] [TypeScript] [Spring Boot] [REST API] ...    │
│                                                         │
│  ○ Encore manquants                                     │
│  [Kubernetes] [Terraform] [AWS]                         │
│  Ces compétences apparaissent dans l'offre mais pas     │
│  dans votre CV. Ne les ajoutez que si vous les         │
│  possédez réellement.                                   │
└─────────────────────────────────────────────────────────┘
```

- Chips verts remplis → mots-clés couverts (`matchedKeywords`)
- Chips gris/outline → mots-clés manquants (`missingKeywords`)
- Compteur `X/Y couverts` dans l'en-tête de la section

---

#### 4.3.3 Backoff/retry sur erreur 429 OpenRouter

OpenRouter retourne HTTP **429 (Too Many Requests)** en cas de saturation du quota sur un modèle gratuit. Sans gestion, cela faisait passer le statut en `FAILED` même si un simple délai aurait suffi.

**Logique ajoutée dans `OpenRouterService.java` :**

```java
// Attente exponentielle avant de retenter sur le même modèle
for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
    try {
        return callModel(model, prompt);
    } catch (RateLimitException e) {
        if (attempt < MAX_RETRIES - 1) {
            Thread.sleep(BACKOFF_BASE_MS * (1L << attempt)); // 2s, 4s, 8s…
        }
    }
}
// Si toujours en 429 après MAX_RETRIES → passer au modèle suivant dans la chaîne de fallback
```

La chaîne complète est donc : **Gemma 4 31B** (3 tentatives avec backoff) → **LLaMA 3.3 70B** (3 tentatives) → **Qwen3 80B** (3 tentatives). Si tous les modèles épuisent leurs tentatives, le statut passe en `FAILED` et un message clair est affiché : *"Réessayez dans quelques minutes"*.

---

### 4.4 Pipeline RabbitMQ (traitement asynchrone)

#### 4.4.1 Queue CV — optimisation IA

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

#### 4.4.2 Queues Scraping — 5 sources en parallèle + chaîne d'enrichissement

Le pipeline de scraping a été migré vers RabbitMQ pour permettre aux **5 sources de scraper en parallèle** au lieu de séquentiellement, et pour **chaîner automatiquement l'enrichissement** après chaque source.

**Nouveaux composants :**

| Fichier | Rôle |
|---------|------|
| `ScrapeMessage.java` | Record Java — `(String source)` |
| `ScrapeProducer.java` | Publie 5 messages dans `scrape.exchange` (un par source) |
| `ScrapeConsumer.java` | `@RabbitListener` — route vers le bon scraper selon `source`, puis publie dans `enrich.exchange` |
| `EnrichMessage.java` | Record Java — `(List<UUID> jobIds)` |
| `EnrichProducer.java` | Publie les IDs des offres nouvellement sauvegardées dans `enrich.exchange` |
| `EnrichConsumer.java` | `@RabbitListener` — appelle `JobEnrichmentService.enrichById(jobIds)` |

**Échanges et queues déclarés dans `RabbitMQConfig.java` :**

| Exchange | Queue | Utilisation |
|----------|-------|------------|
| `cv.exchange` | `cv.optimize.queue` + DLQ | Optimisation IA (existant) |
| `scrape.exchange` | `scrape.queue` | Un message par source → scraping parallèle |
| `enrich.exchange` | `enrich.queue` | IDs après scraping → enrichissement immédiat |

**Nouveau flux `triggerScrape()` :**

```
POST /api/admin/scrape/trigger
        │
        ▼
JobService.triggerScrape()
        │
        ├─ scrapeProducer.publish("rekrute.com")   ─┐
        ├─ scrapeProducer.publish("emploi.ma")      │ 5 messages publiés
        ├─ scrapeProducer.publish("indeed.ma")      │ instantanément
        ├─ scrapeProducer.publish("linkedin.com")   │
        └─ scrapeProducer.publish("marocemploi.net")┘
                │
                ▼ (retourne immédiatement — sans attendre)
        {status: "triggered", sources: 5}

        [En arrière-plan — 5 consumers s'exécutent en parallèle]
        ScrapeConsumer → rekrute.com → sauvegarde → enrichProducer.publish(ids) → EnrichConsumer
        ScrapeConsumer → emploi.ma   → sauvegarde → enrichProducer.publish(ids) → EnrichConsumer
        ScrapeConsumer → indeed.ma   → sauvegarde → enrichProducer.publish(ids) → EnrichConsumer
        ScrapeConsumer → linkedin.com→ sauvegarde → enrichProducer.publish(ids) → EnrichConsumer
        ScrapeConsumer → marocemploi → sauvegarde (enriched inline, pas d'enrich message)
```

**Avantages vs l'approche séquentielle précédente :**

| Critère | Avant (séquentiel) | Après (RabbitMQ parallèle) |
|---------|---------------------|---------------------------|
| Durée totale scraping | ~60–90s (5 sources en chaîne) | ~15–20s (source la plus lente) |
| Enrichissement | Manuel — déclenché séparément | Automatique — chaîné après chaque source |
| Erreur sur une source | Bloque les suivantes dans le même thread | Isolée — les 4 autres continuent |
| Monitoring | Compteur retourné en fin de requête | Visible en temps réel dans l'UI RabbitMQ |

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
| `rejected` | Badge orange "Profil incompatible" + compatibilityScore% | ✅ Cliquable → page rejet (profil ↔ offre + raison) |
| `failed` | Badge rouge "Échec" | ⚠️ Icône erreur (non cliquable) |
| `processing` | Badge jaune "En cours…" | ⏳ Icône hourglass (non cliquable) |

Le tableau a été redessiné pour afficher toutes les optimisations — y compris les rejets — avec leur statut coloré en ligne, permettant au candidat de comprendre pourquoi une tentative n'a pas abouti sans quitter le CV Manager.

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
| **Recherche offres** | `/jobs` | **RÉEL** — offres scrapées depuis rekrute.com + emploi.ma + Indeed + LinkedIn, pagination, filtres, barre admin |
| **Détail offre** | `/jobs/:id` | **RÉEL** — description enrichie structurée, compétences, bouton "Optimiser CV" |
| **Mon CV** | `/cv` | **RÉEL** — upload PDF, ATS réel, sections extraites, historique |
| **Optimizer** | `/cv/optimize/:id` | **RÉEL** — loading IA animé (4 étapes), résultat Gemma 4 (avant/après, mots-clés ATS), état rejeté (profil incompatible), état échec |
| **CV Studio** | `/cv/studio/:id` | **RÉEL** — template designer, photo, live preview, export PDF vectoriel |
| Candidatures | `/applications` | Kanban (mock data) |
| Notifications | `/notifications` | Mock data |

> **Pages en gras = connectées au backend réel.**  
> Les autres pages utilisent des données mock — prêtes à être connectées au fur et à mesure que le backend est développé.

**Intercepteur JWT global :**  
Tous les appels HTTP Angular ont automatiquement le header `Authorization: Bearer <token>` grâce à `authInterceptor`. Si le serveur répond 401/403, l'intercepteur déconnecte automatiquement l'utilisateur et le redirige vers `/login`.

---

---

### 4.9 Scraping des offres d'emploi (multi-sources)

#### 4.9.1 Vue d'ensemble & orchestration

HireSync agrège automatiquement les offres d'emploi de **cinq job boards marocains** (dont LinkedIn) et les stocke dans une **table `jobs` unifiée** en PostgreSQL. Chaque source a son propre service de scraping, mais tous partagent la **même entité `Job`**, la **même clé de déduplication** (`sourceUrl` unique) et le **même pipeline** : collecte du listing, puis enrichissement (page détail) — sauf marocemploi.net qui fait les deux en une seule passe.

| Source | Service | Champ `source` | Technique de collecte | Pourquoi cette technique |
|--------|---------|----------------|-----------------------|--------------------------|
| **rekrute.com** | `JobScraperService` | `"rekrute.com"` | Jsoup → parsing du DOM HTML (`<li class="post-id">`) | Les offres sont rendues côté serveur dans le HTML → parsing DOM direct |
| **emploi.ma** | `EmploiMaScraperService` | `"emploi.ma"` | Jsoup → parsing du DOM HTML (`<div class="card-job">`) | Idem — listing server-side, structure en cartes |
| **Indeed Maroc** | `IndeedScraperService` | `"indeed.ma"` | Jsoup (fetch) + Jackson → **JSON embarqué** dans un `<script>` | Indeed ne rend **pas** les offres en HTML ; elles sont injectées en JSON dans `window.mosaic` |
| **LinkedIn** | `LinkedInScraperService` | `"linkedin.com"` | Jsoup → parsing du DOM HTML (`<div class="base-search-card">`), endpoint "guest" public | Pas de login requis ; le endpoint de recherche invité rend les cartes en HTML pur |
| **marocemploi.net** | `MarocEmploiScraperService` | `"marocemploi.net"` | **FlareSolverr** → Jsoup parsing DOM (`<div class="jobsearch-list-option">`) + page détail inline | Le site est protégé par Cloudflare JS challenge — requiert un vrai navigateur pour passer le contrôle TLS (voir §4.9.6) |

**Orchestration — `JobService.triggerScrape()` :**

`POST /api/admin/scrape/trigger` publie **cinq messages** dans la queue RabbitMQ `scrape.queue` (un par source) et retourne immédiatement. Les cinq sources scrapent ensuite **en parallèle** dans des threads consumers séparés. À la fin de chaque source, les IDs des offres nouvellement sauvegardées sont publiés dans `enrich.queue` pour déclencher automatiquement l'enrichissement (voir §4.4.2).

```java
// JobService.java — nouvelle orchestration via RabbitMQ
public ScrapeTriggerResponse triggerScrape() {
    List<String> sources = List.of(
        "rekrute.com", "emploi.ma", "indeed.ma", "linkedin.com", "marocemploi.net"
    );
    sources.forEach(scrapeProducer::publish);   // 5 messages → scraping parallèle
    return new ScrapeTriggerResponse(sources.size());
}

// ScrapeConsumer.java — route vers le bon scraper
@RabbitListener(queues = "scrape.queue")
public void consume(ScrapeMessage msg) {
    List<UUID> savedIds = switch (msg.source()) {
        case "rekrute.com"      -> rekruteScraper.scrape();
        case "emploi.ma"        -> emploiMaScraper.scrape();
        case "indeed.ma"        -> indeedScraper.scrape();
        case "linkedin.com"     -> linkedInScraper.scrape();
        case "marocemploi.net"  -> marocEmploiScraper.scrape();
        default -> List.of();
    };
    if (!savedIds.isEmpty()) enrichProducer.publish(savedIds);   // → enrichissement auto
}
```

Un seul appel à `POST /api/admin/scrape/trigger` collecte les offres des **cinq sources** en parallèle. La déduplication par `sourceUrl` rend l'opération **idempotente** quelle que soit la source.

**Choix de conception communs (les 5 scrapers) :**

- **Jsoup** comme client HTTP + parseur — une seule dépendance pour `connect().get()` *et* le parsing DOM, plus léger qu'un navigateur headless.
- **FlareSolverr** uniquement pour marocemploi.net — les quatre autres sources sont accessibles en HTTP simple. Chromium n'est utilisé que là où Cloudflare l'exige.
- **User-Agent honnête** : `Mozilla/5.0 (compatible; HireSync-Bot/1.0; +https://hiresync.ma)` — identifie le bot plutôt que d'usurper un vrai navigateur (sauf LinkedIn et marocemploi.net, voir §4.9.5, §4.9.6 et §4.10).
- **`timeout(15_000)`** + `try/catch IOException` par page → un échec réseau interrompt proprement la source sans planter les autres.
- **Déduplication avant insertion** : `if (jobRepository.existsBySourceUrl(sourceUrl)) continue;`
- **`MAX_PAGES = 3`** (rekrute, emploi.ma, LinkedIn & marocemploi.net) — constante en tête de chaque service, facile à augmenter.

```
5 sources                  Spring Boot                   PostgreSQL
    │                           │                             │
    │  [Passe 1 — Collecte]     │                             │
    │◄── rekrute  (3 pages)     │                             │
    │◄── emploi.ma (3 pages)    │                             │
    │◄── indeed   (1 page)      │                             │
    │◄── linkedin (3 pages)     │                             │
    │◄── marocemploi (3 pages)  │                             │
    │    via FlareSolverr       ├─ INSERT INTO jobs ──────────►│
    │                           │   (si sourceUrl inconnu)    │
    │                           │                             │
    │  [Passe 2 — Enrichissement, source-aware]               │
    │  (rekrute, emploi.ma, indeed, linkedin)                 │
    │◄── GET page détail        │                             │
    │  (1 requête / offre)      ├─ UPDATE jobs SET ───────────►│
    │                           │   description, requirements  │
    │                           │   enriched = true            │
    │                           │                             │
    │  marocemploi.net : description + contractType déjà      │
    │  collectés en passe 1 (enriched=true dès l'insertion)   │
```

---

#### 4.9.2 Source A — rekrute.com (HTML classique)

`JobScraperService` — `SOURCE = "rekrute.com"`, `BASE_URL = "https://www.rekrute.com"`.

##### Ce qui est extrait lors de la collecte (page listing)

Chaque carte d'offre sur la page de liste correspond à un élément `<li class="post-id">` dans le HTML de rekrute.com. Le scraper extrait :

| Champ | Sélecteur Jsoup | Exemple |
|-------|----------------|---------|
| `title` | `a.titreJob` (texte avant ` | `) | "Gestionnaire Clientèle (H/F)" |
| `location` | `a.titreJob` (texte après ` | `, sans "(Maroc)") | "Casablanca" |
| `company` | `img.photo[alt]` — ignoré si src contient "confidentiel" | "Attijariwafa bank" |
| `logoUrl` | `BASE_URL/rekrute/file/jobOfferLogo/jobOfferId/{li.id}` | URL de l'image logo |
| `contractType` | `a[href*='contractType']` | "CDI" |
| `sector` | `a[href*='sectorId']` | "Banque / Finance" |
| `experienceLevel` | `a[href*='workExperienceId']` | "Débutant (-1 an)" |
| `description` | Premier `div.info > span` (résumé IA généré par rekrute) | Court texte descriptif |
| `postedAt` | `em.date span` — format `dd/MM/yyyy` | "2026-06-08" |
| `sourceUrl` | `href` du lien `a.titreJob` — **clé de déduplication** | `/offre-emploi-*.html` |

##### Déduplication

Le champ `sourceUrl` porte un **index unique** en base (`UNIQUE CONSTRAINT`). Avant chaque insertion, le scraper vérifie :

```java
// JobScraperService.java
if (jobRepository.existsBySourceUrl(sourceUrl)) continue; // déjà en base → skip
```

Appeler `/api/admin/scrape/trigger` plusieurs fois de suite est donc **idempotent** — les offres déjà présentes ne sont pas dupliquées.

##### Rotation des pages

```
Page 1 → https://www.rekrute.com/offres-emploi-maroc.html       (offres Maroc)
Page 2 → https://www.rekrute.com/offres.html?s=1&p=2&o=1        (tri par date desc)
Page 3 → https://www.rekrute.com/offres.html?s=1&p=3&o=1
```

Le nombre de pages scrapées est configurable via la constante `MAX_PAGES = 3` dans `JobScraperService.java`. Chaque page contient ~10 offres → **30 offres par déclenchement**.

##### Entreprise confidentielle

Rekrute.com masque certains recruteurs. Le scraper le détecte via l'URL de l'image logo :

```java
// Si src contient "confidentiel" → entreprise masquée
if (src.contains("confidentiel")) {
    company = null;   // affiché "Confidentiel" dans l'UI
    logoUrl  = null;
}
```

---

#### Schéma de stockage (commun aux 5 sources)

Table **`jobs`** (PostgreSQL, gérée par Hibernate avec `ddl-auto: update`) — une seule table pour les quatre job boards, distingués par la colonne `source` :

| Colonne | Type | Description |
|---------|------|-------------|
| `id` | UUID | Clé primaire générée |
| `title` | VARCHAR | Intitulé du poste |
| `company` | VARCHAR | Nom de l'entreprise (null si confidentiel) |
| `location` | VARCHAR | Ville (ex : "Casablanca") |
| `contract_type` | VARCHAR | CDI, CDD, Stage… |
| `sector` | VARCHAR | Secteur d'activité |
| `experience_level` | VARCHAR | Débutant, Junior, Senior… |
| `description` | TEXT | Texte court (collecte) → texte complet (après enrichissement) |
| `logo_url` | VARCHAR(512) | URL du logo entreprise |
| `source_url` | VARCHAR(1024) | URL de l'offre — **UNIQUE** (clé de dédup) |
| `source` | VARCHAR | `"rekrute.com"`, `"emploi.ma"`, `"indeed.ma"`, `"linkedin.com"` ou `"marocemploi.net"` |
| `posted_at` | TIMESTAMP | Date de publication originale |
| `scraped_at` | TIMESTAMP | Date d'insertion en base |
| `enriched` | BOOLEAN | `false` → description courte, `true` → description complète |

Table **`job_requirements`** (table de jointure `@ElementCollection`) :

| Colonne | Type | Description |
|---------|------|-------------|
| `job_id` | UUID FK | Référence vers `jobs.id` |
| `requirement` | VARCHAR | Un trait de personnalité ou compétence |

---

#### 4.9.3 Source B — emploi.ma (HTML classique)

`EmploiMaScraperService` — `SOURCE = "emploi.ma"`, `BASE_URL = "https://www.emploi.ma"`.

emploi.ma fonctionne sur le **même principe que rekrute.com** (HTML rendu côté serveur, parsé avec Jsoup), mais la structure du DOM diffère : chaque offre est une **carte** `<div class="card card-job">` et l'URL de l'offre est portée par l'attribut `data-href` de la carte (et non un `<a href>`).

##### Ce qui est extrait lors de la collecte

| Champ | Sélecteur Jsoup | Remarque |
|-------|----------------|----------|
| `sourceUrl` | `card.attr("data-href")` | **clé de déduplication** — l'URL est sur la carte, pas sur un lien |
| `title` | `h3 > a` (texte) | — |
| `company` | `a.card-job-company` | `null` pour les annonces confidentielles (texte "N.C.") |
| `logoUrl` | `picture img[src]` | ignoré si `logo-non-dispo` / `default-logo` (placeholders) |
| `description` | `div.card-job-description p` | court résumé affiché sur la carte |
| `postedAt` | `time[datetime]` — format ISO `yyyy-MM-dd` | parsé via `LocalDate.parse()` |
| `contractType` | `metaValue("Contrat proposé")` | voir helper ci-dessous |
| `experienceLevel` | `metaValue("Niveau d'expérience")` | |
| `location` | `metaValue("Région de")` | |

##### Le helper `metaValue` — métadonnées en liste

Les métadonnées (contrat, expérience, région) sont rangées dans un `<ul><li>` où le **libellé** est en texte et la **valeur** dans un `<strong>`. Le helper retrouve le bon `<li>` par son préfixe de libellé :

```java
// EmploiMaScraperService.java
private String metaValue(Element card, String label) {
    for (Element li : card.select("ul > li")) {
        Element strong = li.selectFirst("strong");
        if (strong != null && li.text().trim().startsWith(label)) {
            return strong.text().trim();   // ex: "Contrat proposé : CDI" → "CDI"
        }
    }
    return null;
}
```

##### Rotation des pages

```
Page 1 → https://www.emploi.ma/recherche-jobs-maroc
Page 2 → https://www.emploi.ma/recherche-jobs-maroc?page=1   (page=1 → 2ᵉ page)
Page 3 → https://www.emploi.ma/recherche-jobs-maroc?page=2
```

`MAX_PAGES = 3`, même logique de boucle que rekrute (la page 1 utilise l'URL par défaut, les suivantes le paramètre `?page=N-1`).

---

#### 4.9.4 Source C — Indeed Maroc (JSON embarqué)

`IndeedScraperService` — `SOURCE = "indeed.ma"`, `BASE_URL = "https://ma.indeed.com"`.

##### Pourquoi Indeed est un cas à part

Contrairement à rekrute et emploi.ma, **Indeed ne rend pas ses offres en HTML**. La page de résultats ne contient qu'une coquille vide ; les offres sont injectées par JavaScript depuis un **gros objet JSON embarqué** dans un `<script>` :

```js
window.mosaic.providerData["mosaic-provider-jobcards"]={"metaData":{
  "mosaicProviderJobCardsModel":{"results":[ /* … les offres … */ ]}}, … };
```

Un parsing DOM classique (`doc.select(...)`) ne trouverait donc **aucune offre**. La stratégie est :

1. **Jsoup** récupère le HTML et localise le `<script>` contenant le marqueur `window.mosaic.providerData["mosaic-provider-jobcards"]=`.
2. Un **extracteur d'objet équilibré** (comptage des `{` / `}`) isole le JSON qui suit le `=`, en s'arrêtant à l'accolade fermante correspondante (le JS continue après avec d'autres assignations).
3. **Jackson `ObjectMapper.readTree(...)`** parse ce JSON, puis on navigue jusqu'à `metaData → mosaicProviderJobCardsModel → results`.

```java
// IndeedScraperService.java — extraction de l'objet JSON équilibré
private String extractJsonObject(String src, int start) {   // start = index du '{'
    int depth = 0;
    for (int i = start; i < src.length(); i++) {
        char c = src.charAt(i);
        if (c == '{') depth++;
        else if (c == '}' && --depth == 0) return src.substring(start, i + 1);
    }
    return null;
}
```

##### En-tête HTTP supplémentaire

Indeed sert du contenu localisé. On ajoute donc un header `Accept-Language: fr-FR,fr;q=0.9` (en plus du User-Agent commun) pour obtenir les offres marocaines en français.

##### Mapping JSON → entité `Job`

Chaque objet du tableau `results` a 90+ champs ; on n'en garde que ceux utiles :

| Champ `Job` | Clé JSON Indeed | Transformation |
|-------------|-----------------|----------------|
| `sourceUrl` | `jobkey` (⚠️ **minuscule**) | `BASE_URL + "/jobs?q=...&vjk=" + jobkey` (voir ci-dessous) |
| `title` | `title` | — |
| `company` | `company` | `null` si vide |
| `location` | `formattedLocation` | ex : "Casablanca", "Rabat", "Remote" |
| `contractType` | `jobTypes[]` | tableau (`["Temps plein","CDI"]`) **joint** en `"Temps plein, CDI"` |
| `description` | `snippet` (HTML `<ul><li>`) | converti en texte à puces via `Jsoup.parseBodyFragment` |
| `postedAt` | `pubDate` (epoch millis) | `Instant.ofEpochMilli(...)` → `LocalDateTime` |

> ⚠️ **Piège** : la clé est `jobkey` tout en minuscule (et non `jobKey`). Une mauvaise casse renvoie silencieusement `null`.

##### Pourquoi `&vjk=` et non `/viewjob`

Le réflexe serait de pointer `sourceUrl` vers `https://ma.indeed.com/viewjob?jk=<jobkey>`. Mais cette URL renvoie **HTTP 403** (anti-bot). En réalité, l'interface Indeed est une **SPA** : cliquer une offre charge son détail **dans le panneau de droite de la même page de recherche**, via le paramètre `&vjk=<jobkey>`. On stocke donc :

```
sourceUrl = https://ma.indeed.com/jobs?q=offre+d%27emploi&l=&vjk=<jobkey>
```

Cette URL sert à la fois de **clé de dédup** et de **cible d'enrichissement** : elle contient le bloc `div#jobDescriptionText` avec la description complète (voir §4.10).

##### Pagination limitée à 1 page

Demander la page 2 (`&start=10`) **redirige vers le mur de connexion** d'Indeed (`secure.indeed.com/auth?...page-two-signin`). Seule la **première page** (~15 offres) est donc accessible sans authentification — d'où l'absence de boucle `MAX_PAGES` pour cette source.

---

#### 4.9.5 Source D — LinkedIn (recherche invité)

`LinkedInScraperService` — `SOURCE = "linkedin.com"`, `BASE_URL = "https://www.linkedin.com"`.

##### Le endpoint "guest" — pas de login requis

LinkedIn expose un endpoint public de recherche d'offres, utilisé en arrière-plan par la page de résultats pour le scroll infini, accessible **sans authentification** :

```
https://www.linkedin.com/jobs-guest/jobs/api/seeMoreJobPostings/search?keywords=offre+d%27emploi&location=Morocco&start=N
```

Cet endpoint renvoie directement du **HTML** (des cartes `<div class="base-search-card">`), pas du JSON — contrairement à Indeed, un parsing DOM classique avec Jsoup suffit.

##### Ce qui est extrait lors de la collecte (page listing)

| Champ | Sélecteur Jsoup | Remarque |
|-------|----------------|----------|
| `sourceUrl` | `a.base-card__full-link[href]`, query string tronquée à `?` | **clé de déduplication** — l'URL brute porte des paramètres de tracking (`position`, `pageNum`, `refId`…) qu'on retire pour obtenir une clé stable |
| `title` | `h3.base-search-card__title` | — |
| `company` | `h4.base-search-card__subtitle` | `null` si absent |
| `location` | `span.job-search-card__location` | ex : "Casablanca, Maroc" |
| `logoUrl` | `img.artdeco-entity-image[data-delayed-url]` | LinkedIn charge les logos en lazy-load — l'URL réelle est dans `data-delayed-url`, pas `src` |
| `postedAt` | `time.job-search-card__listdate[datetime]` — format ISO `yyyy-MM-dd` | parsé via `LocalDate.parse()` |

`description`, `contractType`, `experienceLevel` et `sector` ne sont **pas** disponibles sur la page de listing — ils sont remplis par la passe d'enrichissement (§4.10).

##### Pagination multi-pages

```
Page 1 → .../seeMoreJobPostings/search?...&start=0
Page 2 → .../seeMoreJobPostings/search?...&start=10
Page 3 → .../seeMoreJobPostings/search?...&start=20
```

`MAX_PAGES = 3`, `PAGE_SIZE = 10` — même principe que rekrute/emploi.ma (`start = page * PAGE_SIZE`). La boucle s'arrête **avant** la page suivante dès que :

- la page courante ne contient **aucune carte** `div.base-search-card` (LinkedIn a cessé de paginer), ou
- la requête échoue (`IOException` — anti-bot, timeout…).

Dans les deux cas, ce qui a déjà été collecté sur les pages précédentes est **conservé** (`saved` n'est pas réinitialisé) — aucune exception ne remonte au déclencheur global :

```java
// LinkedInScraperService.scrape()
for (int page = 0; page < MAX_PAGES; page++) {
    int start = page * PAGE_SIZE;
    Document doc;
    try {
        doc = Jsoup.connect(SEARCH_URL + start)
                .userAgent("Mozilla/5.0 (compatible; HireSync-Bot/1.0; +https://hiresync.ma)")
                .header("Accept-Language", "fr-FR,fr;q=0.9")
                .referrer(BASE_URL)
                .timeout(15_000)
                .get();
    } catch (IOException e) {
        break; // garde ce qui a été sauvé sur les pages précédentes
    }

    saved += parsePage(doc);

    if (doc.select("div.base-search-card").isEmpty()) break;
}
```

##### Tolérance aux champs manquants

Comme pour les autres sources, chaque champ est extrait via un helper qui renvoie `null` si l'élément/attribut est absent (`textOrNull`, `attrOrNull`) plutôt que de lever une exception. Seul `sourceUrl` et `title` sont obligatoires — une carte sans l'un des deux est ignorée (`continue`), le reste de la page continue d'être traité.

---

#### 4.9.6 Source E — marocemploi.net (WordPress / FlareSolverr)

`MarocEmploiScraperService` — `SOURCE = "marocemploi.net"`, `BASE_URL = "https://www.marocemploi.net"`.

##### Structure du site

marocemploi.net est un site WordPress propulsé par le plugin **WP-JobSearch**. Les offres sont rendues côté serveur en HTML pur — pas de JavaScript nécessaire pour afficher les cartes. La difficulté vient de la **protection Cloudflare** et non du DOM en lui-même.

##### Pagination — deux patterns d'URL

```
Page 1 → https://www.marocemploi.net/offre/
Page N → https://www.marocemploi.net/offre/?ajax_filter=true&job_page=N
```

La première page utilise l'URL racine de la section `/offre/`. Les pages suivantes utilisent un paramètre AJAX (`ajax_filter=true&job_page=N`) mais renvoient quand même du **HTML complet** (et non du JSON) — Jsoup peut donc le parser directement, quel que soit le numéro de page.

`MAX_PAGES = 3` → environ **90 nouvelles offres** par déclenchement.

##### Structure DOM — page listing

Chaque offre apparaît dans un wrapper `.jobsearch-joblisting-classic-wrap` qui contient une `<figure>` (logo) et un bloc `.jobsearch-list-option` (tous les champs textuels) :

| Champ | Sélecteur Jsoup | Remarque |
|-------|----------------|----------|
| `sourceUrl` | `h2.jobsearch-pst-title a[href]` | **clé de déduplication** |
| `title` | `h2.jobsearch-pst-title a` (texte) | Parfois suivi de " — Casablanca" — conservé tel quel |
| `company` | `li.job-company-name a` (texte, `@` retiré) | `null` si absent |
| `logoUrl` | `figure img[src]` — filtré si placeholder | Voir filtres ci-dessous |
| `location` | Premier `<li>` sans classe contenant un chiffre ou une virgule | Tronqué avant la première virgule |
| `sector` | Dernier `<li>` sans classe qui ne ressemble pas à une localisation | Source card — raffiné par la page détail |

##### Filtres logo — images placeholder

WP-JobSearch insère une image générique quand l'entreprise n'a pas de logo. Le scraper les ignore si l'URL contient l'un des marqueurs suivants :

```java
private static final List<String> PLACEHOLDER_PATTERNS =
        List.of("pasdimage", "profile-img-10", "default-logo", "no-logo");
```

Dans ce cas `logoUrl` reste `null`, et le DTO (`JobResponse.from()`) applique automatiquement le favicon Google comme repli (voir §4.9.1 — fallback logo).

##### Problème Cloudflare — protection JA3

Lors du déploiement en Docker, toutes les requêtes Jsoup vers marocemploi.net recevaient **HTTP 403** avec les headers :

```
cf-mitigated: challenge
server: cloudflare
```

L'analyse a montré que la **même IP publique** (196.119.198.10) obtenait 200 depuis Windows (Schannel) et 403 depuis Docker (Linux OpenSSL) — preuve que le blocage est basé sur l'empreinte TLS **JA3**, pas sur l'IP :

| Contexte | JA3 fingerprint | Réponse CF |
|----------|----------------|------------|
| Windows (Schannel) | `2800f914a7a4ba98aa9df62d316a460c` | ✅ 200 OK |
| Docker (Linux OpenSSL) | `4ea056e63b7910cbf543f0c095064dfe` | ❌ 403 challenge |

L'ajout de headers Chrome (`sec-ch-ua`, `sec-fetch-*`, etc.) à Jsoup ne changeait rien — Cloudflare analyse la **couche TLS**, pas les en-têtes HTTP.

##### Solution — FlareSolverr (sidecar Docker)

[FlareSolverr](https://github.com/FlareSolverr/FlareSolverr) est un conteneur Docker qui expose un vrai **navigateur headless Chromium** via une API REST simple. Son empreinte JA3 passe Cloudflare là où OpenSSL échoue.

Il est déclaré comme service dans `docker-compose.yml` :

```yaml
flaresolverr:
  image: ghcr.io/flaresolverr/flaresolverr:latest
  container_name: hiresync-flaresolverr
  environment:
    LOG_LEVEL: warn
  ports:
    - "8191:8191"
  restart: on-failure
```

Et le backend reçoit son URL via une variable d'environnement :

```yaml
# docker-compose.yml — service backend
environment:
  FLARESOLVERR_URL: http://hiresync-flaresolverr:8191
depends_on:
  flaresolverr:
    condition: service_started
```

##### Intégration Spring Boot — `@Value` + méthode `fetch()`

```java
@Value("${FLARESOLVERR_URL:}")   // vide si non défini (mode local)
private String flareSolverrUrl;
```

La méthode `fetch(url)` route la requête selon le contexte :

```java
private Document fetch(String url) throws IOException {
    if (flareSolverrUrl != null && !flareSolverrUrl.isBlank()) {
        return fetchViaFlareSolverr(url);     // Docker → via FlareSolverr
    }
    return Jsoup.connect(url)                  // local → Jsoup direct (Windows Schannel)
            .userAgent(USER_AGENT)
            /* en-têtes Chrome complets */
            .get();
}
```

L'appel FlareSolverr est un simple `POST` JSON :

```java
private Document fetchViaFlareSolverr(String targetUrl) throws IOException {
    String apiUrl = flareSolverrUrl + "/v1";
    String body = String.format(
            "{\"cmd\":\"request.get\",\"url\":\"%s\",\"maxTimeout\":60000}", targetUrl);

    HttpURLConnection conn = (HttpURLConnection) new URL(apiUrl).openConnection();
    conn.setRequestMethod("POST");
    conn.setRequestProperty("Content-Type", "application/json");
    conn.setDoOutput(true);
    conn.setConnectTimeout(10_000);
    conn.setReadTimeout(70_000);
    // ... écriture du body, lecture de la réponse

    JsonNode root = objectMapper.readTree(conn.getInputStream());
    String html = root.path("solution").path("response").asText("");
    String resolvedUrl = root.path("solution").path("url").asText(targetUrl);
    return Jsoup.parse(html, resolvedUrl);
}
```

FlareSolverr renvoie `{"status":"ok","solution":{"response":"<html>...","url":"..."}}`. Le HTML extrait est parsé par Jsoup exactement comme pour les autres sources.

##### Enrichissement inline — page détail

Contrairement aux quatre autres sources qui sauvegardent d'abord les offres en stub et attendent la passe d'enrichissement (§4.10), `MarocEmploiScraperService` **récupère la page de détail pendant le scraping lui-même** :

```java
// parseCard() — après avoir extrait titre + URL depuis la carte listing
try {
    Document detail = fetch(sourceUrl);   // FlareSolverr si Docker

    // Type de contrat : .jobsearch-jobdetail-type → "CDI", "CDD", "Stage"
    Element typeEl = detail.selectFirst(".jobsearch-jobdetail-type");

    // Secteur depuis la détail (plus fiable que depuis la carte)
    Element sectorEl = detail.selectFirst(".post-in-category");

    // Description complète : .jobsearch-description
    Element descEl = detail.selectFirst(".jobsearch-description");

    // Date de publication : "Date de Parution : 1 juin 2026"
    for (Element li : detail.select("li")) {
        if (li.text().contains("Date de Parution")) {
            postedAt = parseFrenchDate(li.text());
            break;
        }
    }
} catch (IOException e) {
    log.warn("Could not fetch detail page {}: {}", sourceUrl, e.getMessage());
}

return Job.builder()
        /* ... */
        .enriched(description != null)   // true dès l'insertion si détail obtenu
        .build();
```

L'offre est insérée avec `enriched = true` si la page de détail a pu être lue — elle **n'apparaît jamais dans la file d'enrichissement** de `JobEnrichmentService`. Si la page détail échoue (rate-limit passager), l'offre est quand même sauvegardée avec les champs de la carte et `enriched = false` — elle sera reprise lors d'une prochaine passe d'enrichissement.

##### Parsing de la date — français avec accents

La date est affichée en français sur la page de détail : `"Date de Parution : 1 juin 2026"`. Elle est extraite par regex et convertie via un dictionnaire de mois :

```java
private static final Map<String, Integer> FR_MONTHS = Map.ofEntries(
        Map.entry("janvier", 1), Map.entry("février", 2), Map.entry("fevrier", 2),
        Map.entry("mars", 3),    Map.entry("avril", 4),   Map.entry("mai", 5),
        Map.entry("juin", 6),    Map.entry("juillet", 7), Map.entry("août", 8),
        Map.entry("aout", 8),    Map.entry("septembre", 9), Map.entry("octobre", 10),
        Map.entry("novembre", 11), Map.entry("décembre", 12), Map.entry("decembre", 12)
);
private static final Pattern DATE_PATTERN =
        Pattern.compile("(\\d{1,2})\\s+(\\p{L}+)\\s+(\\d{4})");

// "1 juin 2026" → LocalDateTime(2026, 6, 1, 0, 0)
```

Les variantes avec et sans accent (`février`/`fevrier`, `août`/`aout`) sont toutes couvertes — utile quand l'encodage de la page produit des caractères non-accentués.

---

### 4.10 Enrichissement des descriptions (source-aware)

#### Pourquoi une deuxième passe ?

Sur quatre des cinq sources, la page de liste ne contient qu'un **court résumé** de l'offre. La description complète (missions détaillées, profil recherché, formation requise) se trouve **uniquement sur la page de détail** de chaque offre.

> **Exception — marocemploi.net :** `MarocEmploiScraperService` récupère la page de détail **inline, pendant la passe de collecte** (voir §4.9.6). Les offres marocemploi.net arrivent en base avec `enriched = true` et ne passent jamais par `JobEnrichmentService`.

Effectuer une requête détail par offre lors du scraping initial ralentirait excessivement l'opération pour ces sources. L'enrichissement est donc une **étape séparée**, déclenchée manuellement via le bouton "Enrichir descriptions" dans l'interface (`POST /api/admin/enrich/trigger`).

#### Aiguillage par source (`enrichOne`)

Un seul service, `JobEnrichmentService`, enrichit les quatre sources gérées ici (rekrute, emploi.ma, Indeed, LinkedIn). marocemploi.net est auto-enrichi à la collecte et n'arrive jamais ici. Comme chaque job board a une structure HTML de détail différente, l'extraction est **aiguillée selon `job.getSource()`** :

```java
// JobEnrichmentService.enrichOne()
if ("emploi.ma".equals(job.getSource())) {
    description  = extractEmploiMaDescription(doc);   // div.job-description + div.job-qualifications
    requirements = extractEmploiMaSkills(doc);         // ul.skills li
} else if ("indeed.ma".equals(job.getSource())) {
    description  = extractIndeedDescription(doc);      // div#jobDescriptionText
    requirements = List.of();                          // pas de liste de skills propre sur Indeed
} else if ("linkedin.com".equals(job.getSource())) {
    description  = extractLinkedInDescription(doc);    // div.show-more-less-html__markup
    requirements = List.of();                          // pas de liste de skills propre sur LinkedIn

    LinkedInCriteria criteria = extractLinkedInCriteria(doc); // seniority / contrat / secteur
    if (criteria.experienceLevel() != null) job.setExperienceLevel(criteria.experienceLevel());
    if (criteria.contractType() != null)    job.setContractType(criteria.contractType());
    if (criteria.sector() != null)          job.setSector(criteria.sector());
} else {
    description  = extractRekruteDescription(doc);     // div.blc "Poste" + "Profil"
    requirements = extractRekruteSkills(doc);          // span.tagSkills
}
```

| Source | Sélecteur description | Sélecteur requirements |
|--------|-----------------------|------------------------|
| rekrute.com | `div.blc` dont le `<h2>` contient "Poste" / "Profil" | `span.tagSkills` |
| emploi.ma | `div.job-description` + `div.job-qualifications` | `ul.skills li` |
| indeed.ma | `div#jobDescriptionText` | *(aucun — Indeed n'expose pas de liste de compétences propre)* |
| linkedin.com | `div.show-more-less-html__markup` | *(aucun — remplacé par `experienceLevel`/`contractType`/`sector` extraits du même passage, voir ci-dessous)* |

Tous réutilisent le même helper de conversion DOM→texte (`divToText` / `nodeToText`, voir plus bas) pour préserver la structure en paragraphes et puces — sauf LinkedIn, qui a sa propre variante (`htmlToText`, voir ci-dessous) car son HTML est principalement constitué de texte et de balises inline (`<br>`, `<strong>`) plutôt que de `<p>` de premier niveau.

#### Ce que l'enrichissement extrait — cas rekrute.com

Le scraper visite la page de détail de chaque offre (`sourceUrl`) et extrait deux sections depuis le HTML :

```html
<!-- Structure HTML de rekrute.com — page détail -->
<div class="col-md-12 blc">
  <h2>Poste :</h2>
  <p>Dans le cadre du développement de notre réseau d'Agences...</p>
  <p>Vos missions sont les suivantes:</p>
  <p>Assurer l'accueil de la clientèle...<br>
     Assurer les travaux d'ouverture...<br>
     Prendre en charge la gestion...</p>
</div>

<div class="col-md-12 blc">
  <h2>Profil recherché :</h2>
  <p>De formation Bac+2/Bac+3...</p>
  <p>Pourquoi nous rejoindre ?<br>
     Intégrer une banque leader...<br>
     Bénéficiez d'un environnement...</p>
</div>

<span class="tagSkills">Volonté de persuasion</span>
<span class="tagSkills">Flexibilité</span>
<span class="tagSkills">Implication au travail</span>
```

#### Préservation de la structure (nodeToText)

Contrairement à `element.text()` de Jsoup qui écrase tout en une seule ligne, l'enrichisseur utilise un parcours récursif du DOM pour **conserver les sauts de ligne** :

```java
// JobEnrichmentService.java
private String nodeToText(Node node) {
    StringBuilder sb = new StringBuilder();
    for (Node child : node.childNodes()) {
        if (child instanceof TextNode) {
            sb.append(((TextNode) child).text());       // texte brut
        } else if (child instanceof Element el) {
            if (el.tagName().equals("br")) {
                sb.append("\n");                         // <br> → saut de ligne
            } else {
                sb.append(nodeToText(el));               // récursion
            }
        }
    }
    return sb.toString();
}
```

Résultat stocké en base pour la section "Poste" :

```
Dans le cadre du développement de notre réseau d'Agences...

Vos missions sont les suivantes:

Assurer l'accueil de la clientèle...
Assurer les travaux d'ouverture...
Prendre en charge la gestion de sa caisse...
Contribuer au dénouement des réclamations...
```

La description finale en base est :

```
{texte Poste}\n\nProfil recherché :\n{texte Profil}
```

#### Ce que l'enrichissement extrait — cas LinkedIn

##### User-Agent dédié — l'erreur HTTP 999

Le User-Agent commun (`HireSync-Bot/1.0`) fonctionne pour les pages de listing, mais les pages de détail `/jobs/view/...` répondent **HTTP 999** (code maison de blocage anti-bot de LinkedIn) à toute requête qui n'a pas l'air d'un vrai navigateur. La solution est un User-Agent **dédié à LinkedIn**, utilisé uniquement pour l'enrichissement de cette source :

```java
// JobEnrichmentService.java
private static final String LINKEDIN_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36";

var connection = Jsoup.connect(job.getSourceUrl()).timeout(12_000);
if ("linkedin.com".equals(job.getSource())) {
    connection.userAgent(LINKEDIN_USER_AGENT)
               .header("Accept-Language", "fr-FR,fr;q=0.9");
} else {
    connection.userAgent(USER_AGENT)
               .referrer("https://www." + job.getSource());
}
```

> ⚠️ Une piste explorée puis abandonnée : forcer le cookie `lang=v=2&lang=fr-FR` pour obtenir directement une page en français. Testé isolément (curl) il fonctionnait, mais une fois utilisé en série sur toutes les offres il a fait retomber **toutes** les requêtes en HTTP 999 — retiré. La traduction des champs "criteria" passe donc par le dictionnaire AR→FR décrit plus bas.

##### Description — `div.show-more-less-html__markup` et `htmlToText`

La description complète d'une offre LinkedIn vit dans `div.show-more-less-html__markup`. Contrairement aux autres sources, ce bloc est surtout composé de **texte et de balises inline** (`<br>`, `<strong>`) avec occasionnellement des `<ul><li>`, plutôt que d'une suite de `<p>` de premier niveau — `divToText`/`nodeToText` ne convient donc pas tel quel. Un helper dédié, `htmlToText`, parcourt récursivement le DOM :

```java
// JobEnrichmentService.java
private void htmlToText(Element el, StringBuilder sb) {
    for (Node node : el.childNodes()) {
        if (node instanceof TextNode tn) {
            sb.append(tn.text());
        } else if (node instanceof Element child) {
            switch (child.tagName()) {
                case "br" -> sb.append("\n");
                case "li" -> { sb.append("\n- "); htmlToText(child, sb); sb.append("\n"); }
                case "ul", "ol", "p" -> { htmlToText(child, sb); sb.append("\n\n"); }
                default -> htmlToText(child, sb);  // <strong> etc. — déballé en place
            }
        }
    }
}
```

Le résultat est ensuite nettoyé (espaces multiples, sauts de ligne en trop) par les mêmes regex de normalisation que les autres sources.

##### Champs "criteria" — `experienceLevel`, `contractType`, `sector`

La page de détail contient une liste `ul.description__job-criteria-list > li.description__job-criteria-item` avec **toujours 4 entrées dans le même ordre** : *Seniority level*, *Employment type*, *Job function*, *Industries*. Le `<h3>` de chaque entrée est un libellé **traduit par LinkedIn selon la locale géo-IP** (arabe pour les IP marocaines, quel que soit le header `Accept-Language`) — donc peu fiable pour matcher par texte. On lit donc **par position** :

```java
// JobEnrichmentService.java
private LinkedInCriteria extractLinkedInCriteria(Document doc) {
    Elements items = doc.select("ul.description__job-criteria-list > li.description__job-criteria-item");

    String experienceLevel = toFrench(criteriaValue(items, 0)); // Seniority level
    String contractType    = toFrench(criteriaValue(items, 1)); // Employment type
    String jobFunction     = toFrench(criteriaValue(items, 2)); // Job function
    String industries      = toFrench(criteriaValue(items, 3)); // Industries

    String sector = (jobFunction != null && industries != null)
            ? jobFunction + " / " + industries
            : jobFunction != null ? jobFunction : industries;

    return new LinkedInCriteria(experienceLevel, contractType, sector);
}
```

`criteriaValue(items, index)` renvoie `null` si l'élément à cette position n'existe pas ou si son `span.description__job-criteria-text` est vide — donc 0 à 4 critères présents sont tous gérés sans erreur, et seuls les champs non-`null` sont écrits sur l'entité `Job` (les valeurs précédentes, si déjà renseignées, sont préservées).

##### Dictionnaire AR→FR (`LINKEDIN_AR_TO_FR`)

Les valeurs "criteria" proviennent d'un **vocabulaire fixe et restreint** (une douzaine de niveaux de séniorité, types de contrat, secteurs...), mais LinkedIn les sert en **arabe pour les requêtes géolocalisées au Maroc**. Plutôt que de risquer un nouveau blocage 999 en bricolant les headers/cookies, `toFrench()` traduit ces termes connus vers le français via une `Map<String, String>` statique :

```java
// JobEnrichmentService.java
private static final Map<String, String> LINKEDIN_AR_TO_FR = Map.ofEntries(
        Map.entry("غير مطبق", "Non précisé"),
        Map.entry("دوام كامل", "Temps plein"),
        Map.entry("تكنولوجيا المعلومات", "Informatique"),
        // ... ~20 entrées au total (séniorité, type de contrat, secteurs)
);

private String toFrench(String value) {
    if (value == null) return null;
    return LINKEDIN_AR_TO_FR.getOrDefault(value, value); // terme inconnu → renvoyé tel quel
}
```

Tout terme absent du dictionnaire (nouvelle valeur jamais vue, ou déjà en français/anglais) est **renvoyé inchangé** — le français est utilisé en priorité quand on sait le traduire, sinon le texte original de LinkedIn est conservé tel quel plutôt que de bloquer l'enrichissement.

#### Comportement du flag `enriched`

| Valeur | Signification |
|--------|--------------|
| `false` (défaut) | Offre collectée, description courte (résumé du listing) |
| `true` | Page détail traitée (ou échec définitif) — voir tolérance aux pannes |

Le endpoint `/api/admin/enrich/trigger` récupère les **20 premières offres** avec `enriched = false` (les plus récentes en premier) et les traite séquentiellement avec **400 ms de délai entre chaque requête** pour ne pas saturer les serveurs sources.

Relancer le trigger plusieurs fois enrichit les lots suivants de 20 jusqu'à ce que `enrichedLeft = 0`.

#### Tolérance aux pannes

Chaque enrichissement est isolé dans un `try/catch` : si la page détail échoue (404, timeout, **HTTP 403 anti-bot côté Indeed** ou **HTTP 999 côté LinkedIn**), l'offre est tout de même marquée `enriched = true` pour ne pas boucler indéfiniment sur une URL cassée.

Pour **indeed.ma**, le 403 est intermittent (rate-limiting selon l'IP sortante). En cas d'échec, l'offre **conserve la description courte issue du `snippet`** récupéré à la collecte — le détail complet reste un *bonus* : si l'IP du serveur n'est pas bloquée (ex : en production), `div#jobDescriptionText` fournit la description complète ; sinon le snippet sert de repli, et l'UI reste fonctionnelle.

Pour **linkedin.com**, le 999 est évité en amont par le `LINKEDIN_USER_AGENT` dédié (voir §4.10 ci-dessus) ; en cas d'échec malgré tout, l'offre garde les champs déjà connus depuis la collecte (`title`, `company`, `location`, `logoUrl`, `postedAt`) et reste affichable, simplement sans description longue ni `experienceLevel`/`contractType`/`sector`.

#### Rendu côté Angular

Le composant `JobDetailComponent` analyse la description stockée et la décompose en sections visuelles :

```
┌──────────────────────────────────────────────────────┐
│  📋 Description du poste                             │
│                                                      │
│  │ 🚀 MISSION & RESPONSABILITÉS                      │  ← barre bleue
│  │                                                   │
│  │  Dans le cadre du développement de notre         │
│  │  réseau d'Agences, nous recrutons...             │
│  │                                                   │
│  │  Vos missions sont les suivantes :               │
│  │  ● Assurer l'accueil de la clientèle...          │
│  │  ● Prendre en charge la gestion de caisse...     │
│  │  ● Contribuer au dénouement des réclamations...  │
│  │                                                   │
│  ─────────────────────────────────────────────────  │
│                                                      │
│  │ 👤 PROFIL RECHERCHÉ                               │  ← barre verte
│  │                                                   │
│  │  De formation Bac+2/Bac+3 type BTS, DUT...       │
│  │  ● Intégrer une banque leader sur le marché...   │
│  │  ● Bénéficiez d'un environnement stimulant...    │
└──────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────┐
│  ✅ Compétences & Traits de personnalité              │
│  ✅ Volonté de persuasion  ✅ Flexibilité             │
│  ✅ Conventionnel          ✅ Implication au travail  │
└──────────────────────────────────────────────────────┘
```

La logique de parsing (TypeScript) :
```typescript
// Coupe sur le marqueur injecté lors de l'enrichissement
const idx = desc.indexOf('Profil recherché :');
const posteText  = desc.slice(0, idx);         // → section Mission
const profilText = desc.slice(idx + marker.length); // → section Profil

// Chaque bloc séparé par \n\n → un paragraphe
// Un paragraphe avec des \n internes → liste de bullet points ●
```

---

### 4.11 Pagination côté serveur

#### Pourquoi côté serveur ?

Le nombre total d'offres en base grossit à chaque scraping. Charger toutes les offres en une seule requête serait inefficace. La pagination est entièrement gérée par **Spring Data** (`Page<T>`) et mappée côté Angular.

#### Backend — Spring Data Page

```java
// JobController.java
@GetMapping("/jobs")
public Page<Job> search(
    @RequestParam(defaultValue = "") String q,
    @RequestParam(defaultValue = "0") int page,
    @RequestParam(defaultValue = "20") int size
) {
    return jobRepository.search(q, PageRequest.of(page, size,
        Sort.by("scrapedAt").descending()));
}
```

```java
// JobRepository.java
@Query("""
    SELECT j FROM Job j
    WHERE :q IS NULL OR :q = ''
       OR LOWER(j.title)    LIKE LOWER(CONCAT('%', :q, '%'))
       OR LOWER(j.company)  LIKE LOWER(CONCAT('%', :q, '%'))
       OR LOWER(j.location) LIKE LOWER(CONCAT('%', :q, '%'))
    """)
Page<Job> search(@Param("q") String q, Pageable pageable);
```

La réponse Spring contient : `content[]`, `totalElements`, `totalPages`, `number`, `size`.

#### Frontend — Angular Signals

```typescript
// job-search.component.ts
currentPage = signal(0);       // page active (0-indexed)
totalPages  = signal(0);       // nombre total de pages

// Pages visibles dans la barre (avec ellipsis "...")
readonly visiblePages = computed((): (number | null)[] => {
  // Si ≤ 7 pages → toutes affichées
  // Sinon → [0, null, cur-1, cur, cur+1, null, last]
  // null = "…" non-cliquable
});

// Reset vers la page 1 à chaque changement de filtre
this.filters.valueChanges.pipe(debounceTime(400)).subscribe(() => {
  this.currentPage.set(0);
  this._search();
});
```

#### Barre de pagination rendue

```
Offres 1–10 sur 47        [<]  [1]  [2]  …  [5]  [>]
```

- **`[<]` / `[>]`** désactivés en début / fin de liste
- Page active surlignée en bleu
- Les numéros intermédiaires se recalculent dynamiquement selon la page courante

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

### Jobs (public — sans authentification)
| Méthode | URL | Paramètres | Description |
|---------|-----|-----------|-------------|
| GET | `/api/jobs` | `?q=&page=0&size=10` | Recherche paginée — `q` filtre titre/entreprise/ville |
| GET | `/api/jobs/{id}` | — | Détail d'une offre (description complète si enrichie) |

### Admin (authentification requise)
| Méthode | URL | Description |
|---------|-----|-------------|
| POST | `/api/admin/scrape/trigger` | Lance le scraping des **5 sources** (rekrute.com 3 pages + emploi.ma 3 pages + Indeed 1 page + LinkedIn 3 pages + marocemploi.net 3 pages via FlareSolverr). Retourne `{newJobsSaved, totalJobsInDb}` |
| POST | `/api/admin/enrich/trigger` | Enrichit les 20 prochaines offres non-enrichies (visite page détail, extraction aiguillée par source). Retourne `{enrichedThisRun, totalEnriched, enrichedLeft}` |

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
│   │   ├── AtsScorer.java                   PDFBox extraction + scoring ATS générique + scoreAgainstJob() (mots-clés offre)
│   │   ├── OpenRouterService.java           WebClient → OpenRouter (Gemma 4, fallbacks, backoff 429, checkCompatibility, extractKeywords)
│   │   └── CvService.java                   Logique métier : upload, activate, delete, optimize
│   ├── messaging/
│   │   ├── OptimizationMessage.java         Record Java (optimizationId, cvId, userId, jobDesc)
│   │   ├── OptimizationProducer.java        Publie dans cv.exchange après commit transaction
│   │   └── OptimizationConsumer.java        @RabbitListener → checkCompatibility → extractKeywords → OpenRouter → AtsScorer
│   ├── dto/
│   │   └── CompatibilityVerdict.java        Record Java (compatible, compatibilityScore, candidateProfile, targetProfile, rejectionReason)
│   └── controller/
│       └── CvController.java                REST endpoints CV (upload, list, optimize, history)
│
├── job/
│   ├── entity/Job.java                      Entité JPA offre d'emploi (+ @ElementCollection requirements)
│   ├── repository/JobRepository.java        search() JPQL paginé, existsBySourceUrl, findTop20ByEnrichedFalse
│   ├── controller/JobController.java        GET /api/jobs, GET /api/jobs/{id}, POST /admin/scrape+enrich
│   ├── dto/                                 JobResponse, ScrapeTriggerResponse, EnrichTriggerResponse
│   ├── messaging/
│   │   ├── ScrapeMessage.java               Record Java (source) — identifie la source à scraper
│   │   ├── ScrapeProducer.java              Publie dans scrape.exchange (1 message par source)
│   │   ├── ScrapeConsumer.java              @RabbitListener → route vers le bon scraper, puis publie dans enrich.exchange
│   │   ├── EnrichMessage.java               Record Java (List<UUID> jobIds) — offres à enrichir
│   │   ├── EnrichProducer.java              Publie dans enrich.exchange après scraping d'une source
│   │   └── EnrichConsumer.java              @RabbitListener → appelle JobEnrichmentService.enrichById(jobIds)
│   └── service/
│       ├── JobService.java                  Orchestrateur — triggerScrape() publie 5 messages RabbitMQ (parallèle), mapping DTO
│       ├── JobScraperService.java           Jsoup → rekrute.com listing (3 pages × ~10 offres)
│       ├── EmploiMaScraperService.java      Jsoup → emploi.ma listing (cartes card-job, 3 pages)
│       ├── IndeedScraperService.java        Jsoup + Jackson → Indeed JSON embarqué (window.mosaic, 1 page)
│       ├── LinkedInScraperService.java      Jsoup → LinkedIn guest endpoint (3 pages × 10 offres)
│       ├── MarocEmploiScraperService.java   FlareSolverr + Jsoup → marocemploi.net (3 pages, enrichissement inline)
│       └── JobEnrichmentService.java        Pages détail, extraction aiguillée par source (description + requirements)
│
├── notification/
│   └── NotificationService.java             convertAndSendToUser → Angular WebSocket
│
└── config/
    ├── SecurityConfig.java                  Spring Security (JWT, CORS, GET /api/jobs public)
    ├── WebSocketConfig.java                 STOMP sur SockJS (/ws/notifications)
    └── RabbitMQConfig.java                  cv.exchange + scrape.exchange + enrich.exchange + DLQ + JSON converter
```

---

*HireSync — PFE 2025/2026 — Othmane Sadiky*
