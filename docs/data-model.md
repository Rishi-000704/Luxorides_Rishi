# Data Model

## 1. Tenant model

Most business records carry an `orgId` directly or are reachable through an organization-owned aggregate. Organization ownership is a security boundary, not just a filter. Service/repository methods should prefer `id + orgId` or equivalent org-scoped queries.

`Org` is **not** mapped as a JPA parent of organization-owned entities. Tenant ownership is primarily represented by the scalar `orgId` business identifier and enforced by repository/service queries. For the exact entity associations and scalar ownership/reference links, see [models-and-relationships.md](models-and-relationships.md).

## 2. Models and relationships

Fleetovo uses both true JPA associations and scalar/application-level ID references. These relationship types must be treated separately.

See **[models-and-relationships.md](models-and-relationships.md)** for the complete persistence-model inventory, exact JPA relationships, element collections, embedded types, and important scalar relationships.

## 3. Persisted entity inventory

### Identity/configuration

- `Org`
- `User`
- `Employee`
- `UserOtp`
- `OrgBillingEntity`
- `FinancialYear`
- `CityGarage`
- `PaymentGatewayConfig`
- `EmailProviderConfig`
- `SmsProviderConfig`

### Parties/fleet/catalog

- `Client`
- `ClientBillingEntity`
- `Passenger`
- `Driver`
- `MasterVehicle`
- `FleetVehicle`
- `Package`

### Sales/operations

- `Booking`
- `BookingEntry`
- `ExtraCharge`
- `Estimate`
- `EstimateEntry`
- `EstimateAccessToken`
- `Invoice`
- `InvoiceEntry`
- `InvoiceExtraCharge`
- `Payment`

### Driver duty access

- `DriverDutyAccessToken`
- `DriverDutyCheckpoint`
- `DriverDutyExpense`

### Purchases/reports/utility

- `PurchaseInvoice`
- `PurchaseInvoiceEntry`
- `PurchaseInvoiceExtraCharge`
- `PaymentOut`
- `ReportRequest`
- `Document`
- `Reminder`

## 4. Embedded and persistence support types

The model uses embeddables for reusable values and snapshots, plus a mapped superclass for auditing. Financial/document snapshots reduce dependence on mutable master data.

| Type | Persistence role | Purpose |
|---|---|---|
| `Money` | `@Embeddable` | Amount + currency; database precision/scale are defined in the embeddable |
| `GstSnapshot` | `@Embeddable` | GST type/rate and IGST/CGST/SGST/total tax values |
| `PackageSnapshot` | `@Embeddable` | Package identity/scope/duty rules plus base/extra/night commercial rates |
| `AddressSnapshot` | `@Embeddable` | Formatted/place/coordinate operational location snapshot |
| `DisplayAddress` | `@Embeddable` | Display/postal address value |
| `Name` | `@Embeddable` | Structured person name value |
| `AuditableEntity` | `@MappedSuperclass` | Created/updated timestamps and actors |

### Financial invariant

Use `BigDecimal` for new monetary/tax calculations. The current code contains some legacy `Float` paths (including a `Money.INR(Float)` helper and selected time fields); do not extend that pattern.

Amounts such as subtotal, tax, discount, advance, received, TDS, payable and balance are separate concepts. Never collapse them into one generic total.

## 5. Important status enums

| Domain | Persisted values |
|---|---|
| Booking | `DRAFT`, `REQUESTED`, `CONFIRMED`, `RUNNING`, `COMPLETED`, `BILLED`, `CANCELLED` |
| Duty | `DRAFT`, `REQUESTED`, `CONFIRMED`, `ALLOTTED`, `RUNNING`, `COMPLETED`, `CANCELLED` |
| Estimate | `DRAFT`, `SENT`, `VIEWED`, `PAYMENT_INITIATED`, `PAID`, `CONVERTED`, `EXPIRED`, `CANCELLED`, `REVOKED` |
| Estimate link | `ACTIVE`, `USED`, `EXPIRED`, `REVOKED` |
| Invoice | `DRAFT`, `ISSUED`, `PAID`, `CANCELLED` |
| Purchase invoice | `DRAFT`, `COMPLETED`, `PAID`, `CANCELLED` |
| Payment | `INITIATED`, `PENDING`, `CONFIRMED`, `FAILED`, `REFUNDED`, `CANCELLED` |
| Report request | `QUEUED`, `PROCESSING`, `COMPLETED`, `FAILED`, `CANCELLED`, `EXPIRED` |

Persisted enums are data contracts. Do not remove/rename values without a database migration. In particular, `VehicleStatus` currently contains the persisted spelling `DEPRICATED`; fixing the spelling is a migration, not a cosmetic refactor.

Current currency enum is `INR` only.

## 6. Locks and duplicate protection

Pessimistic-lock queries are present for high-contention state changes involving:

- booking / booking entry;
- estimate;
- purchase invoice;
- payment;
- financial-year numbering;
- report request state.

Important uniqueness/index patterns include:

- hashed estimate/driver-duty access tokens;
- organization + provider configuration rows;
- active purchase-invoice duty linkage;
- invoice/numbering relationships;
- payment lookup/provider identifiers;
- report organization/status/type lookup indexes.

Application-level idempotency and database constraints must work together; do not replace one with frontend duplicate prevention.

## 7. Invoice and purchase snapshots

`Invoice`/`InvoiceEntry` and `PurchaseInvoice`/`PurchaseInvoiceEntry` are intentionally separate from live booking/package objects. They preserve issued commercial representation and permit PDF/reporting without silently adopting later master-data changes.

When modifying synchronization/regeneration behavior, distinguish:

1. correcting an allowed mutable source before/around issue;
2. re-rendering the existing snapshot;
3. recalculating a historical financial document from current configuration.

The third option is unsafe unless the business explicitly requires it and migration/audit implications are handled.
