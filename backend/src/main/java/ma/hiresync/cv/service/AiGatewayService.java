package ma.hiresync.cv.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import ma.hiresync.cv.dto.CompatibilityVerdict;
import ma.hiresync.cv.service.ai.GeminiProvider;
import ma.hiresync.cv.service.ai.GroqProvider;
import ma.hiresync.cv.service.ai.LocalModelProvider;
import ma.hiresync.cv.service.ai.OpenRouterProvider;
import ma.hiresync.cv.service.ai.AiProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * AI Gateway — tries providers in priority order until one succeeds:
 *   1. Gemini 2.0 Flash  (Google, free 1 500 req/day)
 *   2. Groq Llama 3.3 70B (free 14 400 req/day, fastest)
 *   3. OpenRouter          (free :free models, existing fallback chain)
 *   4. Local Ollama        (self-hosted, unlimited — disabled by default)
 *
 * All prompt-building and response-parsing logic lives here so the consumer
 * only needs to call optimizeCv() / assessCompatibility() regardless of which
 * provider actually ran.
 */
@Service
@Slf4j
public class AiGatewayService {

    private final List<AiProvider> providers;
    private final ObjectMapper     objectMapper = new ObjectMapper();

    public AiGatewayService(
            GeminiProvider     gemini,
            GroqProvider       groq,
            OpenRouterProvider openRouter,
            LocalModelProvider local
    ) {
        // Order matters — first enabled provider that succeeds wins
        this.providers = List.of(gemini, groq, openRouter, local);
    }

    // ── DTOs ─────────────────────────────────────────────────────────────────

    /** Returned by optimizeCv() so the consumer can log the actual provider used. */
    public record AiResult(String content, String provider) {}

    /** Returned by the admin test endpoint. */
    public record AiTestResult(String provider, String response, long timeMs, List<String> errors) {}

    // ── Public API ────────────────────────────────────────────────────────────

    public AiResult optimizeCv(String cvText, String jobDescription) {
        return optimizeCv(cvText, jobDescription, Set.of());
    }

    /** optimizeCv variant that can force-skip providers — used for per-provider testing. */
    public AiResult optimizeCv(String cvText, String jobDescription, Set<String> skip) {
        String system = "You are an expert ATS CV optimizer. You respond ONLY with a single valid JSON object, no markdown, no commentary.";
        // 4096 tokens: a complete CV (summary + competencies + experience + projects +
        // education + skills + languages + suggestions) overruns 2800 and gets truncated.
        return route(system, buildOptimizePrompt(cvText, jobDescription), 4096, 0.3, skip);
    }

    /**
     * Generate a professional French cover letter / application email tailored to
     * the job, grounded in the candidate's (optimized) CV. Returns raw JSON
     * {"subject": "...", "body": "..."} as the AiResult content.
     *
     * @param candidateName the account holder's real full name, or null/blank if
     *                       unknown — used to sign the letter instead of a placeholder.
     */
    public AiResult generateCoverLetter(String cvText, String jobTitle, String company,
                                         String jobDescription, String candidateName) {
        String system =
            "You are an expert career writer. You write concise, sincere, professional French cover "
          + "letters that read naturally (never robotic or generic). You respond ONLY with a single "
          + "valid JSON object, no markdown, no commentary.";
        return route(system, buildCoverLetterPrompt(cvText, jobTitle, company, jobDescription, candidateName),
                     1100, 0.6, Set.of());
    }

    /**
     * Pre-flight compatibility check. Fails open — if all providers fail or the
     * response can't be parsed, we allow the optimization rather than blocking
     * the user on an infrastructure hiccup.
     */
    public CompatibilityVerdict assessCompatibility(String cvText, String jobTitle, String jobDescription) {
        String system =
            "You are a senior career advisor and recruiter. You judge whether a candidate is a "
          + "realistic, credible applicant for a target job. You respond ONLY with a single valid "
          + "JSON object, no markdown, no commentary.";
        try {
            AiResult result = route(system, buildCompatibilityPrompt(cvText, jobTitle, jobDescription), 700, 0.1, Set.of());
            return parseVerdict(result.content());
        } catch (Exception e) {
            log.warn("Compatibility check failed across all providers ({}) — allowing optimization by default", e.getMessage());
            return CompatibilityVerdict.allowByDefault();
        }
    }

    /**
     * Ad-hoc test call — used by the admin test endpoint.
     * skipProviders contains provider names to force-skip (e.g. "Gemini 2.0 Flash").
     */
    public AiTestResult testCall(String prompt, Set<String> skipProviders) {
        long start = System.currentTimeMillis();
        List<String> errors = new ArrayList<>();
        String system = "You are a helpful assistant. Be concise.";

        for (AiProvider provider : providers) {
            if (!provider.isEnabled()) continue;
            if (skipProviders.contains(provider.name())) {
                log.info("AI test → force-skipping: {}", provider.name());
                errors.add(provider.name() + ": skipped (forced)");
                continue;
            }
            try {
                log.info("AI test → trying: {}", provider.name());
                String content = provider.call(system, prompt, 300, 0.7);
                long ms = System.currentTimeMillis() - start;
                log.info("AI test → success from: {} ({}ms)", provider.name(), ms);
                return new AiTestResult(provider.name(), content, ms, errors);
            } catch (Exception e) {
                String msg = provider.name() + ": " + e.getMessage();
                errors.add(msg);
                log.warn("AI test → provider failed: {}", msg);
            }
        }

        return new AiTestResult("ALL_FAILED", null, System.currentTimeMillis() - start, errors);
    }

    /** List every provider with its enabled status — used by the status endpoint. */
    public List<ProviderStatus> providerStatuses() {
        return providers.stream()
                .map(p -> new ProviderStatus(p.name(), p.isEnabled()))
                .toList();
    }

    public record ProviderStatus(String name, boolean enabled) {}

    // ── Provider routing ──────────────────────────────────────────────────────

    /**
     * Try each enabled provider in order, skipping any in the skip set.
     * Returns as soon as one succeeds; throws when all fail.
     */
    private AiResult route(String systemPrompt, String userPrompt, int maxTokens,
                           double temperature, Set<String> skip) {
        List<String> errors = new ArrayList<>();

        for (AiProvider provider : providers) {
            if (!provider.isEnabled()) {
                log.debug("AI provider '{}' disabled — skipping", provider.name());
                continue;
            }
            if (skip.contains(provider.name())) continue;

            try {
                log.info("AI gateway → trying provider: {}", provider.name());
                String content = provider.call(systemPrompt, userPrompt, maxTokens, temperature);
                log.info("AI gateway → success from provider: {}", provider.name());
                return new AiResult(content, provider.name());
            } catch (Exception e) {
                String msg = provider.name() + ": " + e.getMessage();
                errors.add(msg);
                log.warn("AI gateway → provider failed: {}", msg);
            }
        }

        throw new RuntimeException("All AI providers failed — " + String.join("; ", errors));
    }

    // ── Prompt builders ───────────────────────────────────────────────────────

    private String buildOptimizePrompt(String cvText, String jobDescription) {
        return """
            You are an expert ATS resume optimizer. Optimize a CV for a specific job so it ranks
            highly in Applicant Tracking Systems. Return ONE JSON object with THREE keys:
            "atsKeywords", "optimizedCv" and "suggestions".

            ## ORIGINAL CV TEXT:
            %s

            ## TARGET JOB DESCRIPTION:
            %s

            ## OUTPUT FORMAT (return ONLY this JSON object, no markdown fences):
            {
              "atsKeywords": ["the 12-18 most important ATS keywords from the JOB DESCRIPTION: hard skills, tools, technologies, certifications, methodologies and role-specific terms — use the job's EXACT wording"],
              "optimizedCv": {
                "fullName": "candidate full name extracted from the CV",
                "jobTitle": "a professional title that mirrors the target job title (French)",
                "contact": { "email": "", "phone": "", "location": "", "linkedin": "" },
                "summary": "2-3 sentence professional summary targeting the job, front-loading the most important job keywords (French)",
                "coreCompetencies": ["8-12 keywords from atsKeywords that the candidate can TRUTHFULLY claim, exact JD wording"],
                "experience": [
                  { "role": "", "company": "", "dates": "", "bullets": ["action verb + what + quantified result, weaving in job keywords"] }
                ],
                "projects": [
                  { "name": "project name from the CV", "description": "1-2 sentences in French, weaving in relevant job keywords", "technologies": ["tech1", "tech2"] }
                ],
                "education": [ { "degree": "", "school": "", "dates": "" } ],
                "certifications": ["any certifications present in the CV — omit the key entirely if none"],
                "skills": ["skills using the job's exact terminology; include full term + abbreviation e.g. 'Intégration Continue (CI/CD)'"],
                "languages": ["Francais (Professionnel)", "Anglais (Professionnel)"]
              },
              "suggestions": [
                { "type": "keyword_added|section_rewritten|format_improved|skill_added",
                  "description": "what was improved (French, concise)",
                  "before": "original text (optional)",
                  "after": "improved text (optional)" }
              ]
            }

            ## COMPLETENESS RULES (CRITICAL — the #1 cause of bad output is DROPPING content):
            - PRESERVE EVERYTHING. The optimized CV must contain EVERY education entry, EVERY language (with its level), EVERY project, EVERY professional experience, and ALL real skills found in the original CV. You REWRITE and REORGANIZE — you NEVER delete a factual entry.
            - "education": include ALL diplomas/degrees from the original, newest first. The original may list 3-4 — return all of them.
            - "languages": include ALL languages with their proficiency level exactly as stated (e.g. "Arabe (Langue maternelle)"). Never drop a language.
            - "projects": include ALL academic/personal projects from the original, each with its real technologies. Never drop the projects section.
            - "skills": keep the FULL breadth of the candidate's real skills, reorganized to surface job-relevant ones first. Do not shrink a rich skills list down to a handful.
            - "experience": include every real job (up to 4). Write 1-3 bullets per job grounded in what the original actually describes — rephrase with strong action verbs and job keywords. Do NOT pad with generic filler ("travail en équipe", "développement d'applications performantes"); a single specific bullet is better than three vague ones.

            ## ATS RULES (follow precisely):
            - KEYWORDS ARE #1: mirror the job's EXACT terminology. If the JD says "gestion de projet", use that, not a synonym. Include full term + abbreviation where relevant: "Search Engine Optimization (SEO)".
            - Incorporate every keyword from "atsKeywords" that the candidate can TRUTHFULLY claim into coreCompetencies, skills, summary and experience bullets. Place the most important keywords in the summary and coreCompetencies (higher ATS weight).
            - Quantify achievements: action verb + concrete result with a number/percentage wherever the original supports it.
            - TRUTHFUL ONLY: never invent jobs, degrees, tools or numbers absent from the original CV. If a job keyword is not credible for this candidate, leave it out of coreCompetencies/skills (it stays in atsKeywords as a gap).
            - All rewritten text in French. Max 6 suggestions.
            - "type" in suggestions MUST be exactly one of: keyword_added, section_rewritten, format_improved, skill_added. Never invent other type values.
            - Extract real contact info from the CV text if present.
            """.formatted(
                cvText.substring(0, Math.min(cvText.length(), 4000)),
                jobDescription.substring(0, Math.min(jobDescription.length(), 1500))
            );
    }

    private String buildCoverLetterPrompt(String cvText, String jobTitle, String company,
                                           String jobDescription, String candidateName) {
        boolean hasName = candidateName != null && !candidateName.isBlank();
        // French cover-letter convention: the closing formula is followed by the
        // candidate's full real name on its own line — never a first name alone or
        // an abbreviation. Only fall back to a placeholder when we truly don't know it.
        String signatureRule = hasName
            ? "Sign the letter with the candidate's EXACT real name on its own line after the closing "
            + "formula: \"%s\". Do not alter, translate, or abbreviate it.".formatted(candidateName)
            : "We don't have the candidate's real name — sign with the placeholder \"[Votre nom]\" on its "
            + "own line after the closing formula.";

        return """
            Write a professional cover letter / application email IN FRENCH for this candidate applying
            to the target job. It must be ready to send: the candidate will paste it into an email or
            attach it. Ground every claim in the candidate's real CV — never invent experience.

            ## CANDIDATE CV (already optimized for this job):
            %s

            ## TARGET JOB TITLE:
            %s

            ## COMPANY:
            %s

            ## JOB DESCRIPTION:
            %s

            ## RULES:
            - French, professional but warm and human — not robotic, not generic filler.
            - 3 short paragraphs max: (1) why this role/company, (2) the 2-3 most relevant strengths
              tied to the job's needs, (3) a courteous call to action (availability for an interview).
            - Reference the actual job title and company. Use real skills/experience from the CV.
            - The "body" must include a greeting (e.g. "Madame, Monsieur,") and end with a polite closing
              formula (e.g. "Cordialement,"). %s
            - The "subject" is a concise email subject line in French (e.g. "Candidature — <poste>").
            - Keep the whole body under ~220 words.

            ## OUTPUT (return ONLY this JSON object, no markdown fences):
            {
              "subject": "email subject line in French",
              "body": "full letter text in French, with \\n line breaks between paragraphs"
            }
            """.formatted(
                cvText == null ? "" : cvText.substring(0, Math.min(cvText.length(), 3500)),
                jobTitle == null ? "" : jobTitle,
                company == null ? "" : company,
                jobDescription == null ? "" : jobDescription.substring(0, Math.min(jobDescription.length(), 1500)),
                signatureRule
            );
    }

    private String buildCompatibilityPrompt(String cvText, String jobTitle, String jobDescription) {
        return """
            Assess whether this candidate is a CREDIBLE, REALISTIC applicant for the target job.

            ## CANDIDATE CV:
            %s

            ## TARGET JOB TITLE:
            %s

            ## TARGET JOB DESCRIPTION:
            %s

            ## DECISION RULES:
            - "compatible": true ONLY if the candidate's background makes them a believable applicant —
              the SAME profession, an ADJACENT/transferable specialization, or a NATURAL next career step.
              Examples that ARE compatible: Software Developer → DevOps Engineer, Backend Dev → Full Stack Dev,
              Accountant → Financial Analyst, Sales Rep → Account Manager.
            - "compatible": false if the role belongs to a FUNDAMENTALLY DIFFERENT profession requiring skills,
              training, or domain knowledge the candidate does NOT demonstrate.
              Examples that are NOT compatible: Software Developer → Commercial Assistant,
              Software Developer → Nurse, Chef → Software Architect, Accountant → Graphic Designer.
            - Do NOT reward a CV simply for being well written. Judge the PROFESSION FIT, not formatting.
            - "score": 0–100 overall fit. Be honest and strict. A fundamentally wrong profession scores under 30.

            ## OUTPUT (return ONLY this JSON object, no markdown fences):
            {
              "compatible": true,
              "score": 0,
              "verdict": "one or two sentences in French explaining the decision",
              "candidateProfile": "the candidate's profession, in French",
              "targetProfile": "the job's profession, in French",
              "transferableSkills": ["skills that genuinely carry over, in French"],
              "missingCriticalSkills": ["critical skills/qualifications the candidate lacks, in French"]
            }
            """.formatted(
                cvText.substring(0, Math.min(cvText.length(), 3000)),
                jobTitle == null ? "" : jobTitle,
                jobDescription.substring(0, Math.min(jobDescription.length(), 1200))
            );
    }

    // ── Response parsers ──────────────────────────────────────────────────────

    private CompatibilityVerdict parseVerdict(String raw) {
        if (raw == null || raw.isBlank()) return CompatibilityVerdict.allowByDefault();
        String json = raw.trim()
                .replaceAll("(?s)^```[a-zA-Z]*\\s*", "")
                .replaceAll("(?s)\\s*```$", "")
                .trim();
        try {
            JsonNode n = objectMapper.readTree(json);
            int score = n.path("score").asInt(60);
            return new CompatibilityVerdict(
                n.path("compatible").asBoolean(true),
                Math.max(0, Math.min(100, score)),
                n.path("verdict").asText(""),
                n.path("candidateProfile").asText(null),
                n.path("targetProfile").asText(null),
                toStringList(n.path("transferableSkills")),
                toStringList(n.path("missingCriticalSkills"))
            );
        } catch (Exception e) {
            log.warn("Could not parse compatibility verdict JSON: {}", e.getMessage());
            return CompatibilityVerdict.allowByDefault();
        }
    }

    private List<String> toStringList(JsonNode arr) {
        List<String> out = new ArrayList<>();
        if (arr != null && arr.isArray()) {
            arr.forEach(el -> {
                String s = el.asText("").trim();
                if (!s.isEmpty()) out.add(s);
            });
        }
        return out;
    }

    /** Used in development when no API keys are configured at all. */
    public String mockOptimization() {
        return """
            {
              "optimizedCv": {
                "fullName": "Othmane Sadiky",
                "jobTitle": "Ingenieur DevOps / Full Stack",
                "contact": { "email": "othmane@hiresync.ma", "phone": "+212 600 000 000", "location": "Casablanca", "linkedin": "" },
                "summary": "Ingenieur Full Stack avec 3 ans d'experience, specialise en automatisation CI/CD et orchestration Kubernetes.",
                "experience": [
                  { "role": "Developpeur Full Stack", "company": "Maroc Telecom", "dates": "2023-2026",
                    "bullets": ["Developpe des applications web pour 3M+ abonnes avec Angular et Spring Boot", "Mise en place de pipelines CI/CD reduisant le temps de deploiement de 40%"] }
                ],
                "education": [ { "degree": "Cycle Ingenieur GSI", "school": "ENSA Agadir", "dates": "2021-2026" } ],
                "skills": ["Angular", "Spring Boot", "Docker", "Kubernetes", "Terraform", "CI/CD"],
                "languages": ["Francais", "Anglais", "Arabe"]
              },
              "suggestions": [
                {"type":"keyword_added","description":"Ajout des mots-cles manquants identifies dans l'offre","after":"Kubernetes, Terraform, CI/CD"},
                {"type":"section_rewritten","description":"Reformulation du resume pour cibler le poste","before":"Developpeur experimente.","after":"Ingenieur DevOps avec expertise en automatisation CI/CD."},
                {"type":"format_improved","description":"Reorganisation des sections selon les standards ATS"},
                {"type":"skill_added","description":"Ajout des competences techniques manquantes"}
              ]
            }
            """;
    }
}
