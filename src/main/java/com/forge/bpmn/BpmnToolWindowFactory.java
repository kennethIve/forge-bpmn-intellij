package com.forge.bpmn;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

/**
 * Java factory on purpose. Kotlin 2.2+ emits JVM default-method bridges for every
 * {@link ToolWindowFactory} default, including deprecated {@code isApplicable} /
 * {@code isDoNotActivateOnStart} and experimental {@code getAnchor} / {@code getIcon} /
 * {@code manage}. Plugin Verifier reports those bridges as plugin API usage.
 * <p>
 * Anchor, icon, and doNotActivateOnStart are declared in {@code plugin.xml}.
 * Only the stable {@link #init} and {@link #createToolWindowContent} methods are implemented.
 */
public final class BpmnToolWindowFactory implements ToolWindowFactory, DumbAware {
    @Override
    public void init(@NotNull ToolWindow toolWindow) {
        toolWindow.setToHideOnEmptyContent(false);
        toolWindow.setAvailable(true);
    }

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        BpmnExplorer explorer = new BpmnExplorer(project, toolWindow.getDisposable());
        Content content = ContentFactory.getInstance().createContent(explorer, "", false);
        content.setCloseable(false);
        toolWindow.getContentManager().addContent(content);
        toolWindow.setAvailable(true);
    }
}
