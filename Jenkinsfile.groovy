#!/usr/bin/env groovy

def deployPlaybook = "playbooks/staging-redeploy.yml"

node {

    def commons

    stage("log") {
        checkout scm
        commons = load("jenkinsfile-commons.groovy")

        commons.dumpEnv()
    }

    try {

        stage("build-deps") {
            dir("project-commons") {
                sh "mvn clean install"
            }
        }

        stage("test") {
            dir("project-bots") {
                sh "mvn clean package"
            }
        }

        stage("deploy to staging") {
            def deploy = commons.prompt('Deploy to staging system?')
            if (!deploy) {
                echo "Skipping deployment to staging system"
            } else {
                echo "Gonna deploy now to staging system"

                sh "echo \"${env.VAULT_PASS}\" > /tmp/${env.BUILD_TAG}_vault_pass"

                dir("project-ops") {
                    sh "ansible-playbook -i inventory -f 5 --vault-password-file /tmp/${env.BUILD_TAG}_vault_pass \"${deployPlaybook}\""
                }
            }
        }

        commons.success()

    } catch (err) {
        commons.handleError(err)
    } finally {

        catchError {
            stage("clean up") {
                // delete the temporary vault passfile. don't fail if the file doesn't exist
                sh "rm /tmp/${env.BUILD_TAG}_vault_pass || true"
            }
        }

        commons.sendNotifications()
    }
}
