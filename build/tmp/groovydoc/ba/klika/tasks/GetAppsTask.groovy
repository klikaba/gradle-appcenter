package ba.klika.tasks

import groovyx.net.http.RESTClient
import org.gradle.api.GradleException
import org.gradle.api.tasks.TaskAction

class GetAppsTask extends AppcenterBaseTask {
    @TaskAction
    def appsGet() {
        RESTClient client = getRestClient("/apps")
        def callResponse = catchHttpExceptions { client.get(headers()) }
        if (callResponse.status == 200) {
            project.logger.lifecycle(sprintf('------------------------------------------------------------------------------------'))
            project.logger.lifecycle(sprintf("%36s|%15s|%15s|%15s", 'id', 'os', 'ownerName', 'appName'))
            project.logger.lifecycle(sprintf('------------------------------------------------------------------------------------'))
            callResponse.getData().each {
                project.logger.lifecycle(sprintf("%36s|%15s|%15s|%15s", it.id, it.os, it.owner.name, it.name))
            }
            project.logger.lifecycle(sprintf('------------------------------------------------------------------------------------'))
        } else {
            throw new GradleException("Error calling AppCenter. Status code " + callResponse.status + '\n' + callResponse.getData().toString())
        }
    }
}

