# Releasing WishTube

This document describes how to generate a release signing key, configure the build system, compile signed release APKs, compute checksums, and publish a release on GitHub.

---

## 1. Creating a Release Keystore

> **CRITICAL WARNING:** Back up your release keystore file and credentials securely. If you lose your keystore or change the signing key, users will not be able to upgrade existing WishTube installations without uninstalling first and losing local data.

To generate a new upload/release keystore using JDK's `keytool`:

```bash
keytool -genkey -v -keystore wishtube-release.keystore \
    -alias wishtube -keyalg RSA -keysize 2048 -validity 10000
```

Store the resulting `wishtube-release.keystore` file in a secure location. **NEVER commit `*.keystore` or `*.jks` files to git.**

---

## 2. Configuring Release Signing Credentials

You can supply signing credentials using either `local.properties` or environment variables.

### Option A: `local.properties` (Local Development)

Add the following properties to `local.properties` in the project root:

```properties
storeFile=/path/to/wishtube-release.keystore
storePassword=YOUR_STORE_PASSWORD
keyAlias=wishtube
keyPassword=YOUR_KEY_PASSWORD
```

### Option B: Environment Variables (CI / CD)

Export the following environment variables:

```bash
export WISHTUBE_STORE_FILE="/path/to/wishtube-release.keystore"
export WISHTUBE_STORE_PASSWORD="YOUR_STORE_PASSWORD"
export WISHTUBE_KEY_ALIAS="wishtube"
export WISHTUBE_KEY_PASSWORD="YOUR_KEY_PASSWORD"
```

If these properties or environment variables are not supplied, Gradle will print a warning and produce **unsigned** release APKs.

---

## 3. Building Signed Release APKs

To compile signed release APKs:

```bash
./gradlew assembleRelease
```

The output APKs will be generated in `app/build/outputs/apk/release/`:
- `app-arm64-v8a-release.apk`
- `app-armeabi-v7a-release.apk`
- `app-x86_64-release.apk`
- `app-universal-release.apk`

---

## 4. Computing SHA-256 Checksums

Before publishing, compute SHA-256 checksums for each release artifact:

```bash
cd app/build/outputs/apk/release/
sha256sum *.apk > SHA256SUMS.txt
```

---

## 5. Publishing on GitHub Releases

1. Create a git tag for the release version:
   ```bash
   git tag -a v0.1.0 -m "WishTube release v0.1.0"
   git push origin v0.1.0
   ```
2. Navigate to your repository on GitHub (`wishissue/wishtube`).
3. Click **Releases** > **Draft a new release**.
4. Select the tag `v0.1.0`.
5. Attach the generated APKs and `SHA256SUMS.txt`.
6. Publish the release.

---

## 6. GitHub Actions Automated Releases

To enable automated builds and releases via `.github/workflows/release.yml`, configure the following repository secrets under **Settings > Secrets and variables > Actions**:
- `WISHTUBE_KEYSTORE_BASE64`: Base64 encoded release keystore (`base64 -w 0 wishtube-release.keystore`)
- `WISHTUBE_STORE_PASSWORD`: Keystore password
- `WISHTUBE_KEY_ALIAS`: Key alias (`wishtube`)
- `WISHTUBE_KEY_PASSWORD`: Key password
