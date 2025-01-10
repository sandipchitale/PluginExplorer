package dev.sandipchitale.pluginexplorer;

import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

public class CheckpointDownloadsAction extends PluginsExplorerAbstractAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        pluginsExplorerToolWindow.checkpointDownloads();
    }
}
