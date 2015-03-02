
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

if (pendingModules + pendingEngines == 0) {
    println "Going to shut down the build agents"
}

return;

