// So yeah, this is totally inefficient and could be replaced with a quick bit of RegEx magic
// I wrote this while sick and can barely manage RegEx when healthy ... :-) Brute force ftw!
// This is written to be used as a POST BUILD Groovy script, not a "normal" Groovy step
// The goal is specific to a job that as its main task creates a new server on DigitalOcean
// where some useful information will be parsed out and made available for any downstream jobs
// It is pretty fragile on what it expects the build log to contain, need RegEx post-haste!

import hudson.model.ParametersAction
import hudson.model.StringParameterValue

String wholeLog = manager.build.logFile.text

manager.listener.logger.println "\n*** Parsing log for droplet info to turn into parameters ***\n"
String snippet
int start = wholeLog .indexOf("Region")
if (start == -1) {
    manager.listener.logger.println "failed to find valid output"
    return
}

snippet = wholeLog.substring(start)
int firstNewLine = snippet.indexOf('\n')
//manager.listener.logger.println "First new line: " + firstNewLine
snippet = snippet.substring(firstNewLine)
//manager.listener.logger.println "snippet follows:"
//manager.listener.logger.println(snippet)

String[] tokens = snippet.split()
//tokens.each { token ->
//    manager.listener.logger.println("Token: " + token)
//}

int numDroplets = tokens.length / 10
if (tokens.length % 10 != 0 || numDroplets < 1) {
    manager.listener.logger.println "Invalid input (not ten tokens per droplet), something broke?"
    return
}

int newDroplet = -1
// Use BUILD_NUMBER from the Jenkins job as a sequence number in the URL
def urlSequence = manager.getEnvVariable("BUILD_NUMBER")
String expectedURL = "builder" + urlSequence + ".terasology.net"
manager.listener.logger.println("Expected URL: " + expectedURL)
for (int i = 0; i < tokens.length / 10; i++) {
    manager.listener.logger.println "Checking for index " + i
    manager.listener.logger.println "URL token is " + tokens[(i * 10 + 1)]
    if (tokens[(i * 10 + 1)].contains(expectedURL)) {
        manager.listener.logger.println "Found the new droplet in row " + (i + 1)
        newDroplet = i
    }
}

if (newDroplet == -1) {
    manager.listener.logger.println "Failed to find the new droplet, sadface"
    return
}

manager.listener.logger.println "Found the droplet!"
String dropletID = tokens[(newDroplet * 10)]
String dropletURL = tokens[(newDroplet * 10 + 1)]
String dropletIP = tokens[(newDroplet * 10 + 6)]
manager.listener.logger.println "Its ID is " + dropletID
manager.listener.logger.println "Its URL is " + dropletURL
manager.listener.logger.println "Its IP is " + dropletIP


StringParameterValue idParam = new StringParameterValue("dropletID", dropletID, "ID of the newly created droplet")
StringParameterValue urlParam = new StringParameterValue("dropletURL", dropletURL, "URL of the newly created droplet")
StringParameterValue ipParam = new StringParameterValue("dropletIP", dropletIP, "IP of the newly created droplet")

ParametersAction paramActions = manager.build.actions.find {
    it instanceof ParametersAction
}

if (paramActions != null) {
    manager.listener.logger.println "Current parameter list exists, so adding the new parameters to it"
    manager.build.replaceAction(paramActions.merge(new ParametersAction(idParam, urlParam, ipParam)))
}
else {
    manager.listener.logger.println "No current parameter list exists, setting it to the new parameters"
    manager.build.addAction(new ParametersAction(idParam, urlParam, ipParam))
}

manager.listener.logger.println "Parameters updated to include new parameters"