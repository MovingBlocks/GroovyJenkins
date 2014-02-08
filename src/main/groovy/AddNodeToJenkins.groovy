import hudson.model.Node.Mode
import hudson.slaves.*
import jenkins.model.Jenkins

// Adds a new node (a "dumb slave") to a Jenkins Master

/*
Jenkins.instance.nodes.each {
    println "BEFORE - Agent: $it"
}
*/

// The "build" object is added by the Jenkins Groovy plugin and can resolve parameters and such
String agentName = build.buildVariableResolver.resolve('AgentName')
String agentDescription = build.buildVariableResolver.resolve('AgentDescription')
String agentHome = build.buildVariableResolver.resolve('AgentHome')
String agentExecutors = build.buildVariableResolver.resolve('AgentExecutors')

// There is a constructor that also takes a list of properties (env vars) at the end
DumbSlave dumb = new DumbSlave(agentName,       // Agent name, usually matches the host computer's machine name
        agentDescription,                       // Agent description
        agentHome,                              // Workspace on the agent's computer
        agentExecutors,                         // Number of executors
        Mode.EXCLUSIVE,                         // "Usage" field, EXCLUSIVE is "only tied to node", NORMAL is "any"
        "",                                     // Labels
        new JNLPLauncher(),                     // Launch strategy, JNLP is the Java Web Start setting services use
        RetentionStrategy.INSTANCE)             // Is the "Availability" field and INSTANCE means "Always"

Jenkins.instance.addNode(dumb)
println "Agent '$agentName' created"

/*
Jenkins.instance.nodes.each {
    println "AFTER - Agent: $it"
}
*/
