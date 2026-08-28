# Nemory — Closed Testing Test Plan

> Share this document with your 15–20 closed testers. Ask them to complete as many scenarios as possible during the 14-day test period and to report bugs or confusing UX.

---

## Tester Information

- **Test period:** 14 continuous days
- **Minimum testers needed:** 12 continuously opted in
- **Target testers:** 15–20
- **Test languages:** English and Arabic
- **Feedback channel:** [your-preferred-channel, e.g., WhatsApp group, Google Form, email]

---

## How to Join

1. Open the Play Store testing invite link with the Google account you gave us.
2. Tap **Become a tester**.
3. Install Nemory from Google Play.
4. Keep the app installed and yourself opted in for at least 14 days.
5. Do **not** leave the testing program during the 14-day window.

---

## General Feedback Questions

After using Nemory for a few days, please answer:

1. Was onboarding clear? Did you understand what Nemory does?
2. Was the app easy to use without reading instructions?
3. Did you trust the app with your documents? Why or why not?
4. Did you encounter any crashes, freezes, or error messages?
5. Did you try Arabic? Did anything look wrong or backwards?
6. Would you keep using Nemory after the test?
7. What is the one thing you would change?

---

## Core Test Scenarios

### 1. First Launch & Setup

| # | Step | Expected Result | Pass / Fail |
|---|---|---|---|
| 1.1 | Install from Play Store and open app | Onboarding or auth gate appears | ⬜ |
| 1.2 | Complete onboarding | Reach Home screen | ⬜ |
| 1.3 | Set up PIN / biometric lock | Lock screen works; can unlock | ⬜ |
| 1.4 | Background the app and reopen after a few minutes | Auth gate reappears if auto-lock is enabled | ⬜ |
| 1.5 | Kill app and relaunch | App restores to expected state | ⬜ |

### 2. Capture & Save

| # | Step | Expected Result | Pass / Fail |
|---|---|---|---|
| 2.1 | Tap Capture → take a photo of a receipt | Save completes quickly; item appears in Vault | ⬜ |
| 2.2 | Import a photo from gallery | Item appears with thumbnail | ⬜ |
| 2.3 | Share a PDF from another app to Nemory | Share sheet closes quickly; item appears | ⬜ |
| 2.4 | Share a photo from another app to Nemory | Item appears without opening Nemory first | ⬜ |
| 2.5 | Tap paste text and enter a note manually | Item saves as a text note | ⬜ |
| 2.6 | Try voice capture if available | Transcript appears in review screen | ⬜ |

### 3. Review & Edit

| # | Step | Expected Result | Pass / Fail |
|---|---|---|---|
| 3.1 | Open a saved receipt | Title, summary, merchant, total, and date are visible | ⬜ |
| 3.2 | Edit title or category | Changes persist after leaving and returning | ⬜ |
| 3.3 | Override the AI category manually | New category is used for routing | ⬜ |
| 3.4 | Add or change a reminder date | Reminder is saved | ⬜ |
| 3.5 | View "Needs review" items | Uncertain items appear for your confirmation | ⬜ |

### 4. Vault Browser & Search

| # | Step | Expected Result | Pass / Fail |
|---|---|---|---|
| 4.1 | Open Vault tab | List of saved items loads | ⬜ |
| 4.2 | Use search bar to find a merchant or word | Relevant items appear | ⬜ |
| 4.3 | Try natural-language search: "receipts from last month" | Filtered results appear | ⬜ |
| 4.4 | Tap a lens filter (Money, Health, Travel, etc.) | Only matching items appear | ⬜ |
| 4.5 | Rotate device while browsing | Layout adapts correctly | ⬜ |

### 5. Brain Chat

| # | Step | Expected Result | Pass / Fail |
|---|---|---|---|
| 5.1 | Open Brain and ask: "How much did I spend at [merchant]?" | Answer shows sources and calculation | ⬜ |
| 5.2 | Ask: "When does my passport expire?" | Correct expiry date with source card | ⬜ |
| 5.3 | Ask an unrelated question | App says it doesn't have that information | ⬜ |
| 5.4 | Try Brain with no saved items | Helpful empty state or suggestions | ⬜ |
| 5.5 | Try an Arabic question if you saved Arabic text | Answer is relevant and RTL readable | ⬜ |

### 6. Today Insights

| # | Step | Expected Result | Pass / Fail |
|---|---|---|---|
| 6.1 | Open Today tab | Cards appear for expiring/upcoming items | ⬜ |
| 6.2 | Save a document with a near-future expiry date | Card appears in Today | ⬜ |
| 6.3 | Tap a Today card | Navigates to the correct item | ⬜ |
| 6.4 | Confirm no card invents fake information | ⬜ |

### 7. Language & RTL

| # | Step | Expected Result | Pass / Fail |
|---|---|---|---|
| 7.1 | Switch device language to Arabic | App layout mirrors correctly | ⬜ |
| 7.2 | Save an item with English merchant name | Title and metadata display correctly | ⬜ |
| 7.3 | Save an item with Arabic text | OCR and search work | ⬜ |
| 7.4 | Mix Arabic and English in one title | Text reads correctly without truncation | ⬜ |

### 8. Permissions & Settings

| # | Step | Expected Result | Pass / Fail |
|---|---|---|---|
| 8.1 | Deny camera permission | App still works via gallery/paste | ⬜ |
| 8.2 | Grant notification permission only when a reminder is set | Permission is requested contextually | ⬜ |
| 8.3 | Open Settings → Privacy | On-device / cloud status is clear | ⬜ |
| 8.4 | Open Settings → Remove Ads | Purchase flow or unavailable message appears | ⬜ |

### 9. Process Death & Backgrounding

| # | Step | Expected Result | Pass / Fail |
|---|---|---|---|
| 9.1 | Open an item, then force-stop the app and reopen | App returns to a safe state | ⬜ |
| 9.2 | Capture a photo, background the app during enrichment, return later | Item shows Ready or Organizing state | ⬜ |
| 9.3 | Rotate during capture review | No data loss or crash | ⬜ |

### 10. Edge Cases

| # | Step | Expected Result | Pass / Fail |
|---|---|---|---|
| 10.1 | Save a blurry or dark photo | App still saves; may show lower confidence | ⬜ |
| 10.2 | Save a multi-page PDF | Item saves and OCR runs | ⬜ |
| 10.3 | Save a handwritten note | Text extraction may be limited; app handles gracefully | ⬜ |
| 10.4 | Save a passport or ID | Category stays sensitive; no cloud processing | ⬜ |

---

## Device-Specific Checks

| Device/OS | Tester | Checks |
|---|---|---|
| Low-end Android 10 (API 29) | ⬜ | App launches; capture/save works; Brain works without Nano |
| Android 12/13 (API 31–33) | ⬜ | Voice model download behavior; on-device speech |
| Pixel / high-end Android 14/15 (API 36) | ⬜ | Full Nano/Brain/Today validation |
| Tablet or foldable | ⬜ | Layout, rotation, multi-pane if supported |

---

## How to Report Bugs

When you find a bug, please send:

1. A short description of what happened.
2. Steps to reproduce it.
3. Your device model and Android version.
4. Screenshots or screen recording if possible.
5. Whether the app crashed or just behaved unexpectedly.

---

## Important Reminders for Testers

- **Keep the app installed and stay opted in for 14 days.** Opting out early may delay production.
- **Use real documents you don't mind storing locally.** Do not test with other people's sensitive documents without permission.
- **The app is ad-free during this test.** No ads should appear.
- **Cloud AI is disabled by default.** All processing is on your device unless you explicitly enable cloud (not required for testing).
- **Your data stays on your device.** Uninstalling the app will delete your saved items.

---

## Bug Severity Scale

When reporting an issue, please mark severity:

| Severity | Meaning | Example |
|---|---|---|
| **P0 — Blocker** | App crashes, freezes, or loses data. You cannot continue testing. | App crashes on every launch; saved item disappears. |
| **P1 — High** | Core feature broken or unusable. Workaround is painful. | Capture fails to save; search returns wrong results. |
| **P2 — Medium** | Feature works but with friction or visual issues. | Layout overlaps; slow loading; typo. |
| **P3 — Low** | Minor polish or suggestion. | Better icon; wording suggestion. |

Please report P0 and P1 issues immediately. P2 and P3 can be collected in a weekly batch.

---

## Bug Report Template

When reporting a bug, please include:

1. **Severity:** P0 / P1 / P2 / P3
2. **Scenario number:** e.g., 2.1
3. **Device:** e.g., Samsung Galaxy A54, Android 14
4. **Language:** English / Arabic
5. **What happened:** One sentence
6. **Steps to reproduce:** 1, 2, 3…
7. **Expected:** What should have happened
8. **Actual:** What actually happened
9. **Screenshots or screen recording:** Attach if possible
10. **Can you reproduce it every time?** Yes / No / Sometimes

---

## Sample Items to Test With

You can use the following examples to verify classification and extraction:

| Item | Suggested Lens | What to Check |
|---|---|---|
| Grocery receipt | Money | Merchant, total, date, tax |
| Pharmacy prescription | Health | Medication name, pharmacy, dates |
| Flight ticket | Travel | Flight number, date, gate/seat if visible |
| Passport or ID | Bureaucracy | Document type, expiry date |
| Warranty card | Home | Product name, warranty period |
| Movie poster or ticket | Media | Title, date, category |
| Handwritten note | General / Productivity | Saves correctly; OCR may be limited |
| Long PDF invoice | Money | Item list, total, due date |

**Important:** Do not photograph other people's sensitive documents without permission. Use your own documents or anonymized examples.

---

## Suggested Daily Usage

To keep feedback balanced, try to:

- Save at least 1–2 items per day.
- Open Vault and search once per day.
- Check Today at least once during the 14 days.
- Ask Brain at least 3 questions total.
- Try Arabic input or switch device language to Arabic at least once.
- Rotate the device or switch apps during capture at least once.

---

## Additional Edge Cases

If you have time, also try:

| # | Edge Case | Expected |
|---|---|---|
| A.1 | Save 20+ items | List scrolls smoothly; search stays fast |
| A.2 | Save a very large PDF | App does not freeze; item appears |
| A.3 | Save a blurry receipt | Item saves; confidence may be low; can edit fields |
| A.4 | Search with misspelled word | Relevant results still appear |
| A.5 | Delete an item | Item disappears; undo works if shown |
| A.6 | Archive an item | Item moves out of active list; can restore |
| A.7 | Set a reminder for tomorrow | Notification appears at expected time |
| A.8 | Open app offline after saving items | All saved content is visible; Brain uses local search |
| A.9 | Background the app during capture/enrichment | No data loss; state shows correctly on return |
| A.10 | Deny camera permission | App still allows gallery, paste, and voice |

---

## Goal

Your feedback helps Nemory reach production. Please use the app naturally, try the scenarios above, and tell us what works and what doesn't.

Thank you!
