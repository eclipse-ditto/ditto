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
package org.eclipse.ditto.connectivity.service.messaging.kafka;

import static org.eclipse.ditto.connectivity.service.EnforcementFactoryFactory.newEnforcementFilterFactory;
import static org.eclipse.ditto.placeholders.PlaceholderFactory.newHeadersPlaceholder;

import java.util.Map;

import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.Enforcement;
import org.eclipse.ditto.connectivity.model.EnforcementFilterFactory;
import org.eclipse.ditto.connectivity.model.Source;
import org.eclipse.ditto.connectivity.service.config.ConnectionThrottlingConfig;
import org.eclipse.ditto.connectivity.service.messaging.AcknowledgeableMessage;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.ConnectionMonitor;

import akka.NotUsed;
import akka.stream.Materializer;
import akka.stream.javadsl.Sink;

/**
 * A factory for building different {@link KafkaConsumerStream} implementations, e.g. for different quality of services.
 */
final class KafkaConsumerStreamFactory {

    private final ConsumerData consumerData;
    private final boolean dryRun;
    private final PropertiesFactory propertiesFactory;
    private final AtMostOnceKafkaConsumerSourceSupplier atMostOnceKafkaConsumerSourceSupplier;
    private final AtLeastOnceKafkaConsumerSourceSupplier atLeastOnceKafkaConsumerSourceSupplier;
    private final ConnectionThrottlingConfig throttlingConfig;

    KafkaConsumerStreamFactory(final ConnectionThrottlingConfig throttlingConfig,
            final PropertiesFactory propertiesFactory,
            final ConsumerData consumerData,
            final boolean dryRun) {

        this.throttlingConfig = throttlingConfig;
        this.consumerData = consumerData;
        this.dryRun = dryRun;
        this.propertiesFactory = propertiesFactory;
        atMostOnceKafkaConsumerSourceSupplier =
                new AtMostOnceKafkaConsumerSourceSupplier(propertiesFactory, consumerData.getAddress(), dryRun);
        atLeastOnceKafkaConsumerSourceSupplier =
                new AtLeastOnceKafkaConsumerSourceSupplier(propertiesFactory, consumerData.getAddress(), dryRun);
    }

    /**
     * Only used for testing purpose
     *
     * @param throttlingConfig the throttling config to use during the test.
     * @param atMostOnceKafkaConsumerSourceSupplier source supplier for "at most once"
     * @param atLeastOnceKafkaConsumerSourceSupplier source supplier for "at least once"
     * @param consumerData the consumer data
     * @param dryRun indicates whether the connection runs in a dry run.
     */
    KafkaConsumerStreamFactory(final ConnectionThrottlingConfig throttlingConfig,
            final AtMostOnceKafkaConsumerSourceSupplier atMostOnceKafkaConsumerSourceSupplier,
            final AtLeastOnceKafkaConsumerSourceSupplier atLeastOnceKafkaConsumerSourceSupplier,
            final ConsumerData consumerData,
            final boolean dryRun) {

        this.consumerData = consumerData;
        this.dryRun = dryRun;
        propertiesFactory = null;
        this.throttlingConfig = throttlingConfig;
        this.atMostOnceKafkaConsumerSourceSupplier = atMostOnceKafkaConsumerSourceSupplier;
        this.atLeastOnceKafkaConsumerSourceSupplier = atLeastOnceKafkaConsumerSourceSupplier;
    }

    KafkaConsumerStream newAtMostOnceConsumerStream(
            final Materializer materializer,
            final ConnectionMonitor inboundMonitor,
            final Sink<AcknowledgeableMessage, NotUsed> messageMappingSink,
            final Sink<DittoRuntimeException, ?> dreSink,
            final ConnectionId connectionId,
            final String consumerId) {

        final KafkaMessageTransformer kafkaMessageTransformer = buildKafkaMessageTransformer(inboundMonitor,
                connectionId);
        return new AtMostOnceConsumerStream(atMostOnceKafkaConsumerSourceSupplier,
                throttlingConfig,
                kafkaMessageTransformer,
                dryRun,
                materializer,
                inboundMonitor,
                messageMappingSink,
                dreSink,
                connectionId,
                consumerId);
    }

    KafkaConsumerStream newAtLeastOnceConsumerStream(
            final Materializer materializer,
            final ConnectionMonitor inboundMonitor,
            final ConnectionMonitor ackMonitor,
            final Sink<AcknowledgeableMessage, NotUsed> messageMappingSink,
            final Sink<DittoRuntimeException, ?> dreSink,
            final ConnectionId connectionId,
            final String consumerId) {

        final KafkaMessageTransformer kafkaMessageTransformer = buildKafkaMessageTransformer(inboundMonitor,
                connectionId);
        return new AtLeastOnceConsumerStream(atLeastOnceKafkaConsumerSourceSupplier,
                propertiesFactory.getCommitterSettings(),
                throttlingConfig,
                kafkaMessageTransformer,
                dryRun,
                materializer,
                inboundMonitor,
                ackMonitor,
                messageMappingSink,
                dreSink,
                connectionId,
                consumerId);
    }

    private KafkaMessageTransformer buildKafkaMessageTransformer(final ConnectionMonitor inboundMonitor,
            final ConnectionId connectionId) {
        final Source source = consumerData.getSource();
        final String address = consumerData.getAddress();
        final Enforcement enforcement = source.getEnforcement().orElse(null);
        final EnforcementFilterFactory<Map<String, String>, Signal<?>> headerEnforcementFilterFactory =
                enforcement != null
                        ? newEnforcementFilterFactory(enforcement, newHeadersPlaceholder())
                        : input -> null;
        return new KafkaMessageTransformer(connectionId, source, address, headerEnforcementFilterFactory,
                inboundMonitor);
    }

}
