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
package org.eclipse.ditto.connectivity.service.messaging.hono;

import static org.eclipse.ditto.connectivity.api.placeholders.ConnectivityPlaceholders.newEntityPlaceholder;
import static org.eclipse.ditto.connectivity.api.placeholders.ConnectivityPlaceholders.newFeaturePlaceholder;
import static org.eclipse.ditto.connectivity.api.placeholders.ConnectivityPlaceholders.newPolicyPlaceholder;
import static org.eclipse.ditto.connectivity.api.placeholders.ConnectivityPlaceholders.newThingPlaceholder;

import java.text.MessageFormat;
import java.util.function.Supplier;

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
import org.eclipse.ditto.placeholders.PlaceholderFactory;

import akka.actor.ActorSystem;
@Immutable
public final class HonoValidator extends AbstractProtocolValidator {

    private static final String INVALID_SOURCE_ADDRESS_ALIAS_FORMAT = "The provided source address is not valid: {0}." +
            " It should be one of the defined {1} aliases.";
    private static final String INVALID_TARGET_ADDRESS_ALIAS_FORMAT = "The provided target address is not" +
            "valid: {0}. It should be 'command' alias.";
    private static final String NOT_EMPTY_FORMAT = "The provided {0} in your target address may not be empty.";

    @Nullable private static HonoValidator instance;

    private HonoValidator() {
        super();
    }

    /**
     * Returns an instance of the Hono validator.
     *
     * @return the instance.
     */
    public static HonoValidator getInstance() {
        HonoValidator result = instance;
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
    protected void validateSource(final Source source, final DittoHeaders dittoHeaders,
            final Supplier<String> sourceDescription) {
        source.getEnforcement().ifPresent(enforcement -> {
            validateTemplate(enforcement.getInput(), dittoHeaders, PlaceholderFactory.newHeadersPlaceholder());
            enforcement.getFilters().forEach(filterTemplate ->
                    validateTemplate(filterTemplate, dittoHeaders, newThingPlaceholder(), newPolicyPlaceholder(),
                            newEntityPlaceholder(), newFeaturePlaceholder()));
        });
        source.getAddresses().forEach(
                address -> validateSourceAddress(address, dittoHeaders));
        validateSourceQos(source, dittoHeaders);
    }

    @Override
    protected void validateTarget(final Target target, final DittoHeaders dittoHeaders,
            final Supplier<String> targetDescription) {
        validateTargetAddress(target.getAddress(), dittoHeaders);
        validateExtraFields(target);
    }

    private static void validateSourceQos(final Source source, final DittoHeaders dittoHeaders) {
        source.getQos().ifPresent(qos -> {
            if (qos < 0 || qos > 1) {
                throw ConnectionConfigurationInvalidException
                        .newBuilder("Invalid 'qos' value for Kafka source, supported are: <0> or <1>. " +
                                "Configured 'qos' value was: <" + qos + ">"
                        )
                        .dittoHeaders(dittoHeaders)
                        .build();
            }
        });
    }

    private static void validateTargetAddress(final String address, final DittoHeaders dittoHeaders) {
        if (address.isEmpty()) {
            throwEmptyException(dittoHeaders);
        }

        HonoAddressAlias.fromName(address).filter(alias -> alias == HonoAddressAlias.COMMAND)
                .orElseThrow(() -> buildInvalidTargetAddressException(address, dittoHeaders));
    }

    private static void validateSourceAddress(final String address, final DittoHeaders dittoHeaders) {
        if (address.isEmpty()) {
            throwEmptyException(dittoHeaders);
        }

        var honoAddressAlias = HonoAddressAlias.fromName(address);
        honoAddressAlias.filter(alias -> alias != HonoAddressAlias.COMMAND)
                .orElseThrow(() -> {
                    String aliases = HonoAddressAlias.names()
                            .stream()
                            .filter(item -> !item.equalsIgnoreCase(HonoAddressAlias.COMMAND.getName()))
                            .toList()
                            .toString();
                    return buildInvalidSourceAddressException(address, dittoHeaders, aliases);
                });

    }

    private static void throwEmptyException(final DittoHeaders dittoHeaders) {
        final String message = MessageFormat.format(NOT_EMPTY_FORMAT, "address");
        throw ConnectionConfigurationInvalidException.newBuilder(message)
                .dittoHeaders(dittoHeaders)
                .build();
    }

    private static ConnectionConfigurationInvalidException buildInvalidTargetAddressException(String address,
            final DittoHeaders dittoHeaders) {
        final String message = MessageFormat.format(INVALID_TARGET_ADDRESS_ALIAS_FORMAT,  address);
        throw ConnectionConfigurationInvalidException.newBuilder(message)
                .dittoHeaders(dittoHeaders)
                .build();
    }

    private static ConnectionConfigurationInvalidException buildInvalidSourceAddressException(String address,
            final DittoHeaders dittoHeaders, String definedAliases) {
        final String message = MessageFormat.format(INVALID_SOURCE_ADDRESS_ALIAS_FORMAT, address,
                definedAliases);
        throw ConnectionConfigurationInvalidException.newBuilder(message)
                .dittoHeaders(dittoHeaders)
                .build();
    }

}
