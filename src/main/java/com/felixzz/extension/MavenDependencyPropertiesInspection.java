package com.felixzz.extension;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.LocalQuickFixBase;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import com.intellij.util.Processor;
import com.intellij.util.xml.DomFileElement;
import com.intellij.util.xml.GenericDomValue;
import com.intellij.util.xml.highlighting.DomElementAnnotationHolder;
import com.intellij.util.xml.highlighting.DomElementsInspection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.model.MavenDomDependency;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;

import java.util.HashSet;
import java.util.Set;

/**
 * @author Felix
 */
public class MavenDependencyPropertiesInspection extends DomElementsInspection<MavenDomProjectModel> {

    public MavenDependencyPropertiesInspection() {
        super(MavenDomProjectModel.class);
    }

    @Override
    public void checkFileElement(DomFileElement<MavenDomProjectModel> domFileElement, DomElementAnnotationHolder holder) {
        process(domFileElement, holder);
    }

    private void process(DomFileElement<MavenDomProjectModel> domFileElement, DomElementAnnotationHolder holder) {
        MavenDomProjectModel projectModel = domFileElement.getRootElement();
        Set<MavenDomDependency> dependencies = new HashSet<>(projectModel.getDependencies().getDependencies());
        dependencies.addAll(projectModel.getDependencyManagement().getDependencies().getDependencies());
        Processor<MavenDomProjectModel> processor = mavenDomProjectModel -> {
            int i = 0;
            for (MavenDomDependency dependency : dependencies) {
                i++;
                String groupId = dependency.getGroupId().getStringValue();
                String artifactId = dependency.getArtifactId().getStringValue();
                if (null == groupId || null == artifactId) {
                    continue;
                }
                String version = dependency.getVersion().getStringValue();
                GenericDomValue<String> domValue = dependency.getVersion();
                String unresolvedValue = domValue.getRawText();
                if (unresolvedValue != null && !"null".equals(unresolvedValue) && !unresolvedValue.startsWith("${")) {
                    addInfo(dependency, holder, projectModel, groupId, artifactId, version);
                }
            }
            return false;
        };
        processor.process(projectModel);
    }

    private void addInfo(MavenDomDependency dependency,
                         DomElementAnnotationHolder holder,
                         MavenDomProjectModel projectModel,
                         String groupId, String artifactId, String version) {
        if (projectModel == null) {
            return;
        }
        String propertyName = artifactId + ".version";
        String expression = "${" + propertyName + "}";
        LocalQuickFix fix = new LocalQuickFixBase("Extract to properties", "B") {
            @Override
            public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
                PsiElement psiElement = descriptor.getPsiElement();
                if (psiElement instanceof XmlTag) {
                    ((XmlTag) psiElement).getValue().setText(expression);
                } else if (psiElement instanceof XmlText) {
                    ((XmlText) psiElement).setValue(expression);
                }
                XmlFile xmlFile = (XmlFile) psiElement.getContainingFile();
                final XmlTag rootTag = xmlFile.getRootTag();
                if (rootTag == null) {
                    return;
                }
                XmlTag propertiesXmlTag = rootTag.findFirstSubTag("properties");
                if (propertiesXmlTag == null) {
                    return;
                }
                final XmlTag newTag = propertiesXmlTag.createChildTag(propertyName, propertiesXmlTag.getNamespace(), null, false);
                newTag.setName(propertyName);
                newTag.getValue().setText(version);
                propertiesXmlTag.addSubTag(newTag, false);
            }
        };
        holder.createProblem(dependency.getVersion(), HighlightSeverity.WEAK_WARNING, null, fix);
    }
}
