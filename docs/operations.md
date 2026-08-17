# Operations and Deployment

## 1. Build

Maven wrapper is included.

```bash
./mvnw clean test
./mvnw clean package
```

For code, configuration, or schema changes, treat a clean package plus appropriate runtime/browser regression as the release baseline.

## 2. Docker image

The checked Dockerfile:

1. builds with Maven on Java 21;
2. packages with tests skipped;
3. runs on Eclipse Temurin 21 JRE;
4. creates a non-root `spring` user;
5. creates `/srv/fleetovo-files`;
6. exposes port `8443`;
7. runs `java -jar /app/app.jar`.

Because Docker packaging uses `-DskipTests`, CI/release automation must run tests **before** the image build if tests are expected to gate deployment.

## 3. Production configuration contract

`prod.application.properties` expects or supports environment-driven values including:

| Environment/property | Purpose |
|---|---|
| `SERVER_PORT` | HTTP server port (default 8443) |
| `FILE_BASE_PATH` | File/report storage root (default `/srv/fleetovo-files`) |
| `SPRING_DATASOURCE_URL` | MySQL JDBC URL |
| `SPRING_DATASOURCE_USERNAME` | MySQL user |
| `SPRING_DATASOURCE_PASSWORD` | MySQL password |
| `FLEETOVO_SECRET_MASTER_KEY` | Encryption master key for organization provider credentials |
| `JWT_SECRET` | JWT signing secret |
| `GOOGLE_MAPS_KEY` | Google Maps server API key |

Do not document actual secret values.

## 4. Database release policy

Current ORM setting is:

```properties
spring.jpa.hibernate.ddl-auto=update
```

No Flyway/Liquibase framework is configured. For meaningful production schema changes:

1. identify old and new application compatibility;
2. take a verified backup;
3. prefer additive columns/indexes first;
4. provide explicit production-safe SQL for constraints/data backfill when needed;
5. deploy compatible code;
6. backfill/validate;
7. tighten constraints only after data is safe;
8. keep rollback SQL/code strategy.

Never rely on Hibernate auto-update alone for destructive enum/column/constraint changes.

## 5. File storage

Default production path is `/srv/fleetovo-files` (or `FILE_BASE_PATH`). This path must be persistent across container replacement and writable by the runtime `spring` user.

Back up file storage together with database records that reference filenames. A database-only restore can leave broken invoice/slip/logo references.

## 6. Pre-deployment checklist

- [ ] Database backup completed and restorable.
- [ ] Persistent file storage backed up when the release touches files/PDF assets.
- [ ] Required environment variables present.
- [ ] No secrets committed/logged in the release diff.
- [ ] Schema impact reviewed; production SQL prepared where meaningful.
- [ ] Backend tests executed outside the Docker `-DskipTests` build.
- [ ] Critical API/browser workflows tested with an organization-scoped demo account.
- [ ] Payment changes tested for amount/currency/org/idempotency failure paths.
- [ ] Invoice changes tested for historical snapshot/regeneration behavior.
- [ ] Report changes tested asynchronously, including failure/download handling.
- [ ] Logs checked for exceptions and `X-Trace-Id` correlation.

## 7. Post-deployment smoke checks

At minimum:

1. employee login and `/auth/employee/me`;
2. client OTP path in a safe test organization if affected;
3. organization-scoped list/read endpoint;
4. create/update a non-financial test record if release scope warrants;
5. booking lifecycle action affected by the release;
6. invoice PDF generation/download when billing/PDF code changed;
7. payment initiation/verification sandbox path when payment code changed;
8. report request → completion → download when report code changed;
9. file image retrieval for a known public image;
10. actuator health exposure and application logs.

## 8. Rollback

Application rollback is only safe when the previous binary understands the post-change schema/data. Before rollout, define:

- previous image/JAR reference;
- SQL rollback or forward-fix strategy;
- whether newly persisted enum/status values are backward compatible;
- whether provider/file side effects are reversible;
- whether issued financial documents require immutable preservation rather than rollback mutation.

Never “rollback” by renumbering/deleting issued invoices or confirmed provider payments.

## 9. Production configuration items to review

The production properties contain settings that deserve explicit hardening:

- `app.seed.enabled=true`;
- wildcard CORS behavior in `SecurityConfiguration`;
- technical exception-message exposure enabled;
- Hibernate `ddl-auto=update`;
- `open-in-view=true`;
- source/default secret material exists in checked configuration.

These are current implementation facts, not recommendations to keep them. Change them deliberately with regression/deployment review.
