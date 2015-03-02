
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

if (pendingEngines > 0 || pendingModules > 2) {
    println "Need a build node! Spinning one up"
} else if (pendingModules == 0) {
    println "Nothing relevant in queue - checking for worker nodes"

    // First step we check nodes and see if any exist named "Worker.."
    def checkExecutors = false
    Jenkins.instance.nodes.each { node ->
        println "Found a node: $node"
        String nodeName = node.name
        if (nodeName.startsWith("Worker")) {
            println "That node was a worker, so we might have something to retire"
            checkExecutors = true;
        }
    }

    // Secondly if we had worker nodes check their executors - if any are busy we're not done
    def retirementNeeded = true
    if (checkExecutors) {
        // Computers, unlike nodes, link director to executors
        Jenkins.instance.computers.each { computer ->
            println "Computer: " + computer
            // So we can check those here
            computer.executors.each { executor ->
                println "Executor: " + executor + ", is it busy? " + executor.active
                if (executor.active) {
                    retirementNeeded = false
                }
            }
        }
    } else {
        println "No active builder nodes , so nothing to retire"
    }

    // Finally if we didn't find a single busy executor we can actually kick off retirement
    if (retirementNeeded) {
        def job = Hudson.instance.getJob('RetireWorkers')
        println "Got the retirement job? " + job
        job.scheduleBuild(new Cause.UpstreamCause(build))
    } else {
        println "An executor was active somewhere, so no need for retirement yet"
    }
}

return;