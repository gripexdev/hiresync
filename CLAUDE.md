# CLAUDE.md

Guidance for Claude Code (and any AI session) working in this repository. **Read this first every session** — it is the persistent project memory so you don't have to re-explain the project each time. For exhaustive feature detail, see [README.md](README.md) (≈1700 lines, French).

---

## What this project is

**HireSync** is an intelligent recruitment platform for the Moroccan market (PFE 2025/2026). Candidates upload their CV, get an **ATS score**, and run **AI-powered optimization** of their CV against a specific job offer (before/after ATS scoring, keyword matching, structured rewrite). Job offers are **scraped** from multiple Moroccan sources (rekrute.com, emploi.ma, Indeed, LinkedIn, marocemploi.net), enriched, and made searchable. Candidates apply, track applications on a **Kanban board**, and receive **notifications** on real events (optimization done/failed, application status changes).

The AI optimization runs **asynchronously** through a RabbitMQ pipeline and falls back across multiple LLM providers (Gemini → Groq → OpenRouter → local Ollama). Results are pushed live to the browser over WebSocket/STOMP, with polling as a reliable fallback.

---

## Tech stack

| Layer | Tech |
|---|---|
| Frontend | Angular 19 (standalone components, **signals**, `@for`/`@if` control flow, Angular Router with lazy `loadComponent`), Angular Material, TailwindCSS, RxJS, `@stomp/stompjs` |
| Backend | Spring Boot 3.5 / **Java 21**, Spring Data JPA, Spring Security + JWT (JJWT 0.12, **HS384**), Spring WebSocket (STOMP), Spring AMQP |
| Data / infra | PostgreSQL 16, RabbitMQ, FlareSolverr (Cloudflare bypass for scraping), backend, and frontend (Nginx) — all via one root **Docker Compose** stack |
| AI | Multi-provider gateway: Gemini 2.0 Flash, Groq Llama 3.3 70B, OpenRouter (`:free`), local Ollama (off by default) |

---

## Repository layout

This repo has **two sub-projects** under one git root (`Desktop/HireSync`):

| Path | What it is |
|---|---|
| `hiresync/` | Angular 19 SPA (the web app); has its own `Dockerfile` + `nginx.conf` |
| `backend/` | Spring Boot API + scrapers + AI pipeline (runs in Docker) |
| `docker-compose.yml` | Root-level — orchestrates all 5 services (postgres, rabbitmq, flaresolverr, backend, frontend) |
| `README.md` | Full French documentation of every feature |

### Frontend (`hiresync/src/app/`)
- `core/` — cross-cutting: `auth/` (guard, interceptor, `AuthService`), `services/` (HTTP services per domain), `models/` (TS interfaces incl. shared `page.model.ts`)
- `features/` — one folder per page/feature (dashboard, jobs, cv, applications, notifications, auth, landing)
- `shared/components/` — reusable UI (`paginator`, `status-badge`, dialogs, sidebar, topbar)
- `layout/` — `app-shell.component` (authenticated sidebar layout)
- Routing: `app.routes.ts` — public routes + protected routes behind `authGuard` inside the shell

### Backend (`backend/src/main/java/ma/hiresync/`)
- `auth/` — User entity, JWT service/filter, register/login
- `cv/` — CV upload, ATS scoring, AI optimization (entities, `messaging/` RabbitMQ producer+consumer, `service/ai/` provider chain, controller)
- `job/` — Job entity, scrapers (`service/*ScraperService`), enrichment, scrape/enrich RabbitMQ pipeline
- `application/` — job applications (apply, status updates, paginated lists)
- `notification/` — persisted notifications (`entity/`, `repository/`, `dto/`, controller) + live WebSocket push
- `config/` — `SecurityConfig`, `WebSocketConfig`, `RabbitMQConfig`

---

## Commands

```bash
# Full stack — DOCKERIZED, run from repo root (Desktop/HireSync)
docker compose up -d                                    # start all 5 services: postgres, rabbitmq, flaresolverr, backend, frontend
docker compose up -d --build --force-recreate backend   # REBUILD after backend code changes
docker compose up -d --build --force-recreate frontend  # REBUILD after frontend code changes (slower — full ng build inside the image)

# Frontend hot-reload dev loop (from hiresync/) — faster than rebuilding the container for every change
npm install
npm start -- --port 4201      # dev server → http://localhost:4201, talks to the dockerized backend on :8080
npm run build                 # production build (ng build) — must pass before "done"
npx tsc --noEmit              # quick type-check

./mvnw -q -DskipTests compile  # backend compile-check on host (does NOT affect the running container)
```

- Frontend (containerized, Nginx) → `http://localhost:4200` — reverse-proxies `/api` and `/ws` to the backend container, so the browser only ever talks to one origin.
- Backend → `http://localhost:8080` · RabbitMQ UI → `http://localhost:15672` (hiresync / hiresync123) · Postgres → `5432`.
- DB schema is auto-created by Hibernate (`ddl-auto: update`). **There is no migrations folder** — new JPA entities auto-create their tables.
- Backend secrets come from `backend/.env` via `env_file:` in the root `docker-compose.yml` — do **not** also redeclare them under that service's `environment:` block, since explicit `environment:` entries silently override `env_file` values with blanks.

---

## Key conventions

- **Frontend state**: Angular **signals** everywhere (`signal`, `computed`, `.set/.update`), not RxJS `BehaviorSubject` for component state. Components are standalone; use `inject()`.
- **Server-side pagination** is the norm for every list. Backend finders return Spring `Page<T>`; frontend maps it via `core/models/page.model.ts` and renders with `shared/components/paginator`. Endpoints take `?page=&size=` (+ optional `status`/`q`). See README §4.11.
- **DTOs**: Java `record` types with a static `from(entity)` factory. Entities use Lombok (`@Getter @Setter @Builder`) + `@GeneratedValue(strategy = UUID)` + `Instant` timestamps.
- **Controllers** extract the user via `extractUserId(authHeader)` → `jwtService.extractUserId(token)`; all `/api/**` is authenticated except `/api/auth/**`, `/api/jobs` (GET), `/ws/**`, health.
- **JWT**: HS384; the secret is **base64-decoded** to build the HMAC key (`Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret))`). Frontend stores `hs_token` + `hs_user` in localStorage.
- **Async work** (CV optimization, scraping, enrichment) goes through RabbitMQ producers/consumers, never inline in the request thread.
- **Notifications** are created in the same transaction as the triggering event (optimization consumer, `updateStatus`). Types serialize lowercase to match the Angular union.
- **French UI text** in source files (accents, « »). Files are UTF-8; the backend serves correct UTF-8.

## Do NOT

- **Do not run the backend with `mvnw`/`java -jar` expecting it to change the live app** — the running backend is the Docker container. After backend edits you **must** `docker compose up -d --build --force-recreate backend`, or the change won't apply.
- **Do not run `docker compose` from `backend/`** — the compose file lives at the repo root now (it orchestrates the frontend too). The old `backend/docker-compose.yml` was removed.
- **Do not use `npm ci` in `hiresync/Dockerfile`** — the committed lockfile is missing some optional `@esbuild/*` platform packages, so `npm ci` fails inside the Linux build stage; `npm install` is used instead.
- **Do not commit `backend/src/main/resources/application.yml`** — it is gitignored and holds secrets (JWT secret, API keys). Edit `application.yml.example` for documented defaults.
- **Do not reintroduce mock data** — services are wired to the real backend. Keep them real.
- **Do not verify UTF-8 by piping `curl | python` on Windows** — Python reads stdin as cp1252 and shows fake "mojibake" for correct UTF-8. Check raw bytes (`curl … | xxd`) or the rendered browser DOM instead.
- **Do not assume a migrations folder or React Router** — neither exists here (don't confuse with sibling projects).
- Don't commit or push unless explicitly asked.

---

## Working effectively with Claude Code (workflow practices)

**A. Use this CLAUDE.md as persistent project memory.** It is auto-loaded at the start of every session, so you stop re-explaining the project. Keep it current: when the project's architecture, conventions, or "do not" rules change, update this file in the same change.

**B. Use `/compact` instead of starting fresh.** When context fills up, run `/compact` — it summarizes the conversation so far and keeps going in the same session, instead of starting over.

**C. Use `/clear` + memory, not full restarts.** If you need a clean session, `/clear` resets the conversation but keeps this CLAUDE.md loaded — much faster than re-explaining from scratch.

**D. Break work into smaller, scoped sessions.** Scope a session to one feature/task: reference CLAUDE.md for context, do the task, commit, close. This keeps context usage low per session and produces cleaner diffs.

**E. For genuinely huge context needs**, consider whether your plan/subscription tier supports a larger context window. (Details vary — confirm current limits before relying on this.)

---

*HireSync — PFE 2025/2026 — Othmane Sadiky*
