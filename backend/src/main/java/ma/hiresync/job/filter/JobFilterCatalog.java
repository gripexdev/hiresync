package ma.hiresync.job.filter;

import java.util.List;

/**
 * Single source of truth that turns clean, user-facing filter buckets into the
 * messy keyword variants actually present in the scraped data.
 *
 * <p>Scraped fields are free-text and wildly inconsistent — a single
 * {@code experience_level} column holds everything from {@code "Junior (1 à 3 ans)"}
 * to {@code "Débutant < 2 ans & Expérience entre 2 ans et 5 ans"}, and
 * {@code contract_type} holds {@code "CDI"}, {@code "Temps plein"} and
 * {@code "CDI & CDD"} alike. Rather than try to normalise every variant into a
 * fixed column (and lose the multi-value cases), each bucket maps to a list of
 * lowercase keywords, and a job matches a bucket if its raw field <em>contains</em>
 * any keyword. Substring matching means a multi-value row such as
 * {@code "CDI & CDD"} correctly appears under <em>both</em> the CDI and CDD
 * buckets, and a row like {@code "Casablanca-Mohammedia"} appears under
 * Casablanca — exactly the behaviour a job-seeker expects.
 *
 * <p>Keys are stable identifiers exchanged with the client; labels are display
 * text. Both the search filtering ({@link JobSpecifications}) and the facet
 * counts read from this catalog, so the dropdowns and the WHERE clause can never
 * drift apart.
 */
public final class JobFilterCatalog {

    private JobFilterCatalog() {}

    public record Bucket(String key, String label, List<String> keywords) {}

    // ── Experience ───────────────────────────────────────────────────────────
    // Collapses 20+ raw strings (years-ranges, FR seniority labels, compounds)
    // into five buckets ordered from least to most experienced.
    public static final List<Bucket> EXPERIENCE = List.of(
        new Bucket("etudiant", "Étudiant / Jeune diplômé",
            List.of("etudiant", "étudiant", "jeune diplôm", "stagiaire")),
        new Bucket("junior", "Débutant / Junior (0-3 ans)",
            List.of("débutant", "debutant", "junior", "-1 an", "< 2 ans", "moins de 2", "1 à 3 ans")),
        new Bucket("intermediaire", "Intermédiaire (2-5 ans)",
            List.of("intermédiaire", "intermediaire", "entre 2 ans et 5 ans", "2 à 5", "3 à 5 ans")),
        new Bucket("confirme", "Confirmé (5-10 ans)",
            List.of("confirmé", "confirme", "entre 5 ans et 10 ans", "5 à 10")),
        new Bucket("senior", "Senior / Expert (10+ ans)",
            List.of("senior", "expert", "> 10 ans", "plus de 10", "10 à 20"))
    );

    // ── Contract type ──────────────────────────────────────────────────────────
    public static final List<Bucket> CONTRACT = List.of(
        new Bucket("cdi", "CDI", List.of("cdi")),
        new Bucket("cdd", "CDD", List.of("cdd")),
        new Bucket("stage", "Stage", List.of("stage")),
        new Bucket("interim", "Intérim", List.of("intérim", "interim")),
        new Bucket("freelance", "Freelance", List.of("freelance")),
        new Bucket("alternance", "Alternance", List.of("alternance")),
        new Bucket("temps-plein", "Temps plein", List.of("temps plein")),
        new Bucket("temps-partiel", "Temps partiel", List.of("temps partiel")),
        new Bucket("anapec", "ANAPEC", List.of("anapec"))
    );

    // ── City ───────────────────────────────────────────────────────────────────
    // Keyword is the city name; substring matching folds in the region/suffix
    // variants ("Casablanca-Mohammedia", "Rabat-Salé-Kénitra", "TANGER").
    public static final List<Bucket> CITY = List.of(
        new Bucket("casablanca", "Casablanca", List.of("casablanca")),
        new Bucket("rabat", "Rabat", List.of("rabat")),
        new Bucket("tanger", "Tanger", List.of("tanger")),
        new Bucket("marrakech", "Marrakech", List.of("marrakech")),
        new Bucket("agadir", "Agadir", List.of("agadir")),
        new Bucket("fes", "Fès", List.of("fès", "fes")),
        new Bucket("meknes", "Meknès", List.of("meknès", "meknes")),
        new Bucket("kenitra", "Kénitra", List.of("kénitra", "kenitra")),
        new Bucket("mohammedia", "Mohammedia", List.of("mohammedia", "mohammédia")),
        new Bucket("oujda", "Oujda", List.of("oujda")),
        new Bucket("tetouan", "Tétouan", List.of("tétouan", "tetouan")),
        new Bucket("el-jadida", "El Jadida", List.of("el jadida")),
        new Bucket("nador", "Nador", List.of("nador")),
        new Bucket("remote", "Remote / Télétravail", List.of("remote", "télétravail", "teletravail"))
    );

    // ── Sector ───────────────────────────────────────────────────────────────────
    // Folds 50+ free-text sectors (many compound, e.g.
    // "Comptabilité / Audit et finance / Services de ressources humaines") into a
    // handful of families. A compound raw value can legitimately match several
    // families at once.
    public static final List<Bucket> SECTOR = List.of(
        new Bucket("it", "Informatique / IT",
            List.of("informatique", "digital", "multimédia", "internet", "logiciel", "ingénierie et informatique")),
        new Bucket("call-center", "Centres d'appels / Relation client",
            List.of("centre d'appel", "centres d'appel", "relation client")),
        new Bucket("finance", "Finance / Banque / Assurance",
            List.of("banque", "finance", "assurance", "courtage", "comptabilité", "audit")),
        new Bucket("commerce", "Commerce / Vente / Distribution",
            List.of("vente", "commerce", "commercial", "distribution")),
        new Bucket("industrie", "Industrie / Production",
            List.of("industrie", "production", "fabrication", "métallurgie", "sidérurgie", "électr", "mécanique")),
        new Bucket("btp", "BTP / Construction",
            List.of("btp", "construction", "génie civil")),
        new Bucket("transport", "Transport / Automobile / Aéronautique",
            List.of("automobile", "aéronautique", "spatial", "transport", "logistique", "messagerie")),
        new Bucket("telecom", "Télécommunications",
            List.of("telecom", "télécom")),
        new Bucket("energie", "Énergie / Pétrole & Gaz / Mines",
            List.of("énergie", "energie", "pétrole", "gaz", "mining", "mines")),
        new Bucket("rh", "RH / Recrutement / Intérim",
            List.of("ressources humaines", "recrutement", "intérim")),
        new Bucket("sante", "Santé / Pharmacie",
            List.of("santé", "pharmacie", "médical", "hôpita")),
        new Bucket("education", "Éducation / Formation",
            List.of("éducation", "education", "formation", "enseignement")),
        new Bucket("hotellerie", "Hôtellerie / Tourisme / Restauration",
            List.of("hôtellerie", "hotellerie", "restauration", "tourisme", "voyage", "loisirs")),
        new Bucket("agro", "Agroalimentaire / Agriculture",
            List.of("agroalimentaire", "agriculture", "environnement"))
    );

    /** Resolves a client-supplied bucket key into its keyword list (empty if unknown/blank). */
    public static List<String> keywordsFor(List<Bucket> buckets, String key) {
        if (key == null || key.isBlank()) return List.of();
        return buckets.stream()
                .filter(b -> b.key().equalsIgnoreCase(key))
                .findFirst()
                .map(Bucket::keywords)
                .orElse(List.of());
    }
}
