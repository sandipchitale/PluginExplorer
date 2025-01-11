package dev.sandipchitale.pluginexplorer;

import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlToken;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;

public class ShowInShowInIntelliJPlatformExplorerAction extends AnAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();

        if (project != null) {
            Editor editor = e.getData(CommonDataKeys.EDITOR);
            if (editor != null) {
                VirtualFile virtualFile = editor.getVirtualFile();
                if (virtualFile != null && virtualFile.getUrl().startsWith(JarFileSystem.PROTOCOL_PREFIX) && virtualFile.getFileType().equals(XmlFileType.INSTANCE)) {
                    PsiFile psiFile = PsiManager.getInstance(project).findFile(virtualFile);
                    if (psiFile != null) {
                        int offset = editor.getCaretModel().getCurrentCaret().getOffset();
                        PsiElement psiElement = psiFile.findElementAt(offset);
                        PsiElement extensionPointPsiElement = PsiTreeUtil.findFirstParent(psiElement, (PsiElement parentPsiElement) -> {
                            return parentPsiElement instanceof XmlTag xmlTag && xmlTag.getName().equals("extensionPoint");
                        });
                        if (extensionPointPsiElement != null) {
                            XmlAttribute @Nullable [] xmlAttributes = PsiTreeUtil.getChildrenOfType(extensionPointPsiElement, XmlAttribute.class);
                            if (xmlAttributes != null) {
                                Arrays.stream(xmlAttributes).forEach((XmlAttribute xmlAttribute) -> {
                                    if (xmlAttribute.getName().equals("qualifiedName")) {
                                        try {
                                            Desktop.getDesktop().browse(new URI(String.format("https://plugins.jetbrains.com/intellij-platform-explorer/extensions?extensions=%s&pluginId=0", xmlAttribute.getValue())));
                                        } catch (IOException | URISyntaxException ignore) {
                                        }
                                    } else if (xmlAttribute.getName().equals("name")) {
                                        IdeaPluginDescriptor ideaPluginDescriptor = (IdeaPluginDescriptor) ((JComponent) editor.getComponent().getParent()).getClientProperty(PluginsExplorerToolWindow.class.getSimpleName() + ".IdeaPluginDescriptor");
                                        try {
                                            Desktop.getDesktop().browse(new URI(String.format("https://plugins.jetbrains.com/intellij-platform-explorer/extensions?extensions=%s&pluginId=0", ideaPluginDescriptor.getPluginId().getIdString() + "." + xmlAttribute.getValue())));
                                        } catch (IOException | URISyntaxException ignore) {
                                        }
                                    }
                                });
                            }
                        } else {
                            PsiElement extensionsPsiElement = PsiTreeUtil.findFirstParent(psiElement, true, (PsiElement parentPsiElement) -> {
                                return parentPsiElement instanceof XmlTag xmlTag && xmlTag.getName().equals("extensions");
                            });
                            if (extensionsPsiElement != null) {
                                PsiElement extensionPsiElement = PsiTreeUtil.findFirstParent(psiElement, true, (PsiElement parentPsiElement) -> {
                                    return parentPsiElement instanceof XmlTag;
                                });
                                if (extensionPsiElement != null) {
                                    if (PsiTreeUtil.isAncestor(extensionsPsiElement, extensionPsiElement, true)) {
                                        String extensionName = "com.intellij." + ((XmlTag) extensionPsiElement).getName();
                                        try {
                                            Desktop.getDesktop().browse(new URI(String.format("https://plugins.jetbrains.com/intellij-platform-explorer/extensions?extensions=%s&pluginId=0", extensionName)));
                                        } catch (IOException | URISyntaxException ignore) {
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        boolean enabledAndVisible = false;

        Project project = e.getProject();

        if (project != null) {
            Editor editor = e.getData(CommonDataKeys.EDITOR);
            if (editor != null) {
                VirtualFile virtualFile = editor.getVirtualFile();
                if (virtualFile != null && virtualFile.getUrl().startsWith(JarFileSystem.PROTOCOL_PREFIX) && virtualFile.getFileType().equals(XmlFileType.INSTANCE)) {
                    PsiFile psiFile = PsiManager.getInstance(project).findFile(virtualFile);
                    if (psiFile != null) {
                        int offset = editor.getCaretModel().getCurrentCaret().getOffset();
                        PsiElement psiElement = psiFile.findElementAt(offset);
                        PsiElement extensionPointPsiElement = PsiTreeUtil.findFirstParent(psiElement, (PsiElement parentPsiElement) -> {
                            return parentPsiElement instanceof XmlTag xmlTag && xmlTag.getName().equals("extensionPoint");
                        });
                        if (extensionPointPsiElement != null) {
                            enabledAndVisible = true;
                        } else {
                            PsiElement extensionsPsiElement = PsiTreeUtil.findFirstParent(psiElement, true, (PsiElement parentPsiElement) -> {
                                return parentPsiElement instanceof XmlTag xmlTag && xmlTag.getName().equals("extensions");
                            });
                            if (extensionsPsiElement != null) {
                                PsiElement extensionPsiElement = PsiTreeUtil.findFirstParent(psiElement, true, (PsiElement parentPsiElement) -> {
                                    return parentPsiElement instanceof XmlTag;
                                });
                                if (extensionPsiElement != null) {
                                    if (PsiTreeUtil.isAncestor(extensionsPsiElement, extensionPsiElement, true)) {
                                        enabledAndVisible = true;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        e.getPresentation().setEnabledAndVisible(enabledAndVisible);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
    }
}
