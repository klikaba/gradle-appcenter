package ba.klika

import ba.klika.tasks.DownloadTask
import ba.klika.tasks.GetAppsTask
import org.gradle.api.Plugin
import org.gradle.api.Project

class AppcenterPluginExtension {
    def String apiToken
}

class AppcenterPlugin implements Plugin<Project> {
    void apply(Project project) {
        def extension = project.extensions.create("appcenter", AppcenterPluginExtension.class)

        project.tasks.register("download", DownloadTask) { t ->
            t.apiToken = extension.apiToken
        }
        project.tasks.register("getApps", GetAppsTask) { t ->
            t.apiToken = extension.apiToken
        }
    }
}