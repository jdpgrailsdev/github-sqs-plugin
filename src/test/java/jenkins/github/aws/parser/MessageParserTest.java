package jenkins.github.aws.parser;

import static net.sf.json.JSONObject.fromObject;

import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;

public class MessageParserTest {

    private final MessageParser messageParser = new MessageParser();

    @Test
    public void extractActualGithubMessage() throws Exception {

        final String snsPayload = readPayload("sns-like-github-message.json");
        final String sqsPayload = readPayload("sqs-like-github-message.json");

        final String snsMessage = messageParser.extractActualGithubMessage(snsPayload);
        final String sqsMessage = messageParser.extractActualGithubMessage(sqsPayload);

        Assert.assertEquals(fromObject(snsMessage), fromObject(sqsMessage));

    }

    private String readPayload(final String resource) throws IOException {
        final InputStream resourceAsStream = this.getClass().getClassLoader().getResourceAsStream(resource);
        return IOUtils.toString(resourceAsStream, "UTF-8");
    }
}