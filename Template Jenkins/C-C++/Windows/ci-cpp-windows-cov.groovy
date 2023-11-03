/** PROJECT PROPERTIES
* project: Project Name
* gitUrl: Link repository
* branch: Branch Name exist in repository
* credentialID: Login to repository
*/
def project = "FHO.PID.Cpp-Windows-Example"
def gitUrl = "https://git3.fsoft.com.vn/GROUP/DevOps/example-application/cpp_windows_example.git"
def branch = "master"
def credentialID = "fsoft-ldap-devopsgit"

/**SONARQUBE PROPERTIES
* sonar_host_url: Sonarqube server
* token_sonarqube: Use token push report 
*/
def sonar_host_url = "${SERVER_SONAR1}"
def token_sonarqube = "70d386d92cfef4229ecd42330336f34dff852c13"

/** COVERITY PROPERTIES
* cov_user: User login 
* cov_pass: Pass login
* cov_server: Server name
* analysis: commandline analyze issues
*/
def cov_key = "pipelinetemplate.key"
def cov_server = "${SERVER_COVERITY1}"
def analysis = "--force --security --distrust-all -en ASSIGN_NOT_RETURNING_STAR_THIS -en COPY_WITHOUT_ASSIGN -en MISSING_COPY_OR_ASSIGN -en SELF_ASSIGN -en AUDIT.SPECULATIVE_EXECUTION_DATA_LEAK -en COM.ADDROF_LEAK -en COM.BSTR.ALLOC -en COM.BSTR.BAD_COMPARE -en COM.BSTR.NE_NON_BSTR -en DC.STRING_BUFFER -en ENUM_AS_BOOLEAN -en FLOATING_POINT_EQUALITY -en HARDCODED_CREDENTIALS -en HFA -en INTEGER_OVERFLOW -en MISRA_CAST -en MIXED_ENUMS -en PARSE_ERROR -en RISKY_CRYPTO -en STACK_USE -en UNENCRYPTED_SENSITIVE_DATA -en USER_POINTER -en VCALL_IN_CTOR_DTOR -en WEAK_GUARD -en WEAK_PASSWORD_HASH --aggressiveness-level high"

/** BLACKDUCK PROPERTIES
* blackduck_server: Black Duck server
* blackduck_token: Use token push report 
*/
def blackduck_server = "${SERVER_BLACKDUCK}"
def blackduck_token = "NzBkOGI0ZWMtNmM5Mi00OTgzLWI2NTctOTcyMTg5ODQzNDdlOjE4MTk0M2U0LTRkNGEtNDRmYi1iYmEwLTIwZmU5N2NjMGU0MA=="

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
        label 'agent-windows-example'
    }

    /** environment
    * SONAR_MSBUILD_HOME = "${tool 'sonar-scanner-3'}": Set up Sonarqube MSBuild tool
    * COVERITY_HOME = "C:/Program Files/Coverity/Coverity Static Analysis": Path to Coverity tool
    * BD_HOME = 'C:/DevOpsTools/scan.cli-2018.12.4': Path to Black Duck tool
    * MSBUILD = "C:/Program Files (x86)/Microsoft Visual Studio/2017/Community/MSBuild/15.0/Bin" : Path to MSBuild
    * PATH: Add environment variable
    */
    environment {
        SONAR_HOME = "${tool 'sonar-scanner-latest-windows'}"
        COVERITY_HOME = "C:/Program Files/Coverity/Coverity Static Analysis"
        BD_HOME = 'C:/DevOpsTools/scan.cli-2018.12.4'
        BD_HUB_TOKEN = "${blackduck_token}"
        MSBUILD = "C:/Program Files (x86)/Microsoft Visual Studio/2017/Community/MSBuild/15.0/Bin"
        PATH = "${env.SONAR_HOME}/bin;${env.COVERITY_HOME}/bin;${env.BD_HOME}/bin;${env.MSBUILD};${env.PATH}"
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
                    extensions: [], gitTool: 'jgitapache',
                    submoduleCfg: [],
                    userRemoteConfigs: [[
                        credentialsId: "${credentialID}",
                        url: "${gitUrl}"]]
                ])
                sleep 5
            }
        }

        /** Build - Compile
        * writeFile: Write file build commandline
        * file: File name
        * text: commandline build
        * build.bat: Build commandline use ant build tool
        */
        // stage('Build') {
        //     steps {
        //         writeFile file: 'build.bat', text: 'MSBuild.exe Demo.sln /t:Rebuild /p:Configuration=Debug'
        //         bat 'build.bat'
        //     }
        // }

        /** Sonarqube Scanner
        * Dsonar.sourceEncoding: Encoding of the source code. Default is default system encoding
        * Dsonar.language: Main language
        * Dsonar.scm.disabled: Disable SCM
        * Dsonar.sources: Path to source folder. Defaults to .
        */
        stage("Sonarqube") {
            steps {
                bat "sonar-scanner.bat " +
                    "-Dsonar.login=${token_sonarqube} " +
                    "-Dsonar.host.url=${sonar_host_url} " +
                    "-Dsonar.projectKey=${project} " +
                    "-Dsonar.projectName=${project} " +
                    "-Dsonar.sourceEncoding=UTF-8 " +
                    "-Dsonar.language=c++ " +
                    "-Dsonar.sources=. " +
                    "-Dsonar.projectVersion=${currentBuild.number}_${branch}"
            }
        }


        /** Coverity Scanner
        * php: Main language
        * fs-capture-search . --no-command: Commandline capture issue
        */
        stage("Scan Coverity"){
            steps{
                bat "cov-configure --compiler cc1plus --comptype g++ --template -c config.xml"
                bat "cov-build --dir covoutput -c config.xml make"
                bat "cov-analyze --dir covoutput -c config.xml ${analysis}"
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
                    "--verbose " +
                    "--snippet-matching ."
            }
        }
    }

    post {
        /** 
        * Delete workspace after run CI
        */
        always {
            deleteDir()
        }

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
