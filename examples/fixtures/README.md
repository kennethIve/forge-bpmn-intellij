# Lint / Problems test fixtures

QA fixtures for Forge BPMN Problems panel + node corner markers (0.4.5+).

| File | Expected lint signal (approx.) |
|------|--------------------------------|
| `empty-feel.bpmn` | Empty condition expression on `Flow_EmptyCond` |
| `dangling-connection.bpmn` | ERROR on `Flow_Broken` / `Flow_Nowhere`: `Sequence flow is missing source or target.` (`Missing_Element_XYZ`, `Missing_Source_ABC`) |
| `missing-start-end.bpmn` | Missing start event; Missing end event |

Open via BPMN Explorer under `examples/fixtures/`. Do not use as happy-path Marketplace demos.
