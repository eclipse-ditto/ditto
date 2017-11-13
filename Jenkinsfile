#!groovy
node {
  // Need to replace the '%2F' used by Jenkins to deal with / in the path (e.g. story/...)
  String theBranch = "${env.BRANCH_NAME}".replace('%2F', '_').replace('feature/','');
  String theMvnRepo = "$WORKSPACE/../feature-repository-${theBranch}";

  stage('Checkout') {
    checkout scm
  }

  // Mark the code build 'stage'....
  stage('Build') {
    withMaven(
      maven: 'maven-3.5.0',
      mavenLocalRepo: theMvnRepo) {

      sh "mvn install javadoc:jar source:jar-no-fork --batch-mode --errors -Pbuild-documentation -DcreateJavadoc=true"
    }
  }
}
