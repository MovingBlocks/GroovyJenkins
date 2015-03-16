/**
 * This script is meant to be executed by a parameterized job in Jenkins to remove an agent definition
 *
 * SUGGESTED PAIRED PARAMETERS IN JENKINS (type, name, default values, description):
 *
 * String - dropletURL - "agentToDelete" - Name of the agent to delete
 */
import jenkins.model.Jenkins

//Handy debug logging
Jenkins.instance.nodes.each {
    println "BEFORE - Agent: $it"
}

String agentName = build.buildVariableResolver.resolve('dropletURL')
println "Resolved the agentName parameter: " + agentName
if (agentName == null) {
    println "Failed to find the agentName parameter, so can't well go delete an agent without that!"
    return
}

Jenkins.instance.nodes.each { node ->
    println "Found a node: $node"
    String nodeName = node.name
    if (nodeName.equals(agentName)) {
        println "Found the agent $agentName - deleting it"
        Jenkins.instance.removeNode(node)
    }
}

Jenkins.instance.nodes.each {
    println "AFTER - Agent: $it"
}

return