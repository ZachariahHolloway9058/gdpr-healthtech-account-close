package com.example.accountdelete;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class AccountDeletionDecisionTest {
    public static void main(String[] args) throws Exception {
        FakeClient client = new FakeClient(false);
        List<String> deleted = new ArrayList<>();
        List<String> notices = new ArrayList<>();
        AccountDeletionService service = new AccountDeletionService(client, deleted::add, notices::add);
        DeletionResult result = service.close(new AccountDeletionRequest("patient-42", "key-17", "appointment-8"));
        check(result.revokedSessions() == 2 && deleted.size() == 1 && notices.size() == 1, "successful closure decision");
        check(client.revokedSessions.equals(List.of("s-1", "s-2")) && client.keyRevoked, "remote controls first");

        FakeClient failed = new FakeClient(true);
        List<String> noDelete = new ArrayList<>();
        try { new AccountDeletionService(failed, noDelete::add, message -> {}).close(new AccountDeletionRequest("p", "k", "a")); }
        catch (InfraiException expected) { check(noDelete.isEmpty(), "failed revoke blocks local deletion"); return; }
        throw new AssertionError("expected a failed revoke");
    }
    private static void check(boolean value, String label) { if (!value) throw new AssertionError(label); }

    static final class FakeClient extends InfraiClient {
        final List<String> revokedSessions = new ArrayList<>(); boolean keyRevoked; final boolean fail;
        FakeClient(boolean fail) { super(new LayeredConfig("test", "http://localhost", 1)); this.fail = fail; }
        @Override public String listSessions(String userId) { return "{\"ok\":true,\"data\":[{\"session_id\":\"s-1\"},{\"session_id\":\"s-2\"}]}"; }
        @Override public String revokeSession(String id) throws IOException { if (fail) throw new InfraiException("REJECTED", "", 400); revokedSessions.add(id); return "{\"ok\":true}"; }
        @Override public String revokeKey(String id) { keyRevoked = true; return "{\"ok\":true}"; }
    }
}
