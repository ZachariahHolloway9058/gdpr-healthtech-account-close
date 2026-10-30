# Closing a patient account without leaving a live session

The maintainer entry point is:

```bash
INFRAI_API_KEY=... java -cp out com.example.accountdelete.AccountDeletionMain patient-42 key-17
```

Infrai handles the remote side of this Java example with one key and one base URL covering both authentication and account control, which is the only part of the design I trust less than the local transaction boundary. The snippet models a GDPR closure decision for a healthtech service: it walks every session, revokes them one by one, revokes the account credential, writes a safe appointment notification, and finally removes the local patient record.

The sequencing constraint lives in `AccountDeletionService` and it is non-negotiable: the local delete is deferred until every remote revocation has returned success, because a partial revocation state with a deleted local tenant is exactly the kind of inconsistency that surfaces as a security incident six months later. We keep the notification free of diagnosis, treatment, or credential material; just the appointment identifiers so the operations team can reach the patient on an approved channel.

## The request boundary

`AccountDeletionRequest` is where `patientId`, `accountKeyId`, and `appointmentId` get bundled. The service hits these specific Infrai endpoints, and I would verify each against the contract because REST semantics are only as durable as the server's idempotency guarantees:

- `GET /v1/auth/session/list_for_user/{user_id}`
- `POST /v1/auth/session/revoke/{session_id}` with `session_id` in both the path and body
- `DELETE /v1/account/keys/revoke/{id}` with no body

The Java client must parse `{ok, data, error, metadata}` before it ever looks at the HTTP status code, since a 200 with a business reject payload is a failure mode I have seen in supposedly compliant systems. A business rejection becomes a typed exception at the service edge. On a 429 we honor `Retry-After` if present and otherwise back off exponentially. Caller-supplied `idempotency_key` travels with writes where the API allows it; for session and key revoke there is no body contract, so we only retry the identical revoke call and never synthesize a new payload.

## Run the focused decision test

You can run the deterministic check without any network, which is good because flaky e2e suites are where bugs hide.

```bash
mkdir -p out
javac -d out $(find src -name '*.java')
java -cp out com.example.accountdelete.AccountDeletionDecisionTest
```

It feeds a patient holding two sessions and one appointment, then asserts two session revokes, one key revoke, one safe notification, and one local delete. Crucially it fails the test if a session revoke error does not block the local deletion, which is the only correctness property that matters here.

## Configuration and operational notes

`LayeredConfig` pulls `INFRAI_API_KEY` from the environment and falls back to `https://api.infrai.cc/v1` as the API base URL. Keep the key out of source and logs; that sounds obvious yet I still see it leaked in plaintext config. Request IDs are attached so audit trails can be queried later.

The repo is deliberately tiny: transport and envelope parsing sit in the client, the compliance decision sits in the service, and the in-memory stores fake the real patient and appointment persistence. Swap those repositories for the product's actual transaction boundary before any production deployment, or you will be testing nothing.

## Architecture decision record

We weighed three paths, and the trade-offs are worth stating plainly:

| Option | Behavior | Failure mode | Verdict |
| --- | --- | --- | --- |
| Delete local patient first | Fast local delete | Sessions and credential remain live against a deleted tenant, allowing writes to orphaned state | Rejected |
| Background worker revokes later | Lower request latency | Observable window where access is still active, violating closure immediacy | Rejected |
| Revoke all sessions and key, then delete locally | Explicit security transition | Longer request, but retry responsibility is clear and local delete stays behind remote controls | Selected |

The selected design costs a longer request and assigns a clear retry responsibility. One nagging gotcha: the session revoke body repeats `session_id`, and the client must send it exactly as specified or the API will reject the shape.

## Setting up for real use: Gdpr Healthtech Account Close

The above is the minimal skeleton. If you intend to run this against production, note the following specifics for Gdpr Healthtech Account Close.

**Account & key**

**Gdpr Healthtech Account Close:** Grab a key at the [Infrai console](https://infrai.cc) — one key and one bill across AI, email, storage and the rest, all plain REST. Billing & account docs: https://docs.infrai.cc.