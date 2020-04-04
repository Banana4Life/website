node {
    def app

    def registry = "docker.cubyte.org"
    def imageName = "${registry}/banana4life/website"

    stage('Clone repository') {
        checkout scm
    }

    stage('Build image') {
        app = docker.build(imageName, "--pull .")
    }

    stage('Push image') {
        def tag = "latest"

        if (env.BRANCH_NAME != null && env.BRANCH_NAME != "master") {
            tag = env.BRANCH_NAME
        }

        docker.withRegistry("https://${registry}", 'deployment-account') {
            app.push(tag)
        }

        withCredentials([string(credentialsId: 'trigger-token', variable: 'token')]) {
            def projectId = 230
            def triggerUrl = "https://git.cubyte.org/api/v4/projects/${projectId}/trigger/pipeline"
            def curlTrigger = "curl -X POST -F 'token=${token}' -F 'ref=master' -F 'variables[IMAGE_NAME]=${imageName}:${tag}' ${triggerUrl}"

            sh curlTrigger
        }
    }
}
