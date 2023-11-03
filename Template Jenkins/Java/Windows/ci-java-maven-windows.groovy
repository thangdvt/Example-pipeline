/** PROJECT PROPERTIES
* project: Project Name mapping project Sonar/Coveirty/Blackduck
* gitUrl: Link repository
* branch: Branch Name exist in repository
* credentialID: Login to repository
*/
def project = "FHO.PID.Java-Maven-Windows-Example"
def gitUrl = "https://git3.fsoft.com.vn/GROUP/DevOps/example-application/java_maven_example.git"
def branch = "master"
def credentialID = "fsoft-ldap-devopsgit"

/**SONARQUBE PROPERTIES
* sonar_host_url: Sonarqube server:https://sonar1.fsoft.com.vn,https://sonar-dn.fsoft.com.vn
* token_sonarqube: Use token push report 
*/
def sonar_host_url = "${SERVER_SONAR1}"
def token_sonarqube = "a01b0d4b38c5bb560da29d28b9fbeae49b7af37e"

/** BLACKDUCK PROPERTIES
* blackduck_server: Black Duck server
* blackduck_token: Use token push report 
*/
def blackduck_server = "blackduck.fsoft.com.vn --port 443 --scheme HTTPS"
def blackduck_token = ""
def blackduck_exclude = "/.scannerwork/"

/** NOTIFICATION PROPERTIES
* email: Email address (Keep this mail, User can add personal email, separate with  ";")
*/
def email = "4e61aeb0.FPTSoftware362.onmicrosoft.com@apac.teams.ms"

pipeline {
    /** agent
    * label: Name of node build
    */
    agent {
        label 'agent-windows-example'
    }

    /** environment
    * MAVEN_HOME = "${tool 'maven-3.6.3'}": Set up Java jdk
    * SONAR_HOME = tool name: "SONAR_SCANNER_WINDOWS": Set up Sonarqube tool
    * COVERITY_HOME = "C:/Program Files/Coverity/Coverity Static Analysis": Path to Coverity tool
    * BD_HOME = "C:/DevOpsTools/scan.cli-2018.12.4": Path to Black Duck tool
    * PATH: Add environment variable
    */
    environment {
        JAVA_HOME = "${tool 'jdk-lastest-windows'}"
        SONAR_HOME = "${tool 'sonar-scanner-latest-windows'}"
        COVERITY_HOME = "C:/Program Files/Coverity/Coverity Static Analysis"
        BD_HOME = 'C:/DevOpsTools/scan.cli-2018.12.4'
        BD_HUB_TOKEN = "${blackduck_token}"
        MAVEN_HOME = "${tool 'maven-3.6.3'}"
        PATH = "${env.JAVA_HOME}/bin;${env.SONAR_HOME}/bin;${env.COVERITY_HOME}/bin;${env.BD_HOME}/bin;${env.MAVEN_HOME}/bin;${env.PATH}"
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

        /** Build
        * maven-global-settings-hn: Config pointing repository Fsoft, size HN in https://hn-repo.fsoft.com.vn/
        * settings.xml: File defines values that configure Maven execution in various ways. Most commonly, it is used to define a local repository location, alternate remote repository servers, and authentication information for private repositories.
        * bat "mvn -s ${WORKSPACE}/settings.xml -Dversion=cm4.0 -DskipTests clean install": Build a Java - Maven project via the command line
        */
        stage('Build') {
            steps {
                configFileProvider([configFile(fileId: 'maven-global-settings-hn', targetLocation: 'settings.xml', variable: 'MAVEN_SETTINGS')]) { }
                writeFile file: 'build.bat', text: "mvn -s ${WORKSPACE}/settings.xml -Dversion=cm4.0 -DskipTests clean install"
                bat 'build.bat'
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
                bat "mvn sonar:sonar " +
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

        /** Black Duck Scanner
        * snippet-matching: Path to source folder. Defaults to .
        * verbose: Display verbose output
        * insecure: Ignore TLS validation failures
        * 1.0: This is version push report blackduck server, when you have change source code big, then you need change version e.g: 2.0, 3.0
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
