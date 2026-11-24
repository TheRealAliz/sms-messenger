# Contributing

Thanks for taking the time to improve Messages. This document explains how to
build the app and what a good contribution looks like.

## Code of conduct

By participating, you agree to follow the [Code of Conduct](CODE_OF_CONDUCT.md).

## Before you start

- Search existing issues and pull requests to avoid duplicate work.
- For a large feature or a behaviour change, open an issue first so the
  approach can be discussed.
- For security problems, do **not** open a public issue. Follow
  [SECURITY.md](SECURITY.md).

## Development setup

You need:

- JDK 17
- Android SDK Platform 35 and build-tools 35
- An Android device or emulator running Android 8.0 (API 26) or newer

```bash
git clone https://github.com/TheRealAliz/sms-messenger.git
cd sms-messenger
./gradlew assembleDebug
```

Install the debug build:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The debug application ID is `com.tamadon.smsmessenger.debug`, so it does not
replace a release installation.

### Release signing

Never commit a keystore or its passwords. Local release builds read
`keystore.properties`, which is ignored by Git:

```bash
cp keystore.properties.example keystore.properties
# edit the values
./gradlew assembleRelease
```

## Checks

Run these before opening a pull request:

```bash
./gradlew test
./gradlew lintDebug
./gradlew assembleDebug
```

CI runs the same commands.

## Code style

- Kotlin official style (`kotlin.code.style=official`), 4-space indentation.
- Keep user-facing text in `app/src/main/res/values/strings.xml` and
  `values-fa/strings.xml`; do not hardcode strings in Compose code.
- Keep the existing architecture: `core/` for platform work, `data/` for Room and
  preferences, `ui/` for Compose screens.
- Prefer small, focused changes. Avoid unrelated reformatting.
- Do not add analytics, tracking, ads, or network calls unrelated to a feature.
- Preserve the privacy model: data stays local unless the user explicitly
  triggered an action that needs the network (for example a link preview).

## Commit messages

Use short imperative subjects, for example:

```
Fix unread count after archiving
Add Persian plural strings for trash retention
```

Add a body when the reason for the change is not obvious.

## Pull requests

- Describe the problem and the solution.
- Link the related issue.
- Include screenshots or a short screen recording for UI changes.
- Mention the device and Android version used for testing.
- Keep the PR focused; split independent changes.

## License

By contributing, you agree that your contributions are licensed under the
Apache License 2.0, the same license as the project.
