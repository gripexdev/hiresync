export interface Application {
  id: string;
  jobId: string;
  jobTitle: string;
  company: string;
  location: string;
  appliedAt: string;
  status: ApplicationStatus;
  nextAction?: string;
  notes?: string;
  cvId?: string;
  matchScore?: number;
}

export type ApplicationStatus =
  | 'applied'
  | 'in_review'
  | 'interview'
  | 'offer'
  | 'rejected';

export interface ApplicationStats {
  total: number;
  pending: number;
  interviews: number;
  offers: number;
  rejected: number;
}
