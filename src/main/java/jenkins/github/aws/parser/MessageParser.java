package jenkins.github.aws.parser;

import net.sf.json.JSONObject;

public class MessageParser {

    /**
     * The message will be received via SQS, but if Github was configured to submit it to SNS some extra json payload will be added.
     * This method extract the actual Github message.
     *
     * @param originalAwsMessage
     * @return the message as sent from Github
     */
    public String extractActualGithubMessage(final String originalAwsMessage) {
        final JSONObject json = JSONObject.fromObject(originalAwsMessage);
        if (json.has("Type")) {
            final String msg = json.getString("Message");
            return msg;
        } else {
            return originalAwsMessage;
        }
    }
}
