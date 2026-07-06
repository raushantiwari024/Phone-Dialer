# Custom Dialer App — Project Brief for Antigravity

**Goal:** Build a full default-dialer replacement Android app (calls, call log, contacts) using Kotlin + Jetpack Compose, designed as a personal phone management tool.

**Stack:** Kotlin, Jetpack Compose, Android Telecom framework, single-module Gradle project, min SDK 26+ (RoleManager requires API 26+), target latest stable SDK.

---

## 1. Module / Package Structure

```
app/
├── manifests/
│   └── AndroidManifest.xml        # ACTION_DIAL intent-filter, InCallService declaration, permissions
├── kotlin/com.yourname.dialer/
│   ├── MainActivity.kt            # Compose host, handles ACTION_DIAL intent
│   ├── DialerApplication.kt       # Application class, DI setup if using Hilt
│   │
│   ├── telecom/
│   │   ├── MyInCallService.kt     # Core: extends InCallService, manages Call.Callback
│   │   ├── CallRepository.kt      # Singleton exposing call state as a Kotlin Flow
│   │   ├── DefaultDialerManager.kt # RoleManager request/check logic
│   │   └── TelecomHelper.kt       # Wraps TelecomManager.placeCall(), hold/mute/end actions
│   │
│   ├── data/
│   │   ├── CallLogRepository.kt   # Reads CallLog.Calls content provider
│   │   ├── ContactsRepository.kt  # Reads ContactsContract
│   │   └── models/
│   │       ├── CallLogEntry.kt
│   │       └── Contact.kt
│   │
│   ├── ui/
│   │   ├── dialpad/
│   │   │   ├── DialpadScreen.kt
│   │   │   └── DialpadViewModel.kt
│   │   ├── calllog/
│   │   │   ├── CallLogScreen.kt
│   │   │   └── CallLogViewModel.kt
│   │   ├── contacts/
│   │   │   ├── ContactsScreen.kt
│   │   │   ├── ContactDetailScreen.kt
│   │   │   └── ContactsViewModel.kt
│   │   ├── incall/
│   │   │   ├── IncomingCallScreen.kt   # Full-screen, shows over lock screen
│   │   │   ├── ActiveCallScreen.kt     # Mute/hold/speaker/end UI
│   │   │   └── InCallViewModel.kt      # Observes CallRepository Flow
│   │   ├── onboarding/
│   │   │   └── SetDefaultDialerScreen.kt  # First-run: request RoleManager.ROLE_DIALER
│   │   ├── settings/
│   │   │   └── SettingsScreen.kt
│   │   └── theme/
│   │       └── Theme.kt, Color.kt, Type.kt   # Match Stitch design tokens here
│   │
│   └── navigation/
│       └── NavGraph.kt            # Compose Navigation between screens above
│
└── res/
    └── values/strings.xml, colors.xml
```

---

## 2. Manifest Essentials

```xml
<!-- Permissions -->
<uses-permission android:name="android.permission.READ_PHONE_STATE" />
<uses-permission android:name="android.permission.CALL_PHONE" />
<uses-permission android:name="android.permission.READ_CALL_LOG" />
<uses-permission android:name="android.permission.WRITE_CALL_LOG" />
<uses-permission android:name="android.permission.READ_CONTACTS" />
<uses-permission android:name="android.permission.ANSWER_PHONE_CALLS" />
<uses-permission android:name="android.permission.MANAGE_OWN_CALLS" />

<!-- Dialer activity — handles ACTION_DIAL -->
<activity android:name=".MainActivity" android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.DIAL" />
        <category android:name="android.intent.category.DEFAULT" />
    </intent-filter>
    <intent-filter>
        <action android:name="android.intent.action.DIAL" />
        <category android:name="android.intent.category.DEFAULT" />
        <data android:scheme="tel" />
    </intent-filter>
</activity>

<!-- InCallService declaration -->
<service android:name=".telecom.MyInCallService"
    android:permission="android.permission.BIND_INCALL_SERVICE"
    android:exported="true">
    <meta-data android:name="android.telecom.IN_CALL_SERVICE_UI" android:value="true" />
    <intent-filter>
        <action android:name="android.telecom.InCallService" />
    </intent-filter>
</service>
```

---

## 3. Build Order (recommended sequence for Antigravity)

1. **Scaffold project** — empty Compose project, package structure above, Gradle deps (Compose BOM, Navigation-Compose, lifecycle-viewmodel-compose).
2. **Native telecom core first** — `MyInCallService`, `DefaultDialerManager`, `CallRepository` with a `Flow<CallState>`. Verify on a real device: request default dialer role, confirm the role is granted, confirm `onCallAdded`/`onCallRemoved` fire on a real incoming call.
3. **Onboarding screen** — RoleManager request flow, confirm it round-trips correctly before building anything else.
4. **Call log + contacts repositories** — read-only content provider wrappers, unit-testable independent of telecom.
5. **UI screens** — build against Stitch visual reference, wire to ViewModels backed by the repositories/Flow from step 2–4.
6. **In-call UI last** — hardest to test (needs live call state), so leave until the rest is stable.
7. **Polish** — theming to match Stitch tokens, empty states, permission-denied fallbacks.

---

## 4. Known Gotchas to tell Antigravity about up front

- Emulators do **not** reliably simulate telecom role changes or real incoming calls — insist on physical device testing for anything in `telecom/`.
- If `MyInCallService` ever returns a null binding, Android silently falls back to the system dialer — the agent should never let that binding return null.
- Emergency calls must always route through `TelecomManager.placeCall()`, never bypass it, even in testing.
- `RoleManager` can revoke your dialer role automatically if you programmatically disable the `InCallService` component — don't do that at runtime.

---

## 5. Stitch → Compose handoff note

Stitch exports HTML/Tailwind, not Compose. Treat Stitch output as a **visual spec** (screenshots/Figma), not code to port. Extract: color palette, spacing scale, typography, and component layout — hand those as design tokens to Antigravity rather than raw markup.
