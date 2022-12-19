/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.messaging.hono;

import java.text.MessageFormat;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionConfigurationInvalidException;
import org.eclipse.ditto.connectivity.model.ConnectionType;
import org.eclipse.ditto.connectivity.model.HonoAddressAlias;
import org.eclipse.ditto.connectivity.model.Source;
import org.eclipse.ditto.connectivity.model.Target;
import org.eclipse.ditto.connectivity.service.config.ConnectivityConfig;
import org.eclipse.ditto.connectivity.service.messaging.validation.AbstractProtocolValidator;
import org.eclipse.ditto.connectivity.service.placeholders.ConnectivityPlaceholders;
import org.eclipse.ditto.placeholders.PlaceholderFactory;

import akka.actor.ActorSystem;

@Immutable
public final class HonoValidator extends AbstractProtocolValidator {

    @Nullable private static HonoValidator instance;

    private final Set<String> allowedSourceAddressHonoAliasValues;

    private HonoValidator() {
        allowedSourceAddressHonoAliasValues = Stream.of(HonoAddressAlias.values())
                .filter(honoAddressAlias -> HonoAddressAlias.COMMAND != honoAddressAlias)
                .map(HonoAddressAlias::getAliasValue)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * Returns an instance of the Hono validator.
     *
     * @return the instance.
     */
    public static HonoValidator getInstance() {
        var result = instance;
        if (null == result) {
            result = new HonoValidator();
            instance = result;
        }
        return result;
    }

    @Override
    public ConnectionType type() {
        return ConnectionType.HONO;
    }

    @Override
    public void validate(final Connection connection,
            final DittoHeaders dittoHeaders,
            final ActorSystem actorSystem,
            final ConnectivityConfig connectivityConfig) {

        validateSourceConfigs(connection, dittoHeaders);
        validateTargetConfigs(connection, dittoHeaders);
        validatePayloadMappings(connection, actorSystem, connectivityConfig, dittoHeaders);
    }

    @Override
    protected void validateSource(final Source source,
            final DittoHeaders dittoHeaders,
            final Supplier<String> sourceDescription) {

        validateSourceEnforcement(source, dittoHeaders);
        validateSourceAddresses(source, dittoHeaders);
        validateSourceQos(source, dittoHeaders);
    }

    private void validateSourceEnforcement(final Source source, final DittoHeaders dittoHeaders) {
        final Consumer<String> validateInputTemplate =
                inputTemplate -> validateTemplate(inputTemplate,
                        dittoHeaders,
                        PlaceholderFactory.newHeadersPlaceholder());

        final Consumer<Set<String>> validateFilterTemplates =
                filters -> filters.forEach(
                        filterTemplate -> validateTemplate(filterTemplate,
                                dittoHeaders,
                                ConnectivityPlaceholders.newThingPlaceholder(),
                                ConnectivityPlaceholders.newPolicyPlaceholder(),
                                ConnectivityPlaceholders.newEntityPlaceholder(),
                                ConnectivityPlaceholders.newFeaturePlaceholder())
                );

        source.getEnforcement()
                .ifPresent(enforcement -> {
                    validateInputTemplate.accept(enforcement.getInput());
                    validateFilterTemplates.accept(enforcement.getFilters());
                });
    }

    private void validateSourceAddresses(final Source source, final DittoHeaders dittoHeaders) {
        final var sourceAddresses = source.getAddresses();
        sourceAddresses.forEach(address -> validateSourceAddress(address, dittoHeaders));
    }

    private void validateSourceAddress(final String sourceAddress, final DittoHeaders dittoHeaders) {
        if (sourceAddress.isEmpty()) {
            throw newConnectionConfigurationInvalidException("The provided source address must not be empty.",
                    dittoHeaders);
        }

        if (!allowedSourceAddressHonoAliasValues.contains(sourceAddress)) {
            throw newConnectionConfigurationInvalidException(
                    MessageFormat.format("The provided source address <{0}> is invalid." +
                                    " It should be one of the defined aliases: {1}",
                            sourceAddress,
                            allowedSourceAddressHonoAliasValues),
                    dittoHeaders
            );
        }
    }

    private static ConnectionConfigurationInvalidException newConnectionConfigurationInvalidException(
            final String errorMessage,
            final DittoHeaders dittoHeaders
    ) {
        return ConnectionConfigurationInvalidException.newBuilder(errorMessage).dittoHeaders(dittoHeaders).build();
    }

    private static void validateSourceQos(final Source source, final DittoHeaders dittoHeaders) {
        source.getQos()
                .filter(qos -> qos < 0 || qos > 1)
                .ifPresent(qos -> {
                    throw newConnectionConfigurationInvalidException(
                            MessageFormat.format(
                                    "Invalid source ''qos'' value <{0}>. Supported values are <0> and <1>.",
                                    qos),
                            dittoHeaders
                    );
                });
    }

    @Override
    protected void validateTarget(final Target target,
            final DittoHeaders dittoHeaders,
            final Supplier<String> targetDescription) {

        validateTargetAddress(target.getAddress(), dittoHeaders);
        validateExtraFields(target);
    }

    private static void validateTargetAddress(final String targetAddress, final DittoHeaders dittoHeaders) {
        if (targetAddress.isEmpty()) {
            throw newConnectionConfigurationInvalidException("The provided target address must not be empty.",
                    dittoHeaders);
        }

        final var allowedTargetAddressAliasValue = HonoAddressAlias.COMMAND.getAliasValue();
        if (!Objects.equals(allowedTargetAddressAliasValue, targetAddress)) {
            throw newConnectionConfigurationInvalidException(
                    MessageFormat.format("The provided target address <{0}> is invalid. It should be <{1}>.",
                            targetAddress,
                            allowedTargetAddressAliasValue),
                    dittoHeaders
            );
        }
    }

}
