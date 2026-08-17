# Business Workflows

This document records lifecycle behavior visible in the current backend. Backend validation is authoritative; frontend buttons/guards must not be treated as business enforcement.

## 1. Booking lifecycle

Persisted statuses:

```text
DRAFT → CONFIRMED → RUNNING → COMPLETED → BILLED
  └─────────────── cancellation paths ─────────────→ CANCELLED

REQUESTED also exists in the BookingStatus enum and must be preserved for compatibility.
```

### Create and confirm

- New employee-created bookings start as `DRAFT`.
- Booking header captures client/billing information, GST snapshot, discount and commercial totals.
- Duties are `BookingEntry` records.
- Confirming is allowed only from `DRAFT` and requires at least one duty; status becomes `CONFIRMED`.
- Confirmation publishes `BookingConfirmedEvent` for asynchronous notification handling.

### Add/update duties

- A duty snapshots package/commercial values instead of relying only on the current `Package` record.
- Adding or changing a duty recalculates the booking.
- Existing issued invoice data is synchronized when the booking is already linked to an invoice and the workflow allows the mutation.
- The current service contains reopen/recalculation handling for changes made after completion; callers must use service methods rather than directly mutating statuses.

### Allot / re-allot

Duty statuses include:

```text
DRAFT / REQUESTED / CONFIRMED → ALLOTTED → RUNNING → COMPLETED
                                        └──────────→ CANCELLED (where supported)
```

- Initial allotment requires the duty to be `REQUESTED`.
- Assignment can include driver, supplier/vendor and fleet vehicle.
- Allotment pushes a non-terminal booking into `RUNNING` as appropriate.
- Re-allotment is supported for `ALLOTTED`, `RUNNING` and `COMPLETED` duties and preserves the explicit re-allot event path.

### Close / re-close

- Normal close is allowed for `ALLOTTED` or `RUNNING` duties.
- Close stores running/closing metrics, slip images and extra charges, then recalculates the duty and booking.
- The duty becomes `COMPLETED`.
- If all duties are complete, the booking can become `COMPLETED`.
- Re-close is a dedicated operation for an already `COMPLETED` duty; it replaces/recomputes close data instead of pretending the original close never happened.

### Complete booking and invoice trigger

- `completeBooking` accepts a `CONFIRMED` or `RUNNING` booking when every duty is complete.
- `COMPLETED` is handled idempotently.
- A `BILLED` booking is not completed again.
- Completion publishes `BookingCompletedEvent`.
- After the transaction commits, a new transaction creates the invoice and moves the booking to `BILLED` once invoice creation succeeds.

### Cancellation

The current cancellation service sets the booking to `CANCELLED`, records remarks, synchronizes an existing invoice where relevant, and publishes cancellation/refund events. Do not add frontend-only assumptions about allowed prior statuses; enforce any tighter lifecycle policy in the backend first.

## 2. Manual booking payment workflow

- Manual booking payments use local/manual entry semantics and initially enter a pending state.
- Confirmation is a separate backend action.
- Confirmed payments feed booking/invoice financial calculations and notification events.
- Payment status enum values are `INITIATED`, `PENDING`, `CONFIRMED`, `FAILED`, `REFUNDED`, `CANCELLED`.

## 3. Sales invoice lifecycle

Invoice statuses:

```text
DRAFT → ISSUED → PAID
          └────→ CANCELLED
```

Current automatic creation normally creates an **issued** invoice from a completed booking.

### Creation rules

- Booking is locked pessimistically.
- Booking must be `COMPLETED` and all duties must be complete.
- Duplicate invoice creation is prevented by existing-link checks plus database constraints.
- An organization billing entity is resolved.
- Invoice number is generated from a locked `FinancialYear` counter.
- Number format is `<invoicePrefix>-<six-digit counter>`.
- Booking, duty, party, GST and monetary information is copied/synchronized into invoice snapshot entities.
- Booking becomes `BILLED` and stores the invoice number.

### Financial fields remain distinct

Invoice keeps separate values for:

- subtotal;
- discount;
- taxable amount;
- GST components/total;
- grand total;
- total paid;
- balance.

Confirmed incoming payment calculation includes received amount and TDS according to the current model. Historical invoices must not be casually rebuilt from today's package/GST configuration.

### PDF

Sales invoice PDF generation uses server-side Thymeleaf/OpenHTMLToPDF. Organization billing data may supply logo, terms and UPI information. Duty/extra-charge slip images can be appended as attachments.

## 4. Estimate lifecycle

Statuses:

```text
DRAFT → SENT → VIEWED
               ↓
       PAYMENT_INITIATED → PAID → CONVERTED

DRAFT/SENT/... may also end as EXPIRED, CANCELLED or REVOKED where the service permits.
```

Link status is tracked separately as `ACTIVE`, `USED`, `EXPIRED`, `REVOKED`.

### Drafting and public link

- Employee workflow creates and edits estimates while allowed by estimate state.
- Entries and extra charges are recalculated into subtotal, discount, taxable amount, GST, estimated payable and advance.
- Creating a public link validates the estimate, revokes an earlier active token, stores a SHA-256 hash of a cryptographically random raw token, and transitions the estimate to `SENT`.
- Public access resolves the token and records view state/timestamps.

### Public payment

- Razorpay is the implemented public estimate gateway.
- Payable amount is the configured advance when positive; otherwise estimated payable.
- Local payment and provider order identities, amount and currency are verified.
- Verification uses provider-authoritative order/payment data and is designed to be idempotent.
- A confirmed provider payment is settled independently of booking conversion.

### Estimate → booking conversion

- Estimate is locked.
- A confirmed payment is required for the payment-driven conversion path.
- Existing `convertedBookingId` makes retry idempotent.
- The new booking copies estimate party, GST, discount, package/commercial snapshots, entries and extra charges.
- Duties are created as requested work and the booking is confirmed according to conversion rules.
- The payment is linked locally and the estimate becomes `CONVERTED`.

**Critical rule:** do not recalculate converted historical commercial values from current package rates.

## 5. Client-app booking/payment workflow

- Client authentication uses OTP plus organization context before a JWT exists.
- Client booking drafts are associated with the authenticated client after login.
- Client payment checkout/verification supports Razorpay.
- Provider confirmation is authoritative; frontend success alone must never mark a payment successful.
- The service validates organization, local payment/order identity, amount and currency before final settlement.

## 6. Driver duty public-link workflow

Employee side creates a short-lived access token for an allotted/running duty. Only the token hash is stored.

Default expiry logic in the current code:

- drop time + 24 hours when drop time exists;
- otherwise reporting time + 48 hours;
- otherwise roughly now + 48 hours.

### Start

- Booking must be `CONFIRMED` or `RUNNING`.
- Duty must be `ALLOTTED`.
- Duplicate start checkpoint is rejected.
- Start odometer must be non-negative.
- Odometer proof uses the backend image contract.
- Duty becomes `RUNNING`; booking becomes/runs as `RUNNING`.

### End

- Duty must be `RUNNING` and have a start checkpoint.
- Duplicate end is rejected.
- End KM cannot be below start KM.
- Expense/extra-charge/closing data is persisted.
- If geo coordinates are available, the service attempts return-to-garage distance/time estimation; geo failure is non-fatal.
- Duty becomes `COMPLETED`; totals are recalculated.
- When all duties complete, booking completion is invoked.

After completion the public flow can expose payment status and, when an amount is pending, generate a short-lived Razorpay fixed UPI QR. QR/provider failure is not allowed to undo duty completion.

## 7. Purchase invoice workflow

Purchase invoice statuses:

```text
DRAFT → COMPLETED → PAID
  └──────────────→ CANCELLED
```

- Vendor is represented by a supplier `Client`.
- Draft creation validates organization ownership of vendor, billing entities and selected duties.
- Selected duties are locked and snapshotted into purchase invoice entries.
- `activeBookingEntryId` uniqueness prevents the same duty from belonging to multiple active purchase invoices.
- Completion requires a vendor invoice number and at least one duty.
- A `PAID` purchase invoice cannot be directly cancelled by the current cancellation rule.
- Cancellation clears the active duty link so an eligible duty may be used again while retaining historical entry identity.

### Payment out

- Payment-out entries are local confirmed outbound payments.
- Payment cannot push paid amount over the purchase-invoice total.
- Paid/balance are recalculated; full settlement moves status to `PAID`.
- Paid and TDS are kept separately in the payment model and aggregated according to current service rules.

## 8. Report workflow

```text
QUEUED → PROCESSING → COMPLETED
   └───────────────→ FAILED / CANCELLED / EXPIRED
```

- API validates requested report against `ReportDefinitionRegistry`.
- Request state is persisted before background processing.
- After commit, a dedicated async executor runs generation.
- State transitions use new transactions and locks.
- Recovery can retry stale queued/processing records subject to configured timeouts and attempt limits.
- Download is only valid for a completed report owned by the requesting organization.

See [integrations.md](integrations.md) for currently exposed report types.
