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
            project.logger.lifecycle(sprintf('-----------------------------------------------------------------------------------------------------------------------------------------'))
            project.logger.lifecycle(sprintf("%36s|%15s|%20s|%30s| %s", 'id', 'os', 'ownerName', 'appName', 'groups'))
            project.logger.lifecycle(sprintf('-----------------------------------------------------------------------------------------------------------------------------------------'))
            callResponse.getData().each {
                def groups = getDistributionGroups(it.owner.name, it.name)
                project.logger.lifecycle(sprintf("%36s|%15s|%20s|%30s| %s", it.id, it.os, it.owner.name, it.name, groups))
            }
            project.logger.lifecycle(sprintf('-----------------------------------------------------------------------------------------------------------------------------------------'))
        } else {
            throw new GradleException("Error calling AppCenter GET Apps. Status code " + callResponse.status + '\n' + callResponse.getData().toString())
        }
    }

    def getDistributionGroups(String ownerName, String appName) {
        RESTClient client = getRestClient("/apps/${ownerName}/${appName}/distribution_groups")
        def callResponse = catchHttpExceptions { client.get(headers()) }
        if (callResponse.status == 200) {
            return callResponse.getData().inject([]) { result, entry ->
                result << entry.name
            }.join(',')
        } else {
            throw new GradleException("Error calling AppCenter GET Distribution Groups. Status code " + callResponse.status + '\n' + callResponse.getData().toString())
        }
    }
}

