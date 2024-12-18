package dev.sandipchitale.pluginexplorer;

import com.intellij.openapi.actionSystem.AnAction;

public abstract class PluginsExplorerAbstractAction extends AnAction {
    protected PluginsExplorerToolWindow pluginsExplorerToolWindow;

    void setPluginsExplorerToolWindow(PluginsExplorerToolWindow pluginsExplorerToolWindow) {
        this.pluginsExplorerToolWindow = pluginsExplorerToolWindow;
    }
}
