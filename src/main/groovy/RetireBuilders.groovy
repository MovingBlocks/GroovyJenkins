
import hudson.model.*
import jenkins.model.Jenkins

def queue = Hudson.instance.queue
println "Queue contains ${queue.items.length} items"
def pendingModules = 0
def pendingEngines = 0
queue.items.each {
    println "Queue item: " + it.task.displayName
    println "Label: " + it.task.assignedLabel
    if (it.task.assignedLabel.toString().equals("module")) {
        println "Found a module! Incrementing"
        pendingModules++
    } else if (it.task.assignedLabel.toString().equals("engine")) {
        println "Found an engine! Incrementing"
        pendingEngines++
    }
}

println "Found $pendingModules modules and $pendingEngines engines in queue"

if (pendingModules + pendingEngines > 0) {
    println "There's interesting stuff in the queue so we won't be retiring anybody today"
    return;
}

// First step we check nodes and see if any exist named "builder.." - if so keep a name/id combo for later
def checkExecutors = false
String nameToRetire
String idToRetire
Jenkins.instance.nodes.each { node ->
    println "Found a node: $node"
    String nodeName = node.name
    if (nodeName.startsWith("builder")) {
        println "That node was a builder, so we might have something to retire"
        checkExecutors = true;
        nameToRetire = node.getNodeName()
        idToRetire = node.nodeDescription
    }
}

// Secondly if we had builder nodes check their executors - if any are busy we're not done
def retirementNeeded = true
if (checkExecutors) {
    println "Checking executors for builders"
    // Computers, unlike nodes, link directly to executors
    Jenkins.instance.computers.each { computer ->
        println "Computer: " + computer.name
        if (computer.name.startsWith("builder")) {
            // So we can check those here
            computer.executors.each { executor ->
                println "Executor: " + executor + ", is it busy? " + executor.busy
                if (executor.busy) {
                    retirementNeeded = false
                }
            }
        }
    }
} else {
    println "No builders, so nothing to retire"
    return
}

// Finally if we didn't find a single busy executor on a builder anywhere we can take one out
if (retirementNeeded) {
    // Earlier we set a single pair of node name + description (the droplet id) for a builder. Confirm.
    if (nameToRetire == null || idToRetire == null) {
        println "Huh, we didn't get a valid name + id combo to retire after all. Bug!"
        return
    }
    println "Going to retire $nameToRetire with id $idToRetire"

    StringParameterValue urlParam = new StringParameterValue("dropletURL", nameToRetire, "URL of the droplet")
    StringParameterValue idParam = new StringParameterValue("dropletID", idToRetire, "ID of the droplet")

    ParametersAction paramActions = build.actions.find {
        it instanceof ParametersAction
    }

    if (paramActions != null) {
        println "Current parameter list exists, so adding the new parameters to it"
        paramActions = paramActions.merge(new ParametersAction(idParam, urlParam))
        build.replaceAction(paramActions)
    }
    else {
        println "No current parameter list exists, setting it to the new parameters"
        paramActions = new ParametersAction(idParam, urlParam)
        build.addAction(paramActions)
    }

    def job = Hudson.instance.getJob('DestroyDropletAgent')
    println "Got the destroy droplet agent job? " + job
    job.scheduleBuild2(0, new Cause.UpstreamCause(build), paramActions)
} else {
    println "An executor was active somewhere, so no need for retirement yet"
}

return;
