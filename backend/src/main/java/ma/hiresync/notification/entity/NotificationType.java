package ma.hiresync.notification.entity;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * The kinds of notification HireSync generates from real product events.
 *
 * Serialized to JSON in lower-case (e.g. {@code "cv_optimized"}) so the Angular
 * {@code NotificationType} union and its icon/label maps line up one-to-one.
 */
public enum NotificationType {

    /** A CV optimization finished successfully. */
    CV_OPTIMIZED,
    /** An optimization was refused because the profile doesn't fit the job. */
    CV_REJECTED,
    /** An optimization failed (all AI providers unavailable). */
    CV_FAILED,
    /** A job application moved to a new status (generic). */
    APPLICATION_UPDATE,
    /** An application reached the interview stage. */
    INTERVIEW_SCHEDULED,
    /** An application reached the offer stage. */
    OFFER_RECEIVED;

    @JsonValue
    public String json() {
        return name().toLowerCase();
    }
}
