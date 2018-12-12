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
            println "versionnumber : "+versionnumber
            println "gitcommitid : "+gitcommitid

            int newVersion = versionnumber.toInteger()
            newVersion = newVersion + 1
            def newVersionString = newVersion+":"+commitId
            println "newVersionString : "+newVersionString
            sh(returnStdout: true, script: "echo ${newVersionString} > ${versionFileName}")
            def newversiondata = sh(returnStdout: true, script: "cat ${versionFileName} | head -1")
            println "newversiondata : "+newversiondata
            sshagent(['1c0e3c0a-f7bd-444e-918f-69799380d061']) {
                sh ("git config --global user.email \"justinndavis@gmail.com\"")
                sh ("git config --global user.name \"Justin Davis\"")
                sh ("git add version")
                sh ("git commit -m\"updating version data to ${newVersionString}\"")
                sh ("git push")
            }

            //sh(returnStdout: true, script: "git add version && git commit -m\"updating version data to ${newVersionString}\" && git push")
        }

    }
}
