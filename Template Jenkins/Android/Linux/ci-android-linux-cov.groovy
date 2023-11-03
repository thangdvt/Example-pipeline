/** PROJECT PROPERTIES
* project: Project Name
* gitUrl: Link repository
* branch: Branch Name exist in repository
* credentialID: Login to repository
*/
def project = "FHO.PID.Android-Linux-COV-Example"
def gitUrl = "https://git3.fsoft.com.vn/GROUP/DevOps/example-application/android_example.git"
def branch = "master"
def credentialID = "fsoft-ldap-devopsgit"

/**SONARQUBE PROPERTIES
* sonar_host_url: Sonarqube server
* token_sonarqube: Use token push report 
*/
def sonar_host_url = "${SERVER_SONAR1}"
def token_sonarqube = ""

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
* --android-security : enable checker for android app, lưu ý sử dụng vì sẽ tốn  thời gian và bộ nhớ 
* --force: This setting forces a full re-analysis of the source, even if the source file or other source files on which it depends have not changed since it was previously analyzed.
* --webapp-security: Enables the checkers that are used for Web application security analyses.
* --webapp-security-aggressiveness-level <low|medium|high>: This option can assist security auditors who need to see more defects than developers might need to see. When analyzing code that uses unsupported Web application frameworks, medium or high
aggressiveness levels can be more useful than the default. Default is low.
*/
def cov_key = "authentication.key"
def cov_server = "${SERVER_COVERITY1}"
def analysis = "--distrust-all --android-security -en HARDCODED_CREDENTIALS -en RISKY_CRYPTO  --aggressiveness-level medium"

/** BLACKDUCK PROPERTIES
* blackduck_server: Black Duck server
* blackduck_token: Use token push report 
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
        
        label 'agent-linux-example'
    }

    /** environment
    * JAVA_HOME = "${tool 'jdk-lastest-linux'}": Set up Java 
    * ANDROID_HOME = "/opt/android-sdk-linux" : Path to Android 
    * COVERITY_HOME ="/opt/cov-analysis-linux64-2020.03": Path to Coverity tool
    * BLACKDUCK_DETECT_HOME = "${tool 'synopsys-detect-latest'}" :  Setup Black Duck tool
    * PATH: Add environment variable
    */
    environment {
        JAVA_HOME = "${tool 'jdk-lastest-linux'}"
        ANDROID_HOME = "/opt/android-sdk-linux"
        COVERITY_HOME ="/opt/cov-analysis-linux64-2020.03"
        BLACKDUCK_DETECT_HOME = "${tool 'synopsys-detect-latest'}" 
        SYNOPSYS_SKIP_PHONE_HOME=true
        PATH = "${env.JAVA_HOME}/bin;${env.COVERITY_HOME}/bin;${env.ANDROID_HOME}/bin;${env.ANDROID_HOME}/tools;${env.ANDROID_HOME}/platform-tools;${env.PATH}"
    }

    /** Checkout
    * Get source code from SVN, Git,...
    */
    stages {
        stage('Checkout') {
            steps {
                script{
                    checkout([$class: 'GitSCM',
                        branches: [[name: "${branch}"]],
                        doGenerateSubmoduleConfigurations: false,
                        extensions: [], gitTool: 'jgitapache',   // extensions: [[$class: 'CleanBeforeCheckout']]: Clean Before Checkout
                        submoduleCfg: [],
                        userRemoteConfigs: [[credentialsId: "${credentialID}",
                            url: "${gitUrl}"]]
                    ])
                }
            }
        }

        /** Sonarqube Scanner
        * Dsonar.sourceEncoding: Encoding of the source code. Default is default system encoding
        * Dsonar.scm.disabled: Disable SCM
        * Dsonar.sources: Path to source folder. Defaults to .
        * Dsonar.java.binaries: Comma-separated paths to directories containing the compiled bytecode files corresponding to your source files
        * Dsonar.exclusions: exclude folder, language 
        * Dsonar.inclusions: include folder, language 
        * Dsonar.branch.target: Determines the branch that will merge after the short-lived branch ends the life cycle.
        * Dsonar.branch.name=${branch} : multi branch
        */
        stage('Build & Sonar') {
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
                       } 
                      sh "type ${WORKSPACE}\\build.gradle"
                      sh "cd ${WORKSPACE}\\Project & ./gradlew assembleDebug sonarqube --no-daemon"+ // tùy vào dự án sử dụng "build" hay sử dụng "assembleDebug"
                      "-Dsonar.login=${token_sonarqube} "+
                      "-Dsonar.host.url=${sonar_host_url} "+
                      "-Dsonar.projectKey=${project} "+
                      "-Dsonar.projectName=${project} "+
                      "-Dsonar.sourceEncoding=UTF-8 "+
                      "-Dsonar.branch.name=${branch} "+ //update branch cần scan
                      "-Dsonar.exclusions= "+ // update các folder cần exclude
                      "-Dsonar.java.binaries=. "+ //update lại đúng path binaries
                      "-Dsonar.projectVersion=${currentBuild.number}_${branch} "
             }
             }
             
        /** Coverity Scanner
        * java: Main language
        * build.bat: Build commandline use ant build tool
        */
        stage('Scan Coverity'){
            steps{
                script{
                    sh "cov-configure --java "
                    sh "cov-build --dir covoutput  --fs-capture-search . ./gradlew assembleDebug cleanBuildCache --no-daemon" //tùy vào dự án sử dụng "build" hay sử dụng "assembleDebug"
                    sh "cov-analyze --dir covoutput  ${analysis} "
                }
            }
        }

        stage('Commit Coverity'){
            steps{
                script{
                    retry (3) {
                        sh "cov-commit-defects --dir covoutput ${cov_server} --auth-key-file ${cov_key} --stream ${project}"
                    }
                }
            }
        }

    
        /** Black Duck Scanner
        * snippet-matching: Path to source folder. Defaults to .
        * verbose: Display verbose output
        * insecure: Ignore TLS validation failures
        * exclusion.name.patterns : exclude folder don't need to scan 
        * java.path : Main path java 
        * Use SNIPPET_MATCHING substitute FULL_SNIPPET_MATCHING for first time to avoid err 
        */
       stage('Blackduck') {
            steps {
                sh "java -jar ${BLACKDUCK_DETECT_HOME}/synopsys-detect-latest.jar \
                    --blackduck.url=${blackduck_server} \
                    --blackduck.api.token=${BD_HUB_TOKEN} \
                    --detect.project.name=${project} \
                    --detect.project.version.name=v1.0 \
                    --detect.code.location.name=${project} \
                    --detect.java.path=. \
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
