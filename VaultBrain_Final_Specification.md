# VaultBrain (PocketSecretary) — Complete Technical Specification
## Final Edition | AI-Executable Build Prompt
**Version:** 3.0-Final  
**Architecture:** Vault-First + Lenses (Not Hubs)  
**Platform:** Android 10+ (API 29+)  
**Language:** Kotlin (100% Native)  
**UI:** Jetpack Compose + Material 3 + Material You Dynamic Color  
**Database:** Room + SQLCipher (AES-256)  
**AI Stack:** Google ML Kit + TensorFlow Lite + Kotlin Heuristics  
**Offline-First:** Yes. Cloud is opt-in, user-controlled, encrypted client-side.  
**Target Markets:** MENA (RTL-first), Global (LTR fallback)  
**Monetization:** Freemium (4 Core Lenses free, Extended Lenses unlockable)

---

## 1. EXECUTIVE SUMMARY

VaultBrain is a **secure, offline-first, AI-assisted life vault**. It does not present itself as 26 separate apps. It presents as **one unified vault** viewed through dynamic **Lenses**.

**The One Promise:**  
> *Scan anything — receipts, IDs, prescriptions, tickets — and VaultBrain will extract the data, warn you before anything expires, and keep it safe behind biometric encryption.*

**Core Philosophy:**
- **One Database to Rule Them All:** A single polymorphic `VaultItem` entity. Lenses are views, not silos.
- **Zero-Server by Default:** All data lives encrypted on-device. Cloud features use the user's own accounts (Google Drive, Gmail) with client-side encryption.
- **AI is a Pipeline, Not a Feature:** ML Kit OCR → TFLite Classification → Regex Heuristics → Confidence Threshold → User Review Fallback.
- **RTL-First Engineering:** Built for Arabic, Hebrew, and Persian from Day 1. English is the fallback, not the default.

---

## 2. ARCHITECTURE OVERVIEW

### 2.1 Modular Gradle Structure
```
:root
├── :app                          (Minimal shell + navigation)
├── :core
│   ├── :database                 (Room + SQLCipher + migrations)
│   ├── :security                 (Keystore, Biometric, encryption utils)
│   ├── :ai                       (ML Kit wrappers, TFLite, heuristics)
│   ├── :notifications            (WorkManager + AlarmManager dispatcher)
│   ├── :common                   (Models, utils, coroutines dispatchers)
│   └── :billing                  (Google Play BillingClient wrapper)
├── :feature
│   ├── :vault                    (Universal item list, search, detail)
│   ├── :capture                  (Camera, OCR, share-intent receiver)
│   ├── :briefing                 (Morning digest / Hero Card)
│   ├── :lens-money               (Finance, subscriptions, receipts)
│   ├── :lens-health              (Prescriptions, labs, diet, workout)
│   ├── :lens-travel              (Flights, hotels, PNR, check-in)
│   ├── :lens-bureaucracy         (IDs, passports, residency, applications)
│   ├── :lens-home                (Appliances, internet, inventory, utilities)
│   ├── :lens-car                 (Maintenance, fuel, parking)
│   ├── :lens-media               (Books, movies, wishlists)
│   ├── :lens-productivity        (Tasks, calendar, business cards)
│   └── :settings                 (Security, backup, export, about)
└── :sync
    ├── :drive                    (Encrypted backup to Google Drive)
    └── :gmail                    (Local-only Gmail API parser)
```

### 2.2 Navigation Architecture
- **Single Activity:** `MainActivity.kt`
- **Compose Navigation:** Deep-linkable routes (`vaultbrain://item/{id}`, `vaultbrain://lens/money`)
- **Predictive Back Gesture:** Enabled for Android 15+
- **Per-App Language:** Supported via `localeConfig` in manifest

### 2.3 State Management
- **Pattern:** MVI (Model-View-Intent) with Unidirectional Data Flow
- **UI Layer:** Compose + ViewModel + StateFlow
- **Domain Layer:** UseCases per action (e.g., `CaptureReceiptUseCase`, `GenerateMorningBriefingUseCase`)
- **Data Layer:** Repository per domain (not per hub). One `VaultRepository` with lens-filtering methods.

---

## 3. SECURITY & PRIVACY SPECIFICATION

### 3.1 Encryption at Rest
- **Database:** SQLCipher for Android with AES-256-CBC.
- **Key Management:** Master key stored in Android Keystore (hardware-backed if available). Key requires biometric auth for decryption.
- **Fallback:** If biometrics unavailable, user must set a 6-digit PIN or alphanumeric passphrase (minimum 8 chars, 1 number, 1 symbol).
- **Decoy Vault:** Secondary passphrase opens a dummy vault with 3-5 fake items. Plausible deniability under duress.

### 3.2 Encryption in Transit (Cloud)
- **Client-Side Encryption (CSE):** All data encrypted with AES-256-GCM using a user-derived key (PBKDF2, 100k iterations) BEFORE upload to Google Drive.
- **Google never sees plaintext.** The app uploads only ciphertext blobs.
- **Gmail Sync:** OAuth 2.0 restricted scope. Parsing happens locally. No email content is stored permanently; only extracted metadata is saved to the encrypted vault.

### 3.3 Runtime Security
- **Screenshot Prevention:** `Window.setFlags(FLAG_SECURE)` on all activities showing sensitive data.
- **Screen Recording Block:** Same flag prevents screen capture.
- **Auto-Lock:** Configurable session timeout (30s / 2min / 5min / Never). Backgrounding the app triggers a countdown. Return requires re-auth.
- **Clipboard Monitor (Opt-in):** If enabled, detect copied IBANs, tracking numbers, or addresses and suggest vault capture. Must show a persistent notification while active.
- **Audit Trail:** Local-only log of all access events (view, export, delete, failed auth). Stored encrypted, viewable in Settings > Security.

### 3.4 Emergency Access
- **Emergency Card:** Accessible from lock screen widget without full vault unlock. Shows:
  - Blood type, allergies, emergency contacts
  - Current prescriptions (names only, not dosages)
  - Medical ID number / Insurance ID
- **Trigger:** Long-press power button → Emergency information (Android native) OR VaultBrain widget.

---

## 4. DATABASE SCHEMA (The Single Source of Truth)

### 4.1 Core Entity
```kotlin
@Entity(tableName = "vault_items")
data class VaultItemEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),

    // Core metadata
    val title: String,                          // User-editable or AI-extracted
    val summary: String?,                       // AI-generated 1-line description
    val rawOcrText: String?,                    // Full text from OCR
    val sourceType: SourceType,                 // CAMERA, SHARE_INTENT, MANUAL, GMAIL_SYNC, DRIVE_RESTORE
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val capturedImageUri: String?,              // Local path to original scan

    // Polymorphic payload (JSON)
    val parsedMetadata: String,                 // JSON string, schema varies by lens

    // Lens tags (many-to-many simulated via CSV or separate table)
    val lensTags: String,                       // JSON array: ["MONEY", "HEALTH", "TRAVEL"]

    // Temporal intelligence
    val expiryDate: Long?,                      // Primary expiry (passport, warranty, subscription)
    val secondaryAlertDate: Long?,              // 48h before, 30 days before, etc.
    val recurringRule: String?,                 // RRule JSON for prescriptions, subscriptions

    // Confidence & AI
    val aiConfidence: Float,                    // 0.0 - 1.0
    val aiClassification: String?,              // TFLite output class
    val needsReview: Boolean = false,           // True if confidence < 0.70

    // User overrides
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val userNotes: String?,
    val customFields: String?,                  // JSON map of user-defined key-values

    // Sync & backup
    val driveBackupId: String?,                 // Google Drive file ID (if backed up)
    val lastSyncAt: Long?,

    // Entitlement (for paywalled lenses)
    val requiredLensTier: Tier = Tier.FREE        // FREE, PRO, ULTRA
)
```

### 4.2 Supporting Tables
```kotlin
// Full-Text Search (FTS4)
@Entity(fts4 = true)
data class VaultItemFts(
    val title: String,
    val rawOcrText: String,
    val summary: String
)

// Audit Log
@Entity(tableName = "audit_logs")
data class AuditLogEntity(
    @PrimaryKey val id: Long = 0,
    val timestamp: Long,
    val action: String,         // VIEW, EDIT, DELETE, EXPORT, AUTH_FAIL, BACKUP
    val itemId: String?,
    val details: String?
)

// Notification Schedule
@Entity(tableName = "notification_queue")
data class NotificationQueueEntity(
    @PrimaryKey val id: String,
    val itemId: String,
    val triggerAt: Long,
    val channelId: String,      // CRITICAL, REMINDER, BACKGROUND, PROMO
    val title: String,
    val body: String,
    val isDelivered: Boolean = false
)
```

### 4.3 JSON Schema Examples for `parsedMetadata`

**Money Lens (Receipt):**
```json
{
  "type": "RECEIPT",
  "merchant": "Carrefour",
  "totalAmount": 450.00,
  "currency": "EGP",
  "items": [{"name": "Milk", "price": 45.00}],
  "paymentMethod": "INSTAPAY",
  "transactionId": "TRX-998877",
  "taxId": "123-456-789",
  "isTaxDeductible": false,
  "subscriptionId": null,
  "warrantyMonths": 12
}
```

**Health Lens (Prescription):**
```json
{
  "type": "PRESCRIPTION",
  "medicationName": "Amoxicillin 500mg",
  "dosage": "1 tablet",
  "frequency": "3 times daily",
  "withFood": true,
  "prescribedBy": "Dr. Ahmed Hassan",
  "pharmacy": "Seif Pharmacy",
  "refillsRemaining": 2,
  "refillAlertDate": 1725148800000,
  "sideEffects": ["Nausea", "Diarrhea"]
}
```

**Bureaucracy Lens (Passport):**
```json
{
  "type": "PASSPORT",
  "documentNumber": "A12345678",
  "issuingCountry": "EGY",
  "issueDate": 1609459200000,
  "expiryDate": 1735689600000,
  "alert90Days": true,
  "alert30Days": true,
  "visaPagesRemaining": 4
}
```

---

## 5. AI & INTELLIGENCE PIPELINE

### 5.1 The Capture Pipeline (Strict Order)
```
User Action (Camera / Share / Screenshot)
    ↓
[1] Image Preprocessing (rotate, denoise, contrast boost)
    ↓
[2] Google ML Kit Text Recognition v2 (Latin + Arabic script)
    ↓
[3] Parallel Fast-Path Heuristics (Regex for dates, amounts, IBANs, emails, phone numbers)
    ↓
[4] TFLite Document Classifier (Input: 224x224 image + OCR text embedding)
    ↓
[5] Confidence Scoring
    ├── Confidence ≥ 0.85 → Auto-save, mark as reviewed
    ├── Confidence 0.70 - 0.84 → Save, flag for user review (yellow badge)
    └── Confidence < 0.70 → Show "Review & Correct" screen before save
    ↓
[6] Metadata Extraction & Entity Linking
    ↓
[7] Save to Room + Schedule Alerts + Update FTS Index + Trigger Morning Briefing refresh
```

### 5.2 TFLite Model Specification
- **Model Type:** Quantized MobileNetV3 + Text CNN hybrid (image + text features)
- **Classes:** RECEIPT, PRESCRIPTION, ID_DOCUMENT, PASSPORT, TICKET_FLIGHT, TICKET_TRAIN, HOTEL_BOOKING, INVOICE, WARRANTY_CARD, BUSINESS_CARD, GENERAL_DOCUMENT, MEME/JUNK
- **Input:** 224x224 RGB image + 128-dim text embedding from OCR
- **Output:** Softmax over 12 classes + confidence score
- **Training Data:** Synthetic (Python Faker + receipt templates) + public datasets (SROIE, CORD) + user opt-in contributions (anonymized, encrypted)
- **Fallback:** If model unavailable or fails, pure heuristic classification takes over.

### 5.3 Heuristic Engine (Fast-Path)
Kotlin-based regex and keyword matching for instant classification without ML inference:
- **Receipt:** Contains total, tax, merchant name, or barcode
- **Prescription:** Contains "mg", "tablet", "Dr.", "pharmacy", "dosage"
- **Passport/ID:** MRZ code detection, "Republic", "National ID", "Passport No"
- **Flight:** Contains "PNR", "boarding", "gate", "terminal", airline IATA code
- **Invoice:** Contains "IBAN", "SWIFT", "Invoice No", "VAT"
- **Junk/Meme:** Low text density, high image entropy, known meme aspect ratios

### 5.4 Duplicate Detection
- **Image Hashing:** Perceptual hash (pHash) on capture. If Hamming distance < 10 to existing item, suggest merge or skip.
- **Text Fingerprint:** MinHash on OCR text for near-duplicate detection.

### 5.5 Contextual Suggestions (Cross-Lens Intelligence)
After saving an item, the engine suggests cross-links:
- *"You saved a flight ticket. Add a hotel reservation to the same trip?"*
- *"This gym receipt was detected. Track a workout in Health?"*
- *"Dr. Ahmed Hassan appears in 3 prescriptions. View all health items from this doctor?"*

---

## 6. UI/UX ARCHITECTURE

### 6.1 Screen Hierarchy
```
MainActivity
├── Splash / Auth Gate (Biometric / PIN)
│   └── Emergency Access Button (if configured)
│
├── HomeScreen (The Only Home)
│   ├── Morning Briefing Hero Card (collapsible)
│   ├── Quick Capture FAB (Camera / Gallery / Text / Voice Note)
│   ├── Universal Search Bar (sticky, top)
│   ├── Recently Captured (horizontal carousel, last 5 items)
│   └── Active Lenses Grid (dynamic, 2-4 visible based on user data)
│       ├── 💰 Money (if money items exist)
│       ├── 🏥 Health (if health items exist)
│       ├── ✈️ Travel (if travel items exist)
│       └── 📄 Bureaucracy (if docs exist)
│       └── [+ Browse All Lenses]
│
├── VaultBrowserScreen
│   ├── Filter Chips: All | Pinned | Expiring Soon | Needs Review
│   ├── Sort: Date | Expiry | Title | Confidence
│   ├── Bulk Select Mode (long-press to enter)
│   └── Item Cards (swipe to archive, quick actions)
│
├── LensDetailScreen (e.g., lens-money)
│   ├── Lens-specific stats ("$X spent this month", "3 subscriptions expiring")
│   ├── Filtered list of VaultItems tagged with this lens
│   └── Lens-specific quick-add templates
│
├── CaptureFlowScreen
│   ├── Camera Preview (ML Kit real-time text bounding boxes)
│   ├── Review Screen (editable extracted fields, confidence badge)
│   └── Save Confirmation + Suggested Cross-Links
│
├── ItemDetailScreen
│   ├── Hero Image (original scan)
│   ├── Extracted Data (editable form, generated from JSON schema)
│   ├── Timeline (creation, edits, alerts, backups)
│   └── Actions: Share (encrypted PDF), Export, Duplicate, Delete
│
├── SearchScreen
│   ├── FTS4 Query + Fuzzy Matching
│   ├── Filter by Lens, Date Range, Has Image, Expiry Status
│   └── Recent Searches + Suggested Tags
│
└── SettingsScreen
    ├── Security (Biometric, PIN, Auto-lock, Audit Log, Decoy Vault)
    ├── Backup & Sync (Drive backup toggle, Gmail sync toggle, manual export)
    ├── Lenses Management (enable/disable, reorder, unlock Pro)
    ├── Notifications (per-channel settings, DND override rules)
    ├── Data (Export PDF/CSV/JSON, Import, Demo Mode)
    ├── Appearance (Theme, Dynamic Color, Language, Font Size)
    └── About / Help / Privacy Policy
```

### 6.2 Design Tokens (Material 3)
- **Color:** Dynamic color scheme (Material You) with fallback static palette.
- **Typography:** Responsive type scale. Minimum 12sp for captions, 16sp for body.
- **Spacing:** 8dp grid system.
- **Motion:** Predictive back gesture, shared element transitions for image preview, spring animations for list items.
- **RTL:** `LayoutDirection.Rtl` enforced. Icons mirrored where appropriate. Date/number formatting localized.
- **Accessibility:** Minimum touch target 48dp. TalkBack labels on every interactive element. Color contrast WCAG AA minimum.

### 6.3 Widgets & System Integration
- **Morning Briefing Widget (Glance):** 3x2 resizable. Shows today's top 3 urgencies.
- **Quick Capture Tile:** Quick Settings tile for instant camera capture.
- **App Shortcuts:** Long-press launcher icon → "Scan Receipt", "Add Prescription", "View Expiring".
- **Lock Screen Widget (Android 14+):** Emergency Access Card.

---

## 7. NOTIFICATION & ALERT SYSTEM

### 7.1 Unified Dispatcher Architecture
```kotlin
interface AlertDispatcher {
    fun scheduleAlert(itemId: String, triggerAt: Long, channel: AlertChannel)
    fun cancelAlert(itemId: String)
    fun rescheduleAll() // Called after boot, timezone change, or restore
}

class UnifiedAlertManager @Inject constructor(
    private val workManager: WorkManager,
    private val alarmManager: AlarmManager,
    private val notificationManager: NotificationManager
) : AlertDispatcher { ... }
```

### 7.2 Notification Channels
| Channel ID | Importance | Sound | DND Override | Use Case |
|------------|-----------|-------|--------------|----------|
| `critical` | HIGH | Custom urgent tone | YES | Passport expiry (30 days), prescription refill, subscription charge |
| `reminders` | DEFAULT | Standard | NO | General expiry warnings, parking meter, laundry pickup |
| `background` | LOW | None | NO | Backup complete, sync status, gallery scan done |
| `promotions` | MIN | None | NO | New lens available, Pro upgrade offer, tips |
| `capture` | HIGH | Short chime | NO | Quick capture confirmation, low-confidence review needed |

### 7.3 Smart Alert Rules
- **Bureaucracy:** Passport/ID alerts at 90 days, 30 days, 7 days, and day-of.
- **Finance:** Subscription alerts at 48 hours before trial expiry. Invoice due-date alerts.
- **Health:** Prescription dose reminders (respecting RRule). Refill alerts when `refillsRemaining <= 1`.
- **Travel:** Flight check-in reminder 24h before departure. Hotel checkout reminder.
- **Car:** Oil change based on odometer delta or date interval (whichever comes first).
- **Parking:** Countdown timer notification with "Extend" action button (if supported by meter).

### 7.4 Geofenced Reminders (Deferred to v2.0)
- Use `GeofencingClient` for location-based triggers.
- "Remind me to return dry cleaning when near [Shop Name]"
- Battery-conscious: 100m radius, dwell time 2 minutes.

---

## 8. LENS SPECIFICATIONS (Consolidated from 26 Hubs)

### 8.1 Core Lenses (Free Tier — v1.0 MVP)

#### 💰 MONEY LENS
**Covers:** Receipts, subscriptions, invoices, IBANs, promo codes, Instapay transactions  
**Features:**
- OCR receipt capture with auto-extraction of: merchant, total, tax, items list, payment method, transaction ID
- Subscription tracker: Detect recurring charges from receipts. Auto-calculate next billing date.
- Trial expiry hunter: Alert 48h before trial converts to paid.
- Budget matcher: Monthly spend by category (groceries, transport, health) with simple bar charts.
- Promo code wallet: Auto-extract codes from screenshots, show expiry, copy-to-clipboard button.
- IBAN/SWIFT quick-save: Validate checksum, detect bank from IBAN prefix.
- Export: Monthly expense report as PDF or CSV.

#### 🏥 HEALTH LENS
**Covers:** Prescriptions, lab results, InBody scans, workout logs, diet macros  
**Features:**
- Prescription OCR: Extract medication name, dosage, frequency, doctor, pharmacy. Auto-generate dose reminders.
- Complex dose timing: "Every 8 hours with food" → 3 daily alarms with meal proximity logic.
- Lab result history: Store historical values (HbA1c, cholesterol, vitamin D) with trend sparklines.
- Net Carbs / Keto calculator: OCR nutrition labels. Formula: Total Carbs - Fiber = Net Carbs. Flag if > daily limit.
- Workout checklist: Template-based (Push/Pull/Legs, cardio). Check off sets/reps. Track PRs.
- Medical ID: Emergency card data source.

#### ✈️ TRAVEL LENS
**Covers:** Flights, hotels, PNRs, check-in, bill splitting, address grabber  
**Features:**
- Travel Maestro: OCR boarding passes and booking confirmations. Extract: airline, flight number, PNR, gate, terminal, seat, departure time.
- Web check-in QR pins: Store generated QR codes for quick access at gate.
- Hotel booking storage: Check-in/out dates, confirmation number, address, phone.
- Bill Splitter: Group expense items. Calculate per-person share + tip. Generate shareable summary.
- Address/Tracking Grabber: OCR shipping labels. Extract tracking number, carrier, destination. Auto-detect carrier (Aramex, Bosta, FedEx) from tracking format.

#### 📄 BUREAUCRACY LENS
**Covers:** IDs, passports, residency permits, application checklists, document expiry  
**Features:**
- ID/Passport expiry radar: OCR document, extract expiry, MRZ code. Alerts at 90/30/7 days.
- Application checklist: Template for visa, driver's license, residency renewal. Track document collection progress.
- Residency proof sorter: Store rental contracts, utility bills, employer letters with issue dates.
- Document vault: High-security sub-section requiring re-auth every access (configurable).

#### 🎯 QUICK CAPTURE LENS (The Engine)
**Not a user-facing lens, but the ingestion pipeline that feeds all others.**
- **Share Intent Receiver:** `AndroidManifest.xml` declares `ACTION_SEND` and `ACTION_SEND_MULTIPLE` for images/text. `ShareActivity.kt` handles routing.
- **Camera Capture:** Custom camera using CameraX + ML Kit real-time text bounding boxes.
- **Gallery Import:** Multi-select with batch processing. Background processing with progress notification.
- **Text Note:** Manual entry with AI auto-classification attempt.
- **Voice Note (Text):** Speech-to-text (on-device Google STT) for quick capture without typing. **NO AUDIO STORAGE.** Transcribed text only.

### 8.2 Extended Lenses (Pro Tier — v1.5+)

#### 🏠 HOME LENS
**Covers:** Appliances, warranties, inventory, internet quota, utility readings  
**Features:**
- Appliance manual vault: OCR model numbers from appliance stickers. Store manual PDFs or links.
- Home inventory: "Don't buy what you already have." Searchable list of home items with purchase date, warranty link, photo.
- Internet quota tracker: Manual entry of data cap. Usage tracking via manual logs or carrier SMS parsing (if permission granted). Overage alerts.
- Utility readings: Store electricity/water/gas meter readings per rental unit. Track consumption trends.

#### 🚗 CAR LENS
**Covers:** Maintenance, fuel log, odometer, parking  
**Features:**
- Odometer tracking: Log mileage per fuel fill-up. Calculate MPG/L/100km.
- Maintenance intervals: Oil change (every 5,000 km or 6 months), tire rotation, filter changes. Alert based on whichever comes first.
- Fuel log: Price per liter, total cost, station name, location (optional).
- Parking ticket guard: OCR parking meter tickets. Countdown timer notification. "Add 1 hour" quick action if meter supports it.

#### 🧺 SERVICES LENS
**Covers:** Dry cleaning, laundry, returns  
**Features:**
- Dry clean ticket OCR: Store ticket number, items count, promised date. Pickup countdown.
- Return window guard: Auto-calculate return deadline from purchase date + store policy (14/30 days). Alert 2 days before window closes.

#### 🎬 MEDIA LENS
**Covers:** Books, movies, wishlists, gift ideas  
**Features:**
- Chat extraction: Parse shared movie/book recommendations from messaging apps. Extract title + IMDb/Goodreads deep link.
- Wishlist / Gift bank: Stealth mode (hide from main vault, accessible via specific gesture or PIN). Mark items as "purchased" or "idea".
- Watchlist / Readlist: Track completion status, rating, personal notes.

#### 🛡️ SCAM GUARD LENS
**Covers:** URL security, phishing detection, Wi-Fi QR sharing  
**Features:**
- URL scanner: Process shared links. Heuristic checks (suspicious TLD, homograph attacks, known-bad domain list stored locally). Flag risk level: Safe / Caution / Danger.
- Smart link categorization: Auto-tag URLs as Shopping, Banking, News, Social.
- Wi-Fi QR Generator: OCR Wi-Fi credentials from router stickers. Generate QR code for guest sharing. Never store plaintext password in UI (mask it).

#### 📋 PRODUCTIVITY LENS
**Covers:** Tasks, calendar, business cards, error codes, recipes  
**Features:**
- Chat-to-task: OCR or share text messages containing requests. "Pick up dry cleaning by Friday" → Task with deadline inferred from "Friday".
- Business card scanner: OCR name, title, company, phone, email, LinkedIn. Save to contacts (optional) or vault.
- Error code decoder: Manual lookup database of common appliance/car/tech error codes with solutions.
- Recipe-to-grocery: OCR recipe ingredients. Generate shopping checklist. Cross-check against Home Inventory lens to avoid duplicates.
- Financial news sentiment: Manual bookmarking of articles with user-added sentiment tag (Bullish / Bearish / Neutral). Not true AI sentiment (too heavy for on-device).

### 8.3 Future Lenses (Ultra Tier / v2.0+)
- **Gmail Auto-Ingest:** Continuous background sync to detect flight confirmations and invoices.
- **Semantic Gallery Search:** Vector-based image search (requires on-device embedding model).
- **Predictive Task Automation:** Full LLM-based task extraction and calendar insertion.
- **MCP Context Bridge:** Ingest local files (PDFs, docs) into vault context.

---

## 9. CLOUD & SYNC ARCHITECTURE

### 9.1 Google Drive Backup (Opt-in)
- **Frequency:** User-configurable (daily, weekly, manual) or triggered on significant changes (10+ new items).
- **Worker:** `DriveBackupWorker` (WorkManager, requires charging + WiFi).
- **Process:**
  1. Export Room database to JSON.
  2. Encrypt with AES-256-GCM using user passphrase-derived key.
  3. Upload to `AppData` folder in user's Drive (hidden, app-specific).
  4. Store `driveBackupId` and `lastSyncAt` in entity.
- **Restore:** Detect existing backup on fresh install. Prompt user to restore. Download → decrypt → import.
- **Conflict Resolution:** Last-write-wins with manual merge UI for critical items (passports, prescriptions).

### 9.2 Gmail Integration (Opt-in, Restricted Scope)
- **Scope:** `https://www.googleapis.com/auth/gmail.readonly` (restricted, requires Google verification).
- **Process:**
  1. OAuth consent screen + Google Sign-In.
  2. Background worker (`GmailSyncWorker`) queries for emails from known senders (airlines, banks, Amazon, etc.).
  3. Download email text locally. Parse with heuristic engine.
  4. Extract flight details, invoice amounts, tracking numbers.
  5. Save as VaultItem with `sourceType = GMAIL_SYNC`.
  6. Delete email text from local cache immediately after parsing.
- **Fallback:** If Gmail API is blocked or user declines OAuth, offer **manual email forwarding** (user forwards email to a local app intent via Share).

### 9.3 Cross-Device Sync (P2P — Deferred to v2.0)
- **Method:** Nearby Connections API (WiFi Direct + Bluetooth).
- **Pairing:** QR code on Device A scanned by Device B. Encrypted key exchange via ECDH.
- **Sync:** Delta sync of encrypted vault items. No server involved.

---

## 10. MONETIZATION & ENTITLEMENT ENGINE

### 10.1 Tier Structure
| Tier | Price | Access |
|------|-------|--------|
| **Free** | $0 | 4 Core Lenses (Money, Health, Travel, Bureaucracy), 50 items/month capture, local backup only, basic alerts |
| **Pro** | $4.99/mo or $39.99/yr | All Extended Lenses (Home, Car, Services, Media, Scam Guard, Productivity), unlimited capture, Drive backup, Gmail sync, advanced search, custom fields |
| **Ultra** | $9.99/mo or $79.99/yr | Everything + Semantic Search, P2P sync, AI cross-linking, priority support, early access to new lenses |

### 10.2 Implementation
- **BillingClient:** Google Play Billing Library v7.
- **FeatureGate:** Centralized check before routing to any lens or premium feature.
```kotlin
fun canAccessLens(lensId: String): Boolean {
    return when (getSubscriptionTier()) {
        Tier.FREE -> lensId in FREE_LENSES
        Tier.PRO -> lensId in FREE_LENSES + PRO_LENSES
        Tier.ULTRA -> true
    }
}
```
- **Paywall UI:** Bottom sheet or fullscreen dialog when user taps a locked lens. Shows feature preview + pricing.
- **Graceful Degradation:** If subscription expires, existing data remains accessible (read-only). New captures to locked lenses are blocked.

---

## 11. LOCALIZATION & ACCESSIBILITY

### 11.1 Internationalization (i18n)
- **Primary:** Arabic (ar), English (en)
- **Phase 2:** French (fr), German (de), Spanish (es), Turkish (tr)
- **Resources:** `strings.xml` for all UI text. No hardcoded strings.
- **RTL:** All layouts tested with `locale = ar`. Compose `LocalLayoutDirection` used for custom layouts.
- **Date/Time:** `java.time` with locale-aware formatting. Hijri calendar support for MENA users (optional toggle).
- **Currency:** Auto-detect from locale or manual override. Store amounts as `BigDecimal` with currency code.
- **Numbers:** Arabic-Indic digits (٠١٢٣٤٥٦٧٨٩) support in OCR and display.

### 11.2 Accessibility (a11y)
- **TalkBack:** Every button has `semantics { contentDescription = ... }`. Images have descriptive labels.
- **Font Scaling:** UI tested at 1.0x, 1.3x, 1.5x, 2.0x. No truncation or overlap.
- **Colorblind:** Status indicators use icons + text, never color alone. Red/green replaced with checkmark/warning triangle patterns.
- **Motion:** Respect `prefersReducedMotion` system setting. Disable non-essential animations.
- **Switch Access:** All flows navigable via switch control.

---

## 12. TESTING & QUALITY ASSURANCE

### 12.1 Testing Pyramid
| Layer | Tool | Coverage Target |
|-------|------|-----------------|
| **Unit Tests** | JUnit 5 + MockK | Repositories, UseCases, Heuristic Engine, Encryption Utils (80%+) |
| **Integration Tests** | Hilt testing + Turbine | DAO operations, Backup/Restore flow, Billing flow (60%+) |
| **UI Tests** | Compose UI Test + Espresso | Critical paths: Capture → Review → Save → Alert (30%+) |
| **Screenshot Tests** | Paparazzi | All major screens in light/dark/RTL modes |
| **Performance** | Macrobenchmark | App startup < 1.5s (cold), list scroll jank < 10% |
| **Security** | OWASP MASVS checklist | Encryption, auth, storage, network |

### 12.2 CI/CD Pipeline (GitHub Actions)
- **Pull Request:** Lint (ktlint), unit tests, screenshot tests.
- **Nightly:** Full UI test suite on Firebase Test Lab (Pixel + Samsung + Xiaomi devices).
- **Release:** Automated version bump, signed AAB build, Play Console upload to Internal Testing.

### 12.3 Beta Strategy
- **Internal Testing:** Team only (10 users).
- **Closed Testing:** 100 trusted users per region (Egypt, KSA, UAE, Germany).
- **Open Testing:** Public beta with Pro features free for feedback.
- **Staged Rollout:** 5% → 20% → 50% → 100% production release.

---

## 13. PHASED IMPLEMENTATION PLAN (Revised: 20 Weeks)

### Phase 1: Foundation & Security (Weeks 1-3)
- [ ] Modular Gradle setup, Hilt DI, Compose Navigation
- [ ] Room + SQLCipher + Keystore integration
- [ ] BiometricPrompt + PIN fallback + Auto-lock
- [ ] Screenshot prevention (`FLAG_SECURE`)
- [ ] Notification channels + UnifiedAlertManager
- [ ] CI/CD (GitHub Actions), Firebase Crashlytics + Analytics
- [ ] Material You theming, RTL-first layouts, accessibility baseline
- [ ] Feature flag system + BillingClient skeleton
- [ ] **Deliverable:** Secure empty app with auth gate and settings shell.

### Phase 2: Core Vault & Quick Capture (Weeks 4-7)
- [ ] `VaultItemEntity` + `VaultDao` + `VaultRepository` + FTS4
- [ ] CameraX capture + ML Kit OCR + bounding box overlay
- [ ] Share Intent receiver (`ACTION_SEND`)
- [ ] Heuristic engine v1 (receipt, prescription, ID, flight detection)
- [ ] TFLite model integration (document classifier, 12 classes)
- [ ] Review screen (confidence < 0.70 trigger)
- [ ] Save flow + FTS indexing + alert scheduling
- [ ] HomeScreen: Morning Briefing Hero Card, Recent Captures, Search bar
- [ ] **Deliverable:** Users can capture documents, see them in vault, get basic alerts.

### Phase 3: Core Lenses MVP (Weeks 8-11)
- [ ] **Money Lens:** Receipt form, subscription tracker, trial alerts, IBAN validator, expense PDF export
- [ ] **Health Lens:** Prescription form, dose reminder scheduler (AlarmManager), Net Carbs calculator, workout template
- [ ] **Travel Lens:** Flight detail form, PNR storage, check-in reminder, bill splitter UI
- [ ] **Bureaucracy Lens:** ID/passport form with MRZ parsing, expiry radar (90/30/7 day alerts), application checklist template
- [ ] **Vault Browser:** Bulk select, sort, filter, archive, pin
- [ ] **Item Detail:** Editable forms per lens, image viewer, timeline
- [ ] **Deliverable:** 4 core lenses functional. App is usable as a daily tool.

### Phase 4: AI Polish & Automation (Weeks 12-14)
- [ ] Duplicate detection (pHash + MinHash)
- [ ] Cross-lens contextual suggestions
- [ ] Smart tagging (auto + manual)
- [ ] Custom fields per item (user-defined key-values)
- [ ] Advanced search (FTS4 + filters + date range)
- [ ] Morning Briefing v2 (AI-generated summary combining all lenses)
- [ ] Emergency Access Card + lock screen widget
- [ ] **Deliverable:** App feels intelligent. Search works. Briefing is useful.

### Phase 5: Cloud & Extended Lenses (Weeks 15-17)
- [ ] Google Drive encrypted backup (WorkManager + CSE)
- [ ] Gmail integration (OAuth + local parsing + fallback to manual share)
- [ ] **Pro Lens Unlock:** Home, Car, Services, Media, Scam Guard, Productivity
- [ ] Paywall UI + FeatureGate enforcement
- [ ] In-app review API integration
- [ ] **Deliverable:** Freemium model live. Cloud features available.

### Phase 6: Polish, QA & Launch (Weeks 18-20)
- [ ] Full QA cycle: Unit tests, UI tests, screenshot tests, security audit
- [ ] Performance optimization: APK size < 40MB, cold start < 1.5s
- [ ] Beta testing: Closed → Open → Staged rollout
- [ ] Play Store assets: Screenshots (phone + tablet), feature graphic, privacy policy, data safety form
- [ ] Localization: Arabic final review, English polish
- [ ] Marketing site + help center
- [ ] **Deliverable:** v1.0 live on Google Play.

### Phase 7: Post-Launch Roadmap (v1.1 - v3.0)
- **v1.1 (Month 2):** Widgets, Quick Settings tile, app shortcuts, bulk operations
- **v1.2 (Month 3):** Geofenced reminders, Wear OS companion, print framework
- **v1.5 (Month 5):** P2P cross-device sync, import from competitors, statistics dashboard
- **v2.0 (Month 8):** Semantic search (on-device embeddings), predictive automation v2, MCP bridge
- **v3.0 (Month 12):** Hub SDK for third-party lenses, community lens marketplace

---

## 14. RISK MITIGATION & FALLBACKS

| Risk | Likelihood | Mitigation |
|------|-----------|------------|
| **Gmail API restricted scope rejection** | High | Build manual email forwarding fallback from Day 1. Do not block launch on Gmail approval. |
| **TFLite model accuracy < 70%** | Medium | Heuristic engine is primary classifier. TFLite is enhancement. Model can be updated OTA via in-app download. |
| **SQLCipher performance on low-end devices** | Medium | Benchmark on Xiaomi Redmi series. Offer "Light Security" mode (Keystore-only, no SQLCipher) for devices with < 3GB RAM. |
| **Play Store rejection (medical/financial data)** | Medium | Frame as "personal organizer," not "medical device." No diagnostic claims. Clear privacy policy. |
| **User overwhelm (too many lenses)** | Medium | Dynamic lens hiding. Onboarding survey to activate only relevant lenses. Progressive disclosure. |
| **OCR fails on Arabic script** | Medium | ML Kit Arabic OCR is mature, but test extensively on handwritten prescriptions. Fallback to manual entry with optimized Arabic keyboard. |
| **Biometric hardware failure** | Low | PIN fallback is mandatory. Decoy vault is optional but recommended. |
| **Battery drain from background sync** | Medium | Strict WorkManager constraints (charging + WiFi + idle). User-visible sync schedule. No real-time polling. |

---

## 15. APPENDIX: QUICK REFERENCE FOR AI CODING ASSISTANT

### 15.1 First File to Generate
`core/database/src/main/java/com/vaultbrain/core/database/entity/VaultItemEntity.kt`

### 15.2 Key Dependencies (build.gradle.kts)
```kotlin
// Compose BOM
implementation(platform("androidx.compose:compose-bom:2026.02.00"))
implementation("androidx.compose.ui:ui")
implementation("androidx.compose.material3:material3")
implementation("androidx.compose.material:material-icons-extended")

// Navigation
implementation("androidx.navigation:navigation-compose:2.8.0")

// Hilt
implementation("com.google.dagger:hilt-android:2.52")
kapt("com.google.dagger:hilt-compiler:2.52")
implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

// Room
implementation("androidx.room:room-runtime:2.7.0")
kapt("androidx.room:room-compiler:2.7.0")
implementation("androidx.room:room-ktx:2.7.0")

// SQLCipher
implementation("net.zetetic:android-database-sqlcipher:4.6.1")

// ML Kit
implementation("com.google.mlkit:text-recognition:16.0.1")
implementation("com.google.mlkit:text-recognition-arabic:16.0.1")
implementation("com.google.android.gms:play-services-mlkit-barcode-scanning:18.3.1")

// CameraX
implementation("androidx.camera:camera-core:1.4.0")
implementation("androidx.camera:camera-camera2:1.4.0")
implementation("androidx.camera:camera-lifecycle:1.4.0")
implementation("androidx.camera:camera-view:1.4.0")

// WorkManager
implementation("androidx.work:work-runtime-ktx:2.10.0")

// Billing
implementation("com.android.billingclient:billing-ktx:7.0.0")

// Serialization
implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

// TFLite
implementation("org.tensorflow:tensorflow-lite:2.16.1")
implementation("org.tensorflow:tensorflow-lite-support:0.4.4")

// Coroutines + Flow
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")

// Google Play Services (Drive, Sign-In)
implementation("com.google.android.gms:play-services-auth:21.2.0")
implementation("com.google.http-client:google-http-client-gson:1.45.0")
```

### 15.3 Manifest Essentials
```xml
<application
    android:localeConfig="@xml/locales_config"
    android:enableOnBackInvokedCallback="true">

    <!-- Share Intent Receiver -->
    <activity android:name=".feature.capture.ShareActivity"
        android:exported="true"
        android:theme="@android:style/Theme.Translucent.NoTitleBar">
        <intent-filter>
            <action android:name="android.intent.action.SEND"/>
            <category android:name="android.intent.category.DEFAULT"/>
            <data android:mimeType="image/*"/>
            <data android:mimeType="text/plain"/>
        </intent-filter>
        <intent-filter>
            <action android:name="android.intent.action.SEND_MULTIPLE"/>
            <category android:name="android.intent.category.DEFAULT"/>
            <data android:mimeType="image/*"/>
        </intent-filter>
    </activity>

    <!-- Deep Links -->
    <activity android:name=".MainActivity">
        <intent-filter android:autoVerify="true">
            <action android:name="android.intent.action.VIEW"/>
            <category android:name="android.intent.category.DEFAULT"/>
            <category android:name="android.intent.category.BROWSABLE"/>
            <data android:scheme="vaultbrain" android:host="item"/>
            <data android:scheme="vaultbrain" android:host="lens"/>
        </intent-filter>
    </activity>
</application>
```

### 15.4 Success Metrics (Post-Launch)
- **Activation:** 70% of installers complete first capture within 24h.
- **Retention:** D7 > 40%, D30 > 25%.
- **Engagement:** 3+ captures per week per active user.
- **Monetization:** 5% free-to-pro conversion within 30 days.
- **Quality:** Crash-free rate > 99.5%, ANR rate < 0.2%.

---

**End of Specification.**

**Instruction to AI Coding Assistant:**  
Begin implementation with **Phase 1, Step 1**: Define the modular Gradle structure, then generate `VaultItemEntity.kt` with the polymorphic JSON schema, followed by the SQLCipher-encrypted Room database boilerplate. Proceed sequentially through the phases, generating production-ready Kotlin code with comprehensive comments, error handling, and unit tests for each module.
