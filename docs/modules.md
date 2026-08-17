# Module Map

This file is a navigation aid, not a replacement for the source.

| Module | What it owns | Primary implementation |
|---|---|---|
| Authentication | Employee password login, client OTP login, JWT issuance | `AuthenticationController`, `AuthenticationService`, `JwtService`, `JwtAuthenticationFilter` |
| Organization | Organization profile/configuration | `OrgController`, `OrgService`, `Org` |
| Employees & authorities | Employee CRUD, password reset, permission assignment | `EmployeeController`, `Employee`, `User`, `Authority` |
| Clients / vendors | Customer and supplier records; vendors are `Client` records with supplier semantics | `ClientController`, `ClientService`, `Client`, `ClientBillingEntity` |
| Billing configuration | Organization billing entities, GST/bank/UPI/logo/terms data | `OrgBillingEntityController`, `OrgBillingEntityService`, `OrgBillingEntity` |
| Financial years | Invoice numbering scope/counter | `FinancialYearController`, `FinancialYearService`, `FinancialYear` |
| Master vehicles | Vehicle make/model catalog | `MasterVehicleController`, `MasterVehicleService`, `MasterVehicle` |
| Fleet vehicles | Owned/client-supplied operational vehicles | `FleetVehicleController`, `FleetVehicleService`, `FleetVehicle` |
| Drivers | Driver records and client/vendor ownership association | `DriverController`, `DriverService`, `Driver` |
| Packages | Sales/purchase package rates and commercial rules | `PackageController`, `PackageService`, `Package`, `PackageSnapshot` |
| Bookings | Booking header, duties, assignment, closing/re-closing, manual receipts | `EmployeeBookingController`, `BookingService`, `Booking`, `BookingEntry` |
| Client bookings | Client self-service drafts, confirmation and booking history | `ClientBookingController`, `ClientBookingService` |
| Driver duty link | Public token-based start/end duty submissions | `EmployeeDriverDutyLinkController`, `ExternalDriverDutyController`, `EmployeeDriverDutySubmissionService` |
| Estimates | Drafting, public links, payment and conversion to booking | `EmployeeEstimateController`, `PublicEstimateController`, `EstimateService`, `EstimateConversionService` |
| Sales invoices | Issued booking invoice snapshot, PDF, regenerate/sync | `EmployeeInvoiceController`, `InvoiceService`, `PdfService` |
| Purchase invoices | Vendor duty aggregation, completion/cancellation/payment-out | `PurchaseInvoiceController`, `PurchaseInvoiceService` |
| Payments | Booking/estimate payments, Razorpay verification, outbound vendor payments | `ClientPaymentController`, `EstimatePaymentSettlementService`, `Payment`, `PaymentOut` |
| Reports | Queued GST/sales/purchase report generation/download | `ReportRequestController`, `ReportRequestService`, `ReportGenerationProcessorService` |
| Data exchange | CSV metadata/templates/validate/import/export | `DataExchangeController`, handlers under `dataexchange.handler` |
| Notification providers | Organization-specific SMS/email configuration | `SmsProviderConfigController`, `EmailProviderConfigController` |
| Payment gateway config | Encrypted organization-specific gateway credentials | `PaymentGatewayConfigController`, `PaymentGatewayConfigService` |
| Locations/garages | Garage configuration and geo/distance/provider logic | `CityGarageController`, `CityGarageService`, `location.*` |
| Files | Validated public image storage/retrieval | `FileController`, `FileService` |
| PDF rendering | Sales/purchase invoice rendering and attachments | `PdfService`, `templates/invoice`, `templates/purchase-invoice` |
| Errors/auditing | Stable errors, trace IDs, created/updated actors | `GlobalExceptionHandler`, `ErrorCode`, `RequestTraceFilter`, `AuditableEntity` |

## Module coupling that matters

- **Booking → Invoice:** completing a booking triggers sales invoice creation after commit.
- **Booking edits → Invoice:** later eligible booking/duty/payment changes can synchronize an existing invoice snapshot.
- **Estimate → Booking:** conversion creates a booking from the estimate's commercial/GST snapshot rather than reconstructing current rates.
- **Duty → Booking:** closing/re-closing a duty recalculates booking totals and may complete/reopen/synchronize dependent state.
- **Purchase invoice → Duty:** a vendor duty can be protected from inclusion in multiple active purchase invoices through the active-entry uniqueness design.
- **Payment → Financial status:** confirmed incoming payments affect invoice/estimate/booking states; outbound payments affect purchase-invoice paid/balance state.
- **Provider config → runtime adapters:** credentials are stored per organization and decrypted only for server-side adapter use.
