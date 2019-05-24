node {
    def app

    stage('Clone repository') {
        checkout scm
    }

    stage('Build image') {
        app = docker.build("docker.cubyte.org/banana4life/website")
    }

    stage('Push image') {
        def tag = "latest"

        if (env.BRANCH_NAME != null && env.BRANCH_NAME != "master") {
            tag = env.BRANCH_NAME
        }

        docker.withRegistry('https://docker.cubyte.org', 'deployment-account') {
            app.push("${env.BUILD_NUMBER}")
            app.push(tag)
        }
    }
}