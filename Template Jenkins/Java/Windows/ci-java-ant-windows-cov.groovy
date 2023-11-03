/** PROJECT PROPERTIES
* project: Project Name mapping project Sonar/Coverity/Blackduck
* gitUrl: Link repository
* branch: Branch Name exist in repository
* credentialID: Login to repository
*/
def project = "FHO.PID.Java-Ant-Windows-Example"
def gitUrl = "https://git3.fsoft.com.vn/GROUP/DevOps/example-application/java_ant_example.git"
def branch = "master"
def credentialID = "fsoft-ldap-devopsgit"

/**SONARQUBE PROPERTIES
* sonar_host_url: Sonarqube server
* token_sonarqube: Use token push report 
*/
def sonar_host_url = "${SERVER_SONAR1}"
def token_sonarqube = "a01b0d4b38c5bb560da29d28b9fbeae49b7af37e"

/** COVERITY PROPERTIES
* cov_key: authen key file coverity
* cov_server: Server name
* analysis: commandline analyze issues
*/
def cov_key = "<path to auth-key>"
def cov_server = "${SERVER_COVERITY1}"
//def cov_server = "--host coverity.fsoft.com.vn  --dataport 9090"
def analysis = "--force --webapp-security --distrust-all -en RISKY_CRYPTO -en UNENCRYPTED_SENSITIVE_DATA -en WEAK_GUARD -en WEAK_PASSWORD_HASH -en ATOMICITY -en ORM_LOST_UPDATE -en USE_AFTER_FREE --enable-audit-mode --aggressiveness-level high"

/** BLACKDUCK PROPERTIES
* blackduck_server: Black Duck server
* blackduck_token: Use token push report 
*/
def blackduck_server = "blackduck.fsoft.com.vn --port 443 --scheme HTTPS"
def blackduck_token = ""
def blackduck_exclude = "/.scannerwork/"

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
    * label: Name of node build
    */
    agent {
        label 'agent-windows-example'
    }

    /** environment
    * SONAR_HOME = tool name: "SONAR_SCANNER_WINDOWS": Set up Sonarqube tool
    * COVERITY_HOME = "C:/Program Files/Coverity/Coverity Static Analysis": Path to Coverity tool
    * BD_HOME = "C:/DevOpsTools/scan.cli-2018.12.4": Path to Black Duck tool
    * ANT_HOME ="C:/apache-ant-1.10.7": Path to Apache Ant
    * PATH: Add environment variable
    */
    environment {
        JAVA_HOME = "${tool 'jdk-lastest-windows'}"
        SONAR_HOME = tool name: "SONAR_SCANNER_WINDOWS"
        COVERITY_HOME = "C:/Program Files/Coverity/Coverity Static Analysis"
        BD_HOME = "C:/DevOpsTools/scan.cli-2018.12.4"
        BD_HUB_TOKEN = "${blackduck_token}"
        ANT_HOME = "${tool 'ant-1.10.5-windows'}"
        PATH = "${env.JAVA_HOME}/bin;${env.SONAR_HOME}/bin;${env.ANT_HOME}/bin;${env.BD_HOME}/bin;${env.COVERITY_HOME}/bin;${env.PATH}"
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
                    extensions: [[$class: 'CleanBeforeCheckout']], gitTool: 'jgitapache',
                    submoduleCfg: [],
                    userRemoteConfigs: [[credentialsId: "${credentialID}",
                        url: "${gitUrl}"]]
                ])
            }
        }

        /** Build - Compile
        * writeFile: Write file build commandline
        * file: File name
        * text: Commandline build
        * build.bat: Build commandline use ant build tool
        */
        stage('Build') {
            steps {
                writeFile file: 'build.bat', text: "ant -buildfile build.xml"
                bat "build.bat"
            }
        }

        /** Sonarqube Scanner
        * Dsonar.sourceEncoding: Encoding of the source code. Default is default system encoding
        * Dsonar.language: Main language
        * Dsonar.scm.disabled: Disable SCM
        * Dsonar.sources: Path to source folder. Defaults to .
        * Dsonar.java.binaries: Comma-separated paths to directories containing the compiled bytecode files corresponding to your source files
        */
        stage("Sonarqube") {
            steps {
                bat "sonar-scanner.bat " +
                    "-Dsonar.login=${token_sonarqube} " +
                    "-Dsonar.host.url=${sonar_host_url} " +
                    "-Dsonar.projectKey=${project} " +
                    "-Dsonar.projectName=${project} " +
                    "-Dsonar.sourceEncoding=UTF-8 " +
                    // "-Dsonar.branch.name=${branch} " +
                    //-Dsonar.coverage.jacoco.xmlReportPaths = path_to_file_report " +
                    "-Dsonar.sources=. " +
                    "-Dsonar.java.binaries=. " +
                    "-Dsonar.projectVersion=${currentBuild.number}_${branch}"
            }
        }

        /** Coverity Scanner
        * java: Main language
        * build.bat: Build commandline use ant build tool
        */
        stage("Scan Coverity"){
            steps{
                bat "cov-configure.exe --java -c config.xml"
                bat "cov-build.exe --dir covoutput -c config.xml build.bat"
                bat "cov-analyze.exe --dir covoutput -c config.xml ${analysis}"
            }
        }

        stage("Commit Coverity"){
            steps{
                retry (3) {
                    bat "cov-commit-defects --dir covoutput -c config.xml ${cov_server} --auth-key-file ${cov_key} --stream ${project}"
                }
            }
        }

        /** Black Duck Scanner
        * snippet-matching: Path to source folder. Defaults to .
        * verbose: Display verbose output
        * insecure: Ignore TLS validation failures
        */
        stage('Blackduck') {
            steps {
                bat "scan.cli.bat " +
                    "--host ${blackduck_server} " +
                    "--project ${project} " +
                    "--name ${project}.scan " +
                    "--insecure " +
                    "--release 1.0 " +
                    "--exclude ${blackduck_exclude} " +
                    "--verbose " +
                    "--snippet-matching ."
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
