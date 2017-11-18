package utilities

public class ProjectTemplates {
    public static void addEmailTemplate(def job, def dslFactory) {
        String emailScript = dslFactory.readFileFromWorkspace("email_template/EmailTemplate.groovy")
        job.with {
            publishers {
                wsCleanup()
                extendedEmail('email@address.com', '$DEFAULT_SUBJECT', '$DEFAULT_CONTENT') {
                    configure { node ->
                        node / presendScript << emailScript
                        node / replyTo << '$DEFAULT_REPLYTO'
                        node / contentType << 'default'
                    }
                    trigger(triggerName: 'StillUnstable', subject: '$DEFAULT_SUBJECT', body: '$DEFAULT_CONTENT', replyTo: '$DEFAULT_REPLYTO', sendToDevelopers: true, sendToRecipientList: true)
                    trigger(triggerName: 'Fixed', subject: '$DEFAULT_SUBJECT', body: '$DEFAULT_CONTENT', replyTo: '$DEFAULT_REPLYTO', sendToDevelopers: true, sendToRecipientList: true)
                    trigger(triggerName: 'Failure', subject: '$DEFAULT_SUBJECT', body: '$DEFAULT_CONTENT', replyTo: '$DEFAULT_REPLYTO', sendToDevelopers: true, sendToRecipientList: true)
                }

            }
        }
    }
}