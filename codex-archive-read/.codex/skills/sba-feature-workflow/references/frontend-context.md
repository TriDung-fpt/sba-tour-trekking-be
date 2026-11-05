# Frontend context map

This is an orientation map. Verify current imports and behavior in source before editing.

## Main boundaries

- `src/app`: application startup/providers and the top-level router.
- `src/portals/{public,admin,partner,customer}`: role-specific route composition, layouts, and guards. `AppRouter` dispatches to portals by path; avoid adding role conditionals across unrelated pages.
- `src/pages/<role>`: screens and role-specific page composition.
- `src/features/<domain>`: domain API clients, types, hooks, and reusable domain components. Existing domains include auth, tours, bookings, equipment, payments, and users.
- `src/components/ui` and `src/components/layout`: shared presentation components/layouts. Changes here have broad impact.
- `src/lib/axios.ts`: shared API client; `src/stores/auth.store.ts`: shared auth state; `src/constants/routes.ts`: shared route names.

## Cross-role domains

Role portals are composition boundaries, not separate copies of domain behavior. Tours are used by public pages and admin management; tour features also connect with partner flows. Bookings/payments span customer and operational flows. Before changing shared feature APIs, types, or components, search all consumers with `rg` and preserve their assumptions or update them together.

## Avoid duplicate APIs and types

Before adding a client method, type, hook, or page-specific fetch:

1. Search `src/features/*/api`, types, hooks, and all call sites for the existing capability.
2. Check the backend controller mapping and DTO to confirm the actual contract.
3. Extend the existing domain API module and shared type when the behavior belongs to that domain. Avoid one-off `axios` calls in pages.
4. Add a new API method only for a distinct backend capability or a justified contract gap; explain why existing calls cannot serve it.
5. Keep API paths and request/response shapes aligned with backend DTOs. Do not infer fields from UI mock data alone.

Follow current repo conventions in code and `package.json`; do not introduce a new state/data library or parallel architecture for a single feature.
