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
| Observability | Prometheus + Grafana, backend metrics via Micrometer (`/actuator/prometheus`), RabbitMQ's native `rabbitmq_prometheus` plugin, `postgres-exporter` for DB metrics |
| CI/CD | GitHub Actions (`backend.yml`, `frontend.yml`) — build/typecheck/Docker-build on every push+PR, image publish to GHCR on push to `main`; Dependabot for Maven/npm/Docker/Actions |

---

## Repository layout

This repo has **two sub-projects** under one git root (`Desktop/HireSync`):

| Path | What it is |
|---|---|
| `hiresync/` | Angular 19 SPA (the web app); has its own `Dockerfile` + `nginx.conf` |
| `backend/` | Spring Boot API + scrapers + AI pipeline (runs in Docker) |
| `infra/rabbitmq/` | Custom RabbitMQ image — pre-enables the `rabbitmq_prometheus` plugin on top of `rabbitmq:3.13-management-alpine` |
| `infra/prometheus/prometheus.yml` | Scrape config — targets backend, rabbitmq, postgres-exporter |
| `infra/grafana/provisioning/` | Auto-provisioned datasource (Prometheus) + the `hiresync-overview` dashboard (8 panels) |
| `.github/workflows/` | `backend.yml` + `frontend.yml` — CI build/typecheck + GHCR publish on push to `main` |
| `.github/dependabot.yml` | Weekly update PRs for Maven, npm, the 3 Dockerfiles, and GitHub Actions |
| `docker-compose.yml` | Root-level — orchestrates all 8 services (postgres, rabbitmq, flaresolverr, backend, frontend, postgres-exporter, prometheus, grafana) |
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

## Observability (Prometheus + Grafana)

| Tool | URL | Notes |
|---|---|---|
| Grafana | `http://localhost:3000` | login `admin` / `admin` (change after first login) — dashboard "HireSync — Vue d'ensemble" auto-provisioned |
| Prometheus | `http://localhost:9090` | check `/targets` for scrape health |
| Backend metrics | `:8080/actuator/prometheus` | via Micrometer (`io.micrometer:micrometer-registry-prometheus`); endpoint is `permitAll()` in `SecurityConfig` since it's never exposed outside the Docker network |
| RabbitMQ metrics | `:15692/metrics` | native `rabbitmq_prometheus` plugin, pre-enabled in `infra/rabbitmq/Dockerfile` (keeps the original image's entrypoint logic intact — don't try to enable the plugin via `command:` overrides, that breaks `RABBITMQ_DEFAULT_USER`/`PASS`) |
| Postgres metrics | `:9187/metrics` | `postgres-exporter` sidecar, no changes to the Postgres image |

Dashboard JSON lives at `infra/grafana/provisioning/dashboards/hiresync-overview.json` — edit it directly (or edit-in-Grafana-then-export) rather than building dashboards through the UI from scratch, so changes survive a `docker compose down -v`.

## CI/CD (GitHub Actions)

Two independent workflows, each path-filtered to its own subtree so an unrelated change doesn't burn CI minutes:

- **`backend.yml`** — triggers on `backend/**` changes. `build` job: `./mvnw -B -ntp -DskipTests package` (tests are skipped — see below) → uploads the jar. `docker` job: smoke-builds the backend + RabbitMQ images (not pushed). `publish` job: only on `push` to `main`, builds+pushes `ghcr.io/gripexdev/hiresync-backend` (tags `latest` + `sha-<short>`).
- **`frontend.yml`** — triggers on `hiresync/**` changes. Mirrors the backend structure: `npx tsc --noEmit` + `npm run build`, then Docker smoke-build, then GHCR publish on push to `main` (`ghcr.io/gripexdev/hiresync-frontend`).
- Both use `docker/build-push-action` with GitHub Actions cache (`type=gha`) and have a `workflow_dispatch` trigger for manual re-runs.

**Why backend tests are skipped in CI**: the only test that exists is the boilerplate `HireSyncBackendApplicationTests.contextLoads()`, which boots the full Spring context — and that needs a live Postgres connection that doesn't exist on a bare GitHub Actions runner (fails with `Failed to determine a suitable driver class`). Confirmed by reproducing the build on a clean checkout locally. Same reason `backend/Dockerfile`'s build stage already uses `-DskipTests`. Drop the flag once real Testcontainers-backed tests exist.

**`backend/mvnw` executable bit**: this repo was developed on Windows, where git doesn't track the executable bit reliably — `mvnw` was committed as `100644` (non-executable), which silently breaks `./mvnw` on the Linux CI runner. Fixed via `git update-index --chmod=+x backend/mvnw`; the workflow also defensively runs `chmod +x mvnw` before invoking it. If you ever see "permission denied" on `mvnw` in CI, this is why.

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
- **Do not use `npm ci` in `hiresync/Dockerfile`** — the committed lockfile is missing some optional `@esbuild/*` platform packages, so `npm ci` fails inside the Linux build stage; `npm install` is used instead. Same reason `frontend.yml` CI uses `npm install` too.
- **Do not redeclare `JWT_SECRET`/`GEMINI_API_KEY`/`GROQ_API_KEY`/`OPENROUTER_API_KEY` under the `backend` service's `environment:` block** — they come from `env_file: ./backend/.env`; an `environment:` entry for the same key wins and silently blanks it.
- **Do not try to enable the RabbitMQ Prometheus plugin via a `command:` override** — it bypasses the image's entrypoint script that applies `RABBITMQ_DEFAULT_USER`/`PASS`. Use the custom image in `infra/rabbitmq/Dockerfile` instead.
- **Do not remove `-DskipTests` from `backend.yml`** without first adding a real DB (e.g. Testcontainers) for the `contextLoads()` test — the CI runner has no Postgres, so the full test suite fails immediately otherwise.
- **Do not commit `backend/src/main/resources/application.yml`** — it is gitignored and holds secrets (JWT secret, API keys). Edit `application.yml.example` for documented defaults.
- **Do not reintroduce mock data** — services are wired to the real backend. Keep them real.
- **Do not verify UTF-8 by piping `curl | python` on Windows** — Python reads stdin as cp1252 and shows fake "mojibake" for correct UTF-8. Check raw bytes (`curl … | xxd`) or the rendered browser DOM instead.
- **Do not assume a migrations folder or React Router** — neither exists here (don't confuse with sibling projects).
- Don't commit or push unless explicitly asked.
- **Do not add a `Co-Authored-By: Claude` (or any AI) trailer to commit messages in this repo.** This is an academic (PFE) project — commits must show only the student's authorship.

---

## Working effectively with Claude Code (workflow practices)

**A. Use this CLAUDE.md as persistent project memory.** It is auto-loaded at the start of every session, so you stop re-explaining the project. Keep it current: when the project's architecture, conventions, or "do not" rules change, update this file in the same change.

**B. Use `/compact` instead of starting fresh.** When context fills up, run `/compact` — it summarizes the conversation so far and keeps going in the same session, instead of starting over.

**C. Use `/clear` + memory, not full restarts.** If you need a clean session, `/clear` resets the conversation but keeps this CLAUDE.md loaded — much faster than re-explaining from scratch.

**D. Break work into smaller, scoped sessions.** Scope a session to one feature/task: reference CLAUDE.md for context, do the task, commit, close. This keeps context usage low per session and produces cleaner diffs.

**E. For genuinely huge context needs**, consider whether your plan/subscription tier supports a larger context window. (Details vary — confirm current limits before relying on this.)

---

*HireSync — PFE 2025/2026 — Othmane Sadiky*
