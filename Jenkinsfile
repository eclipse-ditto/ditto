#!groovy
node {
  // Need to replace the '%2F' used by Jenkins to deal with / in the path (e.g. story/...)
  String theBranch = "${env.BRANCH_NAME}".replace('%2F', '-').replace('/','-')
  String theVersion = "0-${theBranch}-SNAPSHOT"
  String theMvnRepo = "$WORKSPACE/../feature-repository-${theBranch}";

  stage('Checkout') {
    checkout scm
  }

  stage('Build') {
    withMaven(
      maven: 'maven-3.6.0',
      jdk: 'JDK11-OpenJDK',
      mavenLocalRepo: theMvnRepo) {

      sh "mvn clean deploy source:jar" +
              " -T16 --batch-mode --errors" +
              " -Pbuild-documentation,internal-repos" +
              " -Drevision=${theVersion}"
    }
  }

  stage('SonarQube analysis') {
    withSonarQubeEnv("${env.SONAR_QUBE_ENV}") {
      withMaven(
              maven: 'maven-3.6.0',
              jdk: 'JDK8',
              mavenLocalRepo: theMvnRepo) {

        sh "mvn --batch-mode --errors sonar:sonar -Dsonar.branch.name=${theBranch} -Drevision=${theVersion}"
      }
    }
  }
}
