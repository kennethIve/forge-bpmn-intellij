# Test Forge BPMN in IntelliJ

Plugin id: `com.forge.bpmn`  
Needs IntelliJ IDEA 2024.3 or later (2025 / 2026 included).

**Do not install the source ZIP** (the one with `build.gradle.kts`). That is a Gradle project, not a plugin.

**Do not install a GitHub Actions artifact download as-is.** The website wraps the plugin in a second zip.

## Install from GitHub Releases (your daily IntelliJ)

1. Download `forge-bpmn-*.zip` from [Releases](https://github.com/kennethIve/forge-bpmn-intellij/releases) — the file that contains a `lib/` folder, not the source tree.
2. IntelliJ → **Settings → Plugins** → gear → **Install Plugin from Disk…**
3. Pick that zip. Restart the IDE.
4. Open a `.bpmn` file. Bottom of the editor: **BPMN** and **XML**.

If IntelliJ says the plugin is incompatible, you grabbed an old build capped at 2025.3. Use 0.3.1 or later.

## Sandbox IDE (does not touch your daily install)

1. Clone or unzip the **source** project. **File → Open** the folder with `build.gradle.kts`.
2. Trust Gradle. Run **Run Plugin**.
3. In the new IDE window, open a `.bpmn` file.

## Missing diagram assets after a git clone?

```
bash scripts/fetch-modeler-assets.sh
```
