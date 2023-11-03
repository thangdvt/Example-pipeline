/** PROJECT PROPERTIES
* project: Project Name mapping project Sonar/Coveirty/Blackduck
* gitUrl: Link repository
* branch: Branch Name exist in repository
* credentialID: Login to repository
*/
def project = 'FHO.PID.Csharp-Dotnetcore-Windows-Example'
def gitUrl = 'https://git3.fsoft.com.vn/GROUP/DevOps/example-application/csharp_dotnetcore_example.git'
def branch = 'master'
def credentialID = 'fsoft-ldap-devopsgit'

/** COVERITY PROPERTIES
* cov_key: Authentication file
* cov_server: Server name
* analysis: commandline analyze issues
*/
def cov_key = "<path to auth-key>"
def cov_server = "${SERVER_COVERITY1}"
//def cov_server = "--host coverity.fsoft.com.vn --dataport 9090"
def analysis = "--distrust-all -en ASSIGN_NOT_RETURNING_STAR_THIS -en COPY_WITHOUT_ASSIGN -en MISSING_COPY_OR_ASSIGN -en SELF_ASSIGN -en AUDIT.SPECULATIVE_EXECUTION_DATA_LEAK -en COM.ADDROF_LEAK -en COM.BSTR.ALLOC -en COM.BSTR.BAD_COMPARE -en COM.BSTR.NE_NON_BSTR -en DC.STRING_BUFFER -en ENUM_AS_BOOLEAN -en FLOATING_POINT_EQUALITY -en HARDCODED_CREDENTIALS -en HFA -en INTEGER_OVERFLOW -en MISRA_CAST -en MIXED_ENUMS -en PARSE_ERROR -en RISKY_CRYPTO -en STACK_USE -en UNENCRYPTED_SENSITIVE_DATA -en USER_POINTER -en VCALL_IN_CTOR_DTOR -en WEAK_GUARD -en WEAK_PASSWORD_HASH --aggressiveness-level medium"


/**SONARQUBE PROPERTIES
* sonar_host_url: Sonarqube server :https://sonar1.fsoft.com.vn,https://sonar-dn.fsoft.com.vn
* token_sonarqube: Use token push report
*/
def sonar_host_url = "${SERVER_SONAR1}"
def token_sonarqube = " "


/** BLACKDUCK PROPERTIES
* blackduck_server: Black Duck server
* blackduck_token: Use token push report
*/
def blackduck_server = "${SERVER_BLACKDUCK}"
def blackduck_token = " "
def blackduck_exclude = "covoutput"


/** NOTIFICATION PROPERTIES
* email: Email address (Keep this mail, User can add personal email, separate with  ";")
* logSuccessMessage: Print message with green color
* logFailedMessage: Print message with red color
*/
def email = '4e61aeb0.FPTSoftware362.onmicrosoft.com@apac.teams.ms' // Keep this mail, User can add personal email, separate with  ";"

pipeline {
    /** agent
    * label: Name of node build
    */
    agent {
       label 'agent-windows-example'
    }
    /** environment
    * COVERITY_HOME = "/opt/cov-analysis-linux64-2018.12": Path to Coverity tool
    * BD_HOME = '/opt/blackduck-scanner': Path to Black Duck tool
    * MSBUILD = "C:/Program Files (x86)/Microsoft Visual Studio/2017/Community/MSBuild/15.0/Bin" : Path to MSBuild
    * PATH: Add environment variable
    */
    environment {
        JAVA_HOME = "${tool 'jdk-lastest-windows'}"
        BLACKDUCK_DETECT_HOME = "${tool 'synopsys-detect-latest'}" 
        SYNOPSYS_SKIP_PHONE_HOME=true
        DOTNET_SDK = "${tool 'dotnet-sdk-3.1.201-windows'}"
        DOTNET_ROOT =  "${env.DOTNET_SDK}"
        DOTNET_CLI_HOME = "${env.DOTNET_SDK}"
        COVERITY_HOME = 'C:/Program Files/Coverity/Coverity Static Analysis'
        PATH = "${env.DOTNET_CLI_HOME}/.dotnet/tools;${env.DOTNET_SDK};${env.BD_HOME}/bin;${COVERITY_HOME}/bin;${env.PATH}"
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
        /** 
        * Setup Configuration for Csharp registry
        */
          stage('Prepare Configuration') {
            steps {
                configFileProvider([configFile(fileId: 'nuget-hn', targetLocation: 'nuget.config')]) {
                    script {
                        def exists = fileExists "${env.DOTNET_CLI_HOME}/.dotnet/tools/dotnet-sonarscanner.exe"
                        if (!exists) {
                            bat 'dotnet tool install --global dotnet-sonarscanner --configfile nuget.config'
                        } else {
                            bat "echo 'dotnet-sonarscanner was installed in ${env.DOTNET_CLI_HOME}/.dotnet/tools'"
                        }
                    }
                }
            }
        }
        
           stage('Scan Coverity'){
            steps{
                bat "cov-configure --cs -c config.xml"
                bat "cov-build --dir covoutput -c config.xml dotnet build"
                bat "cov-analyze --dir covoutput -c config.xml ${analysis}"
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
                bat "dotnet sonarscanner begin /k:${project} " +
                    "/n:${project} " +
                    "/d:sonar.login=${token_sonarqube} " +
                    "/v:${currentBuild.number} " +
                    "/d:sonar.host.url=${sonar_host_url} " +
                    "/d:sonar.language=cs " +
                    '/d:sonar.sourceEncoding=UTF-8'
                bat 'dotnet build'
                bat "dotnet sonarscanner /d:sonar.login=${token_sonarqube} end"
            }
        }
        /** Coverity Scanner
        * java: Main language
        * build.bat: Build commandline use ant build tool
        */
     

        stage('Commit Coverity') {
            steps {
                retry (3) {
                    bat "cov-commit-defects --dir covoutput -c config.xml ${cov_server} --auth-key-file ${cov_key} --stream ${project}"
                }
            }
        }

        /** Black Duck Scanner
        *  snippet-matching: Path to source folder. Defaults to .
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
