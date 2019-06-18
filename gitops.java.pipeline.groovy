#!groovy

import groovy.transform.Field

node('maven') {

    def mvn          = "mvn -U -B -q -s ../settings.xml -Dmaven.wagon.http.ssl.insecure=true -Dmaven.wagon.http.ssl.allowall=true"
    def dev_project  = "${org}-dev"
    def prod_project = "${org}-prod"
    def app_url_dev  = "http://${app_name}.${dev_project}.svc:8080"
    def sonar_url    = "http://sonarqube.cicd.svc:9000"
    def nexus_url    = "http://nexus.cicd.svc:8081/repository/maven-snapshots"
    def registry     = "docker-registry.default.svc:5000"
    def groupId, version, packaging = null
    def artifactId = null

    stage('Checkout Source') {
        git url: "${git_url}", branch: 'master'
    }

    def commitId  = sh(returnStdout: true, script: "git log -n 1 --pretty=format:'%h'").trim()
    def commitmsg  = sh(returnStdout: true, script: "git log --format=%B -n 1 ${commitId}").trim()

    dir("src/${app_name}") {

        groupId      = getGroupIdFromPom("pom.xml")
        artifactId   = getArtifactIdFromPom("pom.xml")
        version      = getVersionFromPom("pom.xml")
        packaging    = getPackagingFromPom("pom.xml")

        stage('Build jar') {
            echo "Building version : ${version}"
            sh "${mvn} clean package -Dspring.profiles.active=dev -DskipTests"
        }

//        // Using Maven run the unit tests
//        stage('Unit/Integration Tests') {
//            echo "Running Unit Tests"
//            sh "${mvn} test -Dmaven.wagon.http.ssl.insecure=true -Dspring.profiles.active=dev"
//            archive "target/**/*"
//            junit 'target/surefire-reports/*.xml'
//        }

        //Build the OpenShift Image in OpenShift and tag it.
        stage('Build and Tag OpenShift Image') {
            echo "Building OpenShift container image ${app_name}:${devTag}"
            echo "Project : ${dev_project}"
            echo "App : ${app_name}"
            echo "Group ID : ${groupId}"
            echo "Artifact ID : ${artifactId}"
            echo "Version : ${version}"
            echo "Packaging : ${packaging}"
            sh "${mvn} clean"
            sh "${mvn} dependency:copy -DstripVersion=true -Dartifact=${groupId}:${artifactId}:${version}:${packaging} -DoutputDirectory=."
            sh "cp \$(find . -type f -name \"${artifactId}-*.${packaging}\")  ${artifactId}-${commitId}.${packaging}"
            sh "pwd; ls -ltr"

            openshift.withCluster() {
                openshift.withProject("${dev_project}") {

                    echo "Building ...."
                    def nb = openshift.startBuild("${app_name}", "--from-file=${artifactId}-${commitId}.${packaging}")
                    nb.logs('-f')

                    echo "Tagging ...."
                    openshift.tag("${app_name}:latest", "${app_name}:${devTag}")
                    openshift.tag("${app_name}:latest", "${app_name}:${commitId}")
                }
            }

        }

        // Deploy the built image to the Development Environment.
        stage('Deploy to Dev') {
            echo "Deploying container image to Development Project"
            echo "Project : ${dev_project}"
            echo "App : ${app_name}"
            echo "Dev Tag : ${devTag}"

            openshift.withCluster() {
                openshift.withProject(dev_project) {
                    //remove any triggers
                    openshift.set("triggers", "dc/${app_name}", "--remove-all");

                    //update deployment config with new image
                    openshift.set("image", "dc/${app_name}", "${app_name}=${registry}/${dev_project}/${app_name}:${commitId}")

                    //update app config
                    openshift.delete("configmap", "${app_name}-config", "--ignore-not-found=true")
                    openshift.create("configmap", "${app_name}-config", "--from-file=${config_file}")

                    //trigger a rollout of the new image
                    def rm = openshift.selector("dc", [app:app_name]).rollout().latest()
                    //wait for rollout to start
                    timeout(5) {
                        openshift.selector("dc", [app:app_name]).related('pods').untilEach(1) {
                            return (it.object().status.phase == "Running")
                        }
                    }
                    //rollout has started

                    //wait for deployment to finish and for new pods to become active
                    def latestDeploymentVersion = openshift.selector('dc',[app:app_name]).object().status.latestVersion
                    def rc = openshift.selector("rc", "${app_name}-${latestDeploymentVersion}")
                    rc.untilEach(1) {
                        def rcMap = it.object()
                        return (rcMap.status.replicas.equals(rcMap.status.readyReplicas))
                    }
                    //deployment finished
                }
            }
            echo "Deploying container image to Development Project : FINISHED"

        }

        dir("build-metadata") {

            stage('manage version data') {
                manageVersionData(commitId, commitmsg, groupId, artifactId)
            }

        }
    }
}

// Convenience Functions to read variables from the pom.xml
// Do not change anything below this line.
// --------------------------------------------------------
def getVersionFromPom(pom) {
    def matcher = readFile(pom) =~ '<version>(.+)</version>'
    matcher ? matcher[0][1] : null
}
def getGroupIdFromPom(pom) {
    def matcher = readFile(pom) =~ '<groupId>(.+)</groupId>'
    matcher ? matcher[0][1] : null
}
def getArtifactIdFromPom(pom) {
    def matcher = readFile(pom) =~ '<artifactId>(.+)</artifactId>'
    matcher ? matcher[0][1] : null
}
def getPackagingFromPom(pom) {
    def matcher = readFile(pom) =~ '<packaging>(.+)</packaging>'
    matcher ? matcher[0][1] : null
}

def manageVersionData(commitId, commitmsg, groupId, artifactId) {

    withCredentials([usernamePassword(credentialsId: 'github', passwordVariable: 'GIT_PASSWORD', usernameVariable: 'GIT_USERNAME')]) {
        def github_repo = "manifest-test"
        def trackingrepo = "https://github.com/${GIT_USERNAME}/${github_repo}.git"
        git url: "${trackingrepo}", branch: 'master', credentialsId: 'github'
        def workspace = pwd()

        def versionFileName = "version"
        versionFileName = groupId+"."+artifactId+"."+versionFileName
        def file = new File(versionFileName)
        sh("find .")
        def newVersionString = null;


        @Field def timeStamp = Calendar.getInstance().getTime().format('ddMMyy HH:mm:ss',TimeZone.getTimeZone('GMT'))

        def exists = fileExists versionFileName
        if (exists)  {
            echo "${versionFileName} Exists."
            def versiondata = sh(returnStdout: true, script: "cat ${versionFileName} | head -1")
            println "Existing version data : "+versiondata
            newVersionString = "{ \"build\": \"${env.BUILD_NUMBER}\", \"timestamp\": \"${timeStamp}\", \"commitId\": \"${commitId}\", \"commitMsg\": \"${commitmsg}\"}"
            println "New version data :  : "+newVersionString
            sh(returnStdout: true, script: "echo ${newVersionString} > ${versionFileName}")
            def newversiondata = sh(returnStdout: true, script: "cat ${versionFileName} | head -1")
        }
        else {
            echo "${versionFileName} does not Exist."
            sh("touch ${versionFileName}")
            newVersionString = "{ \"build\": \"${env.BUILD_NUMBER}\", \"timestamp\": \"${timeStamp}\", \"commitId\": \"${commitId}\", \"commitMsg\": \"${commitmsg}\"}"
            sh(returnStdout: true, script: "echo ${newVersionString} > ${versionFileName}")
            def newversiondata = sh(returnStdout: true, script: "cat ${versionFileName} | head -1")
        }

        sh (returnStdout: true, script: "git config user.email \"jenkins@${GIT_USERNAME}.dev\"; git config user.name \"${GIT_USERNAME}\"")
        sh (returnStdout: true, script: "git add ${versionFileName}")
        sh (returnStdout: true, script: "git commit -m \"version data update for ${artifactId} to ${newVersionString}\" || true")
        sh (returnStdout: true, script: "git push https://${GIT_USERNAME}:${GIT_PASSWORD}@github.com/${GIT_USERNAME}/${github_repo}.git master || true")
    }
}