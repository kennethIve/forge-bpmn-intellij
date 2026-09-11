# Local development (Forge BPMN)

Plugin id: `com.forge.bpmn`  
IntelliJ IDEA 2024.3+ (2026 included). JDK 17+.

Development happens on the **`dev`** branch. GitHub Actions **does not** build `dev` — only `main`.

## Fastest loop (no zip, no Install from Disk)

This starts a **second IntelliJ** with the plugin already loaded. Your daily IDE is untouched.

1. Clone and check out `dev`:

```
git clone https://github.com/kennethIve/forge-bpmn-intellij.git
cd forge-bpmn-intellij
git checkout dev
```

2. IntelliJ: **File → Open** the folder that contains `build.gradle.kts` (project root).
3. Trust the Gradle project. Wait until indexing finishes (first time downloads the IntelliJ SDK).
4. Top-right run configuration **Run Plugin** → green play (or Gradle task `runIde`).
5. In the **new** IDE window, open `examples/refund-request.bpmn`.

After you change Kotlin/HTML, click **Run Plugin** again. That is the whole loop.

If the canvas is empty on first run, Gradle will fetch modeler assets automatically (needs Node once). Or run:

```
bash scripts/fetch-modeler-assets.sh
```

## Optional: install into your daily IntelliJ

Only if you want the plugin in the same IDE you work in (not needed for development):

```
bash scripts/fetch-modeler-assets.sh
# Gradle tool window → buildPlugin
```

Then Settings → Plugins → gear → **Install Plugin from Disk** → `build/distributions/forge-bpmn-*.zip`.

## GitHub Releases zip

Use this only to test a published build: [Releases](https://github.com/kennethIve/forge-bpmn-intellij/releases).
