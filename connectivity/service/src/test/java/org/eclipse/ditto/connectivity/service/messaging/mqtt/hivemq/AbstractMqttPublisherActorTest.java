/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.ditto.connectivity.api.OutboundSignal;
import org.eclipse.ditto.connectivity.api.OutboundSignalFactory;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.HeaderMapping;
import org.eclipse.ditto.connectivity.model.Target;
import org.eclipse.ditto.connectivity.service.messaging.AbstractPublisherActorTest;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.MqttHeader;
import org.junit.Test;

import com.hivemq.client.mqtt.datatypes.MqttQos;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;

/**
 * Base class for MqttPublisherActorTests.
 */
abstract class AbstractMqttPublisherActorTest extends AbstractPublisherActorTest {

    static final MqttQos DEFAULT_QOS = MqttQos.AT_MOST_ONCE;
    static final boolean DEFAULT_RETAIN = false;

    private static final String CUSTOM_RETAIN_HEADER = "custom.retain";
    private static final String CUSTOM_TOPIC_HEADER = "custom.topic";
    private static final String CUSTOM_QOS_HEADER = "custom.qos";

    @Test
    public void testPublishMessageWithRetainFlag() throws Exception {

        new TestKit(actorSystem) {{

            final TestProbe probe = new TestProbe(actorSystem);
            setupMocks(probe);
            final OutboundSignal.MultiMapped multiMapped =
                    OutboundSignalFactory.newMultiMappedOutboundSignal(
                            List.of(getMockOutboundSignal(CUSTOM_RETAIN_HEADER, "true")),
                            getRef());

            final Props props = getPublisherActorProps();
            final ActorRef publisherActor = childActorOf(props);

            publisherCreated(this, publisherActor);

            publisherActor.tell(multiMapped, getRef());

            verifyPublishedMessageIsRetained();
        }};

    }

    @Test
    public void testPublishMessageWithCustomQos() throws Exception {

        new TestKit(actorSystem) {{

            final TestProbe probe = new TestProbe(actorSystem);
            setupMocks(probe);
            final OutboundSignal.MultiMapped multiMapped =
                    OutboundSignalFactory.newMultiMappedOutboundSignal(
                            List.of(getMockOutboundSignal(CUSTOM_QOS_HEADER, "2")),
                            getRef());

            final Props props = getPublisherActorProps();
            final ActorRef publisherActor = childActorOf(props);

            publisherCreated(this, publisherActor);

            publisherActor.tell(multiMapped, getRef());

            verifyPublishedMessageHasQos(MqttQos.EXACTLY_ONCE);
        }};

    }

    @Test
    public void testPublishMessageWithCustomQosInTarget() throws Exception {

        new TestKit(actorSystem) {{

            final Target target =
                    ConnectivityModelFactory.newTargetBuilder(decorateTarget(createTestTarget())).qos(2).build();
            final TestProbe probe = new TestProbe(actorSystem);
            setupMocks(probe);
            final OutboundSignal.MultiMapped multiMapped =
                    OutboundSignalFactory.newMultiMappedOutboundSignal(List.of(getMockOutboundSignal(target)), getRef());

            final Props props = getPublisherActorProps();
            final ActorRef publisherActor = childActorOf(props);

            publisherCreated(this, publisherActor);

            publisherActor.tell(multiMapped, getRef());

            verifyPublishedMessageHasQos(MqttQos.EXACTLY_ONCE);
        }};

    }

    @Test
    public void testPublishMessageWithCustomTopic() throws Exception {

        new TestKit(actorSystem) {{

            final TestProbe probe = new TestProbe(actorSystem);
            setupMocks(probe);
            final OutboundSignal.MultiMapped multiMapped =
                    OutboundSignalFactory.newMultiMappedOutboundSignal(
                            List.of(getMockOutboundSignal(CUSTOM_TOPIC_HEADER, "my/custom/topic")),
                            getRef());

            final Props props = getPublisherActorProps();
            final ActorRef publisherActor = childActorOf(props);

            publisherCreated(this, publisherActor);

            publisherActor.tell(multiMapped, getRef());

            verifyPublishedMessageHasTopic("my/custom/topic");
        }};

    }

    protected abstract void verifyPublishedMessageIsRetained();

    protected abstract void verifyPublishedMessageHasQos(final MqttQos expectedQos);

    protected abstract void verifyPublishedMessageHasTopic(final String expectedTopic);

    @Override
    protected HeaderMapping getHeaderMapping() {
        final HeaderMapping headerMapping = super.getHeaderMapping();
        final Map<String, String> mappingMap = new HashMap<>(headerMapping.getMapping());

        mappingMap.put(MqttHeader.MQTT_RETAIN.getName(), "{{ header:" + CUSTOM_RETAIN_HEADER + " }}");
        mappingMap.put(MqttHeader.MQTT_TOPIC.getName(), "{{ header:" + CUSTOM_TOPIC_HEADER + " }}");
        mappingMap.put(MqttHeader.MQTT_QOS.getName(), "{{ header:" + CUSTOM_QOS_HEADER + " }}");

        return ConnectivityModelFactory.newHeaderMapping(mappingMap);
    }

}
