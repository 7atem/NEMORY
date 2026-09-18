# Physical-device UX check ? 7 September 2026

Device: Samsung SM-T975, Android 13. Debug APK installed successfully.

Verified through actual touch input and UI Automator hierarchy:
- Tablet navigation rail switches Today / Vault / Brain.
- Today capture labels are available in landscape and portrait at 150% font scale.
- Long-press selects a vault item; tapping a second item selects two.
- Swipe during selection did not delete an item.
- Import launches the system document picker; no file was imported.
- Scan requests camera permission; no photo was taken or saved.
- Ask Nemory now opens Brain with the typed draft; no question was sent.
- Onboarding completion survives force-stop / relaunch after the fix.
- Crash log buffer had no entries during the check.
- Original font scale (1.0), rotation (0), and auto-rotation (1) restored.

Fixes made in VaultBrainNavGraph.kt:
- Wire Today onAskBrain to the encoded Brain query route.
- Persist OnboardingState when completing or skipping onboarding.
- Hide the global capture FAB on Brain to avoid composer overlap.

Validation: final :app:assembleDebug --offline succeeded; APK installed.
Final composer-overlap retest passed after user PIN setup: Brain input and send control
are present, and the floating capture button is absent. Returned the device to Today.
Screenshot capture was blocked by the existing FLAG_SECURE protection. No pixel-level
visual assessment or contrast measurement was performed. Arabic UI was not exercised
on the physical device. No existing vault items were edited or deleted.
