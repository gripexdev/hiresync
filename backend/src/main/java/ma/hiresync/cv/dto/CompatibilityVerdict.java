package ma.hiresync.cv.dto;

import java.util.List;

/**
 * Result of the pre-flight compatibility check between a CV and a target job.
 *
 * Decides whether optimizing the CV for this job is realistic at all —
 * a software developer applying for a "commercial assistant" role should be
 * stopped, not optimized into a fiction.
 */
public record CompatibilityVerdict(
        boolean      compatible,
        int          score,                 // 0–100 overall fit
        String       verdict,               // short human explanation (French)
        String       candidateProfile,      // profession detected from the CV
        String       targetProfile,         // profession the job targets
        List<String> transferableSkills,    // skills that genuinely carry over
        List<String> missingCriticalSkills  // hard blockers the candidate lacks
) {
    /** Safe fallback used when the LLM check itself fails — fail open (allow optimization). */
    public static CompatibilityVerdict allowByDefault() {
        return new CompatibilityVerdict(
                true, 60,
                "Vérification de compatibilité indisponible — optimisation autorisée par défaut.",
                null, null, List.of(), List.of());
    }
}
