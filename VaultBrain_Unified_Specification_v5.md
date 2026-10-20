# VaultBrain (PocketSecretary) — Complete Technical Specification
## Final Edition v5.0 | Unified Build Prompt
**Version:** 5.0-Final-Unified  
**Architecture:** Vault-First + Hybrid Native AI + RAG Brain + Visual Memory  
**Platform:** Android 10+ (API 29+) | Android 14+ recommended for AI features  
**Language:** Kotlin (100% Native)  
**UI:** Jetpack Compose + Material 3 + Material You Dynamic Color  
**Database:** Room + SQLCipher (AES-256) + ObjectBox Vector Store  
**AI Stack:** ML Kit + AICore (Gemini Nano) + Qualcomm QNN + TFLite/ONNX Fallback  
**Offline-First:** Yes. All AI runs locally. Cloud is opt-in, user-controlled, encrypted client-side.  
**Target Markets:** MENA (RTL-first), Global (LTR fallback)  
**Monetization:** Freemium (Core vault free, RAG Brain + Extended Lenses = Pro)

---

## 1. EXECUTIVE SUMMARY

VaultBrain is your **Autonomous On-Device Chief of Staff** and **Visual Memory Extension**.

**The One Promise:**  
> *See it, snap it, screenshot it, share it — dump anything into VaultBrain. Its on-device RAG brain reads, understands, remembers, and connects everything. Then it tells you what matters before you forget.*

**What You Can Dump:**
- WhatsApp / Telegram / Messenger screenshots
- Gallery photos of products in stores
- Camera snaps of menus, serial plates, parking meters
- Screenshots of course ads, flight confirmations, receipts
- Documents, prescriptions, IDs, business cards
- Any image or text from any app

**What VaultBrain Does:**
1. **Reads** — OCR + visual understanding extracts text and meaning
2. **Understands** — On-device AI classifies, embeds, and indexes
3. **Remembers** — Encrypted vault + vector store for semantic search
4. **Connects** — Links related items (flight + hotel + car rental = "Dubai Trip")
5. **Acts** — Alerts before expiry, suggests next steps, answers questions

**Zero data leaves your device. Ever.**

---

## 2. THE "DUMP → EXTRACT → KNOWLEDGE" ARCHITECTURE

### 2.1 Core Philosophy
There are no "hubs." There is only **The Vault**. Items are not stored in silos — they are viewed through **dynamic Lenses** that filter the unified dataset.

A single receipt is simultaneously:
- A **Money** item (expense tracking)
- A **Purchase** item (warranty tracker)
- A **Tax** item (deductible record)
- Part of a **Trip** (if bought before travel)

### 2.2 The Unified Data Model
```
USER DUMP (Any Source)
    ├── Screenshot (WhatsApp, Telegram, Instagram, System)
    ├── Gallery Photo (Product in store, menu, document)
    ├── Camera Capture (Receipt, prescription, serial plate)
    ├── Share Intent (Link, text, image from any app)
    └── Manual Entry (Typed note, voice-to-text)
            ↓
    ┌─────────────────────────────────────────────┐
    │         INGESTION & PREPROCESSING           │
    │  • Image: deskew, denoise, enhance contrast │
    │  • Text: normalize Arabic, fix RTL          │
    │  • Pre-filter: reject memes, low-text photos│
    └─────────────────────────────────────────────┘
            ↓
    ┌─────────────────────────────────────────────┐
    │         EMBEDDING LAYER (All Devices)       │
    │  • Text Embedding: all-MiniLM (25MB)        │
    │  • Vision Embedding: MobileCLIP (150MB)     │
    │  • Output: 384-dim / 512-dim vectors        │
    └─────────────────────────────────────────────┘
            ↓
    ┌─────────────────────────────────────────────┐
    │         CLASSIFICATION & EXTRACTION         │
    │  • TFLite Document/Product Classifier       │
    │  • Heuristic Fast-Path (Regex)              │
    │  • Native LLM (AICore/QNN) for complex text │
    │  • Confidence scoring + threshold           │
    └─────────────────────────────────────────────┘
            ↓
    ┌─────────────────────────────────────────────┐
    │         DUAL STORAGE                        │
    │  Room (SQLCipher): Structured exact data    │
    │  ObjectBox: Vector fingerprints for search  │
    │  FTS4: Fast full-text search                │
    └─────────────────────────────────────────────┘
            ↓
    ┌─────────────────────────────────────────────┐
    │         INTELLIGENCE LAYER                  │
    │  • RAG Retrieval: Semantic + metadata       │
    │  • Native LLM: Summarize, Q&A, briefings    │
    │  • Cross-linking: Connect related dumps     │
    │  • Alert Engine: Schedule notifications     │
    └─────────────────────────────────────────────┘
            ↓
    KNOWLEDGE OUTPUT
    ├── Alert: "Passport expires in 23 days"
    ├── List: "Your Want to Buy items"
    ├── Calculation: "Spent EGP 4,850 this month"
    ├── Reminder: "Dose at 8PM with food"
    ├── Connection: "Link to Dubai Trip?"
    └── Answer: "Dr. Ahmed prescribed Amoxicillin 500mg 3x daily"
```

---

## 3. MODULAR GRADLE STRUCTURE

```
:root
├── :app                          (Shell + Navigation + Deep Links)
├── :core
│   ├── :database                 (Room + SQLCipher + migrations + FTS4)
│   ├── :vectorstore              (ObjectBox with HNSW vector index)
│   ├── :security                 (Keystore, Biometric, encryption, audit)
│   ├── :ai
│   │   ├── :embeddings           (Text + Vision encoders)
│   │   ├── :rag                  (Retrieval + Context builder + rerank)
│   │   ├── :llm                  (AICore + QNN + CPU fallback)
│   │   ├── :heuristics           (Regex rules + fast-path classifiers)
│   │   └── :vision               (Object detection, color analysis)
│   ├── :notifications            (WorkManager + AlarmManager dispatcher)
│   ├── :common                   (Models, utils, coroutines, constants)
│   └── :billing                  (Google Play BillingClient + FeatureGate)
├── :feature
│   ├── :vault                    (Universal browser, search, item detail)
│   ├── :capture                  (Camera, gallery, share-intent receiver)
│   ├── :brain                    (Natural language chat UI)
│   ├── :briefing                 (Morning digest Hero Card)
│   ├── :lens-money               (Receipts, subscriptions, budgets)
│   ├── :lens-health              (Prescriptions, labs, diet, workout)
│   ├── :lens-travel              (Flights, hotels, PNR, itineraries)
│   ├── :lens-bureaucracy         (IDs, passports, residency, applications)
│   ├── :lens-home                (Appliances, inventory, utilities)
│   ├── :lens-car                 (Maintenance, fuel, parking)
│   ├── :lens-shopping            (Want to Buy, Gift Bank, price tracking)
│   ├── :lens-media               (Movies, books, courses, watchlist)
│   ├── :lens-productivity        (Tasks, business cards, error codes)
│   └── :settings                 (Security, backup, export, appearance)
└── :sync
    ├── :drive                    (Encrypted backup to Google Drive)
    └── :gmail                    (Local-only Gmail API parser)
```

---

## 4. DATABASE SCHEMA

### 4.1 Core Entity (Room + SQLCipher)
```kotlin
@Entity(tableName = "vault_items")
data class VaultItemEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),

    // Core metadata
    val title: String,
    val summary: String?,
    val rawOcrText: String?,
    val sourceType: SourceType,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val capturedImageUri: String?,

    // Polymorphic payload (JSON)
    val parsedMetadata: String,

    // Lens tags (dynamic, many-to-many simulated)
    val lensTags: String, // JSON array: ["MONEY", "HEALTH", "SHOPPING"]

    // Temporal intelligence
    val expiryDate: Long?,
    val secondaryAlertDate: Long?,
    val recurringRule: String?, // RRule JSON

    // Location (optional)
    val captureLatitude: Double?,
    val captureLongitude: Double?,
    val locationName: String?, // "Carrefour, City Stars"

    // AI & confidence
    val aiConfidence: Float,
    val aiClassification: String?, // RECEIPT, PRESCRIPTION, PRODUCT_PHOTO, etc.
    val needsReview: Boolean = false,

    // Visual analysis
    val dominantColors: String?, // JSON array of hex colors
    val detectedObjects: String?, // JSON array: ["shoe", "logo", "text"]

    // User overrides
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val isStealth: Boolean = false, // Gift Bank, hidden items
    val userNotes: String?,
    val customFields: String?, // JSON map

    // Shopping-specific
    val targetPrice: Double?, // For price drop alerts
    val affiliateUrl: String?, // Only if user opts in

    // Sync & backup
    val driveBackupId: String?,
    val lastSyncAt: Long?,

    // Entitlement
    val requiredLensTier: Tier = Tier.FREE
)
```

### 4.2 Supporting Tables
```kotlin
// Full-Text Search
@Entity(fts4 = true)
data class VaultItemFts(
    val title: String,
    val rawOcrText: String,
    val summary: String
)

// Vector Embeddings (ObjectBox)
@Entity
class VaultEmbedding {
    @Id var id: Long = 0
    var itemId: String = ""
    var embedding: FloatArray? = null // 384-dim or 512-dim
    var contentType: String = "" // "ocr_text", "image", "summary", "visual"
    var chunkIndex: Int = 0
    var createdAt: Long = 0
}

// Audit Log
@Entity(tableName = "audit_logs")
data class AuditLogEntity(
    @PrimaryKey val id: Long = 0,
    val timestamp: Long,
    val action: String, // VIEW, EDIT, DELETE, EXPORT, AUTH_FAIL, CAPTURE
    val itemId: String?,
    val details: String?
)

// Notification Queue
@Entity(tableName = "notification_queue")
data class NotificationQueueEntity(
    @PrimaryKey val id: String,
    val itemId: String,
    val triggerAt: Long,
    val channelId: String, // CRITICAL, REMINDER, BACKGROUND, SHOPPING
    val title: String,
    val body: String,
    val isDelivered: Boolean = false
)
```

---

## 5. THE CAPTURE PIPELINE (All Sources)

### 5.1 Supported Input Sources
| Source | Method | Privacy |
|--------|--------|---------|
| **Camera** | CameraX in-app | Direct to vault |
| **Gallery Share** | `ACTION_SEND` / `ACTION_SEND_MULTIPLE` | User-initiated |
| **Screenshot Share** | `ACTION_SEND` from Gallery | User-initiated |
| **WhatsApp → Share** | `ACTION_SEND` from WhatsApp | User-initiated |
| **Any App → Share** | `ACTION_SEND` universal | User-initiated |
| **Text Paste** | Manual paste into app | Direct to vault |
| **Voice Note** | On-device STT (no audio stored) | Direct to vault |

### 5.2 The Full Processing Pipeline
```
User dumps image/text
    ↓
[1] SOURCE DETECTION
    ├── Camera/Gallery → Full resolution image
    ├── Share Intent → URI or text payload
    └── Text/Voice → Direct text input
    ↓
[2] PRE-FILTER (Reject obvious junk)
    ├── Image too small (< 100x100)? → Reject
    ├── Image is pure color/gradient? → Reject
    ├── Text is only emojis? → Reject
    ├── Aspect ratio extreme (< 0.2 or > 5)? → Flag for review
    └── Pass → Continue
    ↓
[3] PARALLEL PROCESSING
    ├── Branch A: OCR (ML Kit Text Recognition v2)
    │   ├── Latin script recognition
    │   ├── Arabic script recognition
    │   └── Bounding boxes + confidence per word
    │
    ├── Branch B: Visual Analysis
    │   ├── Vision Embedding (MobileCLIP) → 512-dim vector
    │   ├── Dominant color extraction
    │   └── Object detection (logo, product, scene type)
    │
    └── Branch C: Heuristic Fast-Path (Regex)
        ├── Date patterns (all locales)
        ├── Amount/currency patterns
        ├── IBAN/SWIFT patterns
        ├── Email/phone patterns
        └── URL detection
    ↓
[4] CLASSIFICATION
    ├── High text density (> 1%) → Document classifier
    │   ├── RECEIPT, PRESCRIPTION, PASSPORT, TICKET, INVOICE
    │   ├── WARRANTY_CARD, BUSINESS_CARD, GENERAL_DOCUMENT
    │   └── MEME/JUNK → Reject with explanation
    │
    ├── Low text density (< 0.5%) → Product/Scene classifier
    │   ├── PRODUCT_PHOTO → "What is this?" prompt
    │   ├── MENU_PHOTO → OCR food items + prices
    │   ├── SERIAL_PLATE → OCR model numbers
    │   └── SCENE_PHOTO → "Memory" or "Location" prompt
    │
    └── Unclear → Ambiguous dialog (user chooses)
    ↓
[5] EXTRACTION (Tier-dependent)
    ├── ULTRA/PRO (AICore available):
    │   └── ML Kit GenAI APIs for summarization, structured extraction
    │
    ├── PRO (Snapdragon NPU):
    │   └── QNN LLM for structured extraction
    │
    └── STANDARD/LITE:
        └── Heuristics + template matching only
    ↓
[6] CONFIDENCE SCORING
    ├── Confidence ≥ 0.85 → Auto-save, no review
    ├── Confidence 0.70-0.84 → Save, yellow "review" badge
    └── Confidence < 0.70 → Block save, show review screen
    ↓
[7] EMBEDDING & INDEXING
    ├── Text chunks → all-MiniLM → ObjectBox
    ├── Image → MobileCLIP → ObjectBox
    └── FTS4 index update
    ↓
[8] CROSS-LINKING & ALERTS
    ├── Retrieve similar past items (RAG)
    ├── Suggest connections: "Link to Dubai Trip?"
    ├── Detect duplicates (vector similarity > 0.95)
    └── Schedule alerts (expiry, refill, price drop)
    ↓
[9] USER CONFIRMATION (if needed)
    ├── Review screen: editable fields, original image
    ├── "What is this?" dialog for product photos
    └── "Save to which list?" for ambiguous items
    ↓
[10] FINAL SAVE
    ├── Room DB (encrypted)
    ├── ObjectBox (vectors)
    ├── Audit log entry
    └── Morning Briefing refresh trigger
```

---

## 6. AI & INTELLIGENCE SPECIFICATION

### 6.1 Device Tier Detection
```kotlin
enum class AiTier {
    ULTRA,   // AICore + Gemini Nano available
    PRO,     // Snapdragon 8 Gen 1+ with Hexagon NPU
    STANDARD,// 4-6GB RAM, CPU-only, no LLM
    LITE     // < 4GB RAM, heuristics only
}

class AiCapabilityManager @Inject constructor(context: Context) {
    fun detectTier(): AiTier {
        val hasAicore = checkAicoreAvailability()
        val hasHexagon = checkHexagonNpu()
        val ram = getAvailableRamMb()

        return when {
            hasAicore -> AiTier.ULTRA
            hasHexagon && ram >= 6144 -> AiTier.PRO
            ram >= 4096 -> AiTier.STANDARD
            else -> AiTier.LITE
        }
    }
}
```

### 6.2 LLM Strategy by Tier

| Tier | LLM Source | Model | Size | Use For |
|------|-----------|-------|------|---------|
| **ULTRA** | AICore (native) | Gemini Nano | 0 MB download | Summarization, Q&A, image description, proofreading |
| **PRO** | Qualcomm QNN | Qwen3-0.6B or Llama-3.2-1B | ~700-900 MB | Full chat, long briefings, structured extraction |
| **STANDARD** | None | Heuristics + templates | 0 MB | Rule-based briefing, no chat |
| **LITE** | None | Heuristics only | 0 MB | Basic capture and storage |

### 6.3 Embedding Models (All Tiers)
| Model | Size | Dimensions | Purpose |
|-------|------|-----------|---------|
| all-MiniLM-L6-v2 (INT8) | 25 MB | 384 | Text semantic search |
| MobileCLIP-S0 (INT8) | 150 MB | 512 | Visual semantic search |

**Both downloaded on-demand, not bundled. APK stays < 30MB.**

### 6.4 RAG Retrieval Pipeline
```kotlin
suspend fun ragQuery(query: String, filters: SearchFilters): RagResponse {
    // 1. Embed the query
    val queryVector = textEmbeddingModel.encode(query)

    // 2. Vector search in ObjectBox (top-50 candidates)
    val vectorCandidates = vectorBox.query()
        .nearest(VaultEmbedding_.embedding, queryVector, 50)
        .find()

    // 3. Metadata filtering in Room (exact filters)
    val filteredIds = vaultDao.filterByMetadata(
        itemIds = vectorCandidates.map { it.itemId },
        lensFilter = filters.lensTag,
        dateFrom = filters.dateFrom,
        dateTo = filters.dateTo,
        hasImage = filters.hasImage
    )

    // 4. Rerank by combined score (vector similarity + recency + pin status)
    val reranked = reranker.rerank(filteredIds, queryVector)

    // 5. Retrieve top-10 context chunks
    val contextChunks = reranked.take(10).map { retrieveChunk(it) }

    // 6. Generate response (if LLM available)
    val answer = when (detectTier()) {
        AiTier.ULTRA -> aicore.generate(promptTemplate.format(contextChunks, query))
        AiTier.PRO -> qnnLlm.generate(promptTemplate.format(contextChunks, query))
        else -> null // Return retrieved items as cards, no synthesis
    }

    return RagResponse(answer = answer, sources = reranked, confidence = calculateConfidence())
}
```

### 6.5 Prompt Safety (All Tiers)
```
SYSTEM PROMPT (hardcoded, never retrieved):
You are VaultBrain, a document organizer and life admin assistant.
You run entirely on the user's device. You have NO internet access.

RULES:
1. ONLY use the provided vault context to answer.
2. NEVER give medical advice, dosage recommendations, or diagnoses.
3. NEVER give financial or investment advice.
4. If the context doesn't contain the answer, say "I don't have that information."
5. Always cite your sources (vault item IDs).
6. Be concise. Use the user's language (Arabic or English).
7. For medical items, add disclaimer: "Consult your doctor for medical advice."
8. For financial items, add disclaimer: "Verify with official sources."
```

---

## 7. LENS SPECIFICATIONS

### 7.1 Core Lenses (Free Tier)

#### 💰 MONEY LENS
- Receipt OCR with merchant, total, tax, items extraction
- Subscription tracker with trial expiry alerts (48h before)
- Budget matcher: monthly spend by category
- IBAN/SWIFT validator and quick-save
- Expense PDF/CSV export

#### 🏥 HEALTH LENS
- Prescription OCR: medication, dosage, frequency, doctor
- Complex dose reminders (respecting food relations, intervals)
- Lab result history with trend sparklines
- Net Carbs / Keto calculator from nutrition labels
- Workout checklist templates and PR tracking
- Medical ID for emergency access

#### ✈️ TRAVEL LENS
- Flight/hotel booking OCR: PNR, gate, dates, confirmation
- Web check-in QR storage
- Bill splitter for group expenses
- Address/tracking grabber from shipping labels
- Itinerary linking (flight + hotel + car = trip)

#### 📄 BUREAUCRACY LENS
- ID/passport expiry radar (90/30/7 day alerts)
- MRZ code parsing
- Application checklist templates (visa, license, residency)
- Document vault sub-section with re-auth requirement

### 7.2 Extended Lenses (Pro Tier)

#### 🛒 SHOPPING LENS (NEW — Product Photos)
- **Want to Buy List:** Save product photos from stores. Add notes, target price, store location.
- **Gift Bank:** Stealth mode. Hidden from main vault. Occasion-tagged (Birthday, Anniversary). Alert before date.
- **Price Tracking:** User sets target price. Background check via web scraping (or affiliate API). Alert on price drop.
- **Visual Search:** *"Show me red shoes I photographed"* — pure vision retrieval.
- **Duplicate Prevention:** *"You already own similar Nikes. Buy anyway?"*
- **Affiliate Integration (Opt-In Only):**
  - User explicitly taps "Find Best Price" on a Want to Buy item
  - App searches Amazon/Udemy/etc. via affiliate API
  - Results shown with disclosure: *"VaultBrain earns a small commission at no extra cost to you"*
  - User can disable in Settings → Shopping → "Disable affiliate links"

#### 🏠 HOME LENS
- Appliance manual vault (OCR model numbers, store manual PDFs)
- Home inventory with purchase date, warranty link, photo
- Internet quota tracker with overage alerts
- Utility meter readings per rental unit

#### 🚗 CAR LENS
- Odometer and fuel log tracking
- Maintenance interval alerts (oil, tires, filters)
- Parking ticket guard with countdown timer

#### 🧺 SERVICES LENS
- Dry clean ticket OCR with pickup countdown
- Return window guard (14/30 day alerts)

#### 🎬 MEDIA LENS
- Movie/book extraction from chat screenshots
- IMDb/Goodreads deep link generation
- Watchlist / Readlist tracking
- Course queue (Udemy, Coursera links)

#### 🛡️ SCAM GUARD LENS
- URL heuristic scanner (phishing detection)
- Wi-Fi QR generator from router sticker OCR
- Smart link categorization

#### 📋 PRODUCTIVITY LENS
- Chat-to-task: *"Pick up dry cleaning Friday"* → task with inferred deadline
- Business card scanner
- Error code decoder database
- Recipe-to-grocery checklist

---

## 8. UI/UX ARCHITECTURE

### 8.1 Screen Hierarchy
```
MainActivity
├── Splash / Auth Gate (Biometric / PIN / Decoy Vault)
│   └── Emergency Access Button
│
├── HomeScreen
│   ├── Morning Briefing Hero Card (collapsible, LLM-generated or template)
│   ├── Quick Dump FAB (Camera / Gallery / Paste / Voice)
│   ├── Universal Search Bar (sticky, supports natural language)
│   ├── Recently Dumped (horizontal carousel, last 5 items)
│   └── Active Lenses Grid (dynamic, based on user data)
│       ├── 💰 Money (if money items exist)
│       ├── 🏥 Health (if health items exist)
│       ├── 🛒 Shopping (if product photos exist)
│       ├── ✈️ Travel (if travel items exist)
│       └── [+ Browse All Lenses]
│
├── VaultBrowserScreen
│   ├── Filter Chips: All | Pinned | Expiring Soon | Needs Review | Shopping
│   ├── Sort: Date | Expiry | Title | Confidence | Price
│   ├── Bulk Select Mode (long-press)
│   └── Item Cards (swipe to archive, quick actions)
│
├── LensDetailScreen
│   ├── Lens-specific stats ("EGP 4,850 spent", "3 items expiring")
│   ├── Filtered list of VaultItems
│   └── Lens-specific quick-add templates
│
├── CaptureFlowScreen
│   ├── Camera Preview (ML Kit real-time bounding boxes)
│   ├── Review Screen (editable fields, confidence badge, original image)
│   ├── Ambiguous Dialog (for product photos: "What is this?")
│   └── Save Confirmation + Cross-link suggestions
│
├── ItemDetailScreen
│   ├── Hero Image (original dump)
│   ├── Extracted Data (editable form from JSON schema)
│   ├── Visual Analysis (dominant colors, detected objects)
│   ├── Timeline (creation, edits, alerts, backups)
│   └── Actions: Share (encrypted PDF), Export, Duplicate, Delete
│
├── BrainChatScreen (Pro/Ultra only)
│   ├── Chat history (user + assistant bubbles)
│   ├── Suggested queries ("What did I spend last month?")
│   ├── Citation cards (tap to view source item)
│   └── Input: text or voice
│
├── SearchScreen
│   ├── Natural language query input
│   ├── Visual search: "Search by photo" (take/choose photo → find similar)
│   ├── Filter by Lens, Date, Location, Has Image, Price Range
│   └── Recent Searches + Trending Tags
│
└── SettingsScreen
    ├── Security (Biometric, PIN, Auto-lock, Audit Log, Decoy Vault)
    ├── Backup & Sync (Drive, Gmail, manual export)
    ├── Lenses Management (enable/disable, reorder, unlock Pro)
    ├── Shopping (Affiliate toggle, price tracking settings)
    ├── Notifications (per-channel, DND override)
    ├── Data (Export PDF/CSV/JSON, Import, Demo Mode)
    ├── Appearance (Theme, Dynamic Color, Language, Font Size)
    └── About / Help / Privacy Policy
```

### 8.2 Widgets & System Integration
- **Morning Briefing Widget (Glance):** 3x2 resizable. Top 3 urgencies.
- **Quick Capture Tile:** Quick Settings tile for instant camera.
- **App Shortcuts:** Long-press icon → "Scan Receipt", "Add Product", "View Expiring".
- **Lock Screen Widget (Android 14+):** Emergency Access Card.

---

## 9. SECURITY & PRIVACY

### 9.1 Encryption
- **Database:** SQLCipher AES-256-CBC, master key in Android Keystore
- **Biometric:** Required for vault unlock. PIN fallback (6-digit or alphanumeric)
- **Decoy Vault:** Secondary passphrase opens dummy vault with fake items
- **Screenshot Prevention:** `FLAG_SECURE` on all sensitive activities
- **Auto-Lock:** 30s / 2min / 5min / Never (configurable)

### 9.2 Messaging Screenshot Privacy
- **Immediate Encryption:** Processed in memory, written to SQLCipher within 2 seconds
- **No Cloud Option:** Toggle to exclude screenshots from Drive backup
- **Auto-Delete Original:** Prompt to delete screenshot from Gallery after extraction
- **No Thumbnail in Recents:** `FLAG_SECURE` on review screen

### 9.3 Affiliate Privacy
- **Opt-In Only:** Affiliate links disabled by default
- **Explicit Per-Item:** User must tap "Find Best Price" — never auto-injected
- **Full Disclosure:** Clear text below every affiliate result
- **No Tracking:** No user behavior tracking for affiliate optimization

### 9.4 Audit Trail
Local-only, encrypted log of all access events. Viewable in Settings > Security.

---

## 10. MONETIZATION

### 10.1 Tier Structure
| Tier | Price | Access |
|------|-------|--------|
| **Free** | $0 | Core vault, 4 lenses (Money, Health, Travel, Bureaucracy), 50 items/month, keyword search, heuristics-only briefing |
| **Pro** | $4.99/mo or $39.99/yr | All Extended Lenses (Shopping, Home, Car, Services, Media, Scam Guard, Productivity), unlimited capture, semantic search, Brain Chat (if device supports), Drive backup, price tracking |
| **Ultra** | $9.99/mo or $79.99/yr | Everything + AICore priority, Gemini Nano features, P2P sync, early access |

### 10.2 FeatureGate
```kotlin
fun canAccessLens(lensId: String): Boolean = when(getTier()) {
    Tier.FREE -> lensId in FREE_LENSES
    Tier.PRO -> lensId in FREE_LENSES + PRO_LENSES
    Tier.ULTRA -> true
}

fun canUseBrainChat(): Boolean = 
    getTier() >= Tier.PRO && detectTier() in listOf(AiTier.ULTRA, AiTier.PRO)
```

---

## 11. PHASED IMPLEMENTATION (24 Weeks)

### Phase 1: Foundation & Security (Weeks 1-3)
- Modular Gradle, Hilt, Compose Navigation, deep links
- Room + SQLCipher + Keystore + Biometric + PIN + Decoy Vault
- Notification channels + UnifiedAlertManager
- Material You + RTL-first + Accessibility baseline
- Feature flags + Billing skeleton + CI/CD
- **Deliverable:** Secure app shell with auth gate

### Phase 2: Core Vault & Capture (Weeks 4-7)
- VaultItemEntity + polymorphic JSON + FTS4 + Audit Log
- CameraX + ML Kit OCR (Latin + Arabic) + bounding box overlay
- Share Intent receiver (`ACTION_SEND` universal)
- Gallery multi-select + batch processing
- Heuristic extraction engine v1 (dates, amounts, IBANs)
- TFLite document classifier (12 classes: receipt, prescription, passport, ticket, hotel, invoice, warranty, business card, product photo, menu, general doc, meme/junk)
- Review screen + confidence threshold UX
- HomeScreen + Morning Briefing v1 (template-based)
- **Deliverable:** Users can capture and organize documents and product photos

### Phase 3: RAG Brain — Embeddings & Search (Weeks 8-11)
- ObjectBox vector store integration + HNSW index
- all-MiniLM text embedding (25MB) TFLite conversion
- MobileCLIP vision embedding (150MB) TFLite conversion
- Embedding pipeline: text chunks + image → vectors
- Hybrid search: vector similarity + Room metadata filters
- Visual search: "Search by photo" (upload photo → find similar items)
- Semantic search UI (natural language search bar)
- **Deliverable:** Users can search by meaning and by visual similarity

### Phase 4: Native AI & LLM Integration (Weeks 12-15)
- AICore detection + ML Kit GenAI API integration (ULTRA tier)
- Qualcomm QNN detection + Qwen3-0.6B integration (PRO tier)
- CPU fallback strategy (STANDARD/LITE tiers)
- Brain Chat UI (natural language interface)
- Morning Briefing v2 (LLM-generated for ULTRA/PRO)
- Prompt engineering + safety guardrails + citation system
- Streaming LLM output for perceived speed
- **Deliverable:** Users can ask questions and get synthesized answers

### Phase 5: Extended Lenses (Weeks 16-19)
- **Shopping Lens:** Want to Buy list, Gift Bank (stealth mode), visual search, price tracking
- **Home Lens:** Appliance manual vault, inventory, utility readings
- **Car Lens:** Odometer, fuel log, maintenance alerts, parking guard
- **Services Lens:** Dry clean tracker, return window guard
- **Media Lens:** Movie/book extraction, watchlist, course queue
- **Scam Guard Lens:** URL scanner, Wi-Fi QR generator
- **Productivity Lens:** Chat-to-task, business card scanner, recipe-to-grocery
- **Deliverable:** All Pro lenses functional

### Phase 6: Cloud, Polish & Launch (Weeks 20-24)
- Google Drive encrypted backup (WorkManager + CSE)
- Gmail integration (OAuth + local parsing + manual fallback)
- Paywall UI + FeatureGate enforcement
- In-app review API
- Full QA: Unit tests, UI tests, screenshot tests, security audit
- Performance: APK < 30MB, cold start < 2s
- Beta testing: Closed → Open → Staged rollout
- Play Store assets, privacy policy, data safety form
- **Deliverable:** v1.0 live on Google Play

### Phase 7: Post-Launch (v1.1+)
- v1.1: Screenshot auto-suggest (opt-in), widgets, Quick Settings tile
- v1.2: Wear OS companion, geofenced reminders, print framework
- v1.5: P2P sync, import from competitors, statistics dashboard
- v2.0: Semantic search v2, predictive automation, MCP bridge

---

## 12. RISK MITIGATION

| Risk | Likelihood | Mitigation |
|------|-----------|------------|
| **AICore unavailable on user's device** | High (85% of devices) | Graceful fallback to QNN or heuristics. Clear messaging: "Upgrade to Pro for AI chat." |
| **TFLite model misclassifies product photos** | Medium | User correction feedback loop. Manual tagging for ambiguous items. |
| **Affiliate integration triggers Play Store rejection** | Medium | Full disclosure on every link. Opt-in only. Separate Shopping lens. |
| **Arabic OCR fails on handwritten prescriptions** | High | Always require user review for medical items. Add "This is handwritten" flag. |
| **NPU thermal throttling degrades UX** | Medium | Batch operations during charging. Show "Cooling down" states. Unload model when app backgrounds. |
| **LLM hallucinates medical/financial facts** | High | System prompt blocks advice. Always cite sources. User must verify critical facts. |
| **Vector store corruption** | Low | Regular integrity checks. Backup alongside Room DB. Rebuild index if corrupted. |
| **Gmail API scope rejection** | High | Build manual email forwarding fallback from Day 1. |
| **User overwhelm from too many lenses** | Medium | Dynamic lens hiding. Onboarding survey activates relevant lenses only. Progressive disclosure. |

---

## 13. KEY DEPENDENCIES

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

// ObjectBox (Vector Store)
implementation("io.objectbox:objectbox-android:4.0.0")
kapt("io.objectbox:objectbox-processor:4.0.0")

// ML Kit
implementation("com.google.mlkit:text-recognition:16.0.1")
implementation("com.google.mlkit:text-recognition-arabic:16.0.1")
implementation("com.google.android.gms:play-services-mlkit-barcode-scanning:18.3.1")
implementation("com.google.mlkit:vision-common:17.3.0")

// ML Kit GenAI (AICore)
implementation("com.google.mlkit:genai-common:19.0.0")
implementation("com.google.mlkit:text-generation:1.0.0")

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
implementation("org.tensorflow:tensorflow-lite-gpu:2.16.1")
implementation("org.tensorflow:tensorflow-lite-support:0.4.4")

// ONNX Runtime (for QNN/CPU LLM)
implementation("com.microsoft.onnxruntime:onnxruntime-android:1.19.0")

// Google Play Services (Drive, Sign-In)
implementation("com.google.android.gms:play-services-auth:21.2.0")

// Coroutines
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

// Glance (Widgets)
implementation("androidx.glance:glance-appwidget:1.1.0")
implementation("androidx.glance:glance-material3:1.1.0")
```

---

## 14. MANIFEST ESSENTIALS

```xml
<application
    android:localeConfig="@xml/locales_config"
    android:enableOnBackInvokedCallback="true">

    <!-- Universal Share Intent Receiver -->
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
            <data android:scheme="vaultbrain" android:host="brain"/>
        </intent-filter>
    </activity>

    <!-- Quick Settings Tile -->
    <service android:name=".core.notifications.QuickCaptureTileService"
        android:permission="android.permission.BIND_QUICK_SETTINGS_TILE"
        android:exported="true">
        <intent-filter>
            <action android:name="android.service.quicksettings.action.QS_TILE"/>
        </intent-filter>
    </service>
</application>
```

---

## 15. SUCCESS METRICS

| Metric | Target | Measurement |
|--------|--------|-------------|
| **Activation** | 70% complete first capture within 24h | Firebase Analytics |
| **Retention** | D7 > 40%, D30 > 25% | Firebase Analytics |
| **Engagement** | 3+ dumps per week per active user | Custom events |
| **Monetization** | 5% free-to-pro conversion | Play Console |
| **OCR Accuracy** | >95% printed, >90% screenshots | Manual evaluation |
| **Classification Accuracy** | >88% overall | Confusion matrix |
| **RAG Retrieval** | >85% top-3 relevance | Manual evaluation |
| **LLM Hallucination** | <5% on factual queries | User feedback + audit |
| **Crash-Free Rate** | >99.5% | Crashlytics |
| **APK Size** | <30MB | Build output |
| **Cold Start** | <2s (all tiers) | Macrobenchmark |

---

**End of Unified Specification v5.0**

**Instruction to AI Coding Assistant:**  
Begin implementation with **Phase 1, Step 1**: Define the modular Gradle structure (`:core`, `:feature`, `:sync`), then generate `VaultItemEntity.kt` with the polymorphic JSON schema, followed by the SQLCipher-encrypted Room database boilerplate and ObjectBox vector store setup. Proceed sequentially through the phases, generating production-ready Kotlin code with comprehensive comments, error handling, and unit tests for each module. The Shopping Lens and Product Photo pipeline are core features — build them alongside the Money and Health lenses in Phase 5.
