# SecurePay API — Developer Guide (excerpt)

## Authentication
All API requests must include an `Authorization: Bearer <API_KEY>` header. API
keys are issued from the developer dashboard and can be rotated at any time.
Requests without a valid key return HTTP 401.

## Rate Limits
The API allows 100 requests per minute per API key. Exceeding this limit returns
HTTP 429 with a `Retry-After` header indicating how many seconds to wait. The
limit resets on a rolling 60-second window.

## Creating a Payment
Send a POST request to `/v1/payments` with an amount in cents, a currency code,
and a customer id. The `amount` must be a positive integer. Payments are settled
within two business days.

## Webhooks
SecurePay sends a `payment.succeeded` event to your configured webhook URL when
a payment completes. Webhook payloads are signed with an HMAC-SHA256 signature in
the `X-SecurePay-Signature` header; you should verify this signature before
trusting the payload.
