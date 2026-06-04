export interface Job {
  id: string;
  title: string;
  company: string;
  location: string;
  contractType: ContractType;
  sector: string;
  experienceLevel: ExperienceLevel;
  salary?: string;
  description: string;
  requirements: string[];
  postedAt: string;
  matchScore?: number;
  logoUrl?: string;
  source: string;
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
  page: number;
  size: number;
}
