/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.amqpbridge.messaging;

import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.qpid.jms.exceptions.IdConversionException;
import org.apache.qpid.jms.message.JmsMessage;
import org.apache.qpid.jms.provider.amqp.message.AmqpJmsTextMessageFacade;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.amqpbridge.MappingContext;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAttribute;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.cluster.pubsub.DistributedPubSub;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.routing.DefaultResizer;
import akka.routing.RoundRobinPool;
import akka.testkit.javadsl.TestKit;

/**
 * Tests the {@link CommandProcessorActor}.
 */
public class CommandProcessorActorTest {

    private static final Config CONFIG = ConfigFactory.load("test");

    private static ActorSystem actorSystem;
    private static ActorRef pubSubMediator;

    @BeforeClass
    public static void setUp() {
        actorSystem = ActorSystem.create("AkkaTestSystem", CONFIG);
        pubSubMediator = DistributedPubSub.get(actorSystem).mediator();
    }

    @AfterClass
    public static void tearDown() {
        if (actorSystem != null) {
            TestKit.shutdownActorSystem(actorSystem, scala.concurrent.duration.Duration.apply(5, TimeUnit.SECONDS),
                    false);
        }
    }

    @Test
    @Ignore
    public void plainStringMappingTest() throws IdConversionException {
        new TestKit(actorSystem) {{
            final String targetActorPath = getTestActor().path().toStringWithoutAddress();
            pubSubMediator.tell(new DistributedPubSubMediator.Put(getTestActor()), null);

            final List<MappingContext> mappingContexts = new ArrayList<>();
            // TODO: fix mapping (code below causes timeout in CommandProcessorActorTest)
//            mappingContexts.add(AmqpBridgeModelFactory.newMappingContext(
//                    "text/plain",
//                    "JavaScript",
//                    PayloadMappers.createJavaScriptMapperOptionsBuilder()
//                        .loadMustacheJS(false)
//                        .incomingMappingScript("ditto_protocolJson.topic = 'org.eclipse.ditto/foo-bar/things/twin/commands/modify';" +
//                                "ditto_protocolJson.path = '/attributes/foo';" +
//                                "ditto_protocolJson.headers = ditto_mappingHeaders;" +
//                                "ditto_protocolJson.value = ditto_mappingString;")
//                        .outgoingMappingScript("ditto_mappingString = " +
//                                "\"Topic was: \" + ditto_protocolJson.topic + \"\\n\" +\n" +
//                                "\"Header correlation-id was: \" + ditto_protocolJson.headers['correlation-id'];")
//                        .build()
//                        .getAsMap()
//            ));

            final Props amqpCommandProcessorProps =
                    CommandProcessorActor.props(pubSubMediator, getRef(), AuthorizationSubject.newInstance("foo:bar"),
                            mappingContexts);
            final String amqpCommandProcessorName = CommandProcessorActor.ACTOR_NAME_PREFIX + "foo";

            final DefaultResizer resizer = new DefaultResizer(1, 5);
            final ActorRef underTest = actorSystem.actorOf(new RoundRobinPool(2)
                    .withDispatcher("command-processor-dispatcher")
                    .withResizer(resizer)
                    .props(amqpCommandProcessorProps), amqpCommandProcessorName);

            final String plainPayload = "hello world!";
            final String correlationId = "cor-";

            final AmqpJmsTextMessageFacade messageFacade = new AmqpJmsTextMessageFacade();
            messageFacade.setText(plainPayload);
            messageFacade.setContentType("text/plain");
            messageFacade.setCorrelationId(correlationId);
            final JmsMessage jmsMessage = messageFacade.asJmsMessage();
            underTest.tell(jmsMessage, null);

            final Command command = expectMsgClass(Command.class);
            assertThat(command.getType()).isEqualTo(ModifyAttribute.TYPE);
            assertThat(command.getDittoHeaders().getCorrelationId()).contains(correlationId);
            assertThat(((ModifyAttribute) command).getAttributePointer()).isEqualTo(JsonPointer.of("/foo"));
            assertThat(((ModifyAttribute) command).getAttributeValue()).isEqualTo(JsonValue.of(plainPayload));
        }};
    }
}
