// This script is meant to run in the Jenkins console
// It adds the Notification Plugin to all jobs in the View "Modules" and configures it

for (item in Hudson.instance.getView("Modules").getItems()) {
    notification = item.properties.find { 
      it.getKey().getClass() == com.tikal.hudson.plugins.notification.HudsonNotificationPropertyDescriptor
  }

  if (notification != null) {
    println(">>>>>>>> Skipping $item.name")
    continue
  }

  println(">>>>>>>> Adding notification plugin to $item.name")

  protocol = com.tikal.hudson.plugins.notification.Protocol.HTTP
  url = 'http://meta.terasology.org/modules/update'
  event = 'finalized'
  format = com.tikal.hudson.plugins.notification.Format.JSON
  timeout = 10000
  loglines = 0

  endpoint = new com.tikal.hudson.plugins.notification.Endpoint(protocol, url, event, format, timeout, loglines)
  notification = new com.tikal.hudson.plugins.notification.HudsonNotificationProperty([endpoint])

  item.addProperty(notification)
  item.save()
}
