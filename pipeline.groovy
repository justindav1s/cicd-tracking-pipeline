#!groovy

node('maven') {

    stage('Checkout Source') {
        git url: "${git_url}", branch: 'master', credentialsId: '1c0e3c0a-f7bd-444e-918f-69799380d061'
    }

    dir(".") {

        def commitId  = sh(returnStdout: true, script: "git log -n 1 --pretty=format:'%h'").trim()

        stage('loadfile') {
            def workspace = pwd()
            def versionFileName = "version"
            versionFileName = workspace+"/"+versionFileName
            println versionFileName
            def versiondata = sh(returnStdout: true, script: "cat ${versionFileName} | head -1")
            println versiondata
            def versionnumber = versiondata.tokenize(':')[0]
            def gitcommitid = versiondata.tokenize(':')[1]
            println "versionnumber : " versionnumber
            println "gitcommitid : " gitcommitid

            def newVersion = versionnumber++
            def newVersionString = newVersion+":"+commitId
            println "newVersionString : " newVersionString

        }

    }
}
