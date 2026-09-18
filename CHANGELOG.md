# Changelog

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
