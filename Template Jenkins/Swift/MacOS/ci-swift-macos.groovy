/** PROJECT PROPERTIES
* project: Project Name
* gitUrl: Link repository
* branch: Branch Name exist in repository
* credentialID: Login to repository
*/
def project = "FHO.PID.Swift-MacOS-Example"
def gitUrl = "https://git3.fsoft.com.vn/GROUP/DevOps/example-application/swift_example.git"
def branch = "master"
def credentialID = "fsoft-ldap-devopsgit"

/**SONARQUBE PROPERTIES
* sonar_host_url: Sonarqube server
* token_sonarqube: Use token push report 
*/
def sonar_host_url = "${SERVER_SONAR1}"
<<<<<<< HEAD
def token_sonarqube = ""
=======
def token_sonarqube = "828c68d64870e6830e65300295dc95620b40e91b"
>>>>>>> c95934639f3e542e57b811c6d5c364d06109f04c

/** BLACKDUCK PROPERTIES
* blackduck_server: Black Duck server
* blackduck_token: Use token push report 
* blackduck_exclude : Use to exclude folder or file 
*/
def blackduck_server = "${SERVER_BLACKDUCK}"
<<<<<<< HEAD
def blackduck_token = ""
=======
def blackduck_token = "Y2JjZjg5MjgtOWI5OC00NjQzLTk2NmEtYzQzZmMzYmUwZGZmOjE5MGIzMmFhLWViZTQtNGU5MS1hMmUyLTc3YzJiODJhY2MxMQ=="
>>>>>>> c95934639f3e542e57b811c6d5c364d06109f04c
def blackduck_exclude = "covoutput"

/** NOTIFICATION PROPERTIES
* email: Email address (Keep this mail, User can add personal email, separate with  ";")
* logSuccessMessage: Print message with green color
* logFailedMessage: Print message with red color
*/
def email = "4e61aeb0.FPTSoftware362.onmicrosoft.com@apac.teams.ms"
def logSuccessMessage(_message){
    println("\u001b[32m>>>>>>>>>>>>>>>>>>>>${_message}<<<<<<<<<<<<<<<<<<<\u001b[32m.\u001b[0m")
}
def logFailedMessage(_message){
    println("\u001b[31m>>>>>>>>>>>>>>>>>>>>${_message}<<<<<<<<<<<<<<<<<<<\u001b[31m.\u001b[0m")
}

pipeline {

    /** agent
    * label: Agent name will execute pipeline
    */
    agent {
        label 'agent-macos-example'
    }

    /** environment
    * JAVA_HOME = "${tool 'jdk-8'}": Set up Java jdk    
    * SONAR_HOME = "${tool 'sonar-scanner-latest-linux'}": Set up Sonarqube tool
    * BD_HOME = '/opt/blackduck-scanner': Path to Black Duck tool
    * PATH: Add environment variable
    */
    environment {
        SONAR_HOME = "${tool 'sonar-scanner-latest-windows'}"
        BLACKDUCK_DETECT_HOME = "${tool 'synopsys-detect-latest'}" 
        BLACKDUCK_CREDENTIALS = "${blackduck_token}"
        SYNOPSYS_SKIP_PHONE_HOME=true
        PATH = "${env.SONAR_HOME}/bin:${env.BD_HOME}/bin:${env.PATH}"
    }


    /** Checkout
    * Get source code from SVN, Git,...
    */
    stages {
        stage('Checkout') {
            steps {
                checkout([$class: 'GitSCM',
                    branches: [[name: "${branch}"]],
                    doGenerateSubmoduleConfigurations: false,
                    extensions: [], gitTool: 'jgitapache', // extensions: [[$class: 'CleanBeforeCheckout']]: Clean Before Checkout
                    submoduleCfg: [],
                    userRemoteConfigs: [[credentialsId: "${credentialID}",
                        url: "${gitUrl}"]]
                ])
            }
        }

        /** Sonarqube Scanner
       * Dsonar.sourceEncoding: Encoding of the source code. Default is default system encoding
       * Dsonar.language: Main language
       * Dsonar.scm.disabled: Disable SCM
       * Dsonar.branch.name: Sonar multibranch
       * Dsonar.branch.name=${branch}: Name of the branch (visible in the UI). Use Long living branches or Short-lived branches
       * Dsonar.branch.target=${branch}: Name of the branch where you intend to merge your short-lived branch at the end of its life. If left blank, this defaults to the master branch.
       * Dsonar.sources: Path to source folder. Defaults to .
       */
        stage('Sonarqube') {
            steps {
                sh "sonar-scanner " +
                    "-Dsonar.login=${token_sonarqube} " +
                    "-Dsonar.host.url=${sonar_host_url} " +
                    "-Dsonar.projectKey=${project} " +
                    "-Dsonar.projectName=${project} " +
                    "-Dsonar.sourceEncoding=UTF-8 " +
                    "-Dsonar.branch.name=${branch} " +
                    //"-Dsonar.branch.target=${branch} " +
                    // -Dsonar.coverageReportPaths=sonarqube-generic-coverage.xml " + // XCode version 9.3 - 9.4.1
                    // -Dsonar.swift.coverage.reportPath = "Path of the coverage report generated from “llvm-cov show”" + " // XCode 7 - 9.2 
                    "-Dsonar.sources=. " +
                    "-Dsonar.projectVersion=${currentBuild.number}_${branch}"
            }
        }

        /** Black Duck Scanner
       * snippet-matching: Path to source folder. Defaults to .
       * verbose: Display verbose output
       * insecure: Ignore TLS validation failures
       */
      stage('Blackduck') {
            steps {
                script{
                    sh "java -jar ${BLACKDUCK_DETECT_HOME}/synopsys-detect-latest.jar \
                        --blackduck.url=${blackduck_server} \
                        --blackduck.api.token=${blackduck_token} \
                        --detect.project.name=${project} \
                        --detect.project.version.name=v1.0 \
                        --detect.code.location.name=${project} \
                        --detect.blackduck.signature.scanner.license.search=true \
                        --detect.blackduck.signature.scanner.snippet.matching=SNIPPET_MATCHING \
                        --detect.blackduck.signature.scanner.exclusion.name.patterns=covoutput \
                        "
                }
            }
        }
    }

    post {
        /**
        * Update status to GitLab after run CI
        * Send email notification 
        */
        success {
            emailext(attachLog: false,
                body: 'Please check it out, link : $BUILD_URL',
                subject: "SUCCESS :Job ${env.JOB_NAME} - Build# ${env.BUILD_NUMBER}",
                to: "${email}")
        }

        failure {
            emailext(attachLog: true,
                body: 'Please check it out , link : $BUILD_URL',
                subject: "FAILED :Job ${env.JOB_NAME} - Build# ${env.BUILD_NUMBER}",
                to: "${email}")
        }
    }
}
