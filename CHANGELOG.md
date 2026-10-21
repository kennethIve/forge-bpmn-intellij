# Changelog

## [0.4.5]

- Diagram ↔ XML selection sync: canvas select moves XML caret to the element `id` and paints solid `#3574F0` + white text (no transparency / no underline stack); XML caret on `id` selects on canvas; selection outline + bendpoints use solid `#3574F0` / 2px in Light and Dark
- Fix Problems row click after XML navigate: drop the “focus must be in Problems tree” gate — mark mouse-presses on the tree via `AWTEventListener` so a single click still moves the caret when focus is on XML; keep `ReadAction` wrappers and force-caret on `openXmlAtElementId`
- Fix consecutive Problems clicks on dangling (no-shape) flows: always force XML caret to the new element `id` even when the XML tab is already focused; wrap PSI reads (`syncSelectionFromCaret` / `resolveElementId` / `findIdAttributeOffset`) in `ReadAction` to stop the IDE error toast
- No-shape fallback: Problems / Select on diagram / navigator — if the `elementId` has no bpmn-js shape (e.g. dangling `Flow_Broken` / `Flow_Nowhere`), switch to the **XML** tab and put the caret on that element’s `id` (no fake bendpoints); shapes still select on the BPMN canvas
- Stop fixture wipe on open: diagram import / post-import repair no longer autosaves a cleaned model back to disk — only real user edits (`commandStack` after import) call `__onXmlChange`; `examples/fixtures/*.bpmn` survive open → BPMN/XML switch → close with no edits
- Lint findings appear in the IDEA **Problems** tool window via `BpmnAnnotator` (works without a module / fixture opens; full-sentence messages; error vs warning). `BpmnInspection` kept in sources but unregistered to avoid duplicate rows
- Problems / intention **Select on diagram** opens the BPMN tab and selects the element (`window.__selectElement` JS bridge)
- Problems **single-click** selects on diagram (Problems tree selection hook); F4 / double-click / Autoscroll still sync via XML caret on `id`
- Sequence-flow lint: blank **or** unresolved `sourceRef`/`targetRef` (dangling ids) → error `Sequence flow is missing source or target.`
- Canvas lint corner badges: error `#E5484D` / warn `#EF9F27`, 6px dots (top-right); click badge selects the element; process-level ERROR badges fall back to the first shape child so red shows on fixtures like missing-start-end
- Dirty / external file change: dialog title **File changed on disk**, primary **Reload**, secondary **Keep mine**
- Replace deprecated `FileEditorManager.openFile(VirtualFile, boolean)` with `OpenFileDescriptor.navigate` (Plugin Verifier)
- Extract `feel-focus-fix.css` into the IDE temp assets (was @imported but never copied)
- FEEL CodeMirror: padding-right so long first lines clear the pop-out icon
- Marketplace description: keep UTF-8 HTML from plugin.xml (no gradle.properties dash mojibake)
- Site copy: IntelliJ IDEA 2025.3+ / Java 21 (match README/gradle)
- Diagram Ctrl+Z / Ctrl+Y (and Cmd): undo/redo via bpmn-js commandStack when focus is on the canvas; text/FEEL fields keep their own undo; skip no-op XML reload that wiped history
- Undo/Redo hardened: persist XML after undo/redo even when the command stack is empty (undo-to-pristine no longer leaves disk ahead of the canvas); post-import stack clear skips if the user already edited; flush pending canvas XML when leaving the BPMN tab; FEEL pop-out treated as editable so Ctrl+Z does not steal diagram undo
- FEEL/condition Ctrl+Z/Y: after CodeMirror text history, flush the properties-panel debounce into the model and `__flushPersistNow` so disk matches the undone/redone UI (tab leave also flushes pending FEEL debounce first)


## [0.4.4]

- FEEL / condition expression editor: visible caret (theme-aware), selection tint, active-line tint, accent focus ring; High Contrast theme flag for caret
- FEEL dark/HC: native caret-color on .cm-content; readable token colors; gutter matches input chrome
- Properties panel: drop doubled FEEL/input focus outline (keep single 1px accent border)
- Context pad on connections opens near the click (or longest-segment midpoint), stays anchored through zoom/scroll

## [0.4.3]

- Plugin Verifier: optional `com.intellij.modules.jcef` now declares `config-file="forge-bpmn-jcef.xml"`
- Extra descriptor is empty on purpose — diagram editor stays in the main plugin.xml so 2025.3.0 Community still opens the canvas

## [0.4.2]

- Install on IntelliJ IDEA 2025.3 Community and later (since-build 253, Java 21)
- Plugin Verifier: Java `ToolWindowFactory` so Kotlin 2.2+ default-method bridges are not reported
- Stripe icon, anchor, and doNotActivateOnStart stay declarative in plugin.xml
- VFS/document writes use `WriteAction.compute` / `WriteAction.run`
- Explorer background scan uses `AppExecutorUtil`; UI refresh is cancelled with `project.disposed`
- Element names are plain Camunda text (no filled label chip)

## [0.4.1]

- Install on IntelliJ IDEA 2025.3 Community: JCEF is an optional dependency (`com.intellij.modules.jcef` is not a plugin on 2025.3)
- since-build 253, compile against 2025.3 / Java 21
- Document listener uses a parent Disposable (no deprecated addDocumentListener overload)

## [0.4.0]

- Develop against Java 25 / IntelliJ IDEA 2026.2 (since-build 262)
- 16x16 file icon (tab badge no longer clips)
- Diagram canvas fills the JCEF editor and refits on resize
- Camunda Modeler-style BPMN editor with properties panel
- Native BPMN / XML editor tabs
- Basic BPMN syntax lint (XML, start/end events, disconnected nodes, Camunda 8 job types)
- GitHub Actions publish to JetBrains Marketplace
