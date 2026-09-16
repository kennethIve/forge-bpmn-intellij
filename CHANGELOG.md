# Changelog

## [0.4.1]

- Migrate deprecated/obsolete IntelliJ platform API usages for Marketplace compatibility
- Use `WriteAction.run` / `WriteAction.compute` instead of `Application.runWriteAction`
- Register document listeners with a `Disposable` parent
- Replace `UIUtil` panel/help colors with `JBColor`
- Drop redundant ToolWindow stripe title/button overrides (plugin.xml already defines them)

## [0.4.0]

- Develop against Java 25 / IntelliJ IDEA 2026.2 (since-build 262)
- 16x16 file icon (tab badge no longer clips)
- Diagram canvas fills the JCEF editor and refits on resize
- Camunda Modeler-style BPMN editor with properties panel
- Native BPMN / XML editor tabs
- Basic BPMN syntax lint (XML, start/end events, disconnected nodes, Camunda 8 job types)
- GitHub Actions publish to JetBrains Marketplace
