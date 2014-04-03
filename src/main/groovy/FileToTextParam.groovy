/**
 * This script converts a given File parameter to a Text parameter and re-adds it to the currently executing job in Jenkins.
 * Since you cannot forward File parameters to downstream jobs this allows a way to reuse text-based files as parameters
 *
 * Suggested config in Jenkins
 * - File parameter named anything of the users choice
 * - Add properties under "Advanced" on a "Execute system Groovy script" step:
 * fileParameterName=[name of your file parameter field in Jenkins]
 * futureTextParameterName=CovertedFileToText
 */

import hudson.model.*

// Validate parameters - these are expected as Groovy properties, so if not defined will cause script failure
if (build.buildVariableResolver.resolve(fileParameterName) == null || build.buildVariableResolver.resolve(fileParameterName) == "") {
    println "Variable '$fileParameterName' was not found or empty. Exiting early"
    return -1
}

// Printing the futureTextParameterName variable here will fail early if it isn't defined properly
println "Going to convert the '$fileParameterName' File parameter to a Text parameter named '$futureTextParameterName'"

// We know the job is parameterized or the earlier variable resolution would've failed
ParametersAction paramActions = build.actions.find {
    it instanceof ParametersAction
}

// Grab the right file parameter so we can use it File style - we know it exists
FileParameterValue fileParam = paramActions.find {
    it instanceof FileParameterValue && it.name.equals(fileParameterName)
}

// In theory you *could* have this hit a NPE by using a non-file parameter with the correct name but .. details details
println "The file parameter's file's text is \n" + fileParam.file.getString()

TextParameterValue textParam = new TextParameterValue(futureTextParameterName, fileParam.file.getString(), "File converted to Text parameter")
build.replaceAction(paramActions.merge(new ParametersAction(textParam)))

println "Parameters updated to include new Text parameter. Available now:"
build.actions.find{ it instanceof ParametersAction }.parameters.each {
    println it.name
}

// Without return we get a value in Jenkins printed that doesn't really serve a purpose
return