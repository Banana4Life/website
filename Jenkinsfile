node {
    def app

    stage('Clone repository') {
        checkout scm
    }

    stage('Build image') {
        app = docker.build("banana4life/website")
    }

    stage('Push image') {
        def tag = "latest"

        if (env.BRANCH_NAME != null && env.BRANCH_NAME != "master") {
            tag = env.BRANCH_NAME
        }

        docker.withRegistry('index.docker.io', 'dockerhub') {
            app.push("${env.BUILD_NUMBER}")
            app.push(tag)
        }
    }
}