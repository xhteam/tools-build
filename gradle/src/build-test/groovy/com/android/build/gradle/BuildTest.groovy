/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.build.gradle

import com.android.build.gradle.internal.test.BaseTest
import com.google.common.collect.Sets

import javax.imageio.ImageIO
import java.awt.image.BufferedImage

/**
 * Build tests.
 *
 * This requires an SDK, found through the ANDROID_HOME environment variable or present in the
 * Android Source tree under out/host/<platform>/sdk/... (result of 'make sdk')
 */
class BuildTest extends BaseTest {

    private File testDir
    private File sdkDir
    private static Set<String> builtProjects = Sets.newHashSet()

    @Override
    protected void setUp() throws Exception {
        testDir = getTestDir()
        sdkDir = getSdkDir()
    }

    void testAidl() {
        buildProject("aidl")
    }

    void testApi() {
        buildProject("api")
    }

    void testAppLibTest() {
        buildProject("applibtest")
    }

    void testBasic() {
        buildProject("basic")
    }

    void testReportsOnBasic() {
        runTasksOn("basic", "androidDependencies", "signingReport")
    }

    void testDependencies() {
        buildProject("dependencies")
    }

    void testFlavored() {
        buildProject("flavored")
    }

    void testFlavorLib() {
        buildProject("flavorlib")
    }

    void testReportsOnFlavorlib() {
        runTasksOn("flavorlib", "androidDependencies", "signingReport")
    }

    void testFlavors() {
        buildProject("flavors")
    }

    void testLibsTest() {
        buildProject("libsTest")
    }

    void testMigrated() {
        buildProject("migrated")
    }

    void testMultiProject() {
        buildProject("multiproject")
    }

    void testMultiRes() {
        buildProject("multires")
    }

    void testOverlay1() {
        File project = buildProject("overlay1")
        File drawableOutput = new File(project, "build/res/all/debug/drawable" )

        checkImageColor(drawableOutput, "no_overlay.png", (int) 0xFF00FF00)
        checkImageColor(drawableOutput, "type_overlay.png", (int) 0xFF00FF00)
    }

    void testOverlay2() {
        File project = buildProject("overlay2")
        File drawableOutput = new File(project, "build/res/all/one/debug/drawable" )

        checkImageColor(drawableOutput, "no_overlay.png", (int) 0xFF00FF00)
        checkImageColor(drawableOutput, "type_overlay.png", (int) 0xFF00FF00)
        checkImageColor(drawableOutput, "flavor_overlay.png", (int) 0xFF00FF00)
        checkImageColor(drawableOutput, "type_flavor_overlay.png", (int) 0xFF00FF00)
    }

    void testRenderscript() {
        buildProject("renderscript")
    }

    void testRenderscriptInLib() {
        buildProject("renderscriptInLib")
    }

    void testRenderscriptMultiSrc() {
        buildProject("renderscriptMultiSrc")
    }

    void testRepo() {
        File repo = new File(testDir, "repo")

        try {
            runGradleTasks(sdkDir, new File(repo, "util"), "clean", "uploadArchives")
            runGradleTasks(sdkDir, new File(repo, "baseLibrary"), "clean", "uploadArchives")
            runGradleTasks(sdkDir, new File(repo, "library"), "clean", "uploadArchives")
            runGradleTasks(sdkDir, new File(repo, "app"), "clean", "assemble")
        } finally {
            // clean up the test repository.
            File testRepo = new File(repo, "testrepo")
            testRepo.deleteDir()
        }
    }

    void testTicTacToe() {
        buildProject("tictactoe")
    }

    void testOtherProjects() {
        File[] projects = testDir.listFiles()
        for (File project : projects) {
            if (!project.isDirectory()) {
                continue
            }

            String name = project.name
            if (name.startsWith(".")) {
                continue
            }

            if (!builtProjects.contains(name)) {
                buildProject(name)
            }
        }
    }

    private File buildProject(String name) {
        File project = new File(testDir, name)
        builtProjects.add(name)

        File buildGradle = new File(project, "build.gradle")
        if (!buildGradle.isFile()) {
            return null
        }

        // build the project
        runGradleTasks(sdkDir, project, "clean", "assembleDebug")

        return project;
    }

    private static void checkImageColor(File folder, String fileName, int expectedColor) {
        File f = new File(folder, fileName)
        assertTrue("File '" + f.getAbsolutePath() + "' does not exist.", f.isFile())

        BufferedImage image = ImageIO.read(f)
        int rgb = image.getRGB(0, 0)
        assertEquals(String.format("Expected: 0x%08X, actual: 0x%08X for file %s",
                expectedColor, rgb, f),
                expectedColor, rgb);
    }
}
