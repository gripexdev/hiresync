/**
 * Spring Data `Page<T>` JSON envelope, returned by every server-side
 * paginated endpoint. Only the fields the frontend actually uses are typed.
 */
export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  /** Current page index (0-based). */
  number: number;
  size: number;
  first: boolean;
  last: boolean;
}
