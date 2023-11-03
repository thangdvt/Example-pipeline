/** PROJECT PROPERTIES
* project: Project Name mapping project Sonar/Coverity/Blackduck
* gitUrl: Link repository
* branch: Branch Name exist in repository
* credentialID: Login to repository
*/
def project = "FHO.PID.Java-Maven-AP-COV-Example"
def gitUrl = "https://git3.fsoft.com.vn/GROUP/DevOps/example-application/java_maven_example.git"
def branch = "master"
def credentialID = "fsoft-ldap-devopsgit"

/**SONARQUBE PROPERTIES
* sonar_host_url: Sonarqube server:https://sonar1.fsoft.com.vn,https://sonar-dn.fsoft.com.vn
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
*/
def email = "4e61aeb0.FPTSoftware362.onmicrosoft.com@apac.teams.ms"

pipeline {
    agent {
        kubernetes {
            label "jenkins-${JOB_BASE_NAME}-${BUILD_NUMBER}"
            inheritFrom 'centos-76-cov-2020'
            defaultContainer 'centos-76-cov'
        }
    }

    /** environment
    * JAVA_HOME = "${tool 'jdk-lastest-linux'}" : Set up Java jdk
    * MAVEN_HOME = "${tool 'maven-3.6.3'}": Set up Maven
    * BLACKDUCK_DETECT_HOME = "${tool 'synopsys-detect-latest'}" : download file synopsys-detect-latest.jar
    * PATH: Add environment variable
    */
    environment {
        JAVA_HOME = "${tool 'jdk-lastest-linux'}"
        MAVEN_HOME = "${tool 'maven-3.6.3'}"
        BLACKDUCK_DETECT_HOME = "${tool 'synopsys-detect-latest'}" 
        SYNOPSYS_SKIP_PHONE_HOME=true
        PATH = "${env.JAVA_HOME}/bin:${env.COVERITY_HOME}/bin:${env.MAVEN_HOME}/bin:${env.PATH}"
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


        /** Build
        * maven-global-settings-hn: Config pointing repository Fsoft, size HN in https://hn-repo.fsoft.com.vn/
        * settings.xml: File defines values that configure Maven execution in various ways. Most commonly, it is used to define a local repository location, alternate remote repository servers, and authentication information for private repositories.
        * bat "mvn -s %MAVEN_SETTINGS% -Dversion=cm4.0 -DskipTests clean install": Build a Java - Maven project via the command line
        * cat build.sh: Reads data from the file 
        * chmod +x build.sh: add permission file
        * ./build.sh: execute file
        */
        stage('Build') {
            steps {
                configFileProvider([configFile(fileId: 'maven-global-settings-hn', targetLocation: 'settings.xml', variable: 'MAVEN_SETTINGS')]) { }
                writeFile file: 'build.sh', text: "mvn -s ${WORKSPACE}/settings.xml -Dversion=cm4.0 -DskipTests clean install"
                sh "build.sh"
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
        stage("Sonarqube") {
            steps {
                sh "mvn sonar:sonar " +
                    "-Dsonar.login=${token_sonarqube} " +
                    "-Dsonar.host.url=${sonar_host_url} " +
                    "-Dsonar.projectKey=${project} " +
                    "-Dsonar.projectName=${project} " +
                    "-Dsonar.sourceEncoding=UTF-8 " +
                    "-Dsonar.branch.name=${Branch} " +
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
                sh "java -jar ${BLACKDUCK_DETECT_HOME}/synopsys-detect-latest.jar \
                --blackduck.url=${blackduck_server} \
                --blackduck.api.token=${blackduck_token} \
                --detect.project.name=${project} \
                --detect.project.version.name=v1.0 \
                --detect.maven.build.command='--batch-mode -s settings.xml' \
                --detect.code.location.name=${project} \
                --detect.blackduck.signature.scanner.license.search=true \
                --detect.blackduck.signature.scanner.snippet.matching=SNIPPET_MATCHING \
                --detect.blackduck.signature.scanner.exclusion.name.patterns=${blackduck_exclude}"
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
