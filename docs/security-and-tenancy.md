# Security and Tenancy

## 1. Authentication model

Fleetovo uses stateless Spring Security with JWT.

- `SessionCreationPolicy.STATELESS`
- JWT filter runs before `UsernamePasswordAuthenticationFilter`.
- JWT subject is the user ID.
- Token claims include account type, organization ID and authorities.
- User details are loaded again during token authentication rather than trusting claims as the only source of truth.
- Account types currently include `EMPLOYEE` and `CLIENT`.
- Granted authorities include `ROLE_<ACCOUNT_TYPE>` plus Fleetovo `Authority` values.

Employee login uses password authentication. Client login uses organization-aware OTP flow and then issues JWT.

## 2. Filter-chain public namespaces

Current `SecurityConfiguration` marks these matchers `permitAll`:

```text
/auth/**
/file/**
/
/external/**
/driver-api/**
/actuator/**
/estimate-api/**
```

Everything else requires authentication at the filter chain.

`permitAll` means the security filter does not require authentication; it does **not** guarantee the controller can produce useful data anonymously. For example, some `/auth/**/me` methods still consume the `Authentication` object.

## 3. Method security

`@EnableMethodSecurity` is enabled. Employee controllers commonly apply:

```text
ROLE_EMPLOYEE
+ a specific Authority for mutations/reads
```

Frontend guards are UX only; backend annotations/service ownership validation remain authoritative.

### Authority enum

```text
ORG_VIEW
ORG_EDIT

CLIENT_VIEW
CLIENT_ADD
CLIENT_EDIT

DRIVER_VIEW
DRIVER_ADD
DRIVER_EDIT
DRIVER_DELETE

BOOKING_VIEW
BOOKING_ADD
BOOKING_EDIT
BOOKING_CONFIRM
BOOKING_CANCEL
BOOKING_COMPLETE
BOOKING_ADD_DUTY
BOOKING_EDIT_DUTY
BOOKING_ALLOT_DUTY
BOOKING_REALLOT_DUTY
BOOKING_CLOSE_DUTY
BOOKING_RECLOSE_DUTY
BOOKING_ADD_PAYMENT
BOOKING_EDIT_PAYMENT
BOOKING_CONFIRM_PAYMENT

INVOICE_GENERATE
INVOICE_REGENERATE

PURCHASE_INVOICE_VIEW
PURCHASE_INVOICE_ADD
PURCHASE_INVOICE_EDIT
PURCHASE_INVOICE_COMPLETE
PURCHASE_INVOICE_CANCEL

PAYMENT_OUT_VIEW
PAYMENT_OUT_ADD
PAYMENT_OUT_EDIT
PAYMENT_OUT_CANCEL

PACKAGE_VIEW
PACKAGE_ADD
PACKAGE_EDIT
PACKAGE_DELETE

EMPLOYEE_VIEW
EMPLOYEE_ADD
EMPLOYEE_EDIT
EMPLOYEE_RESET_PASSWORD
EMPLOYEE_UPDATE_AUTHORITIES

FINANCIAL_YEAR_VIEW
FINANCIAL_YEAR_ADD
FINANCIAL_YEAR_EDIT

ORG_BILLING_ENTITY_VIEW
ORG_BILLING_ENTITY_ADD
ORG_BILLING_ENTITY_EDIT

FLEET_VEHICLE_VIEW
FLEET_VEHICLE_ADD
FLEET_VEHICLE_EDIT
FLEET_VEHICLE_DELETE

MASTER_VEHICLE_VIEW
MASTER_VEHICLE_ADD
MASTER_VEHICLE_EDIT
MASTER_VEHICLE_DELETE

GARAGE_ADD
GARAGE_EDIT
GARAGE_VIEW

ORG_NOTIFICATION_CONFIG_VIEW
ORG_NOTIFICATION_CONFIG_EDIT

ORG_PAYMENT_GATEWAY_CONFIG_VIEW
ORG_PAYMENT_GATEWAY_CONFIG_EDIT

DATA_EXCHANGE_VIEW
DATA_EXCHANGE_EXPORT
DATA_EXCHANGE_IMPORT

REPORT_VIEW
REPORT_REQUEST
REPORT_DOWNLOAD
REPORT_DELETE
```

When adding an authority, backend and frontend authority contracts/assignments must be updated together. Existing assignments need an explicit rollout strategy.

## 4. Organization isolation rule

For authenticated business operations:

1. derive organization from `SecurityContext`/trusted token context;
2. query records using `id + orgId` (or equivalent organization ownership predicate);
3. validate every related entity belongs to the same organization;
4. never create cross-organization relationships;
5. do not accept browser-supplied `orgId` as authoritative when an authenticated organization is already known.

This pattern is visible throughout org-scoped repository/service methods and must be preserved for new work.

### Public-token exception

Estimate links and driver-duty links operate before normal authenticated context. They therefore resolve a stored hashed access token to an entity that already contains organization ownership. Raw access tokens are random and only hashes are persisted.

### OTP bootstrap exception

Client OTP generation/verification needs organization context before a client JWT exists. The request supplies an organization identifier used to select/reconcile the organization/client/provider path. Treat this as a bootstrap boundary: organization existence/provider ownership must be validated server-side before issuing authenticated context.

## 5. Credential/provider secrets

Payment/email/SMS provider credentials are organization-specific and encrypted through `SecretCryptoService`. They must never be returned in readable form, logged, placed in DTOs for display, or copied to client code.

The source configuration contains credential/default-secret material. Production practice should externalize secrets and rotate any value that has been committed or shared beyond its intended environment.

## 6. Traceable errors

`RequestTraceFilter` manages `X-Trace-Id` and MDC logging. `GlobalExceptionHandler` emits structured `ApiError` with fields including:

- stable error code;
- safe message;
- HTTP status;
- path/method;
- timestamp;
- trace ID;
- optional technical/metadata fields.

Technical-message exposure is configurable. The current checked production properties enable it; production should expose only what support/debugging actually requires and must continue to sanitize credentials/tokens.

## 7. Current security hardening items

These are current-code observations, not evidence of an exploit:

- CORS uses `allowedOriginPatterns("*")` while credentials are enabled; restrict to trusted frontend origins in production.
- `/file/**` and `/actuator/**` are filter-chain public. Ensure only intended actuator endpoints are exposed and treat file URLs as public.
- Source properties contain development/default secret values; externalize and rotate for production.
- `app.seed.enabled=true` is present in production properties. Seeder currently exits when organizations already exist, but production should explicitly disable seed behavior rather than rely on database state.
- Technical exception details are currently configured for exposure in production properties; review before public deployment.

See [testing-and-known-risks.md](testing-and-known-risks.md) for the maintenance checklist.
