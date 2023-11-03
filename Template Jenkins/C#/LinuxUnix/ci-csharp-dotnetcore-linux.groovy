/** PROJECT PROPERTIES
* project: Project Name mapping project Sonar/Coveirty/Blackduck
* gitUrl: Link repository
* branch: Branch Name exist in repository
* credentialID: Login to repository
*/
def project = 'FHO.PID.Csharp-dotnetcore-AAP-Example'
def gitUrl = 'https://git3.fsoft.com.vn/GROUP/DevOps/example-application/csharp_dotnetcore_example.git'
def branch = 'master'
def credentialID = 'fsoft-ldap-devopsgit'

/**SONARQUBE PROPERTIES
* sonar_host_url: Sonarqube server
* token_sonarqube: Use token push report 
*/
def sonar_host_url = "${SERVER_SONAR1}"
def token_sonarqube = "0db0c92ac53bcf6903fffa6cf5293d268f2a2967"

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
    * label: Agent name will execute pipeline
    * inheritFrom: Container template
    * defaultContainer: Container 
    */
    agent {
        kubernetes {
            label "jenkins-${JOB_BASE_NAME}-${BUILD_NUMBER}"
            inheritFrom 'ubuntu-1604-cov'
            defaultContainer 'ubuntu-1604-cov'
        }
    }

    /** environment
    * COVERITY_HOME = "/opt/cov-analysis-linux64-2018.12": Path to Coverity tool
    * BLACKDUCK_DETECT_HOME = "${tool 'synopsys-detect-latest'}" : download file synopsys-detect-latest.jar
    * MSBUILD = "C:/Program Files (x86)/Microsoft Visual Studio/2017/Community/MSBuild/15.0/Bin" : Path to MSBuild
    * PATH: Add environment variable
    */
    environment {
        DOTNET_SDK = "${tool 'dotnet-sdk-3.1.201-linux'}"
        DOTNET_ROOT =  "${env.DOTNET_SDK}"
        DOTNET_CLI_HOME = "${env.DOTNET_SDK}"
        BLACKDUCK_DETECT_HOME = "${tool 'synopsys-detect-latest'}" 
        SYNOPSYS_SKIP_PHONE_HOME=true
        PATH = "${env.BD_HOME}/bin:${env.DOTNET_CLI_HOME}/.dotnet/tools:${env.DOTNET_SDK}:${env.PATH}"
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

        stage('Prepare Configuration') {
            steps {
                configFileProvider([configFile(fileId: 'nuget-hn', targetLocation: 'nuget.config')]) {
                    script {
                        def exists = fileExists "${env.DOTNET_CLI_HOME}/.dotnet/tools/dotnet-sonarscanner"
                        if (!exists) {
                            sh 'dotnet tool install --global dotnet-sonarscanner --configfile nuget.config'
                        } else {
                            sh "echo 'dotnet-sonarscanner was installed in ${env.DOTNET_CLI_HOME}/.dotnet/tools'"
                        }
                    }
                    
                }
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
                sh "dotnet sonarscanner begin /k:${project} " +
                    "/n:${project} " +
                    "/d:sonar.login=${token_sonarqube} " +
                    "/v:${currentBuild.number} " +
                    "/d:sonar.host.url=${sonar_host_url} " +
                    '/d:sonar.sourceEncoding=UTF-8'
                sh "dotnet build csharp_dotnetcore_example.sln"
                sh "dotnet sonarscanner /d:sonar.login=${token_sonarqube} end"
            }
        }

        /** Black Duck Scanner
        *  snippet-matching: Path to source folder. Defaults to .
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
