# AndroidAlarm
Simple android alarm app
# Build
    JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew build
It must have Bulgarian translation.

## Build & deploy scripts

Helper scripts live in [`scripts/`](scripts/). They pick a compatible JDK
(17–21) automatically without touching your global `JAVA_HOME`, and the deploy
scripts **auto-discover the connected phone** via `adb` — no serial needed for a
single device.

| Script | What it does | Output |
| --- | --- | --- |
| `scripts/build-debug.sh` | Builds the debug APK | `app/build/outputs/apk/debug/app-debug.apk` |
| `scripts/build-release.sh` | Builds the production (release) APK and signs it (see signing note) | `app/build/outputs/apk/release/app-release.apk` |
| `scripts/deploy-debug.sh` | Builds (unless `SKIP_BUILD=1`), installs the debug APK on the phone, and launches it | — |
| `scripts/deploy-release.sh` | Builds (unless `SKIP_BUILD=1`), installs the release APK on the phone, and launches it | — |

### Usage

```bash
# Build only
./scripts/build-debug.sh
./scripts/build-release.sh

# Build + install + launch on the connected phone
./scripts/deploy-debug.sh
./scripts/deploy-release.sh

# Install an already-built APK without rebuilding
SKIP_BUILD=1 ./scripts/deploy-debug.sh
```

**Connecting a phone:** plug it in over USB, enable *Developer options → USB
debugging*, and accept the authorization prompt on the phone. Verify it is seen
with `adb devices`. If **several** devices/emulators are attached, pass the
target serial as the first argument or set `ANDROID_SERIAL`:

```bash
./scripts/deploy-debug.sh <serial>
ANDROID_SERIAL=<serial> ./scripts/deploy-release.sh
```

If no phone is found, the deploy scripts stop with a clear message instead of
failing obscurely.

### Signing the release APK

The project has no release signing config, so Gradle emits an *unsigned* release
APK that cannot be installed. `build-release.sh` therefore signs it:

- **Default:** signs with the Android **debug keystore** — fine for testing on
  your own phone, **not** for Google Play or sharing with others.
- **Real release key:** point it at your keystore via environment variables:

  ```bash
  RELEASE_KEYSTORE=/path/to/my-release.jks \
  RELEASE_KEY_ALIAS=my-alias \
  RELEASE_STORE_PASSWORD=secret \
  ./scripts/build-release.sh
  ```

Installing a build signed with a *different* key over an existing one is
rejected by Android; rerun the release deploy with `REINSTALL=1` to uninstall
the old copy first (this wipes the app's data):

```bash
REINSTALL=1 ./scripts/deploy-release.sh
```


The main screen shall look like this:

