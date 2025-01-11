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
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlToken;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

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
                        if (psiElement instanceof XmlToken xmlToken) {
                            IElementType tokenType = xmlToken.getTokenType();
                            if ("XML_ATTRIBUTE_VALUE_TOKEN".equals(tokenType.toString())) {
                                PsiElement parent = xmlToken.getParent();
                                if (parent != null) {
                                    PsiElement grandParent = parent.getParent();
                                    if (grandParent instanceof XmlAttribute xmlAttribute) {
                                        String attributeName = xmlAttribute.getName();
                                        if ("qualifiedName".equals(attributeName)) {
                                            try {
                                                Desktop.getDesktop().browse(new URI(String.format("https://plugins.jetbrains.com/intellij-platform-explorer/extensions?extensions=%s&pluginId=0", psiElement.getText())));
                                            } catch (IOException | URISyntaxException ignore) {
                                            }
                                        } else if ("name".equals(attributeName)) {
                                            IdeaPluginDescriptor ideaPluginDescriptor = (IdeaPluginDescriptor) ((JComponent) editor.getComponent().getParent()).getClientProperty(PluginsExplorerToolWindow.class.getSimpleName() + ".IdeaPluginDescriptor");
                                            // TODO check if in extensionPoint tag
                                            try {
                                                Desktop.getDesktop().browse(new URI(String.format("https://plugins.jetbrains.com/intellij-platform-explorer/extensions?extensions=%s&pluginId=0", ideaPluginDescriptor.getPluginId().getIdString() + "." + psiElement.getText())));
                                            } catch (IOException | URISyntaxException ignore) {
                                            }
                                        }
                                    }
                                }
                            } else if ("XML_NAME".equals(tokenType.toString()) && "<".equals(xmlToken.getPrevSibling().getText())) {
                                PsiElement parent = xmlToken.getParent();
                                if (parent != null) {
                                    PsiElement grandParent = parent.getParent();
                                    if (grandParent instanceof XmlTag xmlTag) {
                                        if ("extensions".equals(xmlTag.getName())) {
                                            String extensionName = "com.intellij." + xmlToken.getText();
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
                        if (psiElement instanceof XmlToken xmlToken) {
                            IElementType tokenType = xmlToken.getTokenType();
                            if ("XML_ATTRIBUTE_VALUE_TOKEN".equals(tokenType.toString())) {
                                PsiElement parent = xmlToken.getParent();
                                if (parent != null) {
                                    PsiElement grandParent = parent.getParent();
                                    if (grandParent instanceof XmlAttribute xmlAttribute) {
                                        String attributeName = xmlAttribute.getName();
                                        if ("qualifiedName".equals(attributeName)) {
                                            enabledAndVisible = true;
                                        } else if ("name".equals(attributeName)) {
                                            // TODO check if in extensionPoint tag
                                            enabledAndVisible = true;
                                        }
                                    }
                                }
                            } else if ("XML_NAME".equals(tokenType.toString()) && "<".equals(xmlToken.getPrevSibling().getText())) {
                                PsiElement parent = xmlToken.getParent();
                                if (parent != null) {
                                    PsiElement grandParent = parent.getParent();
                                    if (grandParent instanceof XmlTag xmlTag) {
                                        if ("extensions".equals(xmlTag.getName())) {
                                            enabledAndVisible = true;
                                        }
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
