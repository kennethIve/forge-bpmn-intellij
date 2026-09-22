# JetBrains Marketplace + GitHub Actions

Plugin id: `com.forge.bpmn`  
Version: `0.4.3`

## 1. Push to GitHub

Create a repository and push this project root (the folder that contains `build.gradle.kts`).

This ZIP already includes:

- `.github/workflows/build.yml` — build on push and pull request
- `.github/workflows/publish.yml` — publish on GitHub Release (and manual **Run workflow**)

## 2. JetBrains Marketplace token

1. Sign in at [JetBrains Marketplace](https://plugins.jetbrains.com/).
2. Open [My Tokens](https://plugins.jetbrains.com/author/me/tokens) and create a token with upload permission.
3. In the GitHub repo: **Settings → Secrets and variables → Actions** → New repository secret:
   - `INTELLIJ_PERM_TOKEN` = the Marketplace token

## 3. Plugin signing keys (required)

Marketplace rejects unsigned uploads. Generate keys once:

```
openssl genpkey -aes-256-cbc -algorithm RSA -out private_encrypted.pem -pkeyopt rsa_keygen_bits:4096
openssl rsa -in private_encrypted.pem -out private.pem
openssl req -key private.pem -new -x509 -days 3650 -out chain.crt
```

Add three more GitHub secrets (paste **file contents**, not paths):

| Secret | Value |
| --- | --- |
| `CERTIFICATE_CHAIN` | signing certificate chain (PEM text) |
| `PRIVATE_KEY` | signing private key (PEM text) |
| `PRIVATE_KEY_PASSWORD` | key passphrase (if any) |

Keep `private.pem` off git. See [Plugin Signing](https://plugins.jetbrains.com/docs/intellij/plugin-signing.html).

## 4. First upload is manual

JetBrains requires the **first** version to be uploaded in the browser:

1. `gradle buildPlugin`
2. Take `build/distributions/Forge BPMN-*.zip` (or the zip Gradle wrote)
3. Upload at [Submit a plugin](https://plugins.jetbrains.com/plugin/add)
4. Plugin id must be exactly `com.forge.bpmn`

Wait until Marketplace approves that listing.

## 5. Later versions via GitHub Release

1. Bump `pluginVersion` in `gradle.properties` (Marketplace rejects duplicate versions).
2. GitHub → **Releases** → **Draft a new release**
3. Tag `v0.4.0` (or the new version) and publish
4. Workflow **Publish to JetBrains Marketplace** runs `gradle publishPlugin`
5. Optional: **Actions → Publish to JetBrains Marketplace → Run workflow**

A `-eap` / `-alpha` / `-beta` suffix in the version publishes to that channel instead of `default`.

## Secrets checklist

- [ ] `INTELLIJ_PERM_TOKEN`
- [ ] `CERTIFICATE_CHAIN`
- [ ] `PRIVATE_KEY`
- [ ] `PRIVATE_KEY_PASSWORD`
