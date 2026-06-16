package ma.hiresync.job.filter;

/**
 * Optional search filters coming off the query string. Any field may be
 * {@code null}/blank, in which case it contributes no restriction.
 *
 * <p>{@code city}, {@code contractType}, {@code experienceLevel} and
 * {@code sector} carry <em>bucket keys</em> from {@link JobFilterCatalog}
 * (e.g. {@code "junior"}, {@code "cdi"}), not raw scraped values.
 */
public record JobSearchCriteria(
        String q,
        String city,
        String contractType,
        String experienceLevel,
        String sector
) {}
