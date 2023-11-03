/** PROJECT PROPERTIES
* project: Project Name
* gitUrl: Link repository
* branch: Branch Name exist in repository
* credentialID: Login to repository
*/
def project = "FHO.PID.Python-AP-Example"
def gitUrl = "https://git3.fsoft.com.vn/GROUP/DevOps/example-application/python_example.git"
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
*/
def email = "4e61aeb0.FPTSoftware362.onmicrosoft.com@apac.teams.ms"

pipeline {

    /** agent
    * label: Agent name will execute pipeline
    * inheritFrom: Container template
    * defaultContainer: Container 
    */
    agent {
        kubernetes {
            label "jenkins-${JOB_BASE_NAME}-${BUILD_NUMBER}"
            inheritFrom 'centos-76'
            defaultContainer 'centos-76'
        }
    }

    /** environment
    * JAVA_HOME = "${tool 'jdk-lastest-linux'}": Set up Java jdk
    * SONAR_HOME = "${tool 'sonar-scanner-latest-linux'}": Set up Sonarqube tool
    * BLACKDUCK_DETECT_HOME = "${tool 'synopsys-detect-latest'}" : Steup BlackDuck tools
    * PATH: Add environment variable
    */
    environment {
        JAVA_HOME = "${tool 'jdk-lastest-linux'}"
        SONAR_HOME = "${tool 'sonar-scanner-latest-linux'}"
        BLACKDUCK_DETECT_HOME = "${tool 'synopsys-detect-latest'}" 
        SYNOPSYS_SKIP_PHONE_HOME=true
        PATH = "${env.JAVA_HOME}/bin:${env.SONAR_HOME}/bin:${env.PATH}"
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
                    extensions: [], gitTool: 'jgitapache',  // extensions: [[$class: 'CleanBeforeCheckout']]: Clean Before Checkout
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
        * Dsonar.sources: Path to source folder. Defaults to .
        * Dsonar.login : Use to login authentication
        * Dsonar.host.url :	the server URL
        * Dsonar.projectKey : The project's unique key 
        * Dsonar.projectName : Name of the project that will be displayed on the web interface
        * Dsonar.sourceEncoding=UTF-8 : Encoding of the source files
        * Dsonar.projectVersion :The project version.
        * Dsonar.branch.target: Determines the branch that will merge after the short-lived branch ends the life cycle.
        * Dsonar.branch.name=${branch} : multi branch
        */
        stage('Sonarqube') {
            steps {
                sh "sonar-scanner " +
                    "-Dsonar.login=${token_sonarqube} " +
                    "-Dsonar.host.url=${sonar_host_url} " +
                    "-Dsonar.projectKey=${project} " +
                    "-Dsonar.projectName=${project} " +
                    "-Dsonar.sourceEncoding=UTF-8 " + // Can change UTF-8 to accord with your source 
                    //"-Dsonar.branch.name=${branch} " +
                    //"-Dsonar.python.coverage.reportPath = path.coverage.xml " +
                    "-Dsonar.sources=. " +
                    "-Dsonar.projectVersion=${currentBuild.number}_${branch}"
            }
        }

          /** Black Duck Scanner
        * snippet-matching: Path to source folder. Defaults to .
        * insecure: Ignore TLS validation failures
        * exclusion.name.patterns : exclude folder don't need to scan 
        * Use SNIPPET_MATCHING substitute FULL_SNIPPET_MATCHING for first time to avoid err 
        * Use python.python3 if your source is python 3
        * Use python.python2 if your source is python 2
        */

          stage('Blackduck') {
            steps {
                sh "java -jar ${BLACKDUCK_DETECT_HOME}/synopsys-detect-latest.jar \
                --blackduck.url=${blackduck_server} \
                --blackduck.api.token=${blackduck_token} \
                --detect.project.name=${project} \
                --detect.project.version.name=v1.0 \
                --detect.python.python3=true \
                --detect.code.location.name=${project} \
                --detect.blackduck.signature.scanner.license.search=true \
                --detect.blackduck.signature.scanner.snippet.matching=SNIPPET_MATCHING \
                --detect.blackduck.signature.scanner.exclusion.name.patterns=${blackduck_exclude}"
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
