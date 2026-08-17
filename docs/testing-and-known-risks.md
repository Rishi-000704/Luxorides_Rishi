# Testing and Known Risks

## 1. Automated test state

Only two test classes are present in the backend:

- `CoreApplicationTests` (`contextLoads`)
- `CloseDutyCommandBindingTest`

That is not enough automated coverage for the financial/tenant/lifecycle surface of Fleetovo. Until coverage grows, production safety depends heavily on scoped code review plus browser/API regression testing.

The Docker build skips tests, so a successful image build is **not** evidence that tests passed.

## 2. Minimal backend regression inventory

### Authentication and isolation

- [ ] Employee valid/invalid login.
- [ ] Client OTP generate/verify.
- [ ] JWT expiry/invalid signature handling.
- [ ] Employee cannot access another organization's record by changing an ID.
- [ ] Related-record cross-organization IDs are rejected.
- [ ] Missing authority returns the expected authorization failure.

### Booking/duty

- [ ] Create booking.
- [ ] Add/update duty and verify totals.
- [ ] Confirm only with required duties/state.
- [ ] Allot and re-allot with same-org driver/vendor/vehicle.
- [ ] Close duty with normal charges/slip.
- [ ] Re-close and verify replacement/recalculation behavior.
- [ ] Complete booking only when all duties are complete.
- [ ] Cancellation synchronizes dependent invoice/refund behavior as expected.

### Invoice/finance

- [ ] Completed booking creates one invoice, not duplicates under retry/concurrency.
- [ ] Invoice number increments safely for the financial year/billing entity.
- [ ] GST components and place-of-supply behavior are correct.
- [ ] Discount/subtotal/tax/grand-total/paid/balance stay distinct.
- [ ] Multiple confirmed payments/TDS aggregate correctly.
- [ ] PDF includes intended billing entity logo/terms/UPI QR.
- [ ] PDF attachment images render without junk files or blank pages.
- [ ] Regeneration does not silently apply current rates to historical data.

### Estimate/public payment

- [ ] Public link expiry/revoke behavior.
- [ ] Repeated link generation revokes prior active token.
- [ ] Razorpay order amount/currency/organization are correct.
- [ ] Verification rejects wrong order/payment/amount/currency.
- [ ] Verification retry is idempotent.
- [ ] Confirmed payment survives conversion failure and later retry converts once.
- [ ] Converted booking matches estimate snapshots.

### Driver duty link

- [ ] Expired/revoked token rejected.
- [ ] Duplicate start rejected.
- [ ] End KM < start KM rejected.
- [ ] JPEG/PNG odometer proof accepted; invalid/non-image rejected.
- [ ] End completes duty even when non-critical geo lookup fails.
- [ ] All duties complete → booking completes/invoice trigger works.
- [ ] Pending amount QR/payment polling is org/payment safe.

### Purchase invoice

- [ ] Only eligible vendor duties offered.
- [ ] Same duty cannot enter two active purchase invoices.
- [ ] Complete requires vendor invoice number and entries.
- [ ] Payment-out cannot exceed grand total.
- [ ] Full payment marks paid.
- [ ] Cancel releases active duty linkage while retaining history.

### Reports/data exchange

- [ ] Only registry-available report types/formats can be requested.
- [ ] Request is non-blocking and progresses queued → processing → completed.
- [ ] Failed/stale request recovery honors max attempts.
- [ ] Cross-org report download rejected.
- [ ] CSV invalid header/type/reference/duplicate/org ownership rejected.
- [ ] Import permissions distinguish create/update where handlers require it.
- [ ] Invalid import does not produce unintended partial mutations.

## 3. Current implementation risks / maintenance cautions

### High priority operational hardening

1. **Secrets in source/default configuration.** Do not reproduce them; externalize and rotate production credentials.
2. **Production seeding is enabled in properties.** Seeder currently short-circuits when organizations exist, but production should explicitly disable it.
3. **Wildcard CORS with credentials.** Restrict trusted origins before treating the backend as internet-hardened.
4. **Public `/file/**`.** Treat stored images as public and do not route confidential documents through this service.
5. **Technical error details enabled in production properties.** Review exposure level and keep sanitization.
6. **No migration framework.** `ddl-auto=update` needs an explicit SQL/phased strategy for meaningful production schema changes.
7. **Very small automated test suite.** Browser/manual verification currently carries too much regression risk.

### Compatibility cautions

- Provider enums list gateways/mail/SMS providers that do not all have adapters. Do not surface enum values as “supported providers”.
- `VehicleStatus.DEPRICATED` is a persisted typo; rename only with data/code compatibility migration.
- `Currency` currently supports INR only.
- Legacy float-based paths remain in selected monetary/time code. New financial code should use `BigDecimal` end-to-end.
- Sales and purchase invoice PDF templates do not necessarily have feature parity.
- `/auth/**` is filter-chain permit-all, including `.../me`; controller semantics may still require an authenticated token. Do not infer endpoint behavior from the matcher alone.
- Client-facing reads must be reviewed for **record ownership**, not merely organization ownership, before broadening access. Organization scoping prevents cross-tenant access but is not always the same as “this exact client owns this booking”.

## 4. Incident documentation rule

When a production issue reveals a reusable constraint, add a short incident note to this file or a dedicated incident log with:

```text
Symptom
Root cause
Why existing safeguards missed it
Fix
Regression test
Permanent engineering rule
```

Do not accumulate transient debugging logs as permanent documentation.

