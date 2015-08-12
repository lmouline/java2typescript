package org.kevoree.modeling.java2typescript;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.*;
import org.kevoree.modeling.java2typescript.translators.ClassTranslator;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class SourceTranslator {
    //private static final String baseDir = "/Users/duke/Documents/dev/dukeboard/kevoree-modeling-framework/org.kevoree.modeling.microframework/src/main/java";
    //private static final String baseDir = "/Users/duke/Documents/dev/dukeboard/kevoree-modeling-framework/org.kevoree.modeling.microframework.typescript/target/sources";
    //private static final String baseDir = "/Users/duke/IdeaProjects/kmf_mini/src/gen/java";
    // private static final String baseDir = "/Users/gnain/Sources/Kevoree/kevoree-modeling-framework-private/public/test/org.kevoree.modeling.test.datastore/target/generated-sources/kmf";

    /*
    public static void main(String[] args) throws IOException {
        SourceTranslator sourceTranslator = new SourceTranslator();
        sourceTranslator.getAnalyzer().addClasspath("/Users/gnain/.m2/repository/org/kevoree/modeling/org.kevoree.modeling.microframework/4.18.5-SNAPSHOT/org.kevoree.modeling.microframework-4.18.5-SNAPSHOT.jar");
        sourceTranslator.getAnalyzer().addClasspath("/Users/gnain/.m2/repository/junit/junit/4.11/junit-4.11.jar");
        sourceTranslator.translateSources(baseDir, "target", "out");
    }*/

    private JavaAnalyzer analyzer;

    public JavaAnalyzer getAnalyzer() {
        return analyzer;
    }

    public SourceTranslator() {
        analyzer = new JavaAnalyzer();
    }

    private static final String JAVA_TS = "java.ts";
    private static final String JUNIT_TS = "junit.ts";

    private static final String JUNIT_D_TS = "junit.d.ts";
    private static final String JUNIT_JS = "junit.js";

    public String additionalAppend = null;
    public String[] exportPackage = null;

    public void processPsiDirectory(boolean isRoot, PsiDirectory currentDir, TranslationContext ctx, boolean exportRoot) {
        if (exportRoot || !isRoot) {
            ctx.print("export module ");
        } else {
            ctx.print("module ");
        }
        ctx.append(currentDir.getName());
        ctx.append(" {");
        ctx.append("\n");
        ctx.increaseIdent();
        List<PsiClass> toTranslate = new ArrayList<PsiClass>();
        currentDir.acceptChildren(new PsiElementVisitor() {
            @Override
            public void visitElement(PsiElement element) {
                if (element instanceof PsiJavaFile) {
                    element.acceptChildren(this);
                } else if (element instanceof PsiClass) {
                    if (!((PsiClass) element).getName().startsWith("NoJs_")) {
                        toTranslate.add((PsiClass) element);
                    }
                }
            }
        });
        Collections.sort(toTranslate, new Comparator<PsiClass>() {
            @Override
            public int compare(PsiClass o1, PsiClass o2) {
                return o1.getName().compareTo(o2.getName());
            }
        });
        for (PsiClass clazz : toTranslate) {
            ClassTranslator.translate(clazz, ctx);
        }

        List<PsiDirectory> subDirectories = new ArrayList<PsiDirectory>();
        currentDir.acceptChildren(new PsiElementVisitor() {
            @Override
            public void visitElement(PsiElement element) {
                if (element instanceof PsiDirectory) {
                    subDirectories.add((PsiDirectory) element);
                } else {
                    element.acceptChildren(this);
                }
            }
        });
        Collections.sort(subDirectories, new Comparator<PsiDirectory>() {
            @Override
            public int compare(PsiDirectory o1, PsiDirectory o2) {
                return o1.getName().compareTo(o2.getName());
            }
        });
        for (PsiDirectory subDir : subDirectories) {
            processPsiDirectory(false, subDir, ctx, exportRoot);
        }

        ctx.decreaseIdent();
        ctx.print("}");
        ctx.append("\n");
    }

    public void translateSources(String sourcePath, String outputPath, String name, boolean appendJavaStd, boolean appendJunitStd, boolean exportRoot) throws IOException {
        File sourceFolder = new File(sourcePath);
        File targetFolder = new File(outputPath);
        if (sourceFolder.exists()) {
            if (sourceFolder.isFile()) {
                throw new IllegalArgumentException("Source path is not a directory");
            }
        } else {
            sourceFolder.mkdirs();
        }
        if (targetFolder.exists()) {
            if (targetFolder.isFile()) {
                throw new IllegalArgumentException("Target path is not a directory");
            }
        } else {
            targetFolder.mkdirs();
        }
        //copy default library
        /*
        File javaDTS = new File(targetFolder, JAVA_D_TS);
        File javaJS = new File(targetFolder, JAVA_JS);
        Files.copy(this.getClass().getClassLoader().getResourceAsStream(JAVA_D_TS), javaDTS.toPath(), StandardCopyOption.REPLACE_EXISTING);
        Files.copy(this.getClass().getClassLoader().getResourceAsStream(JAVA_JS), javaJS.toPath(), StandardCopyOption.REPLACE_EXISTING);
*/
        if (appendJunitStd) {
            File junitDTS = new File(targetFolder, JUNIT_D_TS);
            File junitJS = new File(targetFolder, JUNIT_JS);
            Files.copy(this.getClass().getClassLoader().getResourceAsStream(JUNIT_D_TS), junitDTS.toPath(), StandardCopyOption.REPLACE_EXISTING);
            Files.copy(this.getClass().getClassLoader().getResourceAsStream(JUNIT_JS), junitJS.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }

        TranslationContext ctx = new TranslationContext();

        //TODO protect by an option
        if (appendJavaStd) {
            if (exportRoot) {
                ctx.append(readFully(this.getClass().getClassLoader().getResourceAsStream(JAVA_TS)).replaceFirst("module java", "export module java"));
            } else {
                ctx.append(readFully(this.getClass().getClassLoader().getResourceAsStream(JAVA_TS)));
            }
            ctx.append("\n");
        }

        if (additionalAppend != null) {
            if (exportRoot) {
                String content = readFully(this.getClass().getClassLoader().getResourceAsStream(additionalAppend));
                content = content.replaceFirst("module java", "export module java");
                if (exportPackage != null) {
                    for (String pack : exportPackage) {
                        content = content.replaceFirst("module " + pack, "export module " + pack);
                    }
                }
                ctx.append(content);
            }
        }

        PsiDirectory root = analyzer.analyze(sourceFolder);
        List<PsiDirectory> subDirectories = new ArrayList<PsiDirectory>();
        root.acceptChildren(new PsiElementVisitor() {
            @Override
            public void visitElement(PsiElement element) {
                if (element instanceof PsiDirectory) {
                    subDirectories.add((PsiDirectory) element);
                } else {
                    element.acceptChildren(this);
                }
            }
        });
        Collections.sort(subDirectories, new Comparator<PsiDirectory>() {
            @Override
            public int compare(PsiDirectory o1, PsiDirectory o2) {
                return o1.getName().compareTo(o2.getName());
            }
        });
        for (PsiDirectory subDir : subDirectories) {
            processPsiDirectory(true, subDir, ctx, exportRoot);
        }

        File generatedTS = new File(targetFolder, name + ".ts");
        FileUtil.writeToFile(generatedTS, ctx.toString().getBytes());
        System.out.println("Transpile Java2TypeScript ended to " + generatedTS.getAbsolutePath());
    }

    public String readFully(InputStream inputStream)
            throws IOException {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int length = 0;
        while ((length = inputStream.read(buffer)) != -1) {
            baos.write(buffer, 0, length);
        }
        return new String(baos.toByteArray());
    }

}
