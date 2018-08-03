/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 *
 */
package org.eclipse.ditto.services.connectivity.messaging.mqtt;

import java.util.HashMap;

import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.ExternalMessage;
import org.eclipse.ditto.services.utils.akka.LogUtil;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.Creator;
import akka.stream.alpakka.mqtt.MqttMessage;

public class MqttConsumerActor extends AbstractActor {

    static final String ACTOR_NAME_PREFIX = "mqttConsumer-";
    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);
    private final ActorRef messageMappingProcessor;
    private final AuthorizationContext sourceAuthorizationContext;


    private MqttConsumerActor(final ActorRef messageMappingProcessor,
            final AuthorizationContext sourceAuthorizationContext) {
        this.messageMappingProcessor = messageMappingProcessor;
        this.sourceAuthorizationContext = sourceAuthorizationContext;
    }

    static Props props(final ActorRef messageMappingProcessor,
            final AuthorizationContext sourceAuthorizationContext) {
        return Props.create(MqttConsumerActor.class, new Creator<MqttConsumerActor>() {
            private static final long serialVersionUID = 1L;

            @Override
            public MqttConsumerActor create() {
                return new MqttConsumerActor(messageMappingProcessor, sourceAuthorizationContext);
            }
        });
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder().match(MqttMessage.class, message -> {

            log.debug("Received MQTT message on topic {}: {}", message.topic(), message.payload().utf8String());

            final HashMap<String, String> headers = new HashMap<>();

            headers.put("mqtt.topic", message.topic());

            final ExternalMessage externalMessage = ConnectivityModelFactory.newExternalMessageBuilder(headers)
                    .withBytes(message.payload().toByteBuffer())
                    .withAuthorizationContext(sourceAuthorizationContext)
                    .build();

            messageMappingProcessor.tell(externalMessage, getSelf());

        }).matchEquals(MqttClientActor.COMPLETE_MESSAGE, cmplt -> {
            log.debug("Underlying stream completed, shutdown consumer actor.");
            getSelf().tell(PoisonPill.getInstance(), getSelf());
        }).matchAny(unhandled -> {
            log.info("Unhandled message: {}", unhandled);
            unhandled(unhandled);
        }).build();
    }
}
