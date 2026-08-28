# Nemory v1.0.0 (versionCode 5) — Play Console Setup Guide

## 1. Upload the AAB

- Go to **Play Console → Nemory → Test and release → Internal testing**.
- Click **Create release**.
- Upload: `D:\Vault Brain\app\release\nemory-v1.0.0-code5.aab`.
- Release name: `v1.0.0-code5`.
- Release notes (English / default):
  > Personalized experiences for every item. After sharing or capturing, pick Expense, Subscription, Prescription, Flight ticket, Warranty, Watchlist, and more. Share-to-Nemory now opens the review flow instead of silently saving. Review screen includes Discard and an experience chip. Ad-free.
- Save, review, and **Start rollout to Internal testing**.

## 2. Store listing assets (verify sizes)

All assets are in `D:\Vault Brain\store-assets`.

| Asset | Required size | File to use |
|---|---|---|
| App icon | 512×512 px, PNG/JPEG, ≤1 MB | `nemory-play-store-icon.png` |
| Feature graphic | 1024×500 px, PNG/JPEG, ≤15 MB | `nemory-feature-graphic.png` |
| Phone screenshots | 2–8 shots, 16:9 or 9:16, 320–3840 px per side | Use files in `store-assets/screenshots-ready/` |
| 10-inch tablet screenshots | 1024×768 min, ≤3840×3840 | Use files in `store-assets/tablet/` (2560×1600) |
| 7-inch tablet screenshots | 320×480 min, ≤3840×3840 | Optional; you can reuse the 10-inch shots or take dedicated ones |

If the tablet screenshots look too small on the listing, capture fresh ones on the Galaxy Tab S7+ emulator at 2800×1752.

## 3. Ads declaration

Because this build is ad-free:

- **Does your app contain ads?** → **No, my app does not contain ads**.

## 4. Target age group

Nemory stores personal documents (IDs, prescriptions, receipts, passports, financial records). Even though the app itself is not harmful, it handles sensitive PII and financial/health data. For compliance and safety, select:

- **13–15**
- **16–17**
- **18 and over**

Leave **5 and under**, **6–8**, and **9–12** unchecked. This avoids the strictest Families/Children policy burden while keeping a broad teen/adult audience.

If you later add cloud sync or ads, revisit this screen and the Data Safety section.

## 5. Data Safety (ad-free, local-only build)

Use these answers:

- **Does your app collect or share any of the required user data types?** → **Yes**
- **Is all of the user data collected by your app encrypted in transit?** → **Yes** (data is encrypted at rest in the local SQLCipher database and within the device)
- **Which of the following methods of account creation does your app support?** → **My app does not allow users to create an account**
- **Can users login to your app with accounts created outside of the app?** → **No**
- **Do you provide a way for users to request that their data is deleted?** → **Yes** (user can delete individual items or all vault data from the app)

### Data types collected/transferred

Select only the types that the app or its libraries handle locally on the device:

- **Photos and videos** → collected by the user (camera/gallery imports)
- **Files and docs** → imported PDFs and documents
- **Calendar** → dates extracted from items for reminders (optional, on-device)
- **App activity** → local search/history inside the vault
- **Device or other IDs** → none intentionally; leave unchecked unless an analytics SDK is added

For each selected type:

- **Collected?** Yes
- **Shared?** No
- **Used for app functionality?** Yes
- **Optional?** Yes for calendar-derived reminders; No for photos/files because they are the core feature
- **Ephemeral?** No

## 6. Content ratings

- **Category**: **All Other App Types**
- **Downloaded app / ratings-relevant content**: **No**
- **User content sharing**: **No**
- **Online content**: **No**
- **Promotion/sale of age-restricted products**: **No**
- **Location sharing**: **No**
- **Digital goods purchase**: **No** (no in-app purchases in this build)
- **Cash rewards / NFTs / crypto**: **No**
- **Web browser/search engine**: **No**
- **News/educational product**: **No**

Expected rating: **PEGI 3 / ESRB Everyone / Rated for 3+**.

## 7. Financial features / Health apps

- **Financial features**: select **My app doesn't provide any financial features** (Nemory only stores document images; it does not lend, transfer, trade, or give financial advice).
- **Health features**: select **My app does not have any health features** (it stores prescription/lab images but does not diagnose, manage disease, or provide clinical decision support).

## 8. App category and tags

- **App or game**: App
- **Category**: **Productivity** or **Tools**
- Tags (pick 3–5): `Personal assistant`, `Productivity`, `Notebook`, `Password manager`, `Privacy & security`

## 9. After internal testing passes

Promote the same AAB to **Closed testing** → invite your testers (including the Galaxy Tab S7+ device user). After closed testing stability, promote to **Production**.

## 10. Known warnings (safe to ignore)

- Build emits `@Inject` annotation-target warnings. These are pre-existing Kotlin compiler warnings and do not affect runtime.
