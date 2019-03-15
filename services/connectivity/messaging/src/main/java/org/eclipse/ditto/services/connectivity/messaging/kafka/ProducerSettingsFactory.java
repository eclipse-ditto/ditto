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
package org.eclipse.ditto.services.connectivity.messaging.kafka;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.services.connectivity.util.KafkaConfigReader;

import akka.kafka.ProducerSettings;

/**
 * Creates {@link akka.kafka.ProducerSettings} from a given {@link
 * org.eclipse.ditto.model.connectivity.Connection} configuration.
 */
final class ProducerSettingsFactory {

    private static final Collection<KafkaSpecificConfig> SPECIFIC_CONFIGS =
            Collections.unmodifiableList(Arrays.asList(KafkaAuthenticationSpecificConfig.getInstance(), KafkaBootstrapServerSpecificConfig.getInstance()));

    private static final ProducerSettingsFactory INSTANCE = new ProducerSettingsFactory();
    private static final Serializer<String> KEY_SERIALIZER = new StringSerializer();
    private static final Serializer<String> VALUE_SERIALIZER = KEY_SERIALIZER;

    static ProducerSettingsFactory getInstance() {
        return INSTANCE;
    }

    ProducerSettings<String, String> createProducerSettings(final Connection connection, final KafkaConfigReader config) {
        ProducerSettings<String, String> settings = ProducerSettings.create(config.internalProducerSettings(), KEY_SERIALIZER, VALUE_SERIALIZER)
                .withProperty(ProducerConfig.MAX_BLOCK_MS_CONFIG, "10000"); // TODO blocking timeout, either due to missing metadata or due to full buffer, reset to 60.000
//                .withCloseTimeout()
//                .withDispatcher()
//                .withEosCommitInterval()
//                .withParallelism()

        // sasl/plain typically uses TLS for encryption to implement secure authentication,
        if (isSecureConnection(connection)) {
            // TODO: probably only SSL for anonymous auth, but it could also be that this means we would do 2-way ssl
            settings = settings.withProperty(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SASL_SSL");
        } else {
            // TODO: probably only PLAINTEXT for anonymous auth
            settings = settings.withProperty(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SASL_PLAINTEXT");
        }

        settings = addSpecificConfigs(settings, connection);

        return settings;
    }

    private ProducerSettings<String, String> addSpecificConfigs(final ProducerSettings<String, String> settings,
            final Connection connection) {
        ProducerSettings<String, String> currentSettings = settings;
        for (final KafkaSpecificConfig specificConfig : SPECIFIC_CONFIGS) {
            currentSettings = specificConfig.apply(currentSettings, connection);
        }
        return currentSettings;
    }

    private static boolean isSecureConnection(final Connection connection) {
        return "ssl".equals(connection.getProtocol());
    }
}
