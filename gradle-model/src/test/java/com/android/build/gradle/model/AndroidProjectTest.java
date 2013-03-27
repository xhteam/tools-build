/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.build.gradle.model;

import com.android.builder.model.SourceProvider;
import junit.framework.TestCase;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.model.GradleProject;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.CodeSource;
import java.util.Set;

public class AndroidProjectTest extends TestCase {

    public void testBasic() {
        // Configure the connector and create the connection
        GradleConnector connector = GradleConnector.newConnector();

        File projectDir = new File(getTestDir(), "basic");
        connector.forProjectDirectory(projectDir);

        ProjectConnection connection = connector.connect();
        try {
            // Load the custom model for the project
            AndroidProject model = connection.getModel(AndroidProject.class);
            assertNotNull("empty model!", model);
            assertEquals("basic", model.getName());
            assertFalse(model.isLibrary());

            ProductFlavorContainer defaultConfig = model.getDefaultConfig();

            new SourceProviderTester(model.getName(), projectDir,
                    "main", defaultConfig.getSourceProvider())
                    .test();
            new SourceProviderTester(model.getName(), projectDir,
                    "instrumentTest", defaultConfig.getTestSourceProvider())
                    .test();
        } finally {
            // Clean up
            connection.close();
        }
    }

    public void testMigrated() {
        // Configure the connector and create the connection
        GradleConnector connector = GradleConnector.newConnector();

        File projectDir = new File(getTestDir(), "migrated");
        connector.forProjectDirectory(projectDir);

        ProjectConnection connection = connector.connect();
        try {
            // Load the custom model for the project
            AndroidProject model = connection.getModel(AndroidProject.class);
            assertNotNull("empty model!", model);
            assertEquals("migrated", model.getName());
            assertFalse(model.isLibrary());

            ProductFlavorContainer defaultConfig = model.getDefaultConfig();

            new SourceProviderTester(model.getName(), projectDir,
                    "main", defaultConfig.getSourceProvider())
                    .setJavaDir("src")
                    .setResourcesDir("src")
                    .setAidlDir("src")
                    .setRenderscriptDir("src")
                    .setResDir("res")
                    .setAssetsDir("assets")
                    .setManifestFile("AndroidManifest.xml")
                    .test();

            new SourceProviderTester(model.getName(), projectDir,
                    "instrumentTest", defaultConfig.getTestSourceProvider())
                    .setJavaDir("tests/java")
                    .setResourcesDir("tests/resources")
                    .setAidlDir("tests/aidl")
                    .setJniDir("tests/jni")
                    .setRenderscriptDir("tests/rs")
                    .setResDir("tests/res")
                    .setAssetsDir("tests/assets")
                    .setManifestFile("tests/AndroidManifest.xml")
                    .test();

        } finally {
            // Clean up
            connection.close();
        }
    }

    public void testTicTacToe() {
        // Configure the connector and create the connection
        GradleConnector connector = GradleConnector.newConnector();

        File projectDir = new File(getTestDir(), "tictactoe");
        connector.forProjectDirectory(projectDir);

        ProjectConnection connection = connector.connect();
        try {
            GradleProject model = connection.getModel(GradleProject.class);
            assertNotNull("empty model!", model);

            for (GradleProject child : model.getChildren()) {
                String path = child.getPath();
                System.out.println(">> " + path);
                String name = path.substring(1);
                File childDir = new File(projectDir, name);

                GradleConnector childConnector = GradleConnector.newConnector();

                childConnector.forProjectDirectory(childDir);

                ProjectConnection childConnection = childConnector.connect();

                AndroidProject androidProject = childConnection.getModel(AndroidProject.class);
                assertNotNull("empty model!", androidProject);
                assertEquals(name, androidProject.getName());
                assertEquals("lib".equals(name), androidProject.isLibrary());
            }
        } finally {
            // Clean up
            connection.close();
        }
    }

    /**
     * Returns the SDK folder as built from the Android source tree.
     * @return the SDK
     */
    protected File getSdkDir() {
        String androidHome = System.getenv("ANDROID_HOME");
        if (androidHome != null) {
            File f = new File(androidHome);
            if (f.isDirectory()) {
                return f;
            }
        }

        throw new IllegalStateException("SDK not defined with ANDROID_HOME");
    }

    /**
     * Returns the root dir for the gradle plugin project
     */
    private File getRootDir() {
        CodeSource source = getClass().getProtectionDomain().getCodeSource();
        if (source != null) {
            URL location = source.getLocation();
            try {
                File dir = new File(location.toURI());
                assertTrue(dir.getPath(), dir.exists());

                File f= dir.getParentFile().getParentFile().getParentFile().getParentFile().getParentFile().getParentFile().getParentFile().getParentFile();
                return  new File(f, "tools" + File.separator + "build");
            } catch (URISyntaxException e) {
                fail(e.getLocalizedMessage());
            }
        }

        fail("Fail to get the tools/build folder");
        return null;
    }

    /**
     * Returns the root folder for the tests projects.
     */
    private File getTestDir() {
        File rootDir = getRootDir();
        return new File(rootDir, "tests");
    }

    private static final class SourceProviderTester {

        private final String projectName;
        private final String configName;
        private final SourceProvider sourceProvider;
        private final File projectDir;
        private String javaDir;
        private String resourcesDir;
        private String manifestFile;
        private String resDir;
        private String assetsDir;
        private String aidlDir;
        private String renderscriptDir;
        private String jniDir;

        SourceProviderTester(String projectName, File projectDir, String configName, SourceProvider sourceProvider) {
            this.projectName = projectName;
            this.projectDir = projectDir;
            this.configName = configName;
            this.sourceProvider = sourceProvider;
            // configure tester with default relative paths
            setJavaDir("src/" + configName + "/java");
            setResourcesDir("src/" + configName + "/resources");
            setManifestFile("src/" + configName + "/AndroidManifest.xml");
            setResDir("src/" + configName + "/res");
            setAssetsDir("src/" + configName + "/assets");
            setAidlDir("src/" + configName + "/aidl");
            setRenderscriptDir("src/" + configName + "/rs");
            setJniDir("src/" + configName + "/jni");
        }

        SourceProviderTester setJavaDir(String javaDir) {
            this.javaDir = javaDir;
            return this;
        }

        SourceProviderTester setResourcesDir(String resourcesDir) {
            this.resourcesDir = resourcesDir;
            return this;
        }

        SourceProviderTester setManifestFile(String manifestFile) {
            this.manifestFile = manifestFile;
            return this;
        }

        SourceProviderTester setResDir(String resDir) {
            this.resDir = resDir;
            return this;
        }

        SourceProviderTester setAssetsDir(String assetsDir) {
            this.assetsDir = assetsDir;
            return this;
        }

        SourceProviderTester setAidlDir(String aidlDir) {
            this.aidlDir = aidlDir;
            return this;
        }

        SourceProviderTester setRenderscriptDir(String renderscriptDir) {
            this.renderscriptDir = renderscriptDir;
            return this;
        }

        SourceProviderTester setJniDir(String jniDir) {
            this.jniDir = jniDir;
            return this;
        }

        void test() {
            testSinglePathSet("java", javaDir, sourceProvider.getJavaDirectories());
            testSinglePathSet("resources", resourcesDir, sourceProvider.getResourcesDirectories());
            testSinglePathSet("res", resDir, sourceProvider.getResDirectories());
            testSinglePathSet("assets", assetsDir, sourceProvider.getAssetsDirectories());
            testSinglePathSet("aidl", aidlDir, sourceProvider.getAidlDirectories());
            testSinglePathSet("rs", renderscriptDir, sourceProvider.getRenderscriptDirectories());
            testSinglePathSet("jni", jniDir, sourceProvider.getJniDirectories());

            assertEquals("AndroidManifest",
                    new File(projectDir, manifestFile).getAbsolutePath(),
                    sourceProvider.getManifestFile().getAbsolutePath());
        }

        private void testSinglePathSet(String setName, String referencePath, Set<File> pathSet) {
            assertEquals(1, pathSet.size());
            assertEquals(projectName + ": " + configName + "/" + setName,
                    new File(projectDir, referencePath).getAbsolutePath(),
                    pathSet.iterator().next().getAbsolutePath());
        }

    }
}
