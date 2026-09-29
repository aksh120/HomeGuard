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
- **Minimal Android Permissions**: HomeGuard requests only strictly needed permissions (e.g. Flashlight torch, Post Notifications). It explicitly avoids location, contacts reading, SMS, or microphone permissions.
- **Row-Level Security**: Remote database connections enforce user isolation via PostgreSQL RLS policies.
- **Local Data Protection**: Critical settings and event queues reside in app-private sandboxed storage.
