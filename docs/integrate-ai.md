# Gemini AI Studio tour consultant

## Configuration

The new endpoint is `POST /api/v1/integrate-ai/chat`. Set `GEMINI_API_KEY` to a Gemini Developer API key created in Google AI Studio. The feature uses the Gemini `generateContent` REST API through Spring's `RestClient`; provider, orchestration, and tour data access are behind separate service interfaces. Existing Spring Boot and Spring AI versions are unchanged. The previous implementation remains isolated in `features/localai`, is disabled by default, and can be activated explicitly with `LOCAL_AI_ENABLED=true`. When enabled, it retains its `/api/v1/local-ai` and legacy `/api/v1/ai` routes. The new integration does not depend on or invoke that module.

```json
{"question":"Tôi muốn đi ngày 16/11/2026, có tour nào phù hợp không?"}
```

The response includes the server-created `sessionId`; pass it on later messages to continue the same conversation. Redis stores a bounded history and expires it using `INTEGRATE_AI_SESSION_TTL`. Anonymous conversations use unpredictable UUIDs; authenticated users' sessions are also bound to their account. Rate limits are stored in Redis and keyed by a keyed hash of the client address.

## Trust boundaries

- The model can only return structured criteria and selected IDs from a bounded list of public tours. It receives no repositories, SQL, URL tools, administrator tools, payment operations, or booking write operations.
- Tour names, descriptions, locations, itinerary, current schedule prices, available seats, and term text are rebuilt from backend services. Selected IDs are checked against the current public catalog. Explicit numeric and ISO/Vietnamese dates are normalized server-side and checked against the schedule's Vietnam-local departure date.
- Budget filters use `BigDecimal`; negative, malformed, and over-limit values are rejected without numeric overflow. Unusually high budgets trigger clarification unless the customer states that the amount is per person or for the group. This never changes prices or favors expensive tours.
- Only currently bookable schedules with available seats are shown. Availability is re-read before constructing the response; it can still change immediately after the response and must be checked again during booking.
- Model output is not used as customer-facing factual prose. Explanations, prices, departures, seats, itinerary, and policies in tour cards are generated from validated backend values.

## Boundaries and known gaps

The current schema does not provide verified elevation, weather, medical fitness, age requirements, or live terrain safety data. The assistant must not claim those facts or guarantee safety. Upcoming schedules are bounded by `INTEGRATE_AI_MAX_SCHEDULES_PER_TOUR`, so a date beyond that page may not appear until the configured cap is increased or a date-specific schedule query is added. Recommendations require a configured AI Studio key and network access; without a key the endpoint returns a safe unavailable message. This integration does not replace or migrate the old local RAG service or its pgvector data.
