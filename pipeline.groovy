#!groovy

node('maven') {

    stage('Checkout Source') {
        git url: "${git_url}", branch: 'master', credentialsId: '1c0e3c0a-f7bd-444e-918f-69799380d061'
    }

    dir(".") {

        def commitId  = sh(returnStdout: true, script: "git log -n 1 --pretty=format:'%h'").trim()

        stage('loadfile') {
            myFileName = "version"
            myFile = new File(myFileDirectory + myFileName)
            echo "Building version : ${version}"
            sh "${mvn} clean package -Dspring.profiles.active=dev -DskipTests"
        }

    }
}
