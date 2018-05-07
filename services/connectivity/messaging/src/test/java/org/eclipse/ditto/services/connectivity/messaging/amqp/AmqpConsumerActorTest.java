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
package org.eclipse.ditto.services.connectivity.messaging.amqp;

import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;
import javax.jms.MessageConsumer;

import org.apache.qpid.jms.exceptions.IdConversionException;
import org.apache.qpid.jms.message.JmsMessage;
import org.apache.qpid.jms.provider.amqp.message.AmqpJmsTextMessageFacade;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.ExternalMessage;
import org.eclipse.ditto.model.connectivity.MappingContext;
import org.eclipse.ditto.services.connectivity.mapping.MessageMappers;
import org.eclipse.ditto.services.connectivity.messaging.MessageMappingProcessor;
import org.eclipse.ditto.services.connectivity.messaging.MessageMappingProcessorActor;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAttribute;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.ExtendedActorSystem;
import akka.actor.Props;
import akka.event.DiagnosticLoggingAdapter;
import akka.routing.DefaultResizer;
import akka.routing.RoundRobinPool;
import akka.testkit.javadsl.TestKit;

/**
 * Tests the AMQP {@link AmqpConsumerActor}.
 */
public class AmqpConsumerActorTest {

    private static final Config CONFIG = ConfigFactory.load("test");
    private static final String CONNECTION_ID = "connection";

    private static ActorSystem actorSystem;

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
    public void plainStringMappingTest() throws IdConversionException {
        new TestKit(actorSystem) {{
            final MappingContext mappingContext = ConnectivityModelFactory.newMappingContext(
                    "JavaScript",
                    MessageMappers.createJavaScriptMapperConfigurationBuilder()
                            .incomingScript("function mapToDittoProtocolMsg(\n" +
                                    "    headers,\n" +
                                    "    textPayload,\n" +
                                    "    bytePayload,\n" +
                                    "    contentType\n" +
                                    ") {\n" +
                                    "\n" +
                                    "    // ###\n" +
                                    "    // Insert your mapping logic here\n" +
                                    "    let namespace = \"org.eclipse.ditto\";\n" +
                                    "    let id = \"foo-bar\";\n" +
                                    "    let group = \"things\";\n" +
                                    "    let channel = \"twin\";\n" +
                                    "    let criterion = \"commands\";\n" +
                                    "    let action = \"modify\";\n" +
                                    "    let path = \"/attributes/foo\";\n" +
                                    "    let dittoHeaders = headers;\n" +
                                    "    let value = textPayload;\n" +
                                    "    // ###\n" +
                                    "\n" +
                                    "    return Ditto.buildDittoProtocolMsg(\n" +
                                    "        namespace,\n" +
                                    "        id,\n" +
                                    "        group,\n" +
                                    "        channel,\n" +
                                    "        criterion,\n" +
                                    "        action,\n" +
                                    "        path,\n" +
                                    "        dittoHeaders,\n" +
                                    "        value\n" +
                                    "    );\n" +
                                    "}")
                            .outgoingScript("function mapFromDittoProtocolMsg(\n" +
                                    "    namespace,\n" +
                                    "    id,\n" +
                                    "    group,\n" +
                                    "    channel,\n" +
                                    "    criterion,\n" +
                                    "    action,\n" +
                                    "    path,\n" +
                                    "    dittoHeaders,\n" +
                                    "    value\n" +
                                    ") {\n" +
                                    "\n" +
                                    "    // ###\n" +
                                    "    // Insert your mapping logic here\n" +
                                    "    let headers = {};\n" +
                                    "    headers['correlation-id'] = dittoHeaders['correlation-id'];\n" +
                                    "    let textPayload = \"Topic was: \" + namespace + \":\" + id;\n" +
                                    "    let contentType = \"text/plain\";\n" +
                                    "    // ###\n" +
                                    "\n" +
                                    "     return Ditto.buildExternalMsg(\n" +
                                    "        headers,\n" +
                                    "        textPayload,\n" +
                                    "        null,\n" +
                                    "        contentType\n" +
                                    "    );" +
                                    "}")
                            .build()
                            .getProperties()
            );

            final MessageMappingProcessor mappingProcessor = getMessageMappingProcessor(mappingContext);

            final Props messageMappingProcessorProps =
                    MessageMappingProcessorActor.props(getRef(), getRef(),
                            AuthorizationContext.newInstance(AuthorizationSubject.newInstance("foo:bar")),
                            mappingProcessor, CONNECTION_ID);

            final ActorRef processor = actorSystem.actorOf(messageMappingProcessorProps,
                    MessageMappingProcessorActor.ACTOR_NAME + "-plainStringMappingTest");

            final ActorRef underTest = actorSystem.actorOf(
                    AmqpConsumerActor.props("foo", Mockito.mock(MessageConsumer.class), processor));

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

    private MessageMappingProcessor getMessageMappingProcessor(@Nullable final MappingContext mappingContext) {
        return MessageMappingProcessor.of(CONNECTION_ID, mappingContext, actorSystem,
                Mockito.mock(DiagnosticLoggingAdapter.class));
    }

    @Test
    public void createWithDefaultMapperOnly() {
        new TestKit(actorSystem) {{
            final ActorRef underTest = setupActor(getTestActor(), null);
            final ExternalMessage in =
                    ConnectivityModelFactory.newExternalMessageBuilder(Collections.emptyMap()).withText("").build();
            underTest.tell(in, null);
        }};
    }

    private ActorRef setupActor(final ActorRef testActor, @Nullable final MappingContext mappingContext) {
        final MessageMappingProcessor mappingProcessor = getMessageMappingProcessor(mappingContext);

        final Props messageMappingProcessorProps =
                MessageMappingProcessorActor.props(testActor, testActor,
                        AuthorizationContext.newInstance(AuthorizationSubject.newInstance("foo:bar")),
                        mappingProcessor, CONNECTION_ID);

        final DefaultResizer resizer = new DefaultResizer(1, 5);

        return actorSystem.actorOf(new RoundRobinPool(2)
                .withDispatcher("message-mapping-processor-dispatcher")
                .withResizer(resizer)
                .props(messageMappingProcessorProps), MessageMappingProcessorActor.ACTOR_NAME + "-createWithDefaultMapperOnly");
    }
}
