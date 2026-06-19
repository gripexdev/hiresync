export interface Job {
  id: string;
  title: string;
  company: string | null;
  location: string | null;
  // Raw scraped values are free-text ("Temps plein", "CDI & CDD", "Junior (1 à 3 ans)"),
  // so these stay strings; filtering against them is bucketed server-side.
  contractType: string | null;
  sector?: string;
  experienceLevel?: string;
  salary?: string;
  description?: string | null;
  requirements?: string[];
  postedAt?: string;
  scrapedAt?: string;
  matchScore?: number;
  logoUrl?: string;
  source: string;
  sourceUrl?: string;
  applyUrl?: string;
}

export interface JobSearchParams {
  q?: string;
  /** Filter values are bucket keys from /jobs/facets, not raw scraped strings. */
  city?: string;
  contractType?: string;
  sector?: string;
  experienceLevel?: string;
  page?: number;
  size?: number;
}

/** One selectable filter option, backed by real data. */
export interface FacetOption {
  key: string;
  label: string;
  count: number;
}

/** Response of GET /api/jobs/facets */
export interface JobFacets {
  cities: FacetOption[];
  contractTypes: FacetOption[];
  experienceLevels: FacetOption[];
  sectors: FacetOption[];
}

export interface JobSearchResult {
  jobs: Job[];
  total: number;
  totalPages: number;
  page: number;
  size: number;
}
