# Architecture

Messages is a single-module Android app. The code is split into three packages
with a one-way dependency direction: `ui` → `data` → `core` → Android platform.

```
app/src/main/java/com/tamadon/smsmessenger/
├── App.kt            Application: preferences, Room database, repository, background jobs
├── core/             Platform-facing helpers
│   ├── Phone.kt      Number normalisation, thread keys, contact cache
│   ├── TextTools.kt  One-time code, date, USSD, and link detection
│   ├── SmsSender.kt  Multipart SMS, SIM selection, delivery reports
│   ├── Receivers.kt  SMS_DELIVER, status, mark-read, reply, copy-code receivers
│   ├── Notifier.kt   Notification channels and privacy-aware notifications
│   ├── Contacts.kt   (in Phone.kt) in-memory contact cache
│   └── ...           SIMs, images, lock manager, localisation
├── data/             Persistence and system-provider synchronisation
│   ├── Db.kt         Room entities, DAO, migrations
│   ├── Repo.kt       Repository used by the UI and receivers
│   ├── Prefs.kt      Observable settings stored in SharedPreferences
│   └── ProviderSync.kt  Import from / write back to the system SMS provider
└── ui/               Jetpack Compose
    ├── MainActivity.kt  Edge-to-edge host, intent parsing, lock gate
    ├── Nav.kt           Navigation graph
    ├── ConversationsScreen.kt, ChatScreen.kt, OtherScreens.kt
    ├── Common.kt        Shared composables, permissions, role request
    └── Theme.kt         Material 3 theme
```

## Data flow

1. **Startup.** `App.onCreate` creates `Prefs`, opens the Room database, and
   builds `Repo`. `Repo` eagerly collects the home conversation list so the first
   frame has data. Background work imports messages from the system provider,
   creates notification channels, loads contacts, and schedules trash cleanup.
2. **Incoming SMS.** The default-SMS role delivers `SMS_DELIVER` to
   `SmsReceiver`. It normalises the address with `Phone.threadKey`, stores the
   message through `Repo.receive`, and posts a notification unless the
   conversation is open, muted, blocked, or hidden.
3. **Outgoing SMS.** The chat screen splits long text with `SmsSender`, inserts a
   local row in `SENDING` state, and sends one part at a time. Status receivers
   update `sentParts` / `deliveredParts` with atomic SQL so multipart messages
   finish in the correct order.
4. **UI state.** Screens collect `StateFlow`s from `Repo` (Room `Flow` queries).
   Preferences that affect rendering are Compose state, so theme, language, and
   text-size changes apply without a database round trip.
5. **Provider sync.** When another app is the default SMS app, `ProviderSync`
   imports new provider rows on resume. When Messages is default, read and
   delivery state are written back so other SMS apps see the same state.

## Thread keys

A conversation is identified by `Phone.threadKey(address)`: the last 10 digits
for phone numbers (so `0912…`, `+98912…`, and `0098912…` group together), or the
lowercased address for alphanumeric sender IDs and short codes.

## Persistence

Room database `sms.db`, version 3:

- `messages` — body, date, direction, read state, status, multipart counters,
  provider id, SIM subscription id, trash timestamp, star flag.
- `threads` — address, pin/lock/archive/mute/block/spam flags, trash timestamp.
- `quick_replies` — ordered phrases.

Migrations `1→2` and `2→3` are explicit; there is no destructive fallback.

## Security and privacy

- No network code except link-preview fetching in `TextTools` / `Images`.
- No analytics SDKs, no advertising IDs, no remote logging.
- `android:allowBackup="false"`.
- Locks are enforced in the UI and in the app lock gate; the PIN is stored as a
  hash in `Prefs`, not in plain text.
- The app never exports the database or message store.
