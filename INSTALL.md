# Test Forge BPMN in IntelliJ

Plugin id: `com.forge.bpmn`  
Needs IntelliJ IDEA 2024.3+ and JDK 17.

## A. Sandbox IDE (fastest)

Does **not** install into your daily IntelliJ. It starts a second IDE with the plugin loaded.

1. Unzip the project. In IntelliJ: **File → Open** the folder that contains `build.gradle.kts` (not only `src`).
2. Trust the Gradle project. Wait until indexing finishes.
3. Run configuration **Run Plugin**, or Gradle task `runIde`.
4. In the new IDE window, create or open a `.bpmn` file.
5. Use the **BPMN** / **XML** buttons under the editor to switch views.

## B. Install into your own IntelliJ

This loads the plugin in the IDE you use every day.

1. Build a distribution zip:
   - Locally: Gradle task `buildPlugin`
   - Or GitHub → Actions → **Build** → download artifact `plugin-distribution`
2. In IntelliJ: **Settings / Preferences → Plugins**.
3. Click the gear → **Install Plugin from Disk…**
4. Choose `build/distributions/*.zip` (the plugin zip, not this source zip).
5. Restart the IDE.
6. Open a `.bpmn` file. Bottom of the editor: **BPMN** and **XML**.

To uninstall: Settings → Plugins → Forge BPMN → Uninstall.

## Missing diagram assets?

If the canvas is empty after a Git clone, run:

```
bash scripts/fetch-modeler-assets.sh
```

The downloadable source ZIP already includes those files.
