package org.eclipse.ditto.services.connectivity.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.services.connectivity.messaging.TestConstants.header;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.Enforcement;
import org.eclipse.ditto.model.connectivity.IdEnforcementFailedException;
import org.eclipse.ditto.services.models.connectivity.OutboundSignal;
import org.eclipse.ditto.signals.commands.things.modify.ModifyThing;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.mockito.Mockito;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.event.DiagnosticLoggingAdapter;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;

public abstract class AbstractConsumerActorTest<M> {

    private static final Config CONFIG = ConfigFactory.load("test");
    private static final String CONNECTION_ID = "connection";
    protected static final Map.Entry<String, String> REPLY_TO_HEADER = header("reply-to", "reply-to-address");

    protected static ActorSystem actorSystem;

    @Rule
    public TestName name = new TestName();
    public static final Enforcement ENFORCEMENT =
            ConnectivityModelFactory.newEnforcement("{{ header:device_id }}", "{{ thing:id }}");

    @BeforeClass
    public static void setUp() {
        actorSystem = ActorSystem.create("AkkaTestSystem", CONFIG);
    }

    @AfterClass
    public static void tearDown() {
        if (actorSystem != null) {
            TestKit.shutdownActorSystem(actorSystem, scala.concurrent.duration.Duration.apply(5, TimeUnit.SECONDS),
                    false);
        }
    }

    @Test
    public void testInboundMessageWithEnforcementSucceeds() {
        testInboundMessageWithEnforcement(header("device_id", TestConstants.Things.THING_ID), true);
    }

    @Test
    public void testInboundMessageWithEnforcementFails() {
        testInboundMessageWithEnforcement(header("device_id", "_invalid"), false);
    }

    protected abstract Props getConsumerActorProps(final ActorRef mappingActor);

    protected abstract M getInboundMessage(final Map.Entry<String, Object> header);

    private void testInboundMessageWithEnforcement(final Map.Entry<String, Object> header,
            final boolean isForwardedToConcierge) {

        new TestKit(actorSystem) {{

            final TestProbe sender = TestProbe.apply(actorSystem);
            final TestProbe concierge = TestProbe.apply(actorSystem);
            final TestProbe publisher = TestProbe.apply(actorSystem);

            final ActorRef mappingActor = setupMessageMappingProcessorActor(publisher.ref(), concierge.ref());

            final ActorRef underTest = actorSystem.actorOf(getConsumerActorProps(mappingActor));

            underTest.tell(getInboundMessage(header), sender.ref());

            if (isForwardedToConcierge) {
                publisher.expectNoMessage();
                final ModifyThing modifyThing = concierge.expectMsgClass(ModifyThing.class);
                assertThat(modifyThing.getThingId()).isEqualTo(TestConstants.Things.THING_ID);
            } else {
                final OutboundSignal.WithExternalMessage outboundSignal =
                        publisher.expectMsgClass(OutboundSignal.WithExternalMessage.class);
                final IdEnforcementFailedException exception =
                        IdEnforcementFailedException.fromMessage(
                                outboundSignal.getExternalMessage().getTextPayload().orElse(""),
                                outboundSignal.getSource().getDittoHeaders());
                assertThat(exception.getErrorCode()).isEqualTo(IdEnforcementFailedException.ERROR_CODE);
                assertThat(exception.getDittoHeaders()).contains(REPLY_TO_HEADER);
                concierge.expectNoMessage();
            }
        }};
    }

    private ActorRef setupMessageMappingProcessorActor(final ActorRef publisherActor,
            final ActorRef conciergeForwarderActor) {
        final MessageMappingProcessor mappingProcessor = MessageMappingProcessor.of(CONNECTION_ID, null, actorSystem,
                Mockito.mock(DiagnosticLoggingAdapter.class));
        final Props messageMappingProcessorProps =
                MessageMappingProcessorActor.props(publisherActor, conciergeForwarderActor, mappingProcessor,
                        CONNECTION_ID);
        return actorSystem.actorOf(messageMappingProcessorProps,
                MessageMappingProcessorActor.ACTOR_NAME + "-" + name.getMethodName());
    }

}