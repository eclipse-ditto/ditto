/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.connectivity.messaging.amqp;

import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;

import java.util.Collections;
import java.util.Map;

import javax.annotation.Nullable;
import javax.jms.JMSException;
import javax.jms.JMSRuntimeException;
import javax.jms.MessageConsumer;

import org.apache.qpid.jms.message.JmsMessage;
import org.apache.qpid.jms.provider.amqp.message.AmqpJmsTextMessageFacade;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.MappingContext;
import org.eclipse.ditto.services.connectivity.mapping.MessageMappers;
import org.eclipse.ditto.services.connectivity.messaging.AbstractConsumerActorTest;
import org.eclipse.ditto.services.connectivity.messaging.MessageMappingProcessor;
import org.eclipse.ditto.services.connectivity.messaging.MessageMappingProcessorActor;
import org.eclipse.ditto.services.connectivity.messaging.TestConstants;
import org.eclipse.ditto.services.models.connectivity.ExternalMessage;
import org.eclipse.ditto.services.models.connectivity.ExternalMessageFactory;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAttribute;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeatureProperty;
import org.junit.Test;
import org.mockito.Mockito;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.DiagnosticLoggingAdapter;
import akka.routing.DefaultResizer;
import akka.routing.RoundRobinPool;
import akka.testkit.javadsl.TestKit;

/**
 * Tests the AMQP {@link AmqpConsumerActor}.
 */
public class AmqpConsumerActorTest extends AbstractConsumerActorTest<JmsMessage> {

    private static final String CONNECTION_ID = "connection";

    @Override
    protected Props getConsumerActorProps(final ActorRef mappingActor) {
        final MessageConsumer messageConsumer = Mockito.mock(MessageConsumer.class);
        return AmqpConsumerActor.props("consumer", messageConsumer, mappingActor,
                TestConstants.Authorization.AUTHORIZATION_CONTEXT, ENFORCEMENT);
    }

    @Override
    protected JmsMessage getInboundMessage(final Map.Entry<String, Object> header) {
        return getJmsMessage(TestConstants.modifyThing(), "enforcement", header, REPLY_TO_HEADER);
    }

    @Test
    public void plainStringMappingTest() throws JMSException {
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
                    MessageMappingProcessorActor.props(getRef(), getRef(), mappingProcessor, CONNECTION_ID);

            final ActorRef processor = actorSystem.actorOf(messageMappingProcessorProps,
                    MessageMappingProcessorActor.ACTOR_NAME + "-plainStringMappingTest");

            final ActorRef underTest = actorSystem.actorOf(
                    AmqpConsumerActor.props("foo", Mockito.mock(MessageConsumer.class), processor,
                            TestConstants.Authorization.AUTHORIZATION_CONTEXT, null));

            final String plainPayload = "hello world!";
            final String correlationId = "cor-";

            underTest.tell(getJmsMessage(plainPayload, correlationId), null);

            final Command command = expectMsgClass(Command.class);
            assertThat(command.getType()).isEqualTo(ModifyAttribute.TYPE);
            assertThat(command.getDittoHeaders().getCorrelationId()).contains(correlationId);
            assertThat(((ModifyAttribute) command).getAttributePointer()).isEqualTo(JsonPointer.of("/foo"));
            assertThat(((ModifyAttribute) command).getAttributeValue()).isEqualTo(JsonValue.of(plainPayload));
        }};
    }

    @SafeVarargs // varargs array is not modified or passed around
    private static JmsMessage getJmsMessage(final String plainPayload, final String correlationId,
            final Map.Entry<String, ?>... headers) {
        try {
            final AmqpJmsTextMessageFacade messageFacade = new AmqpJmsTextMessageFacade();
            messageFacade.setText(plainPayload);
            messageFacade.setContentType("text/plain");
            messageFacade.setCorrelationId(correlationId);
            for (final Map.Entry<String, ?> e : headers) {
                messageFacade.setApplicationProperty(e.getKey(), e.getValue());
            }
            return messageFacade.asJmsMessage();
        } catch (final JMSException e) {
            throw new JMSRuntimeException(e.getMessage(), e.getErrorCode(), e.getCause());
        }
    }

    @Test
    public void createWithDefaultMapperOnly() {
        new TestKit(actorSystem) {{
            final ActorRef underTest = setupActor(getTestActor(), getTestActor(), null);
            final ExternalMessage in =
                    ExternalMessageFactory.newExternalMessageBuilder(Collections.emptyMap()).withText("").build();
            underTest.tell(in, null);
        }};
    }

    private ActorRef setupActor(final ActorRef publisherActor, final ActorRef conciergeForwarderActor,
            @Nullable final MappingContext mappingContext) {
        final MessageMappingProcessor mappingProcessor = getMessageMappingProcessor(mappingContext);

        final Props messageMappingProcessorProps =
                MessageMappingProcessorActor.props(publisherActor, conciergeForwarderActor, mappingProcessor,
                        CONNECTION_ID);

        final DefaultResizer resizer = new DefaultResizer(1, 5);

        return actorSystem.actorOf(new RoundRobinPool(2)
                        .withDispatcher("message-mapping-processor-dispatcher")
                        .withResizer(resizer)
                        .props(messageMappingProcessorProps),
                MessageMappingProcessorActor.ACTOR_NAME + "-" + name.getMethodName());
    }

    @Test
    public void jmsMessageWithNullPropertyAndNullContentTypeTest() throws JMSException {
        new TestKit(actorSystem) {{

            final ActorRef testActor = getTestActor();
            final MessageMappingProcessor mappingProcessor = getMessageMappingProcessor(null);

            final Props messageMappingProcessorProps =
                    MessageMappingProcessorActor.props(testActor, testActor, mappingProcessor, CONNECTION_ID);

            final ActorRef processor = actorSystem.actorOf(messageMappingProcessorProps,
                    MessageMappingProcessorActor.ACTOR_NAME + "-jmsMessageWithNullPropertyAndNullContentTypeTest");

            final ActorRef underTest = actorSystem.actorOf(
                    AmqpConsumerActor.props("foo123", Mockito.mock(MessageConsumer.class), processor,
                            TestConstants.Authorization.AUTHORIZATION_CONTEXT, null));

            final String correlationId = "cor-";
            final String plainPayload =
                    "{ \"topic\": \"com.bosch.test/testThing/things/twin/commands/modify\"," +
                            " \"headers\":{\"device_id\":\"com.bosch.test:testThing\"}," +
                            " \"path\": \"/features/point/properties/x\", \"value\": 42 }";

            final AmqpJmsTextMessageFacade messageFacade = new AmqpJmsTextMessageFacade();
            messageFacade.setApplicationProperty("JMSXDeliveryCount", null);
            messageFacade.setText(plainPayload);
            messageFacade.setContentType(null);
            messageFacade.setCorrelationId(correlationId);
            final JmsMessage jmsMessage = messageFacade.asJmsMessage();
            underTest.tell(jmsMessage, null);

            final Command command = expectMsgClass(Command.class);
            assertThat(command.getType()).isEqualTo(ModifyFeatureProperty.TYPE);
            assertThat(command.getDittoHeaders().getCorrelationId()).contains(correlationId);
            assertThat(((ModifyFeatureProperty) command).getPropertyPointer()).isEqualTo(JsonPointer.of("/x"));
            assertThat(((ModifyFeatureProperty) command).getPropertyValue()).isEqualTo(JsonValue.of(42));
        }};
    }

    private MessageMappingProcessor getMessageMappingProcessor(@Nullable final MappingContext mappingContext) {
        return MessageMappingProcessor.of(CONNECTION_ID, mappingContext, actorSystem,
                Mockito.mock(DiagnosticLoggingAdapter.class));
    }
}
