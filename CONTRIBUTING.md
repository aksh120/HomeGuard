# Contributing to HomeGuard

Thank you for your interest in contributing to HomeGuard! We are building an open, accessible, and ultra-reliable safety companion for people staying at home.

## Code of Conduct
Please review and adhere to our [Code of Conduct](CODE_OF_CONDUCT.md) in all project interactions.

## Core Design Principles for Contributors
1. **Safety First**: Features must enhance reliability. Do not add bloat or non-essential background services that risk battery or memory starvation.
2. **Strict Neutrality**: Never submit PRs containing personal names (e.g. Mom, Dad, specific individuals), country-specific emergency numbers, or family-specific assumptions.
3. **Privacy First**: We do not accept location tracking, GPS features, telemetry harvesting, or non-essential cloud syncing.
4. **100% Localized**: Never hardcode user-facing strings in Kotlin code. Always declare them in `strings.xml`.

## Submitting Pull Requests
1. Fork the repository and create your feature branch: `git checkout -b feat/your-feature-name`.
2. Follow Google/Android Kotlin coding conventions.
3. Ensure unit tests pass: `./gradlew test`.
4. Commit your changes with conventional commit prefixes: `feat:`, `fix:`, `perf:`, `refactor:`, `test:`, `docs:`.
5. Open a Pull Request with a clear description of the problem solved.
