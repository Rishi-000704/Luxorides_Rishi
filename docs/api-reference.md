# API Reference

**Inventory:** 174 controller mappings across 32 controllers.

This is a compact source inventory. It is not an OpenAPI schema and does not duplicate every DTO field. Request/response DTOs remain defined by Java source under `com.core.dtos`.

## Reading protection

- `ROLE_EMPLOYEE + X`: method/class security requires an employee plus the named authority.
- `EMPLOYEE_CONTEXT`: SpEL checks `SecurityContextUtil.isEmployee()`.
- `Authenticated`: an authentication check is present or the filter chain requires authenticated context.
- `Filter permitAll`: the current filter chain permits the namespace. The method may still expect a token/context or validate a public access token.

Organization ownership checks inside services/repositories are still required regardless of the controller annotation.

## AuthenticationController

| Method | Path | Protection | Handler |
|---|---|---|---|
| `POST` | `/auth/client/generate-otp` | Filter `permitAll` | `generateOtp` |
| `GET` | `/auth/client/me` | Filter `permitAll` | `clientme` |
| `POST` | `/auth/client/verify-otp` | Filter `permitAll` | `verifyOtp` |
| `POST` | `/auth/employee/login` | Filter `permitAll` | `login` |
| `GET` | `/auth/employee/me` | Filter `permitAll` | `employeeme` |

## BillingEntityController

| Method | Path | Protection | Handler |
|---|---|---|---|
| `GET` | `/client/app/billing-entities` | ROLE_CLIENT + Authenticated | `list` |
| `GET` | `/client/app/billing-entities/by-gstin/{gstin}` | ROLE_CLIENT + Authenticated | `getByGstin` |
| `POST` | `/client/app/billing-entities/{billingEntityId}/attach` | ROLE_CLIENT + Authenticated | `attach` |
| `DELETE` | `/client/app/billing-entities/{billingEntityId}/detach` | ROLE_CLIENT + Authenticated | `detach` |

## CityGarageController

| Method | Path | Protection | Handler |
|---|---|---|---|
| `POST` | `/config/city-garage` | ROLE_EMPLOYEE + GARAGE_ADD | `addGarage` |
| `PUT` | `/config/city-garage` | ROLE_EMPLOYEE + GARAGE_EDIT | `updateGarage` |
| `GET` | `/config/city-garage/list` | ROLE_EMPLOYEE + GARAGE_VIEW | `getGarageList` |
| `GET` | `/config/city-garage/{city}` | ROLE_EMPLOYEE + GARAGE_VIEW | `getGarage` |

## ClientBookingController

| Method | Path | Protection | Handler |
|---|---|---|---|
| `GET` | `/api/client/bookings` | Authenticated | `getMyBookings` |
| `POST` | `/api/client/bookings` | Authenticated | `draftBooking` |
| `GET` | `/api/client/bookings/invoice/{invoiceNumber}/pdf` | Authenticated | `downloadInvoicePdf` |
| `GET` | `/api/client/bookings/{bookingId}` | Authenticated | `getBookingDetail` |

## ClientController

| Method | Path | Protection | Handler |
|---|---|---|---|
| `POST` | `/client` | ROLE_EMPLOYEE + CLIENT_ADD | `create` |
| `GET` | `/client/add-billing-entity` | ROLE_EMPLOYEE + CLIENT_EDIT | `addBillingEntity` |
| `POST` | `/client/add-passenger` | ROLE_EMPLOYEE + CLIENT_EDIT | `addPassenger` |
| `POST` | `/client/billing-entity` | ROLE_EMPLOYEE + CLIENT_EDIT | `saveEntity` |
| `GET` | `/client/billing-entity/gstin/{gstin}` | ROLE_EMPLOYEE + CLIENT_VIEW | `getEntityByGSTIN` |
| `POST` | `/client/billing-entity/update` | ROLE_EMPLOYEE + CLIENT_EDIT | `updateEntity` |
| `GET` | `/client/org` | ROLE_EMPLOYEE + CLIENT_VIEW | `getByOrg` |
| `GET` | `/client/page` | ROLE_EMPLOYEE + CLIENT_VIEW | `page` |
| `GET` | `/client/remove-billing-entity` | ROLE_EMPLOYEE + CLIENT_EDIT | `removeBillingEntity` |
| `GET` | `/client/suppliers` | ROLE_EMPLOYEE + CLIENT_VIEW | `getSuppliers` |
| `POST` | `/client/update` | ROLE_EMPLOYEE + CLIENT_EDIT | `update` |
| `POST` | `/client/update-passenger` | ROLE_EMPLOYEE + CLIENT_EDIT | `updatePassenger` |
| `POST` | `/client/update-profile/{clientId}` | ROLE_EMPLOYEE + CLIENT_EDIT | `updateProfile` |
| `GET` | `/client/{clientId}` | ROLE_EMPLOYEE + CLIENT_VIEW | `get` |

## ClientPaymentController

| Method | Path | Protection | Handler |
|---|---|---|---|
| `POST` | `/client/app/payments/order` | Authenticated | `createOrder` |
| `POST` | `/client/app/payments/verify` | Authenticated | `verifyPayment` |

## DataExchangeController

| Method | Path | Protection | Handler |
|---|---|---|---|
| `GET` | `/data-exchange/lookups/{type}` | EMPLOYEE_CONTEXT + DATA_EXCHANGE_VIEW | `lookup` |
| `GET` | `/data-exchange/resources` | EMPLOYEE_CONTEXT + DATA_EXCHANGE_VIEW | `resources` |
| `GET` | `/data-exchange/{resource}/export` | EMPLOYEE_CONTEXT + DATA_EXCHANGE_VIEW + DATA_EXCHANGE_EXPORT | `export` |
| `POST` | `/data-exchange/{resource}/import` | EMPLOYEE_CONTEXT + DATA_EXCHANGE_VIEW + DATA_EXCHANGE_IMPORT | `importCsv` |
| `GET` | `/data-exchange/{resource}/metadata` | EMPLOYEE_CONTEXT + DATA_EXCHANGE_VIEW | `metadata` |
| `GET` | `/data-exchange/{resource}/template` | EMPLOYEE_CONTEXT + DATA_EXCHANGE_VIEW | `template` |
| `POST` | `/data-exchange/{resource}/validate` | EMPLOYEE_CONTEXT + DATA_EXCHANGE_VIEW + DATA_EXCHANGE_IMPORT | `validate` |

## DriverController

| Method | Path | Protection | Handler |
|---|---|---|---|
| `GET` | `/employee/drivers` | ROLE_EMPLOYEE + DRIVER_VIEW | `getDrivers` |
| `POST` | `/employee/drivers` | ROLE_EMPLOYEE + DRIVER_ADD | `createDriver` |
| `GET` | `/employee/drivers/client/{clientId}` | ROLE_EMPLOYEE + DRIVER_VIEW | `getDriversByClient` |
| `GET` | `/employee/drivers/self` | ROLE_EMPLOYEE + DRIVER_VIEW | `getSelfDrivers` |
| `DELETE` | `/employee/drivers/{driverId}` | ROLE_EMPLOYEE + DRIVER_DELETE | `deleteDriver` |
| `GET` | `/employee/drivers/{driverId}` | ROLE_EMPLOYEE + DRIVER_VIEW | `getDriver` |
| `PUT` | `/employee/drivers/{driverId}` | ROLE_EMPLOYEE + DRIVER_EDIT | `updateDriver` |
| `POST` | `/employee/drivers/{driverId}/pic` | ROLE_EMPLOYEE + DRIVER_EDIT | `updateDriverPic` |

## EmailProviderConfigController

| Method | Path | Protection | Handler |
|---|---|---|---|
| `GET` | `/config/email-provider` | ROLE_EMPLOYEE + ORG_NOTIFICATION_CONFIG_VIEW | `getCurrent` |
| `POST` | `/config/email-provider` | ROLE_EMPLOYEE + ORG_NOTIFICATION_CONFIG_EDIT | `upsert` |

## EmployeeBookingController

| Method | Path | Protection | Handler |
|---|---|---|---|
| `POST` | `/booking/employee` | ROLE_EMPLOYEE + BOOKING_ADD | `createBooking` |
| `POST` | `/booking/employee/add-duty` | ROLE_EMPLOYEE + BOOKING_ADD_DUTY | `addDuty` |
| `POST` | `/booking/employee/add-payment` | ROLE_EMPLOYEE + BOOKING_ADD_PAYMENT | `savePayment` |
| `POST` | `/booking/employee/allot-duty` | ROLE_EMPLOYEE + BOOKING_ALLOT_DUTY | `allotDuty` |
| `POST` | `/booking/employee/cancel-booking` | ROLE_EMPLOYEE + BOOKING_CANCEL | `cancelBooking` |
| `POST` | `/booking/employee/close-duty` | ROLE_EMPLOYEE + BOOKING_CLOSE_DUTY | `closeDuty` |
| `POST` | `/booking/employee/complete-booking` | ROLE_EMPLOYEE + BOOKING_COMPLETE | `completeBooking` |
| `POST` | `/booking/employee/confirm-booking` | ROLE_EMPLOYEE + BOOKING_CONFIRM | `confirmBooking` |
| `POST` | `/booking/employee/confirm-payment` | ROLE_EMPLOYEE + BOOKING_CONFIRM_PAYMENT | `confirmPayment` |
| `POST` | `/booking/employee/duties/page` | ROLE_EMPLOYEE + BOOKING_VIEW | `getDutyPage` |
| `POST` | `/booking/employee/page` | ROLE_EMPLOYEE + BOOKING_VIEW | `getPage` |
| `POST` | `/booking/employee/reallot-duty` | ROLE_EMPLOYEE + BOOKING_REALLOT_DUTY | `reAllotDuty` |
| `POST` | `/booking/employee/reclose-duty` | ROLE_EMPLOYEE + BOOKING_RECLOSE_DUTY | `reCloseDuty` |
| `POST` | `/booking/employee/update` | ROLE_EMPLOYEE + BOOKING_EDIT | `updateBooking` |
| `POST` | `/booking/employee/update-duty` | ROLE_EMPLOYEE + BOOKING_EDIT_DUTY | `updateDuty` |
| `POST` | `/booking/employee/update-payment` | ROLE_EMPLOYEE + BOOKING_EDIT_PAYMENT | `updatePayment` |
| `GET` | `/booking/employee/{bookingId}` | ROLE_EMPLOYEE + BOOKING_VIEW | `getProfile` |

## EmployeeController

| Method | Path | Protection | Handler |
|---|---|---|---|
| `POST` | `/config/employee` | ROLE_EMPLOYEE + EMPLOYEE_ADD | `addEmployee` |
| `PUT` | `/config/employee` | ROLE_EMPLOYEE + EMPLOYEE_EDIT | `updateEmployee` |
| `GET` | `/config/employee/list` | ROLE_EMPLOYEE + EMPLOYEE_VIEW | `getList` |
| `POST` | `/config/employee/reset-password` | ROLE_EMPLOYEE + EMPLOYEE_RESET_PASSWORD | `resetPassword` |
| `POST` | `/config/employee/update-password` | ROLE_EMPLOYEE | `updatePassword` |
| `POST` | `/config/employee/update-pic` | ROLE_EMPLOYEE | `updatePic` |
| `POST` | `/config/employee/update-profile` | ROLE_EMPLOYEE | `updateProfile` |
| `GET` | `/config/employee/user/{userId}` | ROLE_EMPLOYEE + EMPLOYEE_VIEW | `getUser` |
| `PUT` | `/config/employee/users/{userId}/authorities` | ROLE_EMPLOYEE + EMPLOYEE_UPDATE_AUTHORITIES | `updateAuthorities` |
| `PUT` | `/config/employee/users/{userId}/enabled` | ROLE_EMPLOYEE + EMPLOYEE_EDIT | `updateUserEnabled` |

## EmployeeDriverDutyLinkController

| Method | Path | Protection | Handler |
|---|---|---|---|
| `POST` | `/booking/employee/{bookingId}/duties/{dutyId}/driver-link` | ROLE_EMPLOYEE + BOOKING_ALLOT_DUTY | `generateDriverDutyLink` |

## EmployeeDriverDutySubmissionController

| Method | Path | Protection | Handler |
|---|---|---|---|
| `GET` | `/booking/employee/{bookingId}/duties/{dutyId}/driver-submission` | ROLE_EMPLOYEE + BOOKING_VIEW | `getDriverDutySubmission` |

## EmployeeEstimateController

| Method | Path | Protection | Handler |
|---|---|---|---|
| `POST` | `/booking/employee/estimates` | ROLE_EMPLOYEE + BOOKING_ADD | `create` |
| `POST` | `/booking/employee/estimates/page` | ROLE_EMPLOYEE + BOOKING_VIEW | `page` |
| `POST` | `/booking/employee/estimates/update` | ROLE_EMPLOYEE + BOOKING_EDIT | `update` |
| `GET` | `/booking/employee/estimates/{estimateId}` | ROLE_EMPLOYEE + BOOKING_VIEW | `get` |
| `GET` | `/booking/employee/estimates/{estimateId}/client-link` | ROLE_EMPLOYEE + BOOKING_VIEW | `getClientLink` |
| `POST` | `/booking/employee/estimates/{estimateId}/client-link` | ROLE_EMPLOYEE + BOOKING_EDIT | `createClientLink` |
| `POST` | `/booking/employee/estimates/{estimateId}/client-link/revoke` | ROLE_EMPLOYEE + BOOKING_EDIT | `revokeClientLink` |
| `POST` | `/booking/employee/estimates/{estimateId}/entries` | ROLE_EMPLOYEE + BOOKING_EDIT | `addEntry` |
| `POST` | `/booking/employee/estimates/{estimateId}/entries/update` | ROLE_EMPLOYEE + BOOKING_EDIT | `updateEntry` |
| `POST` | `/booking/employee/estimates/{estimateId}/entries/{estimateEntryId}/delete` | ROLE_EMPLOYEE + BOOKING_EDIT | `deleteEntry` |

## EmployeeInvoiceController

| Method | Path | Protection | Handler |
|---|---|---|---|
| `GET` | `/invoice/client/{clientId}/page` | ROLE_EMPLOYEE + BOOKING_VIEW | `getClientInvoicePage` |
| `GET` | `/invoice/generate/{bookingId}` | ROLE_EMPLOYEE + INVOICE_GENERATE | `create` |
| `GET` | `/invoice/page` | ROLE_EMPLOYEE + BOOKING_VIEW | `getPage` |
| `GET` | `/invoice/pending/client/page` | ROLE_EMPLOYEE + BOOKING_VIEW | `getClientPendingPage` |
| `GET` | `/invoice/pending/page` | ROLE_EMPLOYEE + BOOKING_VIEW | `getInvoicePendingPage` |
| `GET` | `/invoice/reload/{bookingId}/pdf` | ROLE_EMPLOYEE + INVOICE_REGENERATE | `reloadInvoicePdf` |
| `GET` | `/invoice/{invoiceNumber}/pdf` | ROLE_EMPLOYEE + BOOKING_VIEW | `downloadInvoicePdf` |

## ExternalDriverDutyController

| Method | Path | Protection | Handler |
|---|---|---|---|
| `GET` | `/driver-api/duty/{token}` | Filter `permitAll` | `getDutySummary` |
| `POST` | `/driver-api/duty/{token}/end` | Filter `permitAll` | `submitEnd` |
| `GET` | `/driver-api/duty/{token}/payment-status` | Filter `permitAll` | `checkQrPaymentStatus` |
| `POST` | `/driver-api/duty/{token}/start` | Filter `permitAll` | `submitStart` |

## FileController

| Method | Path | Protection | Handler |
|---|---|---|---|
| `GET` | `/file/{filename}` | Filter `permitAll` | `getFile` |

## FinancialYearController

| Method | Path | Protection | Handler |
|---|---|---|---|
| `GET` | `/config/financial-years` | ROLE_EMPLOYEE + FINANCIAL_YEAR_VIEW | `list` |
| `POST` | `/config/financial-years` | ROLE_EMPLOYEE + FINANCIAL_YEAR_ADD | `create` |
| `PUT` | `/config/financial-years` | ROLE_EMPLOYEE + FINANCIAL_YEAR_EDIT | `update` |
| `GET` | `/config/financial-years/current` | ROLE_EMPLOYEE + FINANCIAL_YEAR_VIEW | `current` |
| `GET` | `/config/financial-years/{id}` | ROLE_EMPLOYEE + FINANCIAL_YEAR_VIEW | `get` |

## FleetVehicleController

| Method | Path | Protection | Handler |
|---|---|---|---|
| `POST` | `/vehicle/fleet` | ROLE_EMPLOYEE + FLEET_VEHICLE_ADD | `create` |
| `GET` | `/vehicle/fleet/client/{clientId}` | ROLE_EMPLOYEE + FLEET_VEHICLE_VIEW | `getClientFleet` |
| `GET` | `/vehicle/fleet/master-vehicle/{masterVehicleId}` | ROLE_EMPLOYEE + FLEET_VEHICLE_VIEW | `getFleetByMasterVehicle` |
| `GET` | `/vehicle/fleet/page` | ROLE_EMPLOYEE + FLEET_VEHICLE_VIEW | `page` |
| `GET` | `/vehicle/fleet/self` | ROLE_EMPLOYEE + FLEET_VEHICLE_VIEW | `getSelfFleet` |
| `POST` | `/vehicle/fleet/update` | ROLE_EMPLOYEE + FLEET_VEHICLE_EDIT | `update` |
| `DELETE` | `/vehicle/fleet/{fleetVehicleId}` | ROLE_EMPLOYEE + FLEET_VEHICLE_DELETE | `delete` |
| `GET` | `/vehicle/fleet/{fleetVehicleId}` | ROLE_EMPLOYEE + FLEET_VEHICLE_VIEW | `get` |

## MasterVehicleController

| Method | Path | Protection | Handler |
|---|---|---|---|
| `POST` | `/vehicle/master` | ROLE_EMPLOYEE + MASTER_VEHICLE_ADD | `create` |
| `GET` | `/vehicle/master/org` | ROLE_EMPLOYEE + MASTER_VEHICLE_VIEW | `getByOrg` |
| `GET` | `/vehicle/master/page` | ROLE_EMPLOYEE + MASTER_VEHICLE_VIEW | `page` |
| `POST` | `/vehicle/master/update` | ROLE_EMPLOYEE + MASTER_VEHICLE_EDIT | `update` |
| `POST` | `/vehicle/master/update-pic/{vehicleId}` | ROLE_EMPLOYEE + MASTER_VEHICLE_EDIT | `updatePic` |
| `DELETE` | `/vehicle/master/{vehicleId}` | ROLE_EMPLOYEE + MASTER_VEHICLE_DELETE | `delete` |
| `GET` | `/vehicle/master/{vehicleId}` | ROLE_EMPLOYEE + MASTER_VEHICLE_VIEW | `get` |

## OrgBillingEntityController

| Method | Path | Protection | Handler |
|---|---|---|---|
| `GET` | `/config/billing-entities` | ROLE_EMPLOYEE + ORG_BILLING_ENTITY_VIEW | `list` |
| `POST` | `/config/billing-entities` | ROLE_EMPLOYEE + ORG_BILLING_ENTITY_ADD | `add` |
| `POST` | `/config/billing-entities` | ROLE_EMPLOYEE + ORG_BILLING_ENTITY_ADD | `addWithLogo` |
| `PUT` | `/config/billing-entities` | ROLE_EMPLOYEE + ORG_BILLING_ENTITY_EDIT | `update` |
| `PUT` | `/config/billing-entities` | ROLE_EMPLOYEE + ORG_BILLING_ENTITY_EDIT | `updateWithLogo` |
| `GET` | `/config/billing-entities/{id}` | ROLE_EMPLOYEE + ORG_BILLING_ENTITY_VIEW | `get` |

## OrgController

| Method | Path | Protection | Handler |
|---|---|---|---|
| `GET` | `/config/org` | ROLE_EMPLOYEE + ORG_VIEW | `getOrg` |
| `PUT` | `/config/org` | ROLE_EMPLOYEE + ORG_EDIT | `updateOrg` |

## PackageController

| Method | Path | Protection | Handler |
|---|---|---|---|
| `POST` | `/package` | ROLE_EMPLOYEE + PACKAGE_ADD | `create` |
| `GET` | `/package/client/{clientId}` | ROLE_EMPLOYEE + PACKAGE_VIEW | `getByClient` |
| `GET` | `/package/master-vehicle/{masterVehicleId}` | ROLE_EMPLOYEE + PACKAGE_VIEW | `getByMasterVehicle` |
| `POST` | `/package/package-list-for-duty-form` | ROLE_EMPLOYEE + PACKAGE_VIEW | `resolve` |
| `POST` | `/package/page` | ROLE_EMPLOYEE + PACKAGE_VIEW | `page` |
| `POST` | `/package/update` | ROLE_EMPLOYEE + PACKAGE_EDIT | `update` |
| `DELETE` | `/package/{packageId}` | ROLE_EMPLOYEE + PACKAGE_DELETE | `delete` |
| `GET` | `/package/{packageId}` | ROLE_EMPLOYEE + PACKAGE_VIEW | `get` |

## PassengerController

| Method | Path | Protection | Handler |
|---|---|---|---|
| `GET` | `/client/app/passengers` | ROLE_CLIENT + Authenticated | `list` |
| `POST` | `/client/app/passengers` | ROLE_CLIENT + Authenticated | `add` |
| `DELETE` | `/client/app/passengers/{passengerId}` | ROLE_CLIENT + Authenticated | `delete` |
| `PUT` | `/client/app/passengers/{passengerId}` | ROLE_CLIENT + Authenticated | `update` |

## PaymentGatewayConfigController

| Method | Path | Protection | Handler |
|---|---|---|---|
| `GET` | `/config/payment-gateway` | ROLE_EMPLOYEE + ORG_PAYMENT_GATEWAY_CONFIG_VIEW | `getCurrent` |
| `POST` | `/config/payment-gateway` | ROLE_EMPLOYEE + ORG_PAYMENT_GATEWAY_CONFIG_EDIT | `upsert` |
| `GET` | `/config/payment-gateway/{gateway}` | ROLE_EMPLOYEE + ORG_PAYMENT_GATEWAY_CONFIG_VIEW | `getByGateway` |

## ProfileController

| Method | Path | Protection | Handler |
|---|---|---|---|
| `PUT` | `/client/app/profile` | ROLE_CLIENT + Authenticated | `updateProfile` |
| `POST` | `/client/app/profile/image` | ROLE_CLIENT + Authenticated | `updateProfileImage` |
| `GET` | `/client/app/profile/me` | ROLE_CLIENT + Authenticated | `me` |

## PublicEstimateController

| Method | Path | Protection | Handler |
|---|---|---|---|
| `GET` | `/estimate-api/client/{token}` | Filter `permitAll` | `view` |
| `POST` | `/estimate-api/client/{token}/payment` | Filter `permitAll` | `createPayment` |
| `GET` | `/estimate-api/client/{token}/payment-status` | Filter `permitAll` | `paymentStatus` |
| `POST` | `/estimate-api/client/{token}/payment/verify` | Filter `permitAll` | `verifyPayment` |

## PurchaseInvoiceController

| Method | Path | Protection | Handler |
|---|---|---|---|
| `POST` | `/purchase-invoice/draft` | ROLE_EMPLOYEE + PURCHASE_INVOICE_ADD | `createDraft` |
| `POST` | `/purchase-invoice/page` | ROLE_EMPLOYEE + PURCHASE_INVOICE_VIEW | `page` |
| `POST` | `/purchase-invoice/payment-out` | ROLE_EMPLOYEE + PAYMENT_OUT_ADD | `addPaymentOut` |
| `GET` | `/purchase-invoice/vendors/{vendorId}/duties/{bookingEntryId}/packages` | ROLE_EMPLOYEE + PURCHASE_INVOICE_VIEW | `packageOptions` |
| `GET` | `/purchase-invoice/vendors/{vendorId}/eligible-duties` | ROLE_EMPLOYEE + PURCHASE_INVOICE_VIEW | `eligibleDuties` |
| `GET` | `/purchase-invoice/{id}` | ROLE_EMPLOYEE + PURCHASE_INVOICE_VIEW | `get` |
| `POST` | `/purchase-invoice/{id}/cancel` | ROLE_EMPLOYEE + PURCHASE_INVOICE_CANCEL | `cancel` |
| `POST` | `/purchase-invoice/{id}/complete` | ROLE_EMPLOYEE + PURCHASE_INVOICE_COMPLETE | `complete` |
| `GET` | `/purchase-invoice/{id}/pdf` | ROLE_EMPLOYEE + PURCHASE_INVOICE_VIEW | `downloadPurchaseInvoicePdf` |

## ReportRequestController

| Method | Path | Protection | Handler |
|---|---|---|---|
| `POST` | `/reports/requests` | ROLE_EMPLOYEE + REPORT_REQUEST | `requestReport` |
| `GET` | `/reports/requests/metadata` | ROLE_EMPLOYEE + REPORT_VIEW | `metadata` |
| `POST` | `/reports/requests/page` | ROLE_EMPLOYEE + REPORT_VIEW | `page` |
| `GET` | `/reports/requests/{id}` | ROLE_EMPLOYEE + REPORT_VIEW | `get` |
| `GET` | `/reports/requests/{id}/download` | ROLE_EMPLOYEE + REPORT_DOWNLOAD | `download` |
| `POST` | `/reports/requests/{id}/refresh` | ROLE_EMPLOYEE + REPORT_REQUEST | `refresh` |

## SmsProviderConfigController

| Method | Path | Protection | Handler |
|---|---|---|---|
| `GET` | `/config/sms-provider` | ROLE_EMPLOYEE + ORG_NOTIFICATION_CONFIG_VIEW | `getCurrent` |
| `POST` | `/config/sms-provider` | ROLE_EMPLOYEE + ORG_NOTIFICATION_CONFIG_EDIT | `upsert` |

## VehicleCatalogController

| Method | Path | Protection | Handler |
|---|---|---|---|
| `GET` | `/client/app/catalog/explorer` | ROLE_CLIENT + Authenticated | `explorer` |
| `GET` | `/client/app/catalog/filters` | ROLE_CLIENT + Authenticated | `filters` |
| `GET` | `/client/app/catalog/trending` | ROLE_CLIENT + Authenticated | `trending` |
| `POST` | `/client/app/catalog/vehicles/search-by-itinerary` | ROLE_CLIENT + Authenticated | `searchByItinerary` |
| `POST` | `/client/app/catalog/{vehicleId}/validate` | ROLE_CLIENT + Authenticated | `validate` |

## Welcome

| Method | Path | Protection | Handler |
|---|---|---|---|
| `GET` | `/` | Filter `permitAll` | `getMessage` |

## Contract conventions

- JSON property naming is lower camel case.
- Money/tax calculations should use `BigDecimal`; `Money` carries currency.
- Most business data is organization-scoped.
- Pagination/sort keys should remain backend-allowlisted rather than exposing arbitrary JPA property names.
- Multipart/image operations must honor the exact service/controller field contract and `FileService` image validation.
- API errors are structured and include `X-Trace-Id` correlation.
- Public estimate/driver-duty APIs use opaque access tokens; raw tokens are not stored.
- Payment success is server-verified against provider/local records.
