# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added

- Project documentation, contribution guides, issue and pull request templates.
- GitHub Actions workflow for tests, lint, and debug builds.
- Unit tests for one-time code detection, link extraction, and phone grouping.

## [0.9.0] - 2026-11-27

First public release.

### Added

- SMS sending and receiving with the Android default-SMS role.
- Multipart messages, delivery reports, and dual-SIM support.
- One-time code detection with copy action and optional auto-copy.
- Quick replies, pinned and archived conversations, blocked and spam lists.
- Starred messages, search history, contact names and photos.
- Per-conversation locks and an optional app lock with biometrics and a PIN.
- Notification privacy controls and inline reply.
- Trash with configurable automatic cleanup.
- Material 3 UI, light and dark themes, accent colour, adjustable text size and
  bubble corners.
- English and Persian UI with RTL support and Jalali or Gregorian dates.
- Link and USSD detection with link previews.
- Apache License 2.0 and third-party notices.

### Known limitations

- MMS and RCS are not implemented.
- Group messaging is not supported.
- Delivery reports depend on carrier support.
- Link previews need an internet connection.
- Messages received before the app became the default SMS app may require a
  manual refresh.
