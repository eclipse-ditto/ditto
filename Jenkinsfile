#!groovy
node {
  // Need to replace the '%2F' used by Jenkins to deal with / in the path (e.g. story/...)
  String theBranch = "${env.BRANCH_NAME}".replace('%2F', '-').replace('/','-');
  String theMvnRepo = "$WORKSPACE/../feature-repository-${theBranch}";

  stage('Checkout') {
    checkout scm
  }

  stage('set version to ${theBranch}') {
    withMaven(
      maven: 'maven-3.5.2',
      mavenLocalRepo: theMvnRepo) {

      sh "mvn versions:set -DnewVersion=0-${theBranch}-SNAPSHOT"
    }
  }

  stage('Build') {
    withMaven(
      maven: 'maven-3.5.2',
      mavenLocalRepo: theMvnRepo) {

      sh "mvn clean deploy javadoc:jar source:jar-no-fork" +
              " -T16 --batch-mode --errors" +
              " -Pbuild-documentation,internal-repos -DcreateJavadoc=true"
    }
  }
}
