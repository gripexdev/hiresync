// ── CV entity ─────────────────────────────────────────────────────────────────
export interface CV {
  id: string;
  fileName: string;
  fileSize?: number;       // bytes
  mimeType?: string;
  uploadedAt: string;
  atsScore: number;
  isActive: boolean;
  parsedSections?: CVSection[];
  optimizationCount?: number;
}

export interface CVSection {
  title: string;
  content: string;
}

// ── Upload ────────────────────────────────────────────────────────────────────
export interface CVUploadResponse {
  id: string;
  fileName: string;
  fileSize: number;
  uploadedAt: string;
  atsScore: number;        // initial ATS parse result from backend
  isActive: boolean;
  parsedSections: CVSection[];
}

// ── Optimization request / response ──────────────────────────────────────────
export interface CVOptimizationRequest {
  cvId: string;
  jobId: string;
  jobTitle: string;
  company: string;
  jobDescription: string;
}

/** Response to POST /api/cv/optimize — job queued asynchronously via RabbitMQ */
export interface CVOptimizationTriggerResponse {
  optimizationId: string;
  status: 'queued';
  message: string;
}

/** Full result fetched from GET /api/cv/optimize/{id} or via WebSocket push */
export interface CVOptimizationResult {
  id: string;
  status: OptimizationStatus;
  cvId: string;
  jobId: string;
  jobTitle: string;
  originalScore: number;
  optimizedScore: number;
  // ── Pre-flight compatibility check (CV ↔ job) ──
  compatibilityScore: number;
  rejectionReason?: string;     // set when status === 'rejected'
  candidateProfile?: string;    // profession detected from the CV
  targetProfile?: string;       // profession the job targets
  // ── ATS keyword analysis (CV ↔ job description) ──
  matchedKeywords?: string[];   // job keywords now present in the CV
  missingKeywords?: string[];   // job keywords still missing
  suggestedChanges: SuggestedChange[];
  optimizedCvUrl?: string;
  modelUsed?: string;      // e.g. "mistralai/mistral-7b-instruct:free"
  processingTimeMs?: number;
  createdAt: string;
  completedAt?: string;
}

export type OptimizationStatus = 'pending' | 'queued' | 'processing' | 'completed' | 'failed' | 'rejected';

export interface SuggestedChange {
  type: 'keyword_added' | 'section_rewritten' | 'format_improved' | 'skill_added';
  description: string;
  before?: string;
  after?: string;
}

/** WebSocket push payload — matches Spring Boot @SendToUser payload */
export interface CVOptimizationWsEvent {
  optimizationId: string;
  status: OptimizationStatus;
  message?: string;
  provider?: string;   // actual AI provider that ran (e.g. "Groq Llama 3.3 70B")
}

// ── Optimization history entry ────────────────────────────────────────────────
export interface CVOptimizationHistoryItem {
  id: string;
  jobTitle: string;
  company: string;
  originalScore: number;
  optimizedScore: number;
  compatibilityScore?: number;   // set for rejected/completed — CV ↔ job fit
  createdAt: string;
  status: OptimizationStatus;
  modelUsed?: string;
}
