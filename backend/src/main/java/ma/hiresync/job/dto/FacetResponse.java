package ma.hiresync.job.dto;

import java.util.List;

/**
 * The set of filter options actually backed by data, with live counts.
 * Powers the search dropdowns so they only ever offer buckets that return
 * results, sorted by how many jobs each matches.
 */
public record FacetResponse(
        List<Facet> cities,
        List<Facet> contractTypes,
        List<Facet> experienceLevels,
        List<Facet> sectors
) {
    public record Facet(String key, String label, long count) {}
}
