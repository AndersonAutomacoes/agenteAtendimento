package com.atendimento.cerebro.application.logging;

import com.atendimento.cerebro.application.crm.CrmConversationSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Formato de auditoria para PRD: {@code [APP-CONFIRMED]}, {@code [APP-CANCELLED]}, {@code [APP-ERROR]}.
 */
public final class AppointmentAuditLog {

    private static final Logger LOG = LoggerFactory.getLogger(AppointmentAuditLog.class);

    private AppointmentAuditLog() {}

    public static void appConfirmed(String appointmentId, String phoneDigits, String slotLabel) {
        String phone = phoneDigits == null || phoneDigits.isBlank()
                ? "?"
                : CrmConversationSupport.maskPhoneForAudit(phoneDigits);
        String slot = slotLabel == null || slotLabel.isBlank() ? "?" : slotLabel.strip();
        String id = appointmentId == null || appointmentId.isBlank() ? "?" : appointmentId.strip();
        LOG.info("[APP-CONFIRMED] ID: {} | Phone: {} | Slot: {}", id, phone, slot);
    }

    /**
     * @param googleEvent ex.: {@code deleted}, {@code not_deleted}, {@code not_applicable}
     * @param database ex.: {@code updated}, {@code already}, {@code not_updated}
     */
    public static void appCancelled(long appointmentId, String googleEvent, String database) {
        String g = googleEvent == null || googleEvent.isBlank() ? "?" : googleEvent.strip();
        String d = database == null || database.isBlank() ? "?" : database.strip();
        LOG.info("[APP-CANCELLED] ID: {} | GoogleEvent: {} | DB: {}", appointmentId, g, d);
    }

    public static void appError(String source, String reason) {
        String src = source == null || source.isBlank() ? "Unknown" : source.strip();
        String r = reason == null || reason.isBlank() ? "unspecified" : reason.strip();
        LOG.error("[APP-ERROR] Source: {} | Reason: {}", src, r);
    }

    public static void appErrorWithAppointmentId(String source, long appointmentId, String reason) {
        String src = source == null || source.isBlank() ? "Unknown" : source.strip();
        String r = reason == null || reason.isBlank() ? "unspecified" : reason.strip();
        LOG.error("[APP-ERROR] Source: {} | ID: {} | Reason: {} | reenvio manual possível (WhatsApp)", src, appointmentId, r);
    }
}
