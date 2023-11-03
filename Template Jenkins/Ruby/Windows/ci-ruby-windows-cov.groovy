/** PROJECT PROPERTIES
* project: Project Name
* gitUrl: Link repository
* branch: Branch Name exist in repository
* credentialID: Login to repository
*/
def project = "FHO.PID.Ruby-Windows-Example"
def gitUrl = "https://git3.fsoft.com.vn/GROUP/DevOps/example-application/ruby_example.git"
def branch = "master"
def credentialID = "fsoft-ldap-devopsgit"

/**SONARQUBE PROPERTIES
* sonar_host_url: Sonarqube server
* token_sonarqube: Use token push report 
*/
def sonar_host_url = "${SERVER_SONAR1}"
def token_sonarqube = ""

/** COVERITY PROPERTIES
* cov_key: authen key file coverity
* cov_server: Server name
* analysis: commandline analyze issues
*/
def cov_key = "authentication.key"
def cov_server = "${SERVER_COVERITY1}"
def analysis = "--distrust-all -en INSECURE_DIRECT_OBJECT_REFERENCE"
/** REQUIRED COVERITY ANALYSIS
* --distrust-all: It applies to all the checkers in the group Security (Tainted dataflow checker).
* --enable-audit-mode: Enables audit-mode analysis, which is intended to expose more potential security vulnerabilities by
considering additional potential data sources that could be used in an exploit.
/** OPTION COVERITY ANALYSIS
* --force: This setting forces a full re-analysis of the source, even if the source file or other source files on which it depends have not changed since it was previously analyzed.
* --webapp-security: Enables the checkers that are used for Web application security analyses.
* --webapp-security-aggressiveness-level <low|medium|high>: This option can assist security auditors who need to see more defects than developers might need to see. When analyzing code that uses unsupported Web application frameworks, medium or high
aggressiveness levels can be more useful than the default. Default is low.
*/

/** BLACKDUCK PROPERTIES
* blackduck_server: Black Duck server
* blackduck_token: Use token push report 
* blackduck_exclude: folder exclude
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

    /** agent
    * label: IP Node Build
    */
    agent {
        label 'agent-windows-example'
    }

    /** environment
    * SONAR_HOME = "${tool 'sonar-scanner-latest-windows'}": Set up Sonarqube tool
    * COVERITY_HOME = "C:/Program Files/Coverity/Coverity Static Analysis": Path to Coverity tool
    * BD_HOME = "C:/DevOpsTools/scan.cli-2018.12.4": Path to Black Duck tool
    * PATH: Add environment variable
    */
    environment {
        // RUBY_HOME = "${tool 'ruby-window-2.6.5-1'}"
        SONAR_HOME = "${tool 'sonar-scanner-latest-windows'}"
        COVERITY_HOME = "C:/Program Files/Coverity/Coverity Static Analysis"
        BLACKDUCK_DETECT_HOME = "${tool 'synopsys-detect-latest'}" 
        BLACKDUCK_CREDENTIALS = "${blackduck_token}"
        SYNOPSYS_SKIP_PHONE_HOME=true
        PATH = "${env.SONAR_HOME}/bin;${env.COVERITY_HOME}/bin;${env.BD_HOME}/bin;${env.PATH}"
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
                bat "sonar-scanner.bat " +
                    "-Dsonar.login=${token_sonarqube} " +
                    "-Dsonar.host.url=${sonar_host_url} " +
                    "-Dsonar.projectKey=${project} " +
                    "-Dsonar.projectName=${project} " +
                    "-Dsonar.sourceEncoding=UTF-8 " +
                    "-Dsonar.branch.name=${branch} "+
                    //"-Dsonar.branch.target=${branch} " +
                    //-Dsonar.scala.coverage.reportPaths = "paths to .resultset.json  report files" +
                    "-Dsonar.sources=. " +
                    "-Dsonar.projectVersion=${currentBuild.number}_${branch}"
            }
        }

        /** Coverity Scanner
        * ruby: Main language
        * fs-capture-search . --no-command: commandline capture when not commandlinebuild 
        */
        stage('Scan Coverity') {
            steps{
                bat "cov-configure --ruby -c config.xml"
                bat "cov-build --dir covoutput -c config.xml --fs-capture-search . --no-command --fs-capture-search-exclude-regex '[/\\\\]assets[/\\\\]'"
                bat "cov-analyze --dir covoutput -c config.xml ${analysis}"
            }
        }

        stage('Commit Coverity') {
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
                script{
                    bat "java -jar ${BLACKDUCK_DETECT_HOME}/synopsys-detect-latest.jar \
                        --blackduck.url=${blackduck_server} \
                        --blackduck.api.token=${blackduck_token} \
                        --detect.project.name=${project} \
                        --detect.project.version.name=v1.0 \
                        --detect.code.location.name=${project} \
                        --detect.ruby.include.dev.dependencies=true \
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
