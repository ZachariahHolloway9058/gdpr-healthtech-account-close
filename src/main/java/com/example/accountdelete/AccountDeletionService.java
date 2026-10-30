package com.example.accountdelete;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AccountDeletionService {
    private final InfraiClient client;
    private final PatientRepository patients;
    private final NotificationLog notifications;

    public AccountDeletionService(InfraiClient client, PatientRepository patients, NotificationLog notifications) {
        this.client = client; this.patients = patients; this.notifications = notifications;
    }

    public DeletionResult close(AccountDeletionRequest request) throws IOException, InterruptedException {
        String sessions = client.listSessions(request.patientId());
        List<String> ids = sessionIds(sessions);
        for (String id : ids) client.revokeSession(id);
        client.revokeKey(request.accountKeyId());
        notifications.record("Appointment " + request.appointmentId() + " requires patient contact through the approved channel.");
        patients.delete(request.patientId());
        return new DeletionResult(request.patientId(), ids.size(), true);
    }

    private static List<String> sessionIds(String payload) {
        List<String> ids = new ArrayList<>();
        Matcher m = Pattern.compile("\\\"session_id\\\"\\s*:\\s*\\\"([^\\\"]+)").matcher(payload);
        while (m.find()) ids.add(m.group(1));
        return ids;
    }
}

record AccountDeletionRequest(String patientId, String accountKeyId, String appointmentId) {}
record DeletionResult(String patientId, int revokedSessions, boolean localRecordDeleted) {}
interface PatientRepository { void delete(String patientId); }
interface NotificationLog { void record(String message); }
