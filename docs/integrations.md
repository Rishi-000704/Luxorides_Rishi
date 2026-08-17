# Integrations and Background Processing

## 1. Payment gateways

### Verified runtime adapter: Razorpay

The code contains a working Razorpay integration with organization-specific encrypted credentials. It supports use cases including client booking/estimate payment order creation, verification, capture/settlement logic and driver-duty UPI QR/status flows.

Payment verification must remain:

- organization-safe;
- local payment/entity-safe;
- provider order/payment identity-safe;
- amount-safe;
- currency-safe;
- idempotent.

Never mark a local payment confirmed only because a frontend callback says success.

### Gateway enum vs runtime support

`PaymentGateway` contains:

```text
RAZORPAY
CASHFREE
STRIPE
PAYU
MANUAL_ENTRY
```

Public/client gateway controllers implement Razorpay; `MANUAL_ENTRY` represents local/manual payment recording. The other enum values are **not proof of implemented adapters**.

## 2. Email

Provider enum contains:

```text
ZEPTO_MAIL
SMTP
SENDGRID
AWS_SES
MAILGUN
POSTMARK
```

Current mail delivery service implements the Zepto Mail path. Unsupported provider selections are not equivalent to working adapters.

Thymeleaf email templates exist for:

- booking confirmation/cancellation;
- duty allotment/re-allotment;
- duty closure/re-closure;
- payment pending/confirmation;
- refund initiated/completed.

Notification listeners are generally asynchronous.

## 3. SMS

Provider enum contains:

```text
MSG91
TWILIO
GUPSHUP
TEXTLOCAL
AWS_SNS
```

Current runtime SMS adapter is MSG91. Client OTP and operational notifications depend on organization/provider configuration.

## 4. Google Maps / location

Location logic is abstracted behind a provider chain. The Google provider uses `WebClient` and Resilience4j retry/circuit-breaker configuration. A fallback provider/path exists so selected geo failures can degrade rather than break unrelated workflow completion.

Current responsibilities include combinations of:

- place/address resolution;
- coordinate/state information;
- distance and duration estimation;
- airport proximity/detection logic;
- return-to-garage estimation for public duty completion.

Google Maps API key is configuration/secrets data and must stay server-side.

## 5. File storage

`FileService` is the image-storage boundary.

Current validation includes:

- JPEG/PNG binary validation;
- maximum service-level image size of 10 MB;
- maximum image pixel count of 25 million;
- generated UUID-based filename;
- storage below configured `filepath`.

Global Spring multipart limits are broader (50 MB per file, 5000 MB request), so callers must not assume the global limit equals the stricter image-service rules.

Images retrieved through `/file/{filename}` are in a filter-chain public namespace. Do not store secrets or private documents behind that route.

## 6. Invoice PDF and QR

`PdfService` uses:

- Thymeleaf HTML templates;
- OpenHTMLToPDF/PDFBox;
- Montserrat resources;
- JSoup for HTML processing/sanitization use cases;
- ZXing for QR generation.

Sales invoice PDF can use organization billing entity data such as logo, rich-text terms and UPI ID. Duty/extra-charge slips may be rendered as attachment pages. QR generation failure is handled so it does not necessarily make the invoice itself impossible to render.

Purchase invoice PDF is a separate template path and should not be assumed to have feature parity with sales invoice branding.

## 7. Report engine

Report metadata is centralized in `ReportDefinitionRegistry`. Treat that registry—not enum presence or generator class presence—as the user-facing capability contract.

### Available in the current registry

| Report | Format | Period rule |
|---|---|---|
| GSTR-1 (`GSTR1`) | JSON | Single calendar month |
| Sales Report (`SALES_REGISTER`) | CSV | Date range |
| Purchase Report (`PURCHASE_REGISTER`) | CSV, JSON | Date range |

### Registry entries marked coming soon

- GSTR-3B Summary
- Purchase GST Register
- HSN Summary
- B2B Register
- B2C Register
- Sales Summary
- Purchase Summary
- Client Ledger
- Client Billing Entity Ledger
- Vendor Ledger

Do not expose a report type merely because an enum or generator references it; expose only definitions the registry marks available with supported formats.

### Execution

1. API creates a persistent `ReportRequest` in `QUEUED` state.
2. After transaction commit, `ReportRequestedListener` submits work to `reportTaskExecutor`.
3. Processor moves state through locked/new-transaction operations.
4. Generator writes a report artifact through `ReportFileStorageService`.
5. Completed report can be downloaded by the owning organization.
6. Recovery logic can pick stale queued/processing requests for retry.

Checked default production settings:

- queued timeout: 2 minutes;
- processing timeout: 10 minutes;
- max attempts: 3;
- timezone: Asia/Kolkata;
- executor core: 2;
- max: 4;
- queue capacity: 50.

## 8. Data exchange

CSV data exchange has centralized resource metadata and resource-specific handlers.

Current resource families include:

- clients;
- vendors;
- packages;
- master vehicles;
- fleet vehicles;
- drivers.

Lookup resources include clients, vendors and master vehicles.

Current defaults include schema version `1.0`, maximum 5000 rows and maximum 5 MiB import size. Import first prepares/validates the data and permissions, then applies mutations transactionally; avoid redesigning it into silent partial import without an explicit product decision.

Validation should continue to cover headers, required values, types/enums, references, duplicate semantics, organization ownership and per-resource create/update permissions.
