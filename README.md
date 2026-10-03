# needle2 — Local Android Automation

A lightweight, privacy-first Android automation assistant for Android 9+ and low-RAM phones.

## Features

- Needle 2 runs locally on-device for intent/tool selection.
- Reads the active Accessibility UI tree and can act on visible controls.
- Tap by text/content description or coordinates.
- Type text, scroll, swipe, Back, Home, launch installed apps and wait.
- Send local notifications.
- Schedule one-time and recurring automations.
- Schedule standard 5-field cron jobs.
- Restore scheduled jobs after reboot.
- ChatGPT-style Chat / Automations / Device dashboard.
- Chat and task state stored locally.
- arm64-v8a only to keep the APK focused on the target device class.
- No INTERNET permission.

## Target device

Primary target: OPPO A11k / CPH2083, Android 9 / API 28, 64-bit, 2 GB RAM.

## Local model

Needle 2 is used as the small on-device tool-calling model. The official Android ARM64 runtime and model are fetched at build time from the upstream release and are not committed to Git history.

## Permissions

The app uses:
- Accessibility service: screen/UI reading and UI actions.
- Notifications: local task-result notifications.
- Foreground service: scheduled automation execution.
- Boot completed: restore alarms after reboot.

The app deliberately does not declare INTERNET.

## Automation safety

Tool calls are restricted to the declared tools in \`app/src/main/assets/tools.json\`. Mutation tools require the user to enable "automation actions" from the Device tab.

The preferred UI loop is:
1. read_screen
2. inspect visible controls
3. act with click/type/scroll/swipe
4. repeat until the requested goal is reached

## Scheduling

Examples:
- \`in 20 minutes\`
- \`tomorrow at 08:00\`
- \`every 30 minutes\`
- \`every day at 09:00\`
- \`weekdays at 09:30\`
- \`0 8 * * *\`

## Build

\`\`\`bash
bash scripts/fetch-needle.sh
gradle :app:assembleRelease --no-daemon
\`\`\`

Output:

\`\`\`
app/build/outputs/apk/release/app-release.apk
\`\`\`

GitHub Actions builds the release APK on pushes to \`main\` and uploads it as \`needle2-android9-arm64\`.

## Size strategy

- Platform Android APIs only; no AndroidX or Compose.
- No networking stack.
- arm64-v8a only.
- R8 + resource shrinking for release.
- Static JNI runtime.
- Large model/runtime binaries are fetched during CI rather than stored as Git blobs.

## Privacy

No account, server, analytics SDK or network endpoint is included. Chat, task definitions and screen-derived data remain on the phone.

Needle 2 provenance and licensing are documented in \`third_party/NEEDLE-LICENSE.txt\`.
