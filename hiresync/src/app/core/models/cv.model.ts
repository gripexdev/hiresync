export interface CV {
  id: string;
  fileName: string;
  uploadedAt: string;
  atsScore: number;
  isActive: boolean;
  parsedSections?: CVSection[];
}

export interface CVSection {
  title: string;
  content: string;
}

export interface CVOptimizationRequest {
  cvId: string;
  jobId: string;
}

export interface CVOptimizationResult {
  id: string;
  status: OptimizationStatus;
  cvId: string;
  jobId: string;
  jobTitle: string;
  originalScore: number;
  optimizedScore: number;
  suggestedChanges: SuggestedChange[];
  optimizedCvUrl?: string;
  createdAt: string;
  completedAt?: string;
}

export type OptimizationStatus = 'pending' | 'processing' | 'completed' | 'failed';

export interface SuggestedChange {
  type: 'keyword_added' | 'section_rewritten' | 'format_improved' | 'skill_added';
  description: string;
  before?: string;
  after?: string;
}
