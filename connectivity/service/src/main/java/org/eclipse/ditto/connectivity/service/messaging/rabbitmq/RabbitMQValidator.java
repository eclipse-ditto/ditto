/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.messaging.rabbitmq;

import static org.eclipse.ditto.connectivity.service.placeholders.ConnectivityPlaceholders.newEntityPlaceholder;
import static org.eclipse.ditto.connectivity.service.placeholders.ConnectivityPlaceholders.newFeaturePlaceholder;
import static org.eclipse.ditto.connectivity.service.placeholders.ConnectivityPlaceholders.newPolicyPlaceholder;
import static org.eclipse.ditto.connectivity.service.placeholders.ConnectivityPlaceholders.newThingPlaceholder;

import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionType;
import org.eclipse.ditto.connectivity.model.Source;
import org.eclipse.ditto.connectivity.model.Target;
import org.eclipse.ditto.connectivity.service.config.ConnectivityConfig;
import org.eclipse.ditto.connectivity.service.messaging.Resolvers;
import org.eclipse.ditto.connectivity.service.messaging.validation.AbstractProtocolValidator;
import org.eclipse.ditto.placeholders.PlaceholderFactory;

import akka.actor.ActorSystem;

/**
 * Connection specification for RabbitMQ protocol.
 */
@Immutable
public final class RabbitMQValidator extends AbstractProtocolValidator {

    private static final Collection<String> ACCEPTED_SCHEMES = List.of("amqp", "amqps");
    private static final Collection<String> SECURE_SCHEMES = List.of("amqps");

    /**
     * Create a new {@code RabbitMQConnectionSpec}.
     *
     * @return a new instance.
     */
    public static RabbitMQValidator newInstance() {
        return new RabbitMQValidator();
    }

    @Override
    protected void validateSource(final Source source, final DittoHeaders dittoHeaders,
            final Supplier<String> sourceDescription) {

        source.getEnforcement().ifPresent(enforcement -> {
            validateTemplate(enforcement.getInput(), dittoHeaders, PlaceholderFactory.newHeadersPlaceholder());
            enforcement.getFilters().forEach(filterTemplate ->
                    validateTemplate(filterTemplate, dittoHeaders, newThingPlaceholder(),
                            newPolicyPlaceholder(), newEntityPlaceholder(), newFeaturePlaceholder()));
        });
        validateHeaderMapping(source.getHeaderMapping(), dittoHeaders);
    }

    @Override
    protected void validateTarget(final Target target, final DittoHeaders dittoHeaders,
            final Supplier<String> targetDescription) {
        validateHeaderMapping(target.getHeaderMapping(), dittoHeaders);
        validateTemplate(target.getAddress(), dittoHeaders, Resolvers.getPlaceholders());
        validateExtraFields(target);
    }

    @Override
    public ConnectionType type() {
        return ConnectionType.AMQP_091;
    }

    @Override
    public void validate(final Connection connection,
            final DittoHeaders dittoHeaders,
            final ActorSystem actorSystem,
            final ConnectivityConfig connectivityConfig) {
        validateUriScheme(connection, dittoHeaders, ACCEPTED_SCHEMES, SECURE_SCHEMES, "AMQP 0.9.1");
        validateSourceConfigs(connection, dittoHeaders);
        validateTargetConfigs(connection, dittoHeaders);
        validatePayloadMappings(connection, actorSystem, connectivityConfig, dittoHeaders);
    }
}
