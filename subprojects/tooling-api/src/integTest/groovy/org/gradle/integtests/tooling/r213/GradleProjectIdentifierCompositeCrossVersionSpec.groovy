/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.integtests.tooling.r213
import org.gradle.integtests.tooling.fixture.CompositeToolingApiSpecification
import org.gradle.test.fixtures.file.TestFile
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.connection.GradleConnection
import org.gradle.tooling.internal.consumer.DefaultGradleConnector
import org.gradle.tooling.internal.protocol.DefaultBuildIdentity
import org.gradle.tooling.internal.protocol.DefaultProjectIdentity
import org.gradle.tooling.model.GradleProject
import org.gradle.tooling.model.HasGradleProject
import org.gradle.tooling.model.eclipse.EclipseProject
import org.gradle.tooling.model.eclipse.HierarchicalEclipseProject
import org.gradle.tooling.model.gradle.GradleBuild
import org.gradle.tooling.model.idea.IdeaProject

class GradleProjectIdentifierCompositeCrossVersionSpec extends CompositeToolingApiSpecification {
    static identifiedModels = [GradleProject, EclipseProject, HierarchicalEclipseProject]
    static identifiedBuildModels = [GradleBuild, IdeaProject]
    TestFile rootSingle
    TestFile rootMulti

    TestFile setup() {
        rootSingle = singleProjectJavaBuild("A")
        rootMulti = multiProjectJavaBuild("B", ['x', 'y'])
    }

    def "ProjectConnection provides identified GradleBuild"() {
        when:
        def gradleBuild = getModelWithProjectConnection(rootMulti, GradleBuild)

        then:
        gradleBuild.identifier == new DefaultBuildIdentity(rootMulti)
    }

    def "GradleConnection provides identified GradleBuild for each build"() {
        when:
        def gradleBuilds = withCompositeConnection([rootMulti, rootSingle]) { GradleConnection connection ->
            def modelBuilder = connection.models(GradleBuild)
            modelBuilder.get()
        }.asList()*.model
        def singleProjectBuild = gradleBuilds.find {it.rootProject.projectDirectory == rootSingle}
        def multiProjectBuild = gradleBuilds.find {it.rootProject.projectDirectory == rootMulti}

        then:
        singleProjectBuild.identifier == new DefaultBuildIdentity(rootSingle)
        multiProjectBuild.identifier == new DefaultBuildIdentity(rootMulti)
    }

    def "GradleConnection provides GradleProjects for single project build"() {
        when:
        def gradleProjects = getGradleProjectsWithGradleConnection([rootSingle], modelType)

        then:
        gradleProjects.size() == 1
        hasProject(gradleProjects, rootSingle, ':', 'A')

        where:
        modelType << identifiedModels
    }

    def "ProjectConnection provides all GradleProjects for root of single project build"() {
        when:
        def gradleProjects = getGradleProjectsWithProjectConnectionUsingBuildModel(rootSingle, modelType)

        then:
        gradleProjects.size() == 1
        hasProject(gradleProjects, rootSingle, ':', 'A')

        where:
        modelType << identifiedBuildModels
    }

    def "GradleConnection provides GradleProjects for multi-project build"() {
        when:
        def gradleProjects = getGradleProjectsWithGradleConnection([rootMulti], modelType)

        then:
        gradleProjects.size() == 3
        hasParentProject(gradleProjects, rootMulti, ':', 'B', [':x', ':y'])
        hasChildProject(gradleProjects, rootMulti, ':x', 'x', ':')
        hasChildProject(gradleProjects, rootMulti, ':y', 'y', ':')

        where:
        modelType << identifiedModels
    }

    def "ProjectConnection provides all GradleProjects for root of multi-project build"() {
        when:
        def gradleProjects = getGradleProjectsWithProjectConnectionUsingBuildModel(rootMulti, modelType)

        then:
        gradleProjects.size() == 3
        hasParentProject(gradleProjects, rootMulti, ':', 'B', [':x', ':y'])
        hasChildProject(gradleProjects, rootMulti, ':x', 'x', ':')
        hasChildProject(gradleProjects, rootMulti, ':y', 'y', ':')

        where:
        modelType << identifiedBuildModels
    }

    def "ProjectConnection provides all GradleProjects for subproject of multi-project build"() {
        when:
        def rootDir = rootMulti.file("x")
        def gradleProjects = getGradleProjectsWithProjectConnectionUsingBuildModel(rootDir, modelType)

        then:
        gradleProjects.size() == 3
        hasParentProject(gradleProjects, rootDir, ':', 'B', [':x', ':y'])
        hasChildProject(gradleProjects, rootDir, ':x', 'x', ':')
        hasChildProject(gradleProjects, rootDir, ':y', 'y', ':')

        where:
        modelType << identifiedBuildModels
    }

    def "GradleConnection provides GradleProjects for composite build"() {
        when:
        def gradleProjects = getGradleProjectsWithGradleConnection([rootMulti, rootSingle], modelType)

        then:
        gradleProjects.size() == 4
        hasProject(gradleProjects, rootSingle, ':', 'A')
        hasParentProject(gradleProjects, rootMulti, ':', 'B', [':x', ':y'])
        hasChildProject(gradleProjects, rootMulti, ':x', 'x', ':')
        hasChildProject(gradleProjects, rootMulti, ':y', 'y', ':')

        where:
        modelType << identifiedModels
    }

    def "ProjectConnection provides GradleProject for root of single project build"() {
        when:
        GradleProject project = getGradleProjectWithProjectConnection(rootSingle, modelType)

        then:
        assertProject(project, rootSingle, ':', 'A', null, [])

        where:
        modelType << identifiedModels
    }

    def "ProjectConnection provides GradleProject for root of multi-project build"() {
        when:
        GradleProject project = getGradleProjectWithProjectConnection(rootMulti, modelType)

        then:
        assertProject(project, rootMulti, ':', 'B', null, [':x', ':y'])

        where:
        modelType << identifiedModels
    }

    def "ProjectConnection provides GradleProject for subproject of multi-project build"() {
        given:
        def rootDir = rootMulti.file("x")

        when: "GradleProject is requested directly"
        GradleProject project = getGradleProjectWithProjectConnection(rootDir, GradleProject)

        then: "Get the GradleProject model for the root project"
        assertProject(project, rootDir, ':', 'B', null, [':x', ':y'])

        when: "EclipseProject is requested"
        GradleProject projectFromEclipseProject = getGradleProjectWithProjectConnection(rootDir, EclipseProject)

        then: "Has a GradleProject model for the subproject"
        assertProject(projectFromEclipseProject, rootDir, ':x', 'x', ':', [])
    }

    def "ProjectConnection provides GradleProject for subproject of multi-project build with --no-search-upwards"() {
        when:
        def rootDir = rootMulti.file("x")
        GradleProject project = getGradleProjectWithProjectConnection(rootDir, modelType, false)

        then:
        assertProject(project, rootDir, ':', 'x', null, [])

        where:
        modelType << identifiedModels
    }

    private static void hasProject(def projects, File rootDir, String path, String name) {
        hasProject(projects, rootDir, path, name, null, [])
    }

    private static void hasChildProject(def projects, File rootDir, String path, String name, String parentPath) {
        hasProject(projects, rootDir, path, name, parentPath, [])
    }

    private static void hasParentProject(def projects, File rootDir, String path, String name, List<String> childPaths) {
        hasProject(projects, rootDir, path, name, null, childPaths)
    }

    private static void hasProject(def projects, File rootDir, String path, String name, String parentPath, List<String> childPaths) {
        def project = projects.find {it.name == name}
        assert project != null :  "No project with name $name found"
        assertProject(project, rootDir, path, name, parentPath, childPaths)
     }

    private static void assertProject(def project, File rootDir, String path, String name, String parentPath, List<String> childPaths) {
        assert project.path == path
        assert project.name == name
        if (parentPath == null) {
            assert project.parent == null
        } else {
            assert project.parent.path == parentPath
        }
        assert project.children*.path == childPaths
        assert project.identifier == new DefaultProjectIdentity(new DefaultBuildIdentity(rootDir), path)
    }

    private GradleProject getGradleProjectWithProjectConnection(TestFile rootDir, Class modelType = GradleProject, boolean searchUpwards = true) {
        GradleConnector connector = connector()
        connector.forProjectDirectory(rootDir)
        ((DefaultGradleConnector) connector).searchUpwards(searchUpwards)
        def model = withConnection(connector) { it.getModel(modelType) }
        return toGradleProject(model)
    }

    private <T> T getModelWithProjectConnection(TestFile rootDir, Class<T> modelType) {
        GradleConnector connector = connector()
        connector.forProjectDirectory(rootDir)
        return withConnection(connector) { it.getModel(modelType) }
    }

    private static GradleProject toGradleProject(def model) {
        if (model instanceof GradleProject) {
            return model
        }
        if (model instanceof HasGradleProject) {
            return model.gradleProject
        }
        throw new IllegalArgumentException("Model type does not provide GradleProject")
    }

    private getGradleProjectsWithGradleConnection(List<TestFile> rootDirs, Class modelType = GradleProject) {
        def models = withCompositeConnection(rootDirs) { GradleConnection connection ->
            def modelBuilder = connection.models(modelType)
            modelBuilder.get()
        }.asList()*.model
        return models.collect { toGradleProject(it) }
    }

    private getGradleProjectsWithProjectConnectionUsingBuildModel(TestFile rootDir, Class modelType = GradleProject, boolean searchUpwards = true) {
        GradleConnector connector = connector()
        connector.forProjectDirectory(rootDir)
        ((DefaultGradleConnector) connector).searchUpwards(searchUpwards)
        def buildModel = withConnection(connector) { it.getModel(modelType) }
        return toGradleProjects(buildModel)
    }

    private static toGradleProjects(def model) {
        if (model instanceof GradleBuild) {
            return model.projects
        }
        if (model instanceof IdeaProject) {
            return model.modules*.gradleProject
        }
        throw new IllegalArgumentException("Model type does not provide GradleProjects")
    }
}
