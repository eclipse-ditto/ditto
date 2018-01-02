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
      maven: 'maven-3.3.9',
      mavenLocalRepo: theMvnRepo) {

      sh "mvn clean deploy javadoc:jar source:jar" +
              " -T16 --batch-mode --errors" +
              " -Pbuild-documentation,internal-repos -DcreateJavadoc=true" +
              " -Drevision=${theVersion}"
    }
  }
}
