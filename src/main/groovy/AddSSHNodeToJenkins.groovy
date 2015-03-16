/**
 * This script is meant to be executed by a parameterized job in Jenkins and will then create a new agent
 * (slave) as per the parameters. This version of the script creates agents that use the SSH setup on Linux
 * Furthermore it is set up to expect droplet type parameters from a job making servers on DigitalOcean
 *
 * SUGGESTED PAIRED PARAMETERS IN JENKINS (type, name, default values, description):
 *
 * Password - credentialID - <put your desired id here> - Jenkins configured credential to use
 *
 * Note: The credential needs to be in the format produced by running this in the Jenkins script console:
 *
 * com.cloudbees.plugins.credentials.CredentialsProvider.lookupCredentials(
 *        com.cloudbees.plugins.credentials.common.StandardUsernameCredentials.class,
 *        Jenkins.instance, null, null ).each { credential ->
 *    println(credential.id + ": " + credential.description)
 * }
 *
 * Note: The following are meant to be created in the calling job, but could be made for testing
 *
 * String - dropletURL - "agent1.terasology.net" - Use the droplet url as the agent name
 * String - dropletID - "1234567" - By putting the droplet ID in the agent description it is handy later
 * String - dropletIP - "192.168.1.1" - IP of the droplet becomes the target for the SSH call to hit
 */
import hudson.model.Node.Mode
import hudson.slaves.*
import jenkins.model.Jenkins
import hudson.plugins.sshslaves.SSHLauncher

//Handy debug logging
Jenkins.instance.nodes.each {
    println "BEFORE - Agent: $it"
}

// The "build" object is added by the Jenkins Groovy plugin and can resolve parameters and such
String credentialID = build.buildVariableResolver.resolve('credentialID')
String agentName = build.buildVariableResolver.resolve('dropletURL')
String agentDescription = build.buildVariableResolver.resolve('dropletID')
String agentIP = build.buildVariableResolver.resolve('dropletIP')

// Other parameters are just hard coded for now, adjust as needed
String agentHome = "/opt/jenkinsAgent"
String agentExecutors = 3
String agentLabels = "module engine"

// There is a constructor that also takes a list of properties (env vars) at the end, but haven't needed that yet
DumbSlave dumb = new DumbSlave(agentName, // Agent name, usually matches the host computer's machine name
        agentDescription, // Agent description
        agentHome, // Workspace on the agent's computer
        agentExecutors, // Number of executors
        Mode.EXCLUSIVE, // "Usage" field, EXCLUSIVE is "only tied to node", NORMAL is "any"
        agentLabels, // Labels
        new SSHLauncher(agentIP, 22, SSHLauncher.lookupSystemCredentials(credentialID), "", null, null, "", "", 60, 3, 15),
        RetentionStrategy.INSTANCE) // Is the "Availability" field and INSTANCE means "Always"
Jenkins.instance.addNode(dumb)
println "Agent '$agentName' created with $agentExecutors executors and home '$agentHome'"

Jenkins.instance.nodes.each {
    println "AFTER - Agent: $it"
}
