package ma.hiresync.job.filter;

import jakarta.persistence.criteria.Predicate;
import ma.hiresync.job.entity.Job;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the dynamic {@link Specification} for a job search. Each criterion is
 * optional: a blank value or an unknown bucket key resolves to {@code null} and
 * is silently dropped from the {@code AND} chain (Spring Data treats a
 * {@code null} specification as "no restriction").
 */
public final class JobSpecifications {

    private JobSpecifications() {}

    public static Specification<Job> build(JobSearchCriteria c) {
        Specification<Job> spec = Specification.where(keyword(c.q()));
        spec = spec.and(anyKeyword("location",        JobFilterCatalog.keywordsFor(JobFilterCatalog.CITY, c.city())));
        spec = spec.and(anyKeyword("contractType",    JobFilterCatalog.keywordsFor(JobFilterCatalog.CONTRACT, c.contractType())));
        spec = spec.and(anyKeyword("experienceLevel", JobFilterCatalog.keywordsFor(JobFilterCatalog.EXPERIENCE, c.experienceLevel())));
        spec = spec.and(anyKeyword("sector",          JobFilterCatalog.keywordsFor(JobFilterCatalog.SECTOR, c.sector())));
        return spec;
    }

    /** Free-text match across title / company / location. */
    private static Specification<Job> keyword(String q) {
        if (q == null || q.isBlank()) return null;
        String like = "%" + q.trim().toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("title")),    like),
                cb.like(cb.lower(root.get("company")),  like),
                cb.like(cb.lower(root.get("location")), like)
        );
    }

    /** Matches when the raw {@code field} contains <em>any</em> of the keywords. */
    private static Specification<Job> anyKeyword(String field, List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) return null;
        return (root, query, cb) -> {
            List<Predicate> ors = new ArrayList<>(keywords.size());
            for (String kw : keywords) {
                ors.add(cb.like(cb.lower(root.get(field)), "%" + kw.toLowerCase() + "%"));
            }
            return cb.or(ors.toArray(new Predicate[0]));
        };
    }
}
