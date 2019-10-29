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
package org.eclipse.ditto.services.connectivity.messaging.validation;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectionConfigurationInvalidException;
import org.eclipse.ditto.model.connectivity.ConnectionType;
import org.eclipse.ditto.model.connectivity.credentials.ClientCertificateCredentials;
import org.eclipse.ditto.model.connectivity.credentials.Credentials;
import org.eclipse.ditto.model.query.criteria.CriteriaFactory;
import org.eclipse.ditto.model.query.criteria.CriteriaFactoryImpl;
import org.eclipse.ditto.model.query.expression.ThingsFieldExpressionFactory;
import org.eclipse.ditto.model.query.filter.QueryFilterCriteriaFactory;
import org.eclipse.ditto.model.query.things.ModelBasedThingsFieldExpressionFactory;
import org.eclipse.ditto.services.connectivity.messaging.config.DittoConnectivityConfig;
import org.eclipse.ditto.services.connectivity.messaging.internal.ssl.SSLContextCreator;
import org.eclipse.ditto.services.utils.config.DefaultScopedConfig;

import akka.actor.ActorSystem;
import akka.event.LoggingAdapter;
import akka.http.javadsl.model.Host;
import akka.http.javadsl.model.Uri;

/**
 * Validate a connection according to its type.
 */
@Immutable
public final class ConnectionValidator {

    private final Map<ConnectionType, AbstractProtocolValidator> specMap;
    private final QueryFilterCriteriaFactory queryFilterCriteriaFactory;

    private static final int MAPPING_NUMBER_LIMIT_SOURCE = 10;
    private static final int MAPPING_NUMBER_LIMIT_TARGET = 10;

    private ConnectionValidator(final AbstractProtocolValidator... connectionSpecs) {
        final Map<ConnectionType, AbstractProtocolValidator> specMap = Arrays.stream(connectionSpecs)
                .collect(Collectors.toMap(AbstractProtocolValidator::type, Function.identity()));
        this.specMap = Collections.unmodifiableMap(specMap);

        final CriteriaFactory criteriaFactory = new CriteriaFactoryImpl();
        final ThingsFieldExpressionFactory fieldExpressionFactory =
                new ModelBasedThingsFieldExpressionFactory();
        queryFilterCriteriaFactory = new QueryFilterCriteriaFactory(criteriaFactory, fieldExpressionFactory);
    }

    /**
     * Create a connection validator from connection specs.
     *
     * @param connectionSpecs specs of supported connection types.
     * @return a connection validator.
     */
    public static ConnectionValidator of(final AbstractProtocolValidator... connectionSpecs) {
        return new ConnectionValidator(connectionSpecs);
    }

    /**
     * Check a connection for errors and throw them.
     *
     * @param connection the connection to validate.
     * @param dittoHeaders headers of the command that triggered the connection validation.
     * @param actorSystem the ActorSystem to use.
     * @throws org.eclipse.ditto.model.base.exceptions.DittoRuntimeException if the connection has errors.
     * @throws java.lang.IllegalStateException if the connection type is not known.
     */
    void validate(final Connection connection, final DittoHeaders dittoHeaders, final ActorSystem actorSystem) {
        final AbstractProtocolValidator spec = specMap.get(connection.getConnectionType());
        validateSourceAndTargetAddressesAreNonempty(connection, dittoHeaders);
        // check size of mappings
        checkMappingNumberOfSourcesAndTargets(dittoHeaders, connection);
        validateFormatOfCertificates(connection, dittoHeaders);
        validateBlacklistedHostnames(connection, dittoHeaders, actorSystem);
        if (spec != null) {
            // throw error at validation site for clarity of stack trace
            spec.validate(connection, dittoHeaders, actorSystem);
        } else {
            throw new IllegalStateException("Unknown connection type: " + connection);
        }
    }

    /**
     * Resolve blacklisted hostnames into IP addresses that should not be accessed.
     *
     * @param configuredBlacklistedHostnames blacklisted hostnames.
     * @param log the logger.
     * @return blacklisted IP addresses.
     */
    public static Collection<InetAddress> calculateBlacklistedAddresses(
            final Collection<String> configuredBlacklistedHostnames,
            final LoggingAdapter log) {

        return configuredBlacklistedHostnames.stream()
                .filter(host -> !host.isEmpty())
                .flatMap(host -> {
                    try {
                        return Stream.of(InetAddress.getAllByName(host));
                    } catch (final UnknownHostException e) {
                        log.error(e, "Could not resolve hostname during building blacklisted hostnames set: <{}>",
                                host);
                        return Stream.empty();
                    }
                })
                .collect(Collectors.toSet());
    }

    /**
     * Check if connections to a host are forbidden by a blacklist or by the category of its IP.
     * Loopback, private, multicast and wildcard addresses are allowed only if the blacklist is empty.
     *
     * @param host the host to check.
     * @param blacklistedAddresses list of IP addresses to block. If empty, then all hosts are permitted.
     * @return whether connections to the host are permitted.
     */
    public static boolean isHostForbidden(final Host host, final Collection<InetAddress> blacklistedAddresses) {
        if (blacklistedAddresses.isEmpty()) {
            // If not even localhost is blacklisted, then permit even private, loopback, multicast and wildcard IPs.
            return false;
        } else {
            // Forbid blacklisted, private, loopback, multicast and wildcard IPs.
            return StreamSupport.stream(host.getInetAddresses().spliterator(), false)
                    .anyMatch(requestAddress ->
                            requestAddress.isLoopbackAddress() ||
                                    requestAddress.isSiteLocalAddress() ||
                                    requestAddress.isMulticastAddress() ||
                                    requestAddress.isAnyLocalAddress() ||
                                    blacklistedAddresses.contains(requestAddress));
        }
    }

    /**
     * Check if number of mappings are valid
     * @throws ConnectionConfigurationInvalidException if payload number is over predefined limit
     */
    private void checkMappingNumberOfSourcesAndTargets(final DittoHeaders dittoHeaders, final Connection connection) {

        final String errorMessage = "Payloadmapping number exceeded";

        connection.getSources().forEach(source -> {
                    if (source.getPayloadMapping().getMappings().size() > MAPPING_NUMBER_LIMIT_SOURCE) {
                        throw ConnectionConfigurationInvalidException.newBuilder(errorMessage)
                                .description("Payloadmapping number of source with address " + source.getAddresses() +
                                        " is over the limit of " + MAPPING_NUMBER_LIMIT_SOURCE)
                                .dittoHeaders(dittoHeaders)
                                .build();
                    }
                }
        );
        connection.getTargets().forEach(target -> {
            if (target.getPayloadMapping().getMappings().size() > MAPPING_NUMBER_LIMIT_TARGET) {
                throw ConnectionConfigurationInvalidException.newBuilder(errorMessage)
                        .description("Payloadmapping number of target with address " + target.getAddress() +
                                " is over the limit of " + MAPPING_NUMBER_LIMIT_TARGET)
                        .dittoHeaders(dittoHeaders)
                        .build();
            }
        });
    }

    private void validateSourceAndTargetAddressesAreNonempty(final Connection connection,
            final DittoHeaders dittoHeaders) {

        connection.getSources().forEach(source -> {
            if (source.getAddresses().isEmpty() || source.getAddresses().contains("")) {
                final String location =
                        String.format("Source %d of connection <%s>", source.getIndex(), connection.getId());
                throw emptyAddressesError(location, dittoHeaders);
            }
        });

        connection.getTargets().forEach(target -> {
            if (target.getAddress().isEmpty()) {
                final String location = String.format("Targets of connection <%s>", connection.getId());
                throw emptyAddressesError(location, dittoHeaders);
            }
            target.getTopics().forEach(topic -> topic.getFilter().ifPresent(filter -> {
                // will throw an InvalidRqlExpressionException if the RQL expression was not valid:
                queryFilterCriteriaFactory.filterCriteria(filter, dittoHeaders);
            }));
        });
    }

    private static void validateFormatOfCertificates(final Connection connection, final DittoHeaders dittoHeaders) {
        final Optional<String> trustedCertificates = connection.getTrustedCertificates();
        final Optional<Credentials> credentials = connection.getCredentials();
        // check if there are certificates to check
        if (trustedCertificates.isPresent() || credentials.isPresent()) {
            credentials.orElseGet(ClientCertificateCredentials::empty)
                    .accept(SSLContextCreator.fromConnection(connection, dittoHeaders));
        }
    }

    private static DittoRuntimeException emptyAddressesError(final String location, final DittoHeaders dittoHeaders) {
        final String message = location + ": addresses may not be empty.";
        return ConnectionConfigurationInvalidException.newBuilder(message)
                .dittoHeaders(dittoHeaders)
                .build();
    }

    private static void validateBlacklistedHostnames(final Connection connection, final DittoHeaders dittoHeaders,
            final ActorSystem actorSystem) {

        final Collection<String> configuredBlacklistedHostnames = DittoConnectivityConfig.of(
                DefaultScopedConfig.dittoScoped(actorSystem.settings().config())
        ).getConnectionConfig().getBlacklistedHostnames();
        final Collection<InetAddress> blacklisted =
                calculateBlacklistedAddresses(configuredBlacklistedHostnames, actorSystem.log());

        final Host connectionHost = Uri.create(connection.getUri()).getHost();
        if (isHostForbidden(connectionHost, blacklisted)) {
            final String errorMessage = String.format("The configured host '%s' may not be used for the connection.",
                    connectionHost);
            throw ConnectionConfigurationInvalidException.newBuilder(errorMessage)
                    .description("It is a blacklisted hostname which may not be used.")
                    .dittoHeaders(dittoHeaders)
                    .build();
        }
    }
}
