# Nemory — Play Store Launch Checklist

> Updated with the new personal-account production-access rules. Production is unlocked only after **≥12 testers are continuously opted into Closed Testing for ≥14 days**. Start that clock as early as possible.

---

## Phase 0 — Pre-Flight (Before Any Play Console Work)

| # | Task | Owner | Status |
|---|---|---|---|
| 0.1 | Pay $25 Google Play Developer fee and create account. | You | ✅ |
| 0.2 | Verify `com.nemory.app` application ID. | `app/build.gradle.kts` | ✅ |
| 0.3 | Configure release signing keystore and env vars (`NEMORY_UPLOAD_STORE_*`). | You / CI | ✅ |
| 0.4 | Run all unit tests. | `./gradlew testDebugUnitTest --rerun-tasks` | ✅ |
| 0.5 | Build debug APK. | `./gradlew :app:assembleDebug` | ✅ |
| 0.6 | Build release AAB. | `./gradlew :app:bundleRelease` | ✅ |
| 0.7 | Run lint. | `./gradlew :app:lintDebug` | ✅ |
| 0.8 | Review ProGuard rules. | `app/proguard-rules.pro` | ✅ |

---

## Phase 1 — Internal Test (You + 2–3 People)

Internal testing is optional but strongly recommended. **Do not** put your eventual closed testers here; internal testers must opt out before joining closed testing.

| # | Task | Notes | Status |
|---|---|---|---|
| 1.1 | Create internal testing track. | Max 100 testers. | ⬜ |
| 1.2 | Add yourself + 2–3 trusted testers. | Use individual emails. | ⬜ |
| 1.3 | Upload signed release AAB to internal track. | Use the output of `:app:bundleRelease`. | ⬜ |
| 1.4 | Verify install from Play, startup, auth, capture, Brain, Today, Arabic/English. | ⬜ |
| 1.5 | Check signing and database creation. | ⬜ |
| 1.6 | Collect initial crash/feedback. | ⬜ |

---

## Phase 2 — Play Console Policy & Listing Setup

Complete these sections in Play Console before starting Closed Testing.

### 2.1 Privacy & Access

| # | Task | Guidance | Status |
|---|---|---|---|
| 2.1.1 | Publish privacy policy on a public HTTPS URL. | Must also be reachable inside the app. Cover local storage, camera/mic, on-device OCR/AI, encrypted vault, optional cloud AI, analytics/crash, ads. | ✅ |
| 2.1.2 | Enter privacy policy URL in Play Console. | **Policy and programs → App content → Privacy policy** | ✅ |
| 2.1.3 | Complete **App access** declaration. | If no account is required, say normal functionality is accessible without credentials. Provide reviewer instructions for PIN/biometric/decoy if needed. | ✅ |

### 2.2 Ads, Rating, Audience

| # | Task | Guidance | Status |
|---|---|---|---|
| 2.2.1 | Declare **Ads = No** for the current ad-free build. | Update this to Yes only when you actually release a version with ads. | ✅ |
| 2.2.2 | Complete IARC **Content rating** questionnaire. | Answer based on what Nemory itself contains, not arbitrary user content. | ✅ |
| 2.2.3 | Set **Target audience**. | Nemory is a productivity/vault utility; do not target children unless it is genuinely designed for them. | ✅ |
| 2.2.4 | Declare **Government apps = No** (unless the app is government-affiliated). | Nemory is not a government app. | ✅ |
| 2.2.5 | Answer **Financial features** carefully. | Storing receipts/invoices does not equal banking/lending/crypto. | ✅ |
| 2.2.6 | Answer **Health** carefully. | Storing prescriptions does not equal diagnosis/treatment. Send the exact options if unsure. | ✅ |

### 2.3 Data Safety

Do this slowly and accurately. You must declare data collected/shared by **your code AND included SDKs**.

| # | Task | Guidance | Status |
|---|---|---|---|
| 2.3.1 | Audit the release AAB for SDKs that transmit data. | First release is ad-free. Billing SDK does not transmit user data. No analytics/crash SDKs are included. Cloud AI is not wired. | ✅ |
| 2.3.2 | Complete Data Safety form to match the actual build. | Select **No** for "Does your app collect or share any required user data types?" | ⬜ |
| 2.3.3 | Review Data Safety answers before submission. | Double-check answers match the actual AAB. | ⬜ |

### 2.4 Store Listing

| # | Task | Guidance | Status |
|---|---|---|---|
| 2.4.1 | Choose **App category = Productivity**. | — | ✅ |
| 2.4.2 | Fill developer email, website, phone (if required). | Use `fedrev92@gmail.com` | ✅ |
| 2.4.3 | Write app name (≤30 chars). | **Nemory** | ✅ |
| 2.4.4 | Write short description (≤80 chars). | *Save anything. Find it when you need it.* | ✅ |
| 2.4.5 | Write full description. | Emphasize private vault, on-device AI, automatic organization, reminders, Today, Brain. | ✅ |
| 2.4.6 | Prepare app icon, feature graphic. | App icon 512×512; feature graphic 1024×500 | ✅ |
| 2.4.7 | Capture phone screenshots. | English + Arabic minimum. | ✅ |
| 2.4.8 | Capture tablet/foldable screenshots if supported. | 8 tablet screenshots prepared at 2560×1600. | ✅ |
| 2.4.9 | Add Arabic translation of store listing. | — | ⬜ |

---

## Phase 3 — Closed Testing (The 14-Day Clock)

This is the mandatory gate for new personal developer accounts.

### 3.1 Track Setup

| # | Task | Guidance | Status |
|---|---|---|---|
| 3.1 | Create Closed Testing track. | **Test and release → Testing → Closed testing** | ⬜ |
| 3.1.2 | Select target countries/regions. | Start with countries where your testers are located (e.g., Egypt). | ⬜ |
| 3.1.3 | Add **15–20 testers** (not 12). | Use a Google Group if convenient. Risk buffer against opt-outs. | ⬜ |
| 3.1.4 | Send each tester the opt-in URL. | They must join with the invited Google account. | ⬜ |
| 3.1.5 | Confirm dashboard shows **≥12 testers currently opted in**. | ⬜ |

### 3.2 Closed Testing Release

| # | Task | Guidance | Status |
|---|---|---|---|
| 3.2.1 | Upload signed release AAB. | ⬜ |
| 3.2.2 | Add release notes. | ⬜ |
| 3.2.3 | Review and start rollout to Closed Testing. | Link may take several hours to appear. | ⬜ |
| 3.2.4 | Send for Google review if requested. | ⬜ |

### 3.3 Keep Testers Engaged for 14 Continuous Days

| # | Task | Guidance | Status |
|---|---|---|---|
| 3.3.1 | Share a test plan with testers. | Capture, OCR, edit, archive/restore, reminder, Brain, Today, search, Arabic/English, rotation, background/foreground, permissions. | ⬜ |
| 3.3.2 | Collect feedback and fix bugs. | You can push updated releases during the 14 days. | ⬜ |
| 3.3.3 | Ensure no more than 3 testers opt out at any time. | Maintain ≥12 continuous opt-ins. | ⬜ |
| 3.3.4 | Run physical-device matrix in parallel. | Low-end API 29, API 31 voice, Pixel 9/Nano v4, tablet/foldable, RTL, TalkBack, process death, notifications, offline, thermal, low battery. | ⬜ |

---

## Phase 4 — Apply for Production (After 14 Days)

| # | Task | Guidance | Status |
|---|---|---|---|
| 4.1 | Confirm dashboard shows ≥12 opted-in testers for the preceding 14 days. | ⬜ |
| 4.2 | Click **Apply for production**. | ⬜ |
| 4.3 | Answer questions about closed test. | How testers were recruited, feedback received, changes made. | ⬜ |
| 4.4 | Answer questions about the app. | Intended audience, core value. | ⬜ |
| 4.5 | Answer questions about production readiness. | Why the app is ready. | ⬜ |
| 4.6 | Submit production release AAB. | Final signed release with production ad IDs if ads are enabled. | ⬜ |

---

## Phase 5 — Post-Launch

| # | Task | Guidance | Status |
|---|---|---|---|
| 5.1 | Monitor Crashlytics / Play Console crashes. | ⬜ |
| 5.2 | Monitor ratings and respond to reviews. | ⬜ |
| 5.3 | Track retention and DAU. | ⬜ |
| 5.4 | Enable monetization only after retention is proven. | Ads + Remove Ads lifetime/subscription. | ⬜ |
| 5.5 | Activate cloud AI only after Firebase project is fully configured and reviewed. | ⬜ |

---

## Notes

- **Production is blocked for new personal accounts until the 14-day/12-tester closed-testing requirement is met.** There is no shortcut.
- **Internal testers are not eligible for Closed Testing** until they opt out of Internal Testing.
- **First release is ad-free.** Declare Ads = No. Update the declaration before enabling ads.
- **Phase 5 live cloud activation is blocked** until the Firebase project is configured. The app ships with `DisabledCloudAiRuntime` by default, so this is not a launch blocker.
- **Replace test IDs before any monetized release:**
  - `feature/vault/src/main/res/values/strings.xml` → `admob_banner_home_id`
  - `app/src/main/AndroidManifest.xml` → AdMob app ID
- Use the 14-day closed-testing period to complete the physical-device/release-validation matrix.
