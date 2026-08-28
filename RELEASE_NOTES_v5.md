# Nemory v1.0.0 (versionCode 5) — Release Notes

## What’s new

### Personalized experiences in capture / share flow
- After OCR finishes, users now pick an experience: Expense, Subscription, Prescription, Flight ticket, Watchlist, Warranty, etc. (50+ options).
- Each experience runs a dedicated parser that extracts meaningful fields from OCR text.
- The item is then shown on the review screen with the chosen experience chip, extracted details, and correct smart-space tag.
- “Just save” fallback is available for plain vault items.

### Share-to-Nemory fix
- Shared images, PDFs, and text no longer disappear silently into the vault.
- They now open the capture/review flow so the user can choose an experience and verify details before saving.

### Review screen improvements
- Added **Discard** button.
- Added experience chip so users see which experience was applied.

### UI polish
- Fixed duplicated “Items” label in vault overview.

### Monetization
- This build is ad-free.
- All lenses and experiences are available without tiers.

## Upload instructions

1. Open [Google Play Console](https://play.google.com/console).
2. Go to **Nemory → Test and release → Internal testing → Create release**.
3. Upload: `app/release/nemory-v1.0.0-code5.aab`.
4. Set release notes:
   - English (default): *“Personalized experiences for every item. Pick Expense, Prescription, Warranty, Watchlist, etc. after sharing or capturing. Share-to-Nemory now opens review flow. Ad-free.”*
5. Review the release and start the internal test rollout.
