/** PROJECT PROPERTIES
* project: Project Name mapping project Sonar/Coveirty/Blackduck
* gitUrl: Link repository
* branch: Branch Name exist in repository
* credentialID: Login to repository
*/
def project = "FHO.PID.Java-Gradle-Linux-Example"
def gitUrl = "https://git3.fsoft.com.vn/GROUP/DevOps/example-application/java_gradle_example.git"
def branch = "master"
def credentialID = "fsoft-ldap-devopsgit"

/**SONARQUBE PROPERTIES
* sonar_host_url: Sonarqube server:https://sonar1.fsoft.com.vn,https://sonar-dn.fsoft.com.vn
* token_sonarqube: Use token push report 
*/
def sonar_host_url = "${SERVER_SONAR1}"
def token_sonarqube = " "

/** COVERITY PROPERTIES
* cov_key: authentication key file coverity
* cov_server: Server name
* analysis: commandline analyze issues
*/
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

def cov_key = "<path to auth-key"
def cov_server = "${SERVER_COVERITY1}"
//def cov_server = "--host coverity.fsoft.com.vn --dataport 9090"
def analysis = "--distrust-all -en RISKY_CRYPTO -en UNENCRYPTED_SENSITIVE_DATA -en WEAK_GUARD -en WEAK_PASSWORD_HASH -en ATOMICITY -en ORM_LOST_UPDATE -en USE_AFTER_FREE --aggressiveness-level medium"

/** BLACKDUCK PROPERTIES
* blackduck_server: Black Duck server
* blackduck_token: Use token push report 
*/
def blackduck_server = "${SERVER_BLACKDUCK}"
def blackduck_token = " "
def blackduck_exclude = "covoutput"

/** NOTIFICATION PROPERTIES
* email: Email address (Keep this mail, User can add personal email, separate with  ";")
*/
def email = "4e61aeb0.FPTSoftware362.onmicrosoft.com@apac.teams.ms"

pipeline {

    /** agent
    * label: Name of resoure agent build
    */
    agent {
        label 'agent-linux-example'
    }

    /** environment
    * JAVA_HOME = "${tool 'jdk-lastest-linux'}": Set up Java jdk
    * COVERITY_HOME = "/opt/cov-analysis-linux64-2020.03": Path to Coverity tool
    * BLACKDUCK_DETECT_HOME = "${tool 'synopsys-detect-latest'}" : download file synopsys-detect-latest.jar
    * GRADLE_HOME = "${tool 'gradle-5.3'}": Set up Gradle tool
    * PATH: Add environment variable    
    */
    environment {
        JAVA_HOME = "${tool 'jdk-lastest-linux'}"
        COVERITY_HOME = "/opt/cov-analysis-linux64-2020.03"
        BLACKDUCK_DETECT_HOME = "${tool 'synopsys-detect-latest'}" 
        SYNOPSYS_SKIP_PHONE_HOME=true
        GRADLE_HOME = "${tool 'gradle-5.3.1'}"
        PATH = "${env.JAVA_HOME}/bin:${env.COVERITY_HOME}/bin:${env.GRADLE_HOME}/bin:${env.PATH}"
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
                extensions: [], gitTool: 'jgitapache',                 // extensions: [[$class: 'CleanBeforeCheckout']]: Clean Before Checkout
                submoduleCfg: [], 
                userRemoteConfigs: [[credentialsId: "${credentialID}", 
                url: "${gitUrl}"]]
                ])
            }
        }


        /** Build - Compile
        * writeFile: Write file build commandline
        * file: File name
        * text: commandline build
        * chmod +x build.sh: Add permission build.sh
        * build.bat: Build commandline use ant build tool
        */
        stage("Build") {
            steps {
            script {
                def build = readFile 'build.gradle'
                writeFile file: 'build.gradle', text: build + '''buildscript { 
                    repositories {
                        maven { url "https://hn-repo.fsoft.com.vn/repository/maven-gradle-plugin/" }
                    }
                    dependencies {
                        classpath "org.sonarsource.scanner.gradle:sonarqube-gradle-plugin:3.0"
                    }
                    }
                    apply plugin: "org.sonarqube"'''
                sh "gradle build"
                }
            }
        }

        /** Sonarqube Scanner   
        * Dsonar.sourceEncoding: Encoding of the source code. Default is default system encoding
        * Dsonar.language: Main language
        * Dsonar.scm.disabled: Disable SCM
        * Dsonar.sources: Path to source folder. Defaults to .
        * Dsonar.bracnh.name: Name of the branch (visible in the UI)
        * Dsonar.branch.target: Name of the branch where you intend to merge your short-lived branch at the end of its life. If left blank, this defaults to the master branch.
        */
        stage('Sonarqube') {
            steps {
                sh "gradle sonarqube --no-daemon -g path.gradle -Dhttp.nonProxyHosts=${sonar_host_url} " +
                    "-Dsonar.login=${token_sonarqube} " +
                    "-Dsonar.host.url=${sonar_host_url} " +
                    "-Dsonar.projectKey=${project} " +
                    "-Dsonar.projectName=${project} " +
                    "-Dsonar.sourceEncoding=UTF-8 " +
                    "-Dsonar.branch.name=${branch} " +
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
        stage('Scan Coverity'){
            steps{
                sh "cov-configure --java -c config.xml"
                sh "cov-build --dir covoutput -c config.xml gradle clean build --no-daemon"
                sh "cov-analyze --dir covoutput -c config.xml ${analysis}"
            }
        }

        stage('Commit Coverity'){
            steps{
                sh "chmod go-rwx ${cov_key}"
                retry (3) {
                    sh "cov-commit-defects --dir covoutput -c config.xml ${cov_server} --auth-key-file ${cov_key} --stream ${project}"
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
                sh "java -jar ${BLACKDUCK_DETECT_HOME}/synopsys-detect-latest.jar \
                --blackduck.url=${blackduck_server} \
                --blackduck.api.token=${blackduck_token} \
                --detect.project.name=${project} \
                --detect.project.version.name=v1.0 \
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
