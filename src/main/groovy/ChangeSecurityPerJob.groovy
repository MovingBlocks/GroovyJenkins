/**
 * This script is meant to be executed by a parameterized job in Jenkins and will then update permissions on other jobs as per the parameters.
 *
 * Some shiny Groovy stuff has been added here and there as an exercise in learning more about Groovy and making it look pretty in an IDE.
 *
 * NOTE: It appears that when using Groovy to Job.addProperty() any existing one is not replaced, although that's not the case via UI (works fine).
 * This results in duplicate properties, in this case for AuthorizationMatrixProperty - can replicate by commenting out the removal in updateAMP
 *
 * Inspired by https://wiki.jenkins-ci.org/display/JENKINS/Grant+Cancel+Permission+for+user+and+group+that+have+Build+permission
 *
 * SUGGESTED PAIRED PARAMETERS IN JENKINS (type, name, default values, description):
 *
 * String - ViewName - TestView - What view(s) to process jobs from - can allow for easier testing and more exact tailoring. Comma separate multiple views
 * Boolean - MaintainExisting - Enabled - Whether to maintain existing permissions or replace them entirely
 * String - IncludeString - EVERYTHING - One or more strings to look for in each job name and include said job if found (comma separate multiple values). "EVERYTHING" (or no value) will cause no filtering. Case insensitive
 * String - ExcludeString - NOTHING - One or more strings to look for in each job name and exclude said job if found (comma separate multiple values). "NOTHING" (or no value) will cause no filtering. Case insensitive
 * String - BuildUsers - NOBODY - Which users to assign BUILD permissions for the job. Usernames may be case sensitive. NOBODY or empty is supported
 * String - CancelUsers - NOBODY - Which users to assign CANCEL permissions for the job. Usernames may be case sensitive. NOBODY or empty is supported
 */

import jenkins.model.Jenkins
import hudson.security.*
import hudson.model.*

// PARAMETERS /////////////////////////////////////////////////////////////////////////////////////

// Check on all the expected parameters from Jenkins - first what views to use
List views = build.buildVariableResolver.resolve('ViewName').tokenize(" ,")
println "Going to look for jobs in the following views: $views"

// Should existing permissions be respected or replaced?
String maintainExisting = build.buildVariableResolver.resolve('MaintainExisting')
boolean maintain = Boolean.valueOf(maintainExisting)
println "Are we maintaining old permissions? " + maintain

// List of Strings to include jobs found to contain them
List includes = build.buildVariableResolver.resolve('IncludeString').tokenize(" ,")
println "Including jobs containing the following: $includes - if empty everything will be included"
boolean includesActive = !(includes.size() == 0 || includes.first().equals("EVERYTHING"))

// List of Strings to exclude jobs found to contain them
List excludes = build.buildVariableResolver.resolve('ExcludeString').tokenize(" ,")
println "Excluding jobs containing the following: $excludes - if empty nothing will be excluded"
boolean excludesActive = !(excludes.size() == 0 || excludes.first().equals("NOTHING"))

// Set of users that can build jobs (whitespace and duplicates ignored in comma-separated parameter)
Set buildUsers = build.buildVariableResolver.resolve('BuildUsers').tokenize(" ,").toSet()
println "The following users will be set able to build: " + buildUsers

// Set of users that can cancel jobs (whitespace and duplicates ignored in comma-separated parameter)
Set cancelUsers = build.buildVariableResolver.resolve('CancelUsers').tokenize(" ,").toSet()
println "The following users will be set able to cancel: " + cancelUsers

// VALIDATE SENSIBLE INPUT ////////////////////////////////////////////////////////////////////////

// Prepare new permissions with what we were passed from Jenkins
Map<Permission,Set<String>> newPermissions = new HashMap<Permission, Set<String>>()

if (buildUsers.size() == 0 || buildUsers.first().equals("NOBODY")) {
    println "Was passed 'NOBODY' or nothing for build users, so not adding anybody"
} else {
    newPermissions.put(Item.BUILD, buildUsers)
}

if (cancelUsers.size() == 0 || cancelUsers.first().equals("NOBODY")) {
    println "Was passed 'NOBODY' or nothing for cancel users, so not adding anybody"
} else {
    newPermissions.put(Item.CANCEL, cancelUsers)
}

boolean newPermissionsToSet = true
if (newPermissions.size() == 0) {
    println "No new permissions to add at all"
    newPermissionsToSet = false
    if (maintain) {
        println "Maintaining old permissions yet not adding anything. Eh? Nothing to do, quitting early."
        return
    }
}

println "!" * 140
println " STARTING ".center(140,'!')
println "!" * 140

// For logging clarity (indentation)
String currentDepth = ""

// Because there may be nested views we need to go through them layer by layer. Yay recursion !
recurseViews(Jenkins.instance.getViews(), currentDepth, views, maintain, includes, includesActive, excludes, excludesActive, newPermissions, newPermissionsToSet)

def recurseViews(Collection<View> viewsToRecurse, String currentDepth, List views, boolean maintain, List includes, boolean includesActive, List excludes, boolean excludesActive, Map<Permission,Set<String>> newPermissions, boolean newPermissionsToSet) {
    currentDepth += "-"
    viewsToRecurse.each {
        println "$currentDepth Recursively checking on view: " + it
        if (it instanceof ListView) {
            processView(it, currentDepth, views, maintain, includes, includesActive, excludes, excludesActive, newPermissions, newPermissionsToSet)
        } else if (it instanceof hudson.plugins.nested_view.NestedView) {
            Collection<View> nestedViews = it.getViews()
            //println currentDepth + "- Found a nested view, it contains: $nestedViews"
            recurseViews(nestedViews, currentDepth, views, maintain, includes, includesActive, excludes, excludesActive, newPermissions, newPermissionsToSet)
        } else {
            println currentDepth + "- Ignoring view " + it
        }
    }
    currentDepth -= "-"
}

def processView(View listView, String currentDepth, List views, boolean maintain, List includes, boolean includesActive, List excludes, boolean excludesActive, Map<Permission,Set<String>> newPermissions, boolean newPermissionsToSet) {
    println currentDepth + "- Processing view: " + listView

    // Check that we care about this view
    if (listView.viewName in views) {
        //println currentDepth + "-- View $listView matched our desired views: $views"
    } else {
        println currentDepth + "-- Skipping view $listView as it isn't in our list of target views"
        return
    }

    println "!" * 140
    println " Starting work on view '$listView' ".center(140,'!')
    println "!" * 140

    for (job in listView.getItems()) {
        //println currentDepth + "-- Item: " + job

        println currentDepth + "-- Processing job '$job.name' of " + job.class

        // Expecting this to always be sensible and convenient, but any creative uses of this script may want to review
        job = (Job) job

        // Test includes
        if (includesActive) {
            String includedBy = includes.find { job.name.toString().toLowerCase().contains(it.toString().toLowerCase()) }
            if (includedBy == null) {
                println currentDepth + "--- Skipping job '$job.name' as it didn't match any of the set includes"
                continue
            }
        }

        // Test excludes
        if (excludesActive) {
            String excludedBy = excludes.find { job.name.toString().toLowerCase().contains(it.toString().toLowerCase()) }
            if (excludedBy != null) {
                println currentDepth + "--- Job '$job.name' excluded by '$excludedBy'"
                continue
            }
        }

        // Check if we're resetting old stuff or have no old stuff to maintain anyway (old stuff null or empty)
        AuthorizationMatrixProperty oldAmp = job.getProperty(AuthorizationMatrixProperty.class)
        if (!maintain || oldAmp == null || oldAmp.getGrantedPermissions().size() == 0) {
            println currentDepth + "--- Resetting job as we're not maintaining or have nothing to maintain"
            if (!newPermissionsToSet) {
                println currentDepth + "---- Nothing new to set either so already done"
                updateAMP(job, null)

            } else {
                println currentDepth + "---- Not maintaining old permissions so simply applying the new ones: " + newPermissions
                updateAMP(job, new AuthorizationMatrixProperty(newPermissions))
            }
            println '-' * 140
            continue
        }

        // At this point we know we both have new stuff to set and valid old entries to maintain and possibly merge
        Map<Permission,Set<String>> oldPermissions = oldAmp.getGrantedPermissions()
        Map<Permission,Set<String>> mergedPermissions = new HashMap<Permission,Set<String>> ()

        // For the Permissions that exist in both the existing AND the new Map we can use intersect to get the ones to merge
        oldPermissions.keySet().intersect(newPermissions.keySet()).each{
            def permissionName = it.id.substring(it.id.lastIndexOf('.') + 1)
            println currentDepth + "--- Going to merge OLD '$permissionName' permissions " + oldPermissions.get(it) + " with NEW " + newPermissions.get(it)
            Set<String> mergedSet = oldPermissions.get(it) + newPermissions.get(it)
            println currentDepth + "--- Merged a set of '$permissionName' permissions! New values: " + mergedSet
            mergedPermissions.put(it, mergedSet)
        }

        println currentDepth + "--- NEW permissions: " + newPermissions
        println currentDepth + "--- plus OLD permissions: " + oldPermissions
        println currentDepth + "--- plus MERGED permissions: " + mergedPermissions
        // Fun with Groovy Map math! Each + overwrites matches to the left, so any old replaces new, but is in turn replaced by matching merges
        Map<Permission,Set<String>> finalPermissions = newPermissions + oldPermissions + mergedPermissions
        println currentDepth + "--- equals FINAL permissions: " + finalPermissions
        println '-' * 140

        updateAMP(job, new AuthorizationMatrixProperty(finalPermissions))
    }
}

println "*" * 140
println ' DONE '.center(140,'*')
println "*" * 140

// UTILITY ////////////////////////////////////////////////////////////////////////////////////////
def updateAMP(Job job, AuthorizationMatrixProperty amp) {
    // First remove all old AMPs. Using addProperty from Groovy doesn't appear to replace old ones (using UI does replace)
    job.getAllProperties().each {
        if (it instanceof AuthorizationMatrixProperty) {
            // For each AMP found simply remove one. Not guaranteed to actually be the one we're looping through
            //println "REMOVING an old AMP (may not be this one): " + it
            job.removeProperty(AuthorizationMatrixProperty.class)
        }
    }

    // At this point no permissions are set and it will remain that way if we were passed a null AMP
    if (amp != null) {
        job.addProperty(amp)
    }
    job.save()
/*
    job.getAllProperties().each {
        if (it instanceof AuthorizationMatrixProperty) {
            println "AMP after update: " + it
        }
    }*/
}

// Without this return a list of job objects (without any interesting detail) is dumped at the bottom of the Jenkins console log
return