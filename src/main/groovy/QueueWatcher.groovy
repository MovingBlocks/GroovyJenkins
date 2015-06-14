// This script is meant to run every few minutes and checks to see how busy Jenkins is.
// If the queue has more items than defined quotas then a new builder agent will be created
// If conversely the queue is empty and no builder executors are active a retirement job runs
// Types are available for either module builders or engine + module builders (favor engines)
// Various Jenkins plugins help priority and throttles, although single-executors are easier

import hudson.model.*
import jenkins.model.Jenkins

def queue = Hudson.instance.queue
println "Queue items: ${queue.items.length}"
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

// See how many droplets we have active
int engineDroplets = 0
int moduleDroplets = 0
Jenkins.instance.nodes.each { node ->
    println "Found a node: $node"
    // TODO: Use the engine/module labels to determine capacity rather than node names
    String nodeName = node.name
    if (nodeName.startsWith("enginebuilder")) {
        println "That node was an engine builder, so incrementing the counter"
        engineDroplets++
    } else if (nodeName.startsWith("modulebuilder")) {
        println "That node was a module builder, so incrementing the counter"
        moduleDroplets++
    } else if (nodeName.equals("martin-steiger.de")) {
        println "msteiger's builder is online, so incrementing the engine counter"
        engineDroplets++
    }
}

// Quotas per builder. Note in the if below we consider the Jenkins master to count as half a module builder (the + 0.5)
int engineQuotaPerDroplet = 2
int moduleQuotaPerDroplet = 20
int engineCapacity = engineQuotaPerDroplet * engineDroplets
int moduleCapacity = moduleQuotaPerDroplet * (engineDroplets + moduleDroplets + 0.5)

println "Found $pendingModules module(s) and $pendingEngines engine(s) in queue"
println "Got $engineDroplets engine builders and $moduleDroplets module builders"
println "So engine capacity is $engineCapacity and we need $pendingEngines"
println "And module capacity is $moduleCapacity and we need $pendingModules"
println "(Note: numbers may be off if any builders are still spinning up)"

// SCENARIO: There is stuff in the queue. Do math to see if we have enough capacity
if (pendingEngines > engineCapacity) {
    println "Need a engine builder! Spinning one up. No point checking for retirement"
    def job = Hudson.instance.getJob('ProvisionEngineBuilder')
    println "Got the engine provisioning job? " + job
    job.scheduleBuild(new Cause.UpstreamCause(build))
} else if (pendingModules > moduleCapacity) {
    println "Need a module builder! Spinning one up. No point checking for retirement"
    def job = Hudson.instance.getJob('ProvisionModuleBuilder')
    println "Got the module provisioning job? " + job
    job.scheduleBuild(new Cause.UpstreamCause(build))

// SCENARIO: There isn't anything in the queue at all. See if we need to retire a builder.
// Unless of course we have no builder droplets in the first place, in which case we don't care.
} else if (engineDroplets + moduleDroplets > 0 && pendingEngines + pendingModules == 0) {
    println "Nothing relevant in queue - checking for builder nodes that may need retirement"

    // Secondly if we had builder nodes check their executors - if any are busy we're not done
    def retirementNeeded = true
    if (engineDroplets + moduleDroplets > 0) {
        println "Checking executors for builders"
        // Computers, unlike nodes, link directly to executors
        Jenkins.instance.computers.each { computer ->
            println "Computer: " + computer.name
            if (computer.name.contains("builder")) {
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
        println "No busy builders, so nothing to retire"
        return
    }

    // Finally if we didn't find a single busy executor we can actually kick off retirement
    if (retirementNeeded) {
        def job = Hudson.instance.getJob('RetireBuilders')
        println "Got the retirement job? " + job
        job.scheduleBuild(new Cause.UpstreamCause(build))
    } else {
        println "An executor was active somewhere, so no need for retirement yet"
        return
    }
//SCENARIO: There not enough in the queue to worry about (possibly even nothing)
// If there are any builders then they're busy so no need to check for retirement
} else {
    println "Didn't have enough yet that we can't handle. Keep calm and carry on building when needed"
}

return;