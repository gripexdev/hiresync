// ── Structured CV (returned by GET /api/cv/structured/{id}) ───────────────────
export interface StructuredCv {
  fullName:  string;
  jobTitle:  string;
  photo?:    string;          // base64 data URI (added client-side in the studio)
  contact:   CvContact;
  summary:   string;
  experience: CvExperience[];
  education:  CvEducation[];
  skills:     string[];
  languages?: string[];
}

export interface CvContact {
  email?:    string;
  phone?:    string;
  location?: string;
  linkedin?: string;
}

export interface CvExperience {
  role:    string;
  company: string;
  dates:   string;
  bullets: string[];
}

export interface CvEducation {
  degree: string;
  school: string;
  dates:  string;
}

// ── Studio customization options ──────────────────────────────────────────────
export type TemplateId = 'modern' | 'classic';

export interface StudioOptions {
  template:    TemplateId;
  accentColor: string;
  fontFamily:  string;
  showPhoto:   boolean;
}

export const FONT_CHOICES = [
  { label: 'Inter',        value: "'Inter', sans-serif" },
  { label: 'Roboto',       value: "'Roboto', sans-serif" },
  { label: 'Georgia',      value: "Georgia, serif" },
  { label: 'Times',        value: "'Times New Roman', serif" },
  { label: 'Courier',      value: "'Courier New', monospace" },
];

export const ACCENT_PRESETS = [
  '#2E86AB', // brand blue
  '#17A589', // teal
  '#8B5CF6', // purple
  '#E11D48', // rose
  '#EA580C', // orange
  '#0F766E', // deep teal
  '#1E293B', // slate (mono / classic)
];
