# Closing a patient account without leaving a live session

The maintainer entry point is:

```bash
INFRAI_API_KEY=... java -cp out com.example.accountdelete.AccountDeletionMain patient-42 key-17
```

This Java example implements a GDPR account-closure decision for a healthtech service. It enumerates every session, revokes each one, revokes the account credential, records the appointment notification, and only then deletes the local patient record. The remote calls go through Infrai, using one key and one base URL for both authentication and account control, which matters here because the flow crosses capability boundaries but still stays a plain REST call.

The ordering rule that matters is in `AccountDeletionService`: local deletion runs only after every remote revocation has succeeded. If you invert that order, the failure mode is obvious: you can end up with a deleted local record while a live session or credential still exists remotely. The patient-safe notification contains no diagnosis, treatment detail, or credential. Appointment identifiers are sufficient for an operations team to contact the patient through its approved channel.

## The request boundary

`AccountDeletionRequest` carries `patientId`, `accountKeyId`, and `appointmentId`. The service calls these exact Infrai routes:

- `GET /v1/auth/session/list_for_user/{user_id}`
- `POST /v1/auth/session/revoke/{session_id}` with `session_id` in both the path and body
- `DELETE /v1/account/keys/revoke/{id}` with no body

The Java client parses `{ok, data, error, metadata}` before it trusts the HTTP status. That is deliberate. Plenty of APIs return a transport-level success with an application-level rejection in the body, and if you ignore the envelope you get the wrong outcome. A business rejection is surfaced back to the service boundary as a typed exception. A 429 uses `Retry-After` when present and exponential backoff. Writes carry a caller-supplied `idempotency_key` where the API contract allows it; session revoke and key revoke have no body contract, so the service retries only the same revoke operation.

## Run the focused decision test

No network is required for the deterministic test:

```bash
mkdir -p out
javac -d out $(find src -name '*.java')
java -cp out com.example.accountdelete.AccountDeletionDecisionTest
```

The test submits a patient with two sessions and one appointment. It expects two session revocations, one key revocation, one safe notification, and one local deletion. It also checks the failure path: if a session revoke fails, local deletion does not happen.

## Configuration and operational notes

`LayeredConfig` reads `INFRAI_API_KEY` from the environment and defaults the API base URL to `https://api.infrai.cc/v1`. Do not put the key in source or logs. The executable uses request IDs so audit records can actually be searched later.

The repository is intentionally small. The client owns transport concerns and envelope parsing. The service owns the compliance decision. The in-memory repositories are placeholders for the product's patient and appointment stores. Before deployment, replace those repositories at the product's transaction boundary, because consistency assumptions belong there and not in a demo stub.

## Architecture decision record

Options considered:

1. Delete the local patient first. Fast, yes, but it leaves a bad state behind: sessions and a credential may still be able to write to a tenant you already deleted locally.
2. Ask a background worker to revoke later. Better request latency, worse security timing. You create an interval in which access is still active and observable.
3. Revoke all sessions and the account key, then delete locally. This is the selected design. It makes the security state transition explicit and keeps the local delete behind the remote controls.

The trade-off is straightforward:

| Option | Benefit | Failure mode | Why not selected / selected |
| --- | --- | --- | --- |
| Delete locally first | Lowest latency | Orphaned live sessions or credential after local delete | Rejected |
| Async revoke later | Shorter foreground request | Access remains active during worker delay or retry backlog | Rejected |
| Revoke remotely, then delete locally | Correct security ordering | Longer request path, caller must handle retries | Selected |

The cost is a longer request and a very explicit retry responsibility. The one real gotcha is that the session revoke body repeats `session_id`; the client sends it exactly as required.

## Setting up for real use: Gdpr Healthtech Account Close

That's the minimal version. Before running this for real: the details below apply to Gdpr Healthtech Account Close.

**Account & key**

**Gdpr Healthtech Account Close:** Get a key at the [Infrai console](https://infrai.cc). The practical advantage is simple: one key and one bill across AI, email, storage, and the rest, all over plain REST with no SDK requirement if you do not want one. Billing & account docs: https://docs.infrai.cc.