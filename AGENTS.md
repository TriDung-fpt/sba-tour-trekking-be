# Repository Guidelines & Coding Standards

You are a Senior Java Backend Engineer. This is a Java 21 Spring Boot application built with Maven. When creating, refactoring, or modifying any code in this project, you **MUST** strictly adhere to the following architectural rules and standards.

---

## 1. Feature-Based Architecture Rules

- **Feature Isolation**: Every core business domain (e.g., `auth`, `account`, `otp`) MUST reside in its own dedicated package inside `features/`. **DO NOT** mix `auth` or `otp` logic inside the `account` package or vice versa.
- **Service Layer Sub-packages**: Within a feature's `service/` folder, the Interface MUST be placed directly in `service/`, and its concrete implementation class MUST be placed inside `service/impl/`. **DO NOT** create cross-feature sub-folders (such as `service/auth/` or `service/account/`) inside another feature.

### Standard Directory Structure:

    app/
    ├── core/
    │   ├── common/
    │   ├── config/
    │   ├── exception/
    │   └── security/
    └── features/
        ├── account/                   # Account management business logic
        │   ├── controller/
        │   │   └── AccountController.java
        │   ├── dto/
        │   │   ├── request/
        │   │   │   └── UpdateAccountRequest.java
        │   │   └── response/
        │   │       └── AccountResponse.java
        │   ├── entity/
        │   │   ├── AppAccount.java
        │   │   ├── AccountRole.java
        │   │   └── AccountRoleId.java
        │   ├── repository/
        │   │   ├── AppAccountRepository.java
        │   │   └── AccountRoleRepository.java
        │   └── service/
        │       ├── AccountService.java             # Interface
        │       └── impl/
        │           └── AccountServiceImpl.java     # Implementation
        │
        ├── auth/                      # Authentication business logic
        │   ├── controller/
        │   │   └── AuthController.java
        │   ├── dto/
        │   │   ├── request/
        │   │   │   └── LoginRequest.java
        │   │   └── response/
        │   │       └── AuthResponse.java
        │   ├── repository/
        │   │   └── RedisAuthStore.java
        │   └── service/
        │       ├── AuthService.java                # Interface
        │       └── impl/
        │           └── AuthServiceImpl.java        # Implementation
        │
        └── otp/                       # OTP / Mail business logic
            └── service/
                ├── OtpMailSender.java              # Interface
                └── impl/
                    └── SmtpOtpMailSender.java      # Implementation

---

## 2. DTO Standards

- **MUST use Java `record`**: All Data Transfer Objects (DTOs) for Requests and Responses **MUST** be written using Java `record` (`public record ...`). **DO NOT** use standard classes with Lombok annotations (`@Data`, `@Getter`, `@Setter`).
- **DTO Placement**: All DTOs must be located in the corresponding `dto/request/` or `dto/response/` package within their feature.

---

## 3. Service Layer Rules

- **Interfaces & Implementations Required**: Every business service **MUST** define an Interface directly under `service/` and a corresponding Implementation class inside `service/impl/`.
- **Naming Conventions**:
    - Interface: `<Feature>Service.java` (e.g., `AccountService`)
    - Implementation: `<Feature>ServiceImpl.java` (e.g., `AccountServiceImpl`)
- **File Placement Rules**:
    - `features/<feature_name>/service/<Feature>Service.java`
    - `features/<feature_name>/service/impl/<Feature>ServiceImpl.java`
    - **NEVER** place other feature names as sub-folders inside `service/` (e.g., `service/account/` inside `account` is redundant).

---

## 4. Layering & Encapsulation Rules (Preventing Layer Leakage)

1. **Strict Unidirectional Dependency Flow**:
   `Controller` -> `Service Interface` -> `Repository` -> `Database`

2. **Controller Rules**:
    - Controllers **MUST ONLY** interact with Services through the **Service Interface**.
    - **NEVER** inject or call a `Repository` directly inside a Controller.
    - **NEVER** write business logic or perform direct database queries inside a Controller.
    - Controllers only accept Request DTOs, delegate execution to the Service Interface, and return Response DTOs / `ResponseEntity`.

3. **Service Rules**:
    - Services accept input data from Controllers in the form of DTOs.
    - The Service layer is the **ONLY** layer allowed to interact with Repositories and Entities.
    - Services are responsible for mapping between Entities and DTOs before returning results to Controllers.

4. **Preventing Layer Leakage**:
    - Entities **MUST NEVER** be exposed directly to Controllers or API clients.
    - Repositories **MUST NEVER** be referenced or leaked into the Controller layer.

---

## 5. Build, Test, and Development Commands

Use the Maven wrapper so the project’s configured Maven version is applied:

- `./mvnw clean package` (Windows: `mvnw.cmd clean package`) compiles and packages the application.
- `./mvnw test` runs the test suite; tests may require Docker for Testcontainers.
- `./mvnw spring-boot:run` starts the service locally.
- `docker compose up --build` builds and starts the application with its configured dependencies.

Provide local values through `.env` using `.env.example` as a guide. Never commit real credentials.

---

## 6. Coding Style & Naming Conventions

- Follow existing Java style and package structure.
- Use 4 spaces for indentation, `UpperCamelCase` for types/classes, `lowerCamelCase` for methods/fields.
- Match existing Spring conventions and use Jakarta validation (`jakarta.validation.*`) for request constraints.
- Keep imports tidy and changes focused.

---

## 7. Testing Guidelines

- Write tests under `src/test/java/com/trekking/app` with package paths matching production code.
- Name test classes with the `Tests` suffix (e.g., `AuthIntegrationTests`, `AuthSecurityTests`).
- Use the project's Spring test dependencies and Testcontainers for integration coverage.
- Always run `./mvnw test` before submitting changes.

---

## 8. Commit & Pull Request Guidelines

- Use concise imperative subjects with semantic prefixes: `feat:`, `fix:`, `refactor:`, `test:`, `docs:`.
- Keep unrelated changes separate.
- Explain the behavior changed, link related issues, list validation commands/results, and include API examples or screenshots when applicable.

---

## 9. Security & Configuration

- Keep secrets, tokens, and environment-specific settings out of source control.
- Update `.env.example` when introducing required configurations.
- Document API or authentication behavior changes in `docs/`.

## 10. Business Lifecycle and Recovery

- Every reversible business state transition (archive, deactivate, suspend, or hide) must have an authorized restore/reactivate path and a documented API contract. Do not implement a reversible UI action without its reverse transition.
- If a restored item cannot safely return directly to its prior public/active state, restore it to the safest editable state and document the required review or reactivation steps.
- Preserve the effect of irreversible side effects when restoring. For example, canceled departures are not silently reopened; the API and frontend must tell the partner what must be recreated.
- Keep lifecycle transitions and their side effects explicit in the API specification, including authorization and audit behavior.

## 11. Database Migration Coordination

- Follow `docs/DB_MIGRATIONS.md` for every database schema change.
- Agents MUST NOT guess or reserve Flyway version numbers. Put parallel-work SQL in `schema/migration-drafts/`; the integration coordinator assigns sequential versions and merges each numbered migration.
- Never edit or renumber a versioned migration after it has been merged or applied. Add a new migration to fix forward.
- Do not manually apply Flyway-managed migrations. Keep new database bootstrap in `schema/schema.sql` and versioned changes in `src/main/resources/db/migration`.
