# Tasker SIM Bridge

Android helper for explicitly selecting mobile-data SIM 1 or SIM 2 through Shizuku. It is based on the documented implementation approach used by the Apache-2.0-licensed DataSimTile project, but adds deterministic slot selection and Tasker broadcast actions.

## What it does
- `SET_SIM_1`: selects the active subscription in physical SIM slot 1 (slot index 0).
- `SET_SIM_2`: selects the active subscription in physical SIM slot 2 (slot index 1).
- If the requested SIM is already selected, it reports that and does not toggle away.
- After changing the default data subscription, enables user mobile data for that subscription, matching AOSP Settings behavior.
- `GET_STATUS`: returns the current default data SIM in the app log/response broadcast.

## Install / first run
1. Build and install the APK (Android Studio: open this folder, wait for Gradle sync, Build > Build APK).
2. Start Shizuku and grant `Tasker SIM Bridge` its Shizuku permission from this app's first screen.
3. Test SIM 1 and SIM 2 buttons while the phone is unlocked.
4. Configure Tasker using the actions below.

## Tasker configuration
Use Tasker action **Send Intent**:
- Action: `hu.taskersimbridge.SET_SIM_1` or `hu.taskersimbridge.SET_SIM_2`
- Target: Broadcast Receiver
- Package: `hu.taskersimbridge`
- Class: `hu.taskersimbridge.SimCommandReceiver`
- Cat: leave empty
- Extra: leave empty

For status, use `hu.taskersimbridge.GET_STATUS`.

The helper also sends a broadcast `hu.taskersimbridge.RESULT` with string extra `result` when it completes. Tasker can optionally listen for this action using Event → System → Intent Received. On failure, the result starts with `HIBA:`; details are also written to Logcat under `TaskerSIMBridge`.

## Notes / limitations
- Android's internal telephony Binder APIs are private and can change across Android/One UI releases. This is designed around Android 16/API 36 and the API signatures used by DataSimTile; it still needs device testing on the target Galaxy S25 / One UI 8 build.
- Shizuku must be running and its permission must remain granted. On non-root Shizuku, after a reboot you generally need to start Shizuku again using Wireless debugging.
- The receiver is exported so Tasker can invoke it. Only install this helper from a source you trust; any app able to send these explicit broadcasts could request a SIM change.
- This source project has not been compiled or device-tested in this environment. Build it with Android Studio before installing; do not treat it as a verified APK.


## Phase 1: SIM 1 signal/network diagnostic
This revision adds a read-only SIM 1 diagnostic to the app:
- requests Android `READ_PHONE_STATE` permission;
- monitors the subscription in physical slot 1;
- displays signal level (Android 0–4) and network class (2G/3G/4G/4G+/5G);
- does not switch the data SIM automatically.

On Android 12+, `TelephonyCallback` is used with a `TelephonyManager` scoped to SIM slot 1. The network label uses `TelephonyDisplayInfo` so LTE carrier aggregation (4G+) and 5G display overrides can be distinguished where the modem exposes them.
