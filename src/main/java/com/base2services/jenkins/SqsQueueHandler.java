package com.base2services.jenkins;

import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.DeleteMessageRequest;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.cloudbees.jenkins.GitHubWebHook;

import org.kohsuke.github.GHEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

import hudson.Extension;
import hudson.model.PeriodicWork;
import hudson.util.SequentialExecutionQueue;
import hudson.util.TimeUnit2;
import jenkins.github.aws.parser.MessageParser;

/**
 * Receives a message from SQS and triggers any builds
 *
 * @author aaronwalker
 */
@Extension
public class SqsQueueHandler extends PeriodicWork {

    private static final Logger LOGGER = Logger.getLogger(SqsQueueHandler.class.getName());

    private transient final SequentialExecutionQueue queue = new SequentialExecutionQueue(Executors.newFixedThreadPool(2));

    private final MessageParser messageParser = new MessageParser();

    @Override
    public long getRecurrencePeriod() {
        return TimeUnit2.SECONDS.toMillis(5);
    }

    @Override
    protected void doRun() throws Exception {
        if (queue.getInProgress().size() == 0) {
            final List<SqsProfile> profiles = SqsBuildTrigger.DescriptorImpl.get().getSqsProfiles();
            if (profiles.size() != 0) {
                queue.setExecutors(Executors.newFixedThreadPool(profiles.size()));
                for (final SqsProfile profile : profiles) {
                    queue.execute(new SQSQueueReceiver(profile));
                }
            }
        } else {
            LOGGER.fine("Currently Waiting for Messages from Queues");
        }
    }

    public static SqsQueueHandler get() {
        return PeriodicWork.all().get(SqsQueueHandler.class);
    }

    private class SQSQueueReceiver implements Runnable {

        private final SqsProfile profile;

        private SQSQueueReceiver(final SqsProfile profile) {
            this.profile = profile;
        }

        @Override
        public void run() {
            LOGGER.fine("looking for build triggers on queue:" + profile.sqsQueue);
            final AmazonSQS sqs = profile.getSQSClient();
            final String queueUrl = profile.getQueueUrl();
            final ReceiveMessageRequest receiveMessageRequest = new ReceiveMessageRequest(queueUrl);
            receiveMessageRequest.setWaitTimeSeconds(20);
            List<Message> messages = new ArrayList<>();

            try {
                messages = sqs.receiveMessage(receiveMessageRequest).getMessages();
            } catch (final Exception ex) {
                LOGGER.warning("Unable to retrieve messages from the queue. " + ex.getMessage());
            }

            for (final Message message : messages) {
                //Process the message payload
                try {
                    final String awsMessage = message.getBody();
                    LOGGER.fine("Received Message from AWS: " + awsMessage);

                    final String actualMessage = messageParser.extractActualGithubMessage(awsMessage);
                    LOGGER.fine("Actual Github Message: " + actualMessage);

                    GitHubWebHook.get().doIndex(GHEvent.PUSH, actualMessage);
                    LOGGER.fine("GitHub builds triggered.");

                } catch (final Exception ex) {
                    LOGGER.log(Level.SEVERE, "unable to trigger builds " + ex.getMessage(), ex);
                } finally {
                    //delete the message even if it failed
                    sqs.deleteMessage(new DeleteMessageRequest()
                            .withQueueUrl(queueUrl)
                            .withReceiptHandle(message.getReceiptHandle()));
                }
            }

        }
    }
}
