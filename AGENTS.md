# Coding-agent instructions for needle2

Maintain a genuinely local Android 9 automation assistant for low-RAM devices.

- Do not add INTERNET permission unless explicitly requested.
- Keep release arm64-v8a only.
- Prefer Android platform APIs over third-party runtime dependencies.
- Keep inference and automation off the main thread.
- Accessibility failures must be graceful.
- Scheduled tasks must survive process death and reboot.
- Do not log prompts, screen contents, or task payloads.
- Keep Android 9 compatibility.
- Keep app code small.

Build:
bash scripts/fetch-needle.sh
gradle :app:assembleRelease --no-daemon
