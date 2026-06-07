export interface Job {
  id: string;
  title: string;
  company: string | null;
  location: string | null;
  contractType: ContractType | null;
  sector?: string;
  experienceLevel?: ExperienceLevel;
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

export type ContractType = 'CDI' | 'CDD' | 'Stage' | 'Freelance' | 'Alternance';
export type ExperienceLevel = 'Junior' | 'Mid' | 'Senior' | 'Manager' | 'Director';

export interface JobSearchParams {
  q?: string;
  location?: string;
  contractType?: ContractType;
  sector?: string;
  experienceLevel?: ExperienceLevel;
  page?: number;
  size?: number;
}

export interface JobSearchResult {
  jobs: Job[];
  total: number;
  totalPages: number;
  page: number;
  size: number;
}
