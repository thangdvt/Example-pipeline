/** PROJECT PROPERTIES
* project: Project Name
* gitUrl: Link repository
* branch: Branch Name exist in repository
* credentialID: Login to repository
*/
def project = "FHO.PID.TypeScript-AP-Example"
def gitUrl = "https://git3.fsoft.com.vn/GROUP/DevOps/example-application/typescript_example.git"
def branch = "master"
def credentialID = "fsoft-ldap-devopsgit"


/**SONARQUBE PROPERTIES
* sonar_host_url: Sonarqube server
* token_sonarqube: Use token push report 
*/
def sonar_host_url = "${SERVER_SONAR1}"
def token_sonarqube = ""

/** BLACKDUCK PROPERTIES
* blackduck_server: Black Duck server
* blackduck_token: Use token push report 
* blackduck_exclude : Use to exclude folder or file 
*/
def blackduck_server = "${SERVER_BLACKDUCK}"
def blackduck_token = ""
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
    agent {
        kubernetes {
            label "jenkins-${JOB_BASE_NAME}-${BUILD_NUMBER}"
            inheritFrom 'centos-76-cov-2020'
            defaultContainer 'centos-76'
        }
    }

    /** environment
     * SONAR_HOME = "${tool 'sonar-scanner-latest-linux'}": Set up Sonarqube tool
     * BD_HOME = "C:/DevOpsTools/scan.cli-2018.12.4": Path to Black Duck tool
     * NODEJS_HOME = "${tool 'nodejs-10'}": Set up Nodejs in APP
     * PATH: Add environment variable
     */
    environment {
        JAVA_HOME = "${tool 'jdk-lastest-linux'}"
        SONAR_HOME = "${tool 'sonar-scanner-latest-linux'}"
        BLACKDUCK_DETECT_HOME = "${tool 'synopsys-detect-latest'}" 
        BLACKDUCK_CREDENTIALS = "${blackduck_token}"
        SYNOPSYS_SKIP_PHONE_HOME=true
        NODEJS_HOME = "${tool 'nodejs-10'}"
        PATH = "${env.JAVA_HOME}/bin:${env.SONAR_HOME}/bin:${env.BD_HOME}/bin:${env.NODEJS_HOME}/bin:${env.PATH}"
    }

    stages {
        /** Checkout
        * Get source code from SVN, Git,...
        */
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

        /** Install Typescript
        * npmrc-hn: Configuration pointing repository Fsoft, site HN
        * npm install typescript: Install TypeScript
        */
        stage('Install TypeScript'){
            steps{
                configFileProvider([configFile(fileId: 'npmrc-hn', targetLocation: '.npmrc')]) {
                    sh 'npm install typescript'
                }
            }
        }

        /** Sonarqube Scanner
        * Dsonar.login: Authentication token
        * Dsonar.host.url: URL of sonar server
        * Dsonar.projectKey: Define ID of project
        * Dsonar.projectName: Define Name of project
        * Dsonar.sourceEncoding: Encoding of the source code. Default is default system encoding
        * Dsonar.branch.name: Sonar multibranch
        * Dsonar.typescript.lcov.reportPaths: path_to_test-reporter.xml
        * Dsonar.branch.name=${branch}: Name of the branch (visible in the UI). Use Long living branches or Short-lived branches
        * Dsonar.branch.target=${branch}: Name of the branch where you intend to merge your short-lived branch at the end of its life. If left blank, this defaults to the master branch.         
        * Dsonar.sources: Path to source folder. Defaults to .
        * Dsonar.projectVersion: define version project
        */
        stage("Sonarqube") {
            steps {
                sh "sonar-scanner " +
                    "-Dsonar.login=${token_sonarqube} " +
                    "-Dsonar.host.url=${sonar_host_url} " +
                    "-Dsonar.projectKey=${project} " +
                    "-Dsonar.projectName=${project} " +
                    "-Dsonar.sourceEncoding=UTF-8 " +
                    // "-Dsonar.branch.name=${branch} "+
                    //"-Dsonar.branch.target=${branch} " +
                    // "-Dsonar.typescript.lcov.reportPaths= "+                    
                    "-Dsonar.sources=. " +
                    "-Dsonar.projectVersion=${env.BUILD_NUMBER}_${branch}"
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
            updateGitlabCommitStatus name: 'JenkinsCI', state: 'success'
            emailext(attachLog: false,
                body: 'Please check it out, link : $BUILD_URL',
                subject: "SUCCESS :Job ${env.JOB_NAME} - Build# ${env.BUILD_NUMBER}",
                to: "${email}")
        }

        failure {
            updateGitlabCommitStatus name: 'JenkinsCI', state: 'failed'
            emailext(attachLog: true,
                body: 'Please check it out , link : $BUILD_URL',
                subject: "FAILED :Job ${env.JOB_NAME} - Build# ${env.BUILD_NUMBER}",
                to: "${email}")
        }
    }
}
