# Security policy

## Supported versions

Security fixes are applied to the latest release.

## Reporting a vulnerability

Please **do not** open a public issue for a security problem.

Use GitHub's private vulnerability reporting:

1. Go to the repository's **Security** tab.
2. Choose **Report a vulnerability**.
3. Describe the issue, the affected version, and the steps to reproduce it.

If private reporting is unavailable, contact the maintainer through the GitHub
profile linked in the repository.

You can expect an acknowledgement within a few days. Please give the maintainer
time to prepare a fix before disclosing the issue publicly.

## Scope

Messages handles SMS content, contacts, and local app locks. Reports about the
following are especially useful:

- Bypassing a conversation lock or the app lock
- Leaking message content through notifications, previews, or backups
- Sending SMS to the wrong recipient or SIM
- Exposing local data to other apps through exports, intents, or file providers
- Unsafe handling of links or USSD codes

## Out of scope

- Issues that require a rooted device or a compromised operating system
- Spam or phishing sent by third parties
- Carrier-side delivery report behaviour
- MMS/RCS, which are not implemented
