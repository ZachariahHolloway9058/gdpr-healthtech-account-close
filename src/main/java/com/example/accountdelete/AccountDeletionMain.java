package com.example.accountdelete;

public final class AccountDeletionMain {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("usage: AccountDeletionMain patient-id account-key-id");
        LayeredConfig config = LayeredConfig.fromEnvironment();
        AccountDeletionService service = new AccountDeletionService(new InfraiClient(config),
                patientId -> System.out.println("deleted local patient " + patientId),
                message -> System.out.println("notification: " + message));
        DeletionResult result = service.close(new AccountDeletionRequest(args[0], args[1], "appointment-from-request"));
        System.out.println("closed " + result.patientId() + "; sessions revoked=" + result.revokedSessions());
    }
}
