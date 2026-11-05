---
name: sba-feature-workflow
description: Plan or implement a feature that spans the SBA Spring Boot backend and React frontend, with clear module ownership, API contracts, and safe integration boundaries.
---

# SBA feature workflow

Use this when planning or implementing an SBA product feature across the backend and frontend repositories. The repositories are separate Git projects under the workspace: `sba-tour-trekking-be` and `sba-tour-trekking-fe`. For backend tasks, read [backend-context.md](references/backend-context.md); for frontend tasks, read [frontend-context.md](references/frontend-context.md). Read both for cross-repository work.

## Working rules

- Read the applicable `AGENTS.md` files and inspect the current Git status in each repository before editing. Preserve unrelated user changes; do not reset, stash, or overwrite them.
- Do not create, switch, merge, rebase, commit, push, or deploy branches unless the user explicitly asks. Treat each repository's branch and working tree independently.
- For human team execution, use a short-lived feature branch per contributor in each repository they change; don't have simultaneous contributors push to the same branch. Open focused PRs into the team's agreed integration branch and integrate frequently rather than keeping long-running feature branches.
- Do not assume that a frontend role portal owns a whole domain. Keep domain data/API/types in the corresponding `src/features/<domain>` module and role-specific composition in `src/pages/<role>` and `src/portals/<role>`.
- In the backend, keep business behavior in its `features/<domain>` package. Follow the repository's `AGENTS.md` architecture and coding rules.
- Avoid unrelated refactors. Prefer the smallest change that completes the requested feature.

## Plan the boundary before implementation

1. Identify the user-visible behavior and affected roles.
2. Trace the existing feature in both repositories: routes/pages, feature APIs/types/hooks, backend controller/service/repository, relevant entities and migrations, and authorization checks. Reuse current patterns where they fit.
3. Before proposing a new endpoint, frontend API method, or database table, search both repositories and the database sources for an existing equivalent. Reuse it when its lifecycle and meaning fit. If adding a new one is justified, state the concrete gap and why existing concepts do not fit.
4. Agree on the integration contract before independently implementing both sides. State endpoint/method, request and response fields, error behavior, authentication/authorization, and any schema change. Keep it backward-compatible where practical.
5. List the likely owned files and shared hotspots. Separate feature-local edits from work that must be coordinated. If the request is underspecified, proceed on non-dependent work and make assumptions explicit; ask only for choices that materially change the contract or access behavior.
6. If the user asks for a team split, give each contributor a bounded file/module area and identify one integration owner for shared files. Do not assume permission to spawn agents; only use delegation when explicitly requested and available.

## Shared hotspots to call out

Backend integration-sensitive areas include `core/security`, global configuration, shared exception handling, application startup/configuration, and database schema/migrations. Feature-local config is appropriate for feature behavior, but do not add a competing global security chain or weaken central authorization to make a feature work. Identify the exact endpoints and permissions affected; coordinate changes to shared security wiring with its owner.

Frontend integration-sensitive areas include `src/app/router.tsx`, route constants, `src/portals`, auth state/guards, shared API client, shared layouts/components, and shared feature contracts. Keep role-specific rendering in the relevant role pages/portal. A shared domain module such as `features/tours` can have consumers in public, admin, and partner flows, so check all consumers before changing its API, types, or shared components.

Database migrations are integration points. Add a new uniquely named migration; do not rewrite an already-applied migration. Coordinate ordering and deploy prerequisites when multiple features change the schema.

## Implement and integrate safely

- Keep API DTOs and frontend types aligned with the agreed contract. Do not silently change the meaning of an existing field or endpoint.
- Keep authorization enforced on the backend. Frontend guards improve navigation but are not access control.
- Make schema/API rollout compatible with the currently deployed application when possible: additive schema first, deploy compatible backend, then frontend relying on the new contract; document any required manual migration or coordinated release.
- Before finishing, inspect diffs and status in both repositories. Follow each repository's `AGENTS.md` for validation commands; do not claim checks that were not run.
- Report changed files by repository, contract/schema/security changes, shared hotspots touched, validation performed, and any coordination or rollout step still required.

## Team handoff template

For a feature split, provide a compact handoff:

- **Feature / roles:**
- **Contract:** endpoint, method, request/response, errors, permissions
- **Backend owner/files:**
- **Frontend owner/files:**
- **Shared integration owner/files:**
- **Database/migration:**
- **Merge order / compatibility notes:**
- **Done when:** observable behavior and relevant checks
