package org.eclipse.ditto.services.connectivity.messaging.rabbitmq;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.ditto.model.base.common.DittoConstants;
import org.eclipse.ditto.services.connectivity.messaging.AbstractConsumerActorTest;
import org.eclipse.ditto.services.connectivity.messaging.TestConstants;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Delivery;
import com.rabbitmq.client.Envelope;

import akka.actor.ActorRef;
import akka.actor.Props;

/**
 * Tests {@link RabbitMQConsumerActor}.
 */
public class RabbitMQConsumerActorTest extends AbstractConsumerActorTest<Delivery> {

    private static final Envelope ENVELOPE = new Envelope(1, false, "inbound", "ditto");

    @Override
    protected Props getConsumerActorProps(final ActorRef mappingActor) {
        return RabbitMQConsumerActor.props("rmq-consumer", mappingActor,
                TestConstants.Authorization.AUTHORIZATION_CONTEXT, ENFORCEMENT);
    }

    @Override
    protected Delivery getInboundMessage(final Map.Entry<String, Object> header) {
        final Map<String, Object> headers = new HashMap<>();
        headers.put(REPLY_TO_HEADER.getKey(), REPLY_TO_HEADER.getValue());
        headers.put(header.getKey(), header.getValue());

        return new Delivery(ENVELOPE,
                new AMQP.BasicProperties.Builder()
                        .contentType(DittoConstants.DITTO_PROTOCOL_CONTENT_TYPE)
                        .headers(headers)
                        .replyTo(REPLY_TO_HEADER.getValue()).build(),
                TestConstants.modifyThing().getBytes(StandardCharsets.UTF_8));
    }

}