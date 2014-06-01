/**
 * This script adds a Text parameter named "agentList" to an executing job in Jenkins containing a list of all agents
 * NOTE: Absence of a parameter will default to true (not much point in a parameter-less job always returning empty)
 *
 * SUGGESTED PAIRED PARAMETERS IN JENKINS (type, name, default values, description):
 *
 * Boolean - IncludeOnline - True - Whether to include online agents
 * Boolean - IncludeOffline - True - Whether to include offline agents
 */

import hudson.model.*
import jenkins.model.Jenkins

// Should we include online nodes ? Note that in the absence of the parameter we default to true
String onlineParam = build.buildVariableResolver.resolve('IncludeOnline')
//println "onlineParam is $onlineParam"
boolean includeOnline = onlineParam == null ? true : Boolean.valueOf(onlineParam)
println "Are we including online nodes (default is true)? " + includeOnline

// Same for offline
String offlineParam = build.buildVariableResolver.resolve('IncludeOffline')
//println "offlineParam is $offlineParam"
boolean includeOffline = offlineParam == null ? true : Boolean.valueOf(offlineParam)
println "Are we including offline nodes (default is true)? " + includeOffline

println "Starting - going through the list of agents to prepare a parameter (named 'agentList') to set in the job"

String agentList = ""

// Go through nodes and build a list of agents matching the supplied online/offline booleans
Jenkins.instance.nodes.each {
    //println "Checking agent: $it.nodeName"
    if (includeOnline && !it.computer.offline || includeOffline && it.computer.offline) {
        agentList += it.nodeName + "\n"
        //println "Included agent: $it.nodeName"
    }
}

println "Agent list: \n" + agentList

TextParameterValue textParam = new TextParameterValue("agentList", agentList, "List of agents in this Jenkins")

ParametersAction paramActions = build.actions.find {
    it instanceof ParametersAction
}

if (paramActions != null) {
    println "Current parameter list exists, so adding new parameter to it"
    build.replaceAction(paramActions.merge(new ParametersAction(textParam)))
}
else {
    println "No current parameter list exists, setting it to the new parameter"
    build.addAction(new ParametersAction(textParam))
}

println "Parameters updated to include new 'agentList' Text parameter."
if (agentList.length() == 0) {
    println "WARNING: Said parameter is actually an empty list - may not be what you expected?"
}

/*
println "Available now:"
build.actions.find{ it instanceof ParametersAction }.parameters.each {
    println it.name
}
*/

// Without return we get a value in Jenkins printed that doesn't really serve a purpose
return