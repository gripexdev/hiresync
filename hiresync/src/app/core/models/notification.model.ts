export interface Notification {
  id: string;
  type: NotificationType;
  title: string;
  message: string;
  read: boolean;
  createdAt: string;
  link?: string;
}

export type NotificationType =
  | 'job_match'
  | 'cv_optimized'
  | 'application_update'
  | 'interview_scheduled'
  | 'offer_received';
