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

        withDockerRegistry([ credentialsId: "6544de7e-17a4-4576-9b9b-e86bc1e4f903", url: "" ]) {
          sh 'docker push banana4life/website:' + tag
        }
    }
}