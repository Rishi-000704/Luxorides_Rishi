# Fleetovo Backend Documentation

Fleetovo is a multi-organization car rental platform backend built with Spring Boot. This documentation covers the backend architecture, business workflows, persistence model, security and tenancy, integrations, APIs, operations, and testing considerations.

## Documents

| Document | Use it for |
|---|---|
| [Architecture](docs/architecture.md) | System boundaries, packages, transactions, events, files and background processing |
| [Modules](docs/modules.md) | Feature/module map and primary implementation classes |
| [Business workflows](docs/business-workflows.md) | Booking, duty, estimate, invoice, purchase and payment lifecycles |
| [Data model](docs/data-model.md) | Persistence conventions, snapshots, enums and locking/uniqueness rules |
| [Models and relationships](docs/models-and-relationships.md) | Complete JPA model inventory, mapped relationships and scalar application references |
| [Security and tenancy](docs/security-and-tenancy.md) | JWT, roles/authorities, `orgId` isolation, public/token endpoints and error tracing |
| [Integrations](docs/integrations.md) | Razorpay, SMS, email, Google Maps, PDF/QR, files and reports |
| [API reference](docs/api-reference.md) | Controller endpoint inventory |
| [Operations](docs/operations.md) | Build, configuration, storage, deployment and production checklist |
| [Testing and known risks](docs/testing-and-known-risks.md) | Test coverage, regression checklist and implementation cautions |

## Technology

- Java 21
- Spring Boot 3.3.6
- Spring MVC / REST
- Spring Security + JWT (JJWT 0.11.5)
- Spring Data JPA / Hibernate / MySQL
- Thymeleaf + OpenHTMLToPDF/PDFBox
- Razorpay Java SDK
- ZXing QR generation
- Apache Commons CSV
- WebClient + Resilience4j
- Google Maps APIs
- Spring async events/executors

## Run locally

The project is Maven-based.

```bash
./mvnw clean test
./mvnw spring-boot:run
```

Local execution requires a MySQL database and the required application configuration. Keep credentials, JWT secrets, provider keys, and other sensitive values outside documentation, tickets, logs, and prompts. Prefer environment-based secrets for shared and production environments.

## Documentation maintenance

Update the relevant document when a change alters:

- lifecycle or status transitions;
- organization ownership or authorization;
- money, tax, invoice, estimate, or purchase-invoice behavior;
- persisted enums or schema constraints;
- provider/runtime support;
- public or token-based endpoints;
- report definitions;
- deployment or configuration requirements;
- a reusable operational or engineering rule.

Avoid duplicating every DTO, repository method, or JPA field in narrative documentation. Keep the documentation focused on system behavior, contracts, relationships, constraints, and operational rules.
