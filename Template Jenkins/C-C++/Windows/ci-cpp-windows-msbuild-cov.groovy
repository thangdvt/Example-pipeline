/** PROJECT PROPERTIES
* project: Project Name
* gitUrl: Link repository
* branch: Branch Name exist in repository
* credentialID: Login to repository
*/
def project = 'FHO.PID.Csharp-Windows-Example'
def gitUrl = 'https://git3.fsoft.com.vn/GROUP/DevOps/example-application/csharp_example.git'
def branch = 'master'
def credentialID = 'fsoft-ldap-devopsgit'

/**SONARQUBE PROPERTIES
* sonar_host_url: Sonarqube server
* token_sonarqube: Use token push report 
*/
def sonar_host_url = "${SERVER_SONAR1}"
def token_sonarqube = " "

/** COVERITY PROPERTIES
* cov_server: Server name
* cov_key: path to authentication key coverity
* analysis: commandline analyze issues
*/
def cov_key = "pipelinetemplate.key"
def cov_server = "${SERVER_COVERITY1}"
def analysis = "--security --distrust-all -en ASSIGN_NOT_RETURNING_STAR_THIS -en COPY_WITHOUT_ASSIGN -en MISSING_COPY_OR_ASSIGN -en SELF_ASSIGN -en AUDIT.SPECULATIVE_EXECUTION_DATA_LEAK -en COM.ADDROF_LEAK -en COM.BSTR.ALLOC -en COM.BSTR.BAD_COMPARE -en COM.BSTR.NE_NON_BSTR -en DC.STRING_BUFFER -en ENUM_AS_BOOLEAN -en FLOATING_POINT_EQUALITY -en HARDCODED_CREDENTIALS -en HFA -en INTEGER_OVERFLOW -en MISRA_CAST -en MIXED_ENUMS -en PARSE_ERROR -en RISKY_CRYPTO -en STACK_USE -en UNENCRYPTED_SENSITIVE_DATA -en USER_POINTER -en VCALL_IN_CTOR_DTOR -en WEAK_GUARD -en WEAK_PASSWORD_HASH --aggressiveness-level medium"

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
    * label: IP of agent build
    */
    agent {
        label 'agent-windows-example'
    }

    /** environment
    * SONAR_MSBUILD_HOME = "${tool 'sonar-scanner-3'}": Set up Sonarqube MSBuild tool
    * COVERITY_HOME = "/opt/cov-analysis-linux64-2018.12": Path to Coverity tool
    * BLACKDUCK_DETECT_HOME = "${tool 'synopsys-detect-latest'}" : download file synopsys-detect-latest.jar
    * MSBUILD = "C:/Program Files (x86)/Microsoft Visual Studio/2017/Community/MSBuild/15.0/Bin" : Path to MSBuild
    * PATH: Add environment variable
    */
    environment {
        JAVA_HOME = "${tool 'jdk-lastest-windows'}"
        SONAR_MSBUILD_HOME = tool name: "${SONAR_MSBUILD_WINDOWS}"
        COVERITY_HOME = "C:/Program Files/Coverity/Coverity Static Analysis"
        BLACKDUCK_DETECT_HOME = "${tool 'synopsys-detect-latest'}" 
        SYNOPSYS_SKIP_PHONE_HOME=true
        MSBUILD = 'C:/Program Files (x86)/Microsoft Visual Studio/2017/Community/MSBuild/15.0/Bin'
        PATH = "${env.JAVA_HOME}/bin;${env.SONAR_MSBUILD_HOME};${env.COVERITY_HOME}/bin;${env.MSBUILD};${env.PATH}"
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
        * build.bat: Build commandline use ant build tool
        */
        stage('Build') {
            steps {
                writeFile file: 'build.bat', text: 'MSBuild.exe shadowsocks-csharp.sln /t:Rebuild /p:Configuration=Debug'
                bat "${buildfile}"
            }
        }

        /** Sonarqube Scanner
        * Dsonar.sourceEncoding: Encoding of the source code. Default is default system encoding
        * Dsonar.language: Main language
        * Dsonar.scm.disabled: Disable SCM
        * Dsonar.sources: Path to source folder. Defaults to .
        */
        stage('Sonarqube') {
            steps {
                bat 'call SonarQube.Scanner.MSBuild.exe ' +
                    "begin /k:${project} " +
                    "/n:${project} " +
                    "/d:sonar.login=${token_sonarqube} " +
                    "/v:${currentBuild.number} " +
                    "/d:sonar.host.url=${sonar_host_url} " +
                    '/d:sonar.sourceEncoding=UTF-8'
                bat "call ${buildfile}"
                bat "call SonarQube.Scanner.MSBuild.exe /d:sonar.login=${token_sonarqube} end"
            }
        }

        /** Coverity Scanner
        * java: Main language
        * build.bat: Build commandline use ant build tool
        */
        stage('Scan Coverity'){
            steps{
                bat "cov-configure --cc1plus -c config.xml"
                bat "cov-build --dir covoutput -c config.xml build.bat"
                bat "cov-analyze --dir covoutput -c config.xml ${analysis}"
            }
        }

        stage('Commit Coverity') {
            steps {
                retry (3) {
                    bat "cov-commit-defects --dir covoutput -c config.xml ${cov_server} --auth-key-file ${cov_key} --stream ${project}"
                }
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
                bat "java -jar ${BLACKDUCK_DETECT_HOME}/synopsys-detect-latest.jar \
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
