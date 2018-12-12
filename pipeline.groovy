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
            println versiondata.testString.tokenize(':')[0]
            println versiondata.testString.tokenize(':')[1]
            def versionnumber = sh(returnStdout: true, script: 'echo versiondata | cut -d":" -f1' )
            def gitcommitid = sh(returnStdout: true, script: 'echo ${versiondata} | cut -d":" -f2' )
            println "versionnumber " versionnumber
            println "gitcommitid " gitcommitid

        }

    }
}
