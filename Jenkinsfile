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
        def branch = env.BRANCH_NAME

        if (env.BRANCH_NAME != null && branch != "master") {
            tag = env.BRANCH_NAME
        }

        docker.withRegistry("https://${registry}", 'deployment-account') {
            app.push(tag)
        }

        def token = credentials('trigger-token')
        def curlTrigger = "curl -X POST -F 'token=${token}' -F 'ref=${branch}' -F 'variables[IMAGE_NAME]=${imageName}:${tag}' https://git.cubyte.org/api/v4/projects/230/trigger/pipeline"

        sh curlTrigger
    }
}
