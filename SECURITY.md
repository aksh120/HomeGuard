# Security Policy

## Supported Versions

| Version | Supported          |
| ------- | ------------------ |
| 1.0.x   | :white_check_mark: |

## Reporting a Vulnerability

HomeGuard is safety-critical software. We treat security vulnerabilities with the highest priority.

If you believe you have discovered a security issue, please **DO NOT** create a public GitHub issue.

Instead:
1. Provide a detailed summary including reproduction steps, affected Android versions, and impact analysis.
2. Submit your report privately to the repository maintainers via GitHub Security Advisories or maintainer contact.
3. Allow reasonable time for investigation and patch deployment before any public disclosure.

## Security Practices in HomeGuard
- **No Hardcoded Secrets**: Secrets and API keys must never be committed.
- **Minimal Android Permissions**: HomeGuard requests only what it needs. `CAMERA` (torch flash), `RECORD_AUDIO` (optional acoustic smoke-alarm detection), and `POST_NOTIFICATIONS`. It explicitly avoids location, contacts, and SMS permissions. Both the microphone and the camera are **opt-in**: neither permission is requested at launch or on resume — only when the user turns on the corresponding feature.
- **Row-Level Security**: Every remote table is owned by an `auth.users` row, and every policy is scoped with `auth.uid() = user_id`. Each install bootstraps its own Supabase anonymous session, so one device can never read or write another device's records, even with the public anon key.
- **No location tracking**: The app requests no location permission of any kind and contains no location code.
- **Local Data Protection**: Critical settings and event queues reside in app-private sandboxed storage.
