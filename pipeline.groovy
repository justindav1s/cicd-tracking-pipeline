#!groovy

node('maven') {

    git url: "${build_tracking_git_url}", branch: 'master', credentialsId: '1c0e3c0a-f7bd-444e-918f-69799380d061'
    def commitId  = sh(returnStdout: true, script: "git log -n 1 --pretty=format:'%h'").trim()

    dir("build")    {

        stage('build') {

        }

        stage('test') {

        }
    }


    dir("build-metadata") {

        stage('manage version data') {
            manageVersionData(commitId, git_url)
        }

    }
}

def manageVersionData(commitId, git_url) {
    git url: "${git_url}", branch: 'master', credentialsId: '1c0e3c0a-f7bd-444e-918f-69799380d061'
    def workspace = pwd()
    def versionFileName = "version"
    versionFileName = workspace+"/"+versionFileName
    def versiondata = sh(returnStdout: true, script: "cat ${versionFileName} | head -1")
    println "Existing version data : "+versiondata
    def versionnumber = versiondata.tokenize(':')[0]
    def gitcommitid = versiondata.tokenize(':')[1]
    int newVersion = versionnumber.toInteger()
    newVersion = newVersion + 1
    def newVersionString = newVersion+":"+commitId
    println "New version data :  : "+newVersionString
    sh(returnStdout: true, script: "echo ${newVersionString} > ${versionFileName}")
    def newversiondata = sh(returnStdout: true, script: "cat ${versionFileName} | head -1")
    sshagent(['1c0e3c0a-f7bd-444e-918f-69799380d061']) {
        sh ("git config --global user.email \"justinndavis@gmail.com\"")
        sh ("git config --global user.name \"Justin Davis\"")
        sh ("git add version")
        sh ("git commit -m \"updating version data to ${newVersionString}\"")
        sh ("git push origin master")
    }
}
