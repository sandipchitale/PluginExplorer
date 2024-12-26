package dev.sandipchitale.pluginexplorer;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import org.jetbrains.annotations.NotNull;

public class GotoPluginsAction extends PluginsExplorerAbstractAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent actionEvent) {
        ActionManager actionManager = ActionManager.getInstance();
        AnAction pluginsAction = actionManager.getAction("WelcomeScreen.Plugins");
        if (pluginsAction != null) {
            actionManager.tryToExecute(
                    pluginsAction,
                    actionEvent.getInputEvent(),
                    actionEvent.getDataContext().getData(PlatformDataKeys.CONTEXT_COMPONENT),
                    actionEvent.getPlace(),
                    true
            );
        }
    }
}
