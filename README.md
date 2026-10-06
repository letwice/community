# community-store-poc

Standalone proof-of-concept project — intentionally **not** part of `app-lithium-full`.

The Technical Analysis lives at [`docs/ta/community-store-poc-ta.adoc`](docs/ta/community-store-poc-ta.adoc).

To render it locally:

```bash
gem install asciidoctor asciidoctor-diagram
asciidoctor -r asciidoctor-diagram docs/ta/community-store-poc-ta.adoc -o community-store-poc-ta.html
open community-store-poc-ta.html
```

(Requires Graphviz for the PlantUML diagrams: `brew install graphviz`.)

## What's in the box

| Module | What it is | Port |
| --- | --- | --- |
| `common` | Shared library: JWT resource-server security, error handling, `PageResponse`, service-to-service REST clients | — |
| `server-auth` | OAuth2 token endpoint (password, client_credentials, refresh_token), registration, mocked email verification, JWKS | 9600 (`/auth`) |
| `service-user` | Profiles, roles, admin verification queue, encrypted banking details, payout summaries, session activity | 9601 (`/service-user`) |
| `service-notification` | Rich inbox; EMAIL/SMS are logged as `SIMULATED_SENT` instead of being sent | 9602 (`/service-notification`) |
| `service-product` | Listings, community activities, simulated EFT/escrow orders, reviews, vendor sales summary | 9603 (`/service-product`) |
| `service-ui-desktop` | JavaFX client for Vendor, Community Leader, Faculty and Admin (the only client with admin tools) | desktop app |
| `web-mobile` | Mobile-first vanilla JS SPA. Students browse and buy; vendors, community leaders and faculty also get their role's tools (see below) | 9700 |

All four backend services share one MySQL schema (`community_store`). Each service runs its own Flyway migrations with a separate history table (`flyway_schema_history_<service>`).

## Quick start (no technical knowledge needed)

Follow **[GETTING_STARTED.md](GETTING_STARTED.md)**. Install Docker Desktop, then double-click:

| Step | macOS | Windows |
| --- | --- | --- |
| One-time setup: checks dependencies, explains how to install anything missing, builds the app | `Setup.command` | `Setup.cmd` |
| Start the app (data kept) | `Start.command` | `Start.cmd` |
| Reset, start, then run the 108 API tests, which also load demo data | `Start with test data.command` | `Start with test data.cmd` |
| Stop (data kept) | `Stop.command` | `Stop.cmd` |
| Optional JavaFX desktop client (needs Java 17+) | `Open desktop app.command` | `Open desktop app.cmd` |

The launchers call `scripts/*.sh` (macOS/Linux) and `scripts/*.ps1` (Windows PowerShell 5.1+). From a terminal:

```bash
./scripts/start.sh --with-test-data --yes --no-browser
```

On Windows the equivalent is `scripts\start.ps1 -WithTestData -Yes -NoBrowser`. Use `scripts/stop.sh --reset` (`-Reset` on Windows) to stop and wipe all data.

## Developer setup

**Docker is the only requirement for running the app.** The root `Dockerfile` builds every service with Maven inside Docker, serves the mobile SPA with nginx (`web-mobile` container on :9700), and packages the Postman collection with Newman (`api-tests`, compose profile `tests`). Setup stores the MySQL host port it picked in `.env` (`MYSQL_HOST_PORT`) when 3306 is taken.

```bash
docker compose up -d --build                           # whole app
docker compose --profile tests run --rm api-tests      # run the API tests against it
docker compose down -v                                 # stop and wipe data
```

For development outside Docker you also need JDK 17. Maven comes via the wrapper (`./mvnw`, or `mvnw.cmd` on Windows):

```bash
./mvnw test                                            # unit + Testcontainers integration tests
./mvnw -pl service-ui-desktop -am install -DskipTests && ./mvnw -pl service-ui-desktop javafx:run
docker compose up -d mysql                             # database only, for running a service from the IDE
python3 web-mobile/serve.py                            # SPA without Docker (stop the web-mobile container first)
```

## Demo accounts

Flyway seeds these accounts. They all use the PoC test password `Password123!`.

| Email | Role | State |
| --- | --- | --- |
| `admin@community.store` | Admin | — |
| `student@university.com` | Student | Verified. Has one completed order (ready to review) and one shipped order (ready to confirm receipt) |
| `vendor@community.store` | Vendor | Verified, banking details on file, 12 listings (business no. `2019/123456/07`) |
| `leader@community.store` | Community Leader | Verified |
| `faculty@university.com` | Faculty (Vendor + Community Leader) | Verified, banking details on file, 8 listings |
| `pending.vendor@community.store` | Vendor | `PENDING_REVIEW`: appears in the admin verification queue (business no. `2024/654321/07`) |

The seed data also includes four activities: two upcoming, and two past ones that can be reviewed.

## What each role sees in the mobile web app

The header shows a badge with your role. The extra tab and Home sections depend on it:

| Role | Extra tab | What it adds |
| --- | --- | --- |
| Student | — | Browse, cart and checkout, orders, reviews, bulletin, inbox |
| Vendor | 🏪 **Sell** | Sales dashboard (total, paid out, pending, orders to ship), listings (create, edit, remove), banking details. Vendors can mark orders shipped from the order page |
| Community leader | 📣 **Events** | Post, edit and cancel events on the campus bulletin |
| Faculty | 🧰 **Manage** | Everything vendors and community leaders get |
| Admin | 🛡 **Admin** | Needs-attention list, verification queue, user search, account status and roles, platform and per-vendor sales dashboards, orders and payouts (release funds, refund disputes), event approval with ratings and reviews. Admins can't buy, so the Cart tab is hidden |

Role pages are hidden from other roles, and the server enforces the same rules, so typing a URL doesn't get around them. Selling stays locked until banking details are on file and an admin has verified the account; the UI says which step is missing.

## Admin approvals and alerts

Admins get an inbox notification, linked to the right page, when they need to act:

| Alert | When | Admin action |
| --- | --- | --- |
| **Release funds for ESC-…** | A buyer confirms receipt | Release the escrowed funds to the seller |
| **Dispute on ESC-…** | A buyer raises a dispute | Release to the seller or refund the buyer |
| **New / edited event awaiting approval** | An organiser posts or edits an event | Approve (it goes live on the bulletin) or reject with a reason |
| **Low rating: review account** | A seller's or organiser's average rating first drops below 2.5★ with 3+ reviews | Open the account and suspend it if needed |

Changes from the original TA flow:
- **Funds release:** confirming receipt no longer releases funds automatically; the order waits in `DELIVERED_CONFIRMED` for an admin. Buyers can review as soon as they confirm receipt. An admin can also release a shipped order the buyer never confirmed. Set `COMMUNITY_MARKETPLACE_AUTO_RELEASE_FUNDS=true` to restore automatic release.
- **Event approval:** new events start as `PENDING_APPROVAL` and are hidden from the bulletin until approved. Editing a published or rejected event sends it back for approval.
- **Suspensions:** suspended and deactivated users can't log in or refresh their session; a session that's already open ends when its token expires, within an hour. Admins can't suspend themselves or remove their own admin role.
- **Settings:** the low-rating threshold and minimum review count are `COMMUNITY_MARKETPLACE_LOW_RATING_THRESHOLD` and `COMMUNITY_MARKETPLACE_LOW_RATING_MIN_REVIEWS`.

The desktop app's admin tabs (verification queue, disputes, sales summary) still work but weren't extended; the web app is the full admin console.

## Registration rules

Both registration forms (the SPA and the desktop dialog) validate these rules as you type. `server-auth` enforces them again, so they can't be bypassed through the API.

- **Confirm email:** the confirmation field must match the email field (case-insensitive). This check is client-side only; the API takes a single `email`.
- **University domain:** students and faculty must register with an address on exactly `@university.com`, so `@student.university.com` and `@university.com.example.org` are rejected. Vendors and community leaders can use any domain. The domain is configurable: `COMMUNITY_AUTH_UNIVERSITY_EMAIL_DOMAIN` (server), `universityEmailDomain` in `web-mobile/js/config.js`, and `-Dcommunity.university-email-domain` for the desktop client.
- **Business number:** vendors must enter a business registration number of 4–50 letters, digits, spaces, `/` or `-`. Other account types can't send one. It is stored on the user and shown to admins in the verification queue.

### Vendor onboarding

1. Register with a business number.
2. Verify the email. Until then, login returns 403.
3. Log in to the desktop app. It opens on **Banking Details**. Listing is blocked with "Add your banking details before listing products" until they're on file.
4. Once banking details are saved, listing stays blocked until an admin verifies the account.
5. An admin verifies the account under **Verification Queue**; the business number is shown there. The vendor can now list items.

Faculty follow the same banking-details and admin-verification gate before listing. The Postman folder **Vendor onboarding** runs these steps end to end.

## Walking through the acceptance criteria

- **Student:** register in the SPA (the simulated verification token is pre-filled), browse, add to cart, check out, tap **I have paid**, then **I received my order** once the vendor ships, and leave a review.
- **Vendor:** register in either client, verify the email, and add banking details on the desktop. Then log in as admin and verify the account under **Verification Queue**. The vendor can now list a product, mark orders shipped on the **Dashboard**, and watch Total / Paid Out / Pending update.
- **Community Leader:** register, get verified, and publish an activity under **My Activities**. Students see it on the SPA **Bulletin** and can review it once its date has passed.
- **Faculty:** sees the Vendor tabs and the Activities tab together.

The Postman collection at [`docs/api/community-store-poc.postman_collection.json`](docs/api/community-store-poc.postman_collection.json) exercises every endpoint. Its folders are ordered: run **Auth** first so the token variables are filled in.

## Tests

```bash
mvn test
```

- Unit tests cover the escrow state machine, the order, activity and verification rules, token issuing, encryption, and the RBAC matrix (`ControllerSecurityTest`).
- `*IntegrationTest` classes run the real Flyway migrations against MySQL 8 via Testcontainers. They are skipped automatically when Docker isn't running.

## PoC simplifications (by design — see the TA's risk register)

- **Payments are simulated.** "I have paid" is a buyer self-attestation, and no money moves. The UIs label this as **SIMULATED**.
- **Identity verification is mocked.** Email tokens are logged by `server-auth` and echoed in the register response as `simulatedVerificationToken`; set `COMMUNITY_AUTH_EXPOSE_VERIFICATION_TOKEN=false` to turn this off. Students are verified as soon as their email is verified. Vendors, community leaders and faculty wait for a manual admin decision.
- **Keys and secrets are development values.** The JWT signing key is generated at `server-auth` startup, so restarting it logs everyone out. Client secrets and the AES key for banking details are dev defaults in `application.yml`; override them through environment variables for anything shared.

## Additions beyond the TA's API table

These endpoints were needed to implement flows that the TA describes:

- `service-user`
  - `GET /frontend/users/me/banking-details`
  - `POST /frontend/users/me/logout`
  - System endpoints: `POST /system/users`, `GET /system/users/by-email`, `POST /system/users/{guid}/email-verified`, `POST /system/users/{guid}/sessions`, `PUT /system/users/{guid}/payout-summary` (the "payout summary delta" from the sequence diagram)
- `service-product`
  - `GET /frontend/orders/{guid}`
  - `POST /frontend/orders/{guid}/cancel` and `/dispute`
  - `GET /backoffice/orders`
  - `POST /backoffice/orders/{guid}/resolve`, for the state machine's cancel, dispute and admin-resolution transitions
  - `GET /frontend/activities/{guid}`, `PUT` and `DELETE /frontend/activities/{guid}`, so organisers can manage activities
  - `?mine=true` on the product and activity lists, for the desktop dashboards
- `service-notification`
  - The inbox lists `RICH_INBOX` notifications by default; pass `?type=EMAIL` to see the simulated outbox.
- `UserSummaryResponse` now also returns `email`, `userType`, `verificationStatus` and `hasBankingDetails`. The TA asked for `hasBankingDetails`.
