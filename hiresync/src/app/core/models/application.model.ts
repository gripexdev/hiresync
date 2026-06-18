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
  cvFileName?: string;
  coverNote?: string;
  matchScore?: number;
  updatedAt?: string;
}

/** Body of POST /api/applications/{jobId} */
export interface ApplyRequest {
  cvId: string;
  coverNote?: string;
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
