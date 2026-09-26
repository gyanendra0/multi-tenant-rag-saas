# API Reference

Base URL: `http://localhost:8080`

All endpoints except `/api/auth/*` require a header:

```
Authorization: Bearer <accessToken>
```

The tenant is derived from the JWT — you never pass a tenant id manually.

---

## Auth

### POST `/api/auth/register`
Creates a new tenant and its first user (owner).

```json
{
  "email": "me@acme.com",
  "password": "Passw0rd!",
  "fullName": "Me",
  "tenantName": "Acme",
  "tenantSlug": "acme"
}
```
**201** → user profile `{ "userId", "email", "fullName", "tenantId", "tenantName", "tenantSlug", "role" }` (no tokens — log in next)

### POST `/api/auth/login`
```json
{ "email": "me@acme.com", "password": "Passw0rd!" }
```
**200** → `{ "accessToken", "tokenType", "expiresInSeconds", "user": { ... } }`
plus a `Set-Cookie: refresh_token=…; HttpOnly; SameSite=Strict; Path=/api/auth` header.
The refresh token is **never** in the JSON body (`refreshToken` is `null`).

### POST `/api/auth/refresh`
No body — the browser sends the `refresh_token` cookie automatically.
**200** → new access token (same shape as login) and a **rotated** cookie.
**401** if the cookie is missing, expired, or revoked.

### POST `/api/auth/logout`
No body. Revokes the cookie's refresh token and clears the cookie. **204** (idempotent).

> Testing with curl: store and resend the cookie with `-c jar.txt` / `-b jar.txt`,
> e.g. `curl -c jar.txt -X POST .../login ...` then `curl -b jar.txt -c jar.txt -X POST .../refresh`.

---

## Documents

### POST `/api/documents`
Multipart upload. Field name: `file`.
Stores the file in MinIO, creates a `PENDING` row, and queues ingestion.

**202 Accepted** → `{ "id", "title", "status": "PENDING", ... }`

### GET `/api/documents`
Lists documents for the current tenant only.

**200** → `[ { "id", "title", "status", "createdAt", ... } ]`

### GET `/api/documents/{id}`
**200** → single document, or **404** if not in this tenant.

### DELETE `/api/documents/{id}`
Removes the document, its chunks, and the MinIO object. **204**

---

## Retrieval (developer/test endpoint)

### POST `/api/rag/search`
Returns the raw top‑K chunks for a query (used for debugging retrieval).

```json
{ "query": "vacation policy", "topK": 5 }
```
**200** →
```json
[
  { "chunkId": "...", "documentId": "...", "title": "HR Handbook",
    "chunkIndex": 3, "score": 0.737, "content": "..." }
]
```

---

## Chat (RAG)

### POST `/api/chat`
Answers a question using **only** the tenant's documents, with citations.

```json
{
  "question": "How many vacation days do I get?",
  "conversationId": null
}
```
`conversationId` is optional — omit/null to start a new conversation.

**200** →
```json
{
  "conversationId": "…",
  "messageId": "…",
  "answer": "You get 20 vacation days per year [1].",
  "citations": [
    { "documentId": "…", "chunkId": "…", "chunkIndex": 3,
      "title": "HR Handbook", "score": 0.737 }
  ]
}
```

If nothing relevant is found, the model is instructed to say it doesn't know
rather than invent an answer.
