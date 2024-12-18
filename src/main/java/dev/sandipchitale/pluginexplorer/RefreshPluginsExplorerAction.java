package dev.sandipchitale.pluginexplorer;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

public class RefreshPluginsExplorerAction extends PluginsExplorerAbstractAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        if (pluginsExplorerToolWindow != null) {
            pluginsExplorerToolWindow.refresh();
        }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabledAndVisible(true);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
    }
}