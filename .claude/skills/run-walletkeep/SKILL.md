---
name: run-walletkeep
description: Use when asked to build, install, launch, screenshot, or smoke-test the WalletKeep_demo Android app on the local emulator or a connected device — verified JDK 21 + Gradle 9.5 + AGP 9.3 workflow with domestic mirrors and Windows/Git Bash adb quirks.
---

# Run WalletKeep_demo

`WalletKeep_demo` is a native Android app (Java + XML views), not a web app.
"Running" means: build the APK, install it on an emulator/device, launch
`MainActivity`, and interact with it until the dashboard shows real data.

## Verified environment (this machine)

| Item | Value |
|---|---|
| JDK | `C:\Program Files\Java\jdk-21` |
| Android SDK | `C:\Users\qi\AppData\Local\Android\Sdk` (platform 37, build-tools 36) |
| ADB | `<SDK>\platform-tools\adb.exe` |
| AVDs | `Pixel_8` (API 37, x86_64), `Pixel_5` (API 37.2) |
| Toolchain | Gradle 9.5.0 wrapper, AGP 9.3.0, compileSdk 37 |

If paths differ, read `local.properties` for `sdk.dir` first.

## 1. Build

```bash
cd "<project root>"
export JAVA_HOME="C:/Program Files/Java/jdk-21"

# Windows Git Bash
./gradlew.bat --console=plain testDebugUnitTest assembleDebug
# macOS / Linux
./gradlew --console=plain testDebugUnitTest assembleDebug
```

Success criteria:

- `BUILD SUCCESSFUL`
- `app/build/outputs/apk/debug/app-debug.apk` exists
- Unit test XMLs under `app/build/test-results/testDebugUnitTest/` show `failures="0" errors="0"`

Do **not** downgrade the build back to AGP 3.6.3 / SDK 29 / JDK 8 — those
artifacts are not cached here and the wrapper download times out.

## 2. Start the emulator (if none attached)

```bash
export ANDROID_SDK_ROOT="C:/Users/qi/AppData/Local/Android/Sdk"
"$ANDROID_SDK_ROOT/emulator/emulator" -avd Pixel_8 -no-snapshot-save -no-boot-anim &
```

Wait for boot instead of sleeping blindly:

```bash
ADB="$ANDROID_SDK_ROOT/platform-tools/adb"
"$ADB" wait-for-device
while [ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" != "1" ]; do sleep 5; done
"$ADB" devices
```

If `adb devices` already lists a device, reuse it and skip to step 3.

## 3. Install and launch

```bash
"$ADB" install -r app/build/outputs/apk/debug/app-debug.apk
"$ADB" shell am start -n com.example.walletkeep_demo/.MainActivity
```

On Git Bash, prefix paths passed to `adb shell` with `MSYS_NO_PATHCONV=1`
or Git Bash rewrites `/sdcard/...` into a Windows path:

```bash
MSYS_NO_PATHCONV=1 "$ADB" shell screencap -p /sdcard/home.png
MSYS_NO_PATHCONV=1 "$ADB" pull /sdcard/home.png ./home.png
```

## 4. Drive it (do not stop at launch)

First launch shows a dialog with "暂不，空白开始" and "生成演示数据".
Tap "生成演示数据", then verify the dashboard:

- expense `¥277.50`, income `¥8715.60`, balance `¥8438.10`
- remaining budget `¥2722.50` with a green progress bar
- recent bill list renders `-¥38.50` (red) and `+¥8500.00` (green)

Then open "收支统计" and confirm the custom chart renders percentages
(购物 60.5%, 学习 21.3%, 餐饮 13.9%, 交通 4.3%).

To locate tap targets on an unknown resolution, prefer:

```bash
MSYS_NO_PATHCONV=1 "$ADB" shell uiautomator dump /sdcard/ui.xml
MSYS_NO_PATHCONV=1 "$ADB" shell cat /sdcard/ui.xml   # read bounds="[x1,y1][x2,y2]"
```

Pixel_8 (1080x2400) reference taps used during verification:

| Target | Tap (x y) |
|---|---|
| 生成演示数据 | `816 1410` |
| 收支统计 nav | `648 1104` |
| 预算备份 nav | `906 1104` |

Confirm the process is still alive after interaction:

```bash
MSYS_NO_PATHCONV=1 "$ADB" shell pidof com.example.walletkeep_demo
```

A blank frame, a crash, or `pidof` returning nothing is a failure — check
`adb logcat -d | grep -i "walletkeep\|FATAL"` before reporting success.

## 5. Cleanup

```bash
"$ADB" emu kill          # only if this skill started the emulator
rm -f home.png stats.png settings.png emulator.log
```

Leave an emulator the user started running, and say so in the report.

## Common failures (all observed on this machine)

| Symptom | Cause | Fix |
|---|---|---|
| `Read timed out` downloading `gradle-5.6.4-all.zip` | `services.gradle.org` unreachable | wrapper already points to `mirrors.cloud.tencent.com`; never revert to the official URL |
| `Could not resolve ... jcenter` | jcenter shut down | repositories use Aliyun mirrors + Google/Maven Central in `settings.gradle` |
| `android:exported needs to be explicitly specified` | targetSdk ≥ 31 with launcher intent-filter | `MainActivity` already declares `android:exported="true"`; keep it |
| `Error opening file: D:/Git/sdcard/...` | MSYS path conversion | use `MSYS_NO_PATHCONV=1` |
| `Unsupported class file major version` / AGP version errors | wrong JDK | set `JAVA_HOME` to jdk-21 before invoking Gradle |
| `Manifest merger failed` after moving files | stale manifest package | `namespace` lives in `app/build.gradle`; manifest must have no `package` attribute |
| `resource drawable/... not found` after moving a resource directory | stale incremental resource cache | run `./gradlew.bat clean` once, then rebuild |

## Verification checklist

- [ ] `testDebugUnitTest assembleDebug` succeeds (14 unit tests, 0 failures)
- [ ] If storage/DB logic changed: `connectedDebugAndroidTest` passes (6 isolated tests, includes v1 → v2 migration)
- [ ] APK installs with `Success`
- [ ] `MainActivity` visible (screenshot shows the blue dashboard card)
- [ ] demo data generates correct totals and list rows
- [ ] stats chart renders non-empty bars
- [ ] `pidof` shows the app process
