package dev.sandipchitale.pluginexplorer;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandlerBase;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlToken;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;

public class PluginXmlGotoDeclarationHandler extends GotoDeclarationHandlerBase {

    private static final List<String> potentialTypeNames = List.of(
            "builderClass",
            "class",
            "className",
            "factoryClass",
            "hostElementClass",
            "interface",
            "implementation",
            "implementationClass",
            "referenceClass",
            "serviceInterface",
            "serviceImplementation"
    );

    @Override
    public @Nullable PsiElement getGotoDeclarationTarget(@Nullable PsiElement sourceElement, Editor editor) {
        if (sourceElement != null) {
            PsiFile containingFile = sourceElement.getContainingFile();
            if (containingFile != null && "plugin.xml".equals(containingFile.getName())) {
                if (sourceElement instanceof XmlToken xmlToken) {
                    IElementType tokenType = xmlToken.getTokenType();
                    if ("XML_ATTRIBUTE_VALUE_TOKEN".equals(tokenType.toString())) {
                        PsiElement parent = xmlToken.getParent();
                        if (parent != null) {
                            PsiElement grandParent = parent.getParent();
                            if (grandParent instanceof XmlAttribute xmlAttribute) {
                                String attributeName = xmlAttribute.getName();
                                if (potentialTypeNames.contains(attributeName)) {
                                    VirtualFile virtualFile = containingFile.getVirtualFile();
                                    if (virtualFile != null) {
                                        try {
                                            URI uri = new URI(virtualFile.getUrl().replace(" ", "%20"));
                                            String classFileJarUrl = JarFileSystem.PROTOCOL_PREFIX + uri.getPath().replaceFirst("![^!]+", "") + JarFileSystem.JAR_SEPARATOR + (xmlToken.getText().replace(".", "/") + ".class");
                                            VirtualFile classVirtualFile = VirtualFileManager.getInstance().findFileByUrl(classFileJarUrl);
                                            if (classVirtualFile != null) {
                                                return PsiManager.getInstance(Objects.requireNonNull(editor.getProject())).findFile(classVirtualFile);
                                            }
                                        } catch (URISyntaxException ignore) {
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return null;
    }
}
