#!groovy

node('maven') {

    stage('Checkout Source') {
        git url: "${git_url}", branch: 'master', credentialsId: '1c0e3c0a-f7bd-444e-918f-69799380d061'
    }

    dir(".") {

        def commitId  = sh(returnStdout: true, script: "git log -n 1 --pretty=format:'%h'").trim()

        stage('loadfile') {
            def fileList = sh(returnStdout: true, script: "ls -ltr")
            println fileList
            def workspace = pwd()
            def versionFileName = "version"
            versionFileName = workspace+"/"+versionFileName
            println versionFileName
            def versiondata = sh(returnStdout: true, script: "cat ${versionFileName} | head -1")
            println versiondata

            def versionFile = new File(versionFileName)

            lastLine = versionFile.readLines().get(versionFile.readLines().size().toInteger() - 1)
            if (lastLine ==~ /.Fatal Error.*/ ){
                println "Fatal error found"
                println lastLine
            } else{
                println "nothing to see here"
                println lastLine
            }
        }

    }
}
