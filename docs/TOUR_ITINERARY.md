# Tour itinerary

The itinerary is the reusable day-by-day tour program; departure dates and capacity are managed separately by schedules. `GET /api/v1/tours/{tourId}/itinerary` returns days ordered by `dayNumber`, each with `title`, `meals`, `overnight` and ordered `activities`. Existing activities without day metadata remain visible with a default day title. Anonymous users and customers may read published tours; admin may also read drafts.

`PUT /api/v1/tours/{tourId}/itinerary` requires admin authentication and replaces the complete itinerary atomically. Omit a day or activity to remove it. The list order determines activity display order. Each day number must be positive, unique and within the tour duration when specified. An empty `days` list clears the program. Activity IDs are recreated on save; consumers must not treat them as stable across full replacements. Legacy per-activity endpoints remain available.

```json
{"days":[{"dayNumber":1,"title":"Hà Nội – Xím Vàng","meals":"Bữa tối","overnight":"Nhà nghỉ","activities":[{"startTime":"19:30","title":"Khởi hành","description":"Gặp hướng dẫn viên","locationId":null}]}]}
```

Flyway applies the itinerary migration during backend startup. Existing databases
created before Flyway need a verified one-time baseline; see the
[migration guide](../schema/migrations/README.md). No existing activities are
deleted by the migration.
