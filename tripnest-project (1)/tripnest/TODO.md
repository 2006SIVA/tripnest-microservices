# Project outcomes and SRS acceptance criteria

## 1. Traveller accounts, profiles, and role protection
- **FR-01 — User Registration and Login:** The system shall allow a new user to register with basic personal details and allow a registered user to log in using valid credentials.
- **FR-02 — User Profile Management:** The system shall allow a logged-in user to view and update their profile information, such as name, contact details, and password.
- User Management Module — Main Operations: Register a new user, authenticate login, view and update profile details, and distinguish between traveller and administrator roles.
- Protect administrator-level operations from traveller accounts.

## 2. Destination discovery and destination administration
- **FR-03 — Destination Search:** The system shall allow a user to search for travel destinations using keywords or filters such as location or category.
- **FR-04 — Destination Details:** The system shall display detailed information about a selected destination, including a description and associated travel packages.
- **FR-13 — Admin Management of Destinations:** The system shall allow an administrator to add, update, or remove destination records available to users.

## 3. Travel-package browsing and administration
- **FR-05 — Travel Package Browsing:** The system shall allow a user to browse and view details of travel packages linked to a destination, including package inclusions and price.
- **FR-14 — Admin Management of Travel Packages:** The system shall allow an administrator to add, update, or remove travel packages associated with destinations.

## 4. Hotel search, availability, booking selection, and administration
- **FR-06 — Hotel Search and Booking:** The system shall allow a user to search for available hotels for given travel dates and select one to include in a booking.
- **FR-15 — Admin Management of Hotels:** The system shall allow an administrator to add, update, or remove hotel listings available for booking.

## 5. Transportation search, availability, booking selection, and administration
- **FR-07 — Transportation Search and Booking:** The system shall allow a user to search for available transportation options for given travel dates and select one to include in a booking.
- **FR-16 — Admin Management of Transportation:** The system shall allow an administrator to add, update, or remove transportation listings available for booking.

## 6. Combined booking creation, lifecycle, confirmation, history, and administration
- **FR-08 — Travel Booking Creation:** The system shall allow a user to create a travel booking that combines a selected destination or package with the chosen hotel and/or transportation.
- If no package/hotel/transport is selected, the destination itself remains bookable at its administrator-managed starting price.
- **FR-09 — Booking Management:** The system shall allow a user to view, update where applicable, or cancel an existing booking before it is finalized or completed.
- **FR-11 — Booking Confirmation:** The system shall generate and display a booking confirmation to the user once payment for the booking has been successfully completed.
- **FR-12 — Booking History:** The system shall allow a user to view a history of their past and current bookings along with their status.
- **FR-17 — Admin Management of Bookings:** The system shall allow an administrator to view booking records across users and update a booking's status when required (for example, to mark it as cancelled).
- Use Factory Pattern to create the applicable hotel or transportation booking item and Builder Pattern to assemble a booking with optional package/hotel/transport selections.

## 7. Payment outcomes and data integrity
- **FR-10 — Payment Processing:** The system shall allow a user to make a payment against a created booking and shall record the payment status of the booking.
- A successful payment shall trigger booking confirmation; a failed payment shall not confirm the booking.
- Do not claim that the demonstration mock provider charges money or process/store payment-card details.

## 8. Microservices, database, interfaces, usability, and deployment
- **NFR-01 — Performance:** The system shall respond to common user actions, such as search and booking creation, within an acceptable time under normal load conditions.
- **NFR-02 — Security:** The system shall protect user credentials and payment-related information using secure authentication and data-handling practices.
- **NFR-03 — Scalability:** The microservices-based architecture shall allow individual services, such as booking or payment, to be scaled independently as demand increases.
- **NFR-04 — Availability:** The system shall be designed so that the failure of one non-critical service does not bring down the entire platform.
- **NFR-05 — Reliability:** The system shall consistently and accurately record booking and payment information without data loss under normal operating conditions.
- **NFR-06 — Maintainability:** The system shall be organized into independent, well-documented services and modules so that future changes can be made with minimal impact on unrelated parts of the system.
- **NFR-07 — Usability:** The system shall provide a simple and consistent web interface that allows users to search, book, and manage trips without requiring technical knowledge.
- **NFR-08 — Compatibility:** The system's web interface shall function correctly on commonly used modern web browsers.
- Use Java/Spring Boot backend services communicating through REST APIs, Docker containerization, a browser-accessible web interface, and service-owned persistence; exchange cross-service data via REST rather than direct/shared database access.
- The specific payment provider is not fixed and is a pluggable interface. Native mobile applications, a named payment gateway or third-party travel API, and real-time chat are outside current scope.
- Supply schema/migration and seed setup, API documentation, a Docker Compose launch path, configuration example, and complete setup/run documentation. Clearly disclose if any checks cannot run in this sandbox.
