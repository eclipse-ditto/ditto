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

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.acks.AcknowledgementLabel;
import org.eclipse.ditto.model.base.acks.AcknowledgementLabelInvalidException;
import org.eclipse.ditto.model.base.acks.DittoAcknowledgementLabel;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.ClientCertificateCredentials;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectionConfigurationInvalidException;
import org.eclipse.ditto.model.connectivity.ConnectionType;
import org.eclipse.ditto.model.connectivity.Credentials;
import org.eclipse.ditto.model.connectivity.PayloadMapping;
import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.model.placeholders.ExpressionResolver;
import org.eclipse.ditto.model.placeholders.PlaceholderFactory;
import org.eclipse.ditto.model.query.criteria.CriteriaFactory;
import org.eclipse.ditto.model.query.criteria.CriteriaFactoryImpl;
import org.eclipse.ditto.model.query.expression.ThingsFieldExpressionFactory;
import org.eclipse.ditto.model.query.filter.QueryFilterCriteriaFactory;
import org.eclipse.ditto.model.query.things.ModelBasedThingsFieldExpressionFactory;
import org.eclipse.ditto.services.connectivity.mapping.MapperLimitsConfig;
import org.eclipse.ditto.services.connectivity.messaging.config.ConnectivityConfig;
import org.eclipse.ditto.services.connectivity.messaging.internal.ssl.SSLContextCreator;

import akka.actor.ActorSystem;
import akka.event.LoggingAdapter;

/**
 * Validate a connection according to its type.
 */
@Immutable
public final class ConnectionValidator {

    private final Map<ConnectionType, AbstractProtocolValidator> specMap;
    private final QueryFilterCriteriaFactory queryFilterCriteriaFactory;

    private final int mappingNumberLimitSource;
    private final int mappingNumberLimitTarget;

    private final HostValidator hostValidator;

    private ConnectionValidator(
            final ConnectivityConfig connectivityConfig,
            LoggingAdapter loggingAdapter, final AbstractProtocolValidator... connectionSpecs) {
        final Map<ConnectionType, AbstractProtocolValidator> theSpecMap = Arrays.stream(connectionSpecs)
                .collect(Collectors.toMap(AbstractProtocolValidator::type, Function.identity()));
        this.specMap = Collections.unmodifiableMap(theSpecMap);

        final CriteriaFactory criteriaFactory = new CriteriaFactoryImpl();
        final ThingsFieldExpressionFactory fieldExpressionFactory = new ModelBasedThingsFieldExpressionFactory();
        queryFilterCriteriaFactory = new QueryFilterCriteriaFactory(criteriaFactory, fieldExpressionFactory);

        final MapperLimitsConfig mapperLimitsConfig = connectivityConfig.getMappingConfig().getMapperLimitsConfig();
        mappingNumberLimitSource = mapperLimitsConfig.getMaxSourceMappers();
        mappingNumberLimitTarget = mapperLimitsConfig.getMaxTargetMappers();

        hostValidator = new HostValidator(connectivityConfig, loggingAdapter);
    }

    /**
     * Create a connection validator from connection specs.
     *
     * @param connectivityConfig the connectivity config
     * @param loggingAdapter a logging adapter
     * @param connectionSpecs specs of supported connection types.
     * @return a connection validator.
     */
    public static ConnectionValidator of(final ConnectivityConfig connectivityConfig,
            LoggingAdapter loggingAdapter, final AbstractProtocolValidator... connectionSpecs) {
        return new ConnectionValidator(connectivityConfig, loggingAdapter, connectionSpecs);
    }

    /**
     * Read the declared acknowledgement labels of sources and the issued acknowledgement labels of targets
     * and compute the set of acknowledgement labels the connection needs to declare.
     *
     * @param connection the connection.
     * @return the set of acknowledgement labels to declare.
     */
    public static Stream<AcknowledgementLabel> getAcknowledgementLabelsToDeclare(final Connection connection) {
        final ExpressionResolver connectionIdResolver = PlaceholderFactory.newExpressionResolver(
                PlaceholderFactory.newConnectionIdPlaceholder(), connection.getId());

        final Stream<AcknowledgementLabel> sourceDeclaredAcks = connection.getSources().stream()
                .flatMap(source -> source.getDeclaredAcknowledgementLabels().stream())
                .map(ackLabel -> resolveConnectionIdPlaceholder(connectionIdResolver, ackLabel))
                .filter(Optional::isPresent)
                .map(Optional::get);
        final Stream<AcknowledgementLabel> targetIssuedAcks =
                connection.getTargets().stream()
                        .map(Target::getIssuedAcknowledgementLabel)
                        .flatMap(Optional::stream)
                        .map(ackLabel -> resolveConnectionIdPlaceholder(connectionIdResolver, ackLabel))
                        .flatMap(Optional::stream)
                        // live-response is permitted as issued acknowledgement without declaration
                        .filter(label -> !DittoAcknowledgementLabel.LIVE_RESPONSE.equals(label));
        return Stream.concat(sourceDeclaredAcks, targetIssuedAcks);
    }

    /**
     * Resolves a potentially existing placeholder {@code {{connection:id}}} in the passed {@code ackLabel} with the
     * one resolved by the passed in {@code connectionIdResolver}.
     *
     * @param connectionIdResolver the resolver to use in order to resolve the connection id.
     * @param ackLabel the acknowledgement label to replace the placeholder in.
     * @return the resolved acknowledgement label.
     */
    public static Optional<AcknowledgementLabel> resolveConnectionIdPlaceholder(
            final ExpressionResolver connectionIdResolver,
            final AcknowledgementLabel ackLabel) {

        if (!ackLabel.isFullyResolved()) {
            return connectionIdResolver.resolve(ackLabel.toString())
                    .toOptional()
                    .map(AcknowledgementLabel::of);
        } else {
            return Optional.of(ackLabel);
        }
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
        validateDeclaredAndIssuedAcknowledgements(connection);
        checkMappingNumberOfSourcesAndTargets(dittoHeaders, connection);
        validateFormatOfCertificates(connection, dittoHeaders);
        hostValidator.validateHostname(connection.getHostname(), dittoHeaders);
        if (spec != null) {
            // throw error at validation site for clarity of stack trace
            spec.validate(connection, dittoHeaders, actorSystem);
        } else {
            throw new IllegalStateException("Unknown connection type: " + connection);
        }
    }

    /**
     * Check if number of mappings are valid
     *
     * @throws ConnectionConfigurationInvalidException if payload number is over predefined limit
     */
    private void checkMappingNumberOfSourcesAndTargets(final DittoHeaders dittoHeaders, final Connection connection) {
        connection.getSources().forEach(source -> checkPayloadMappingLimit(source.getPayloadMapping(),
                mappingNumberLimitSource, "source", String.join(",", source.getAddresses()), dittoHeaders));
        connection.getTargets().forEach(target -> checkPayloadMappingLimit(target.getPayloadMapping(),
                mappingNumberLimitTarget, "target", target.getAddress(), dittoHeaders));
    }

    private void checkPayloadMappingLimit(final PayloadMapping mapping, final int limit, final String entity,
            final String address, final DittoHeaders dittoHeaders) {
        final String errorMessage = "The number of configured payload mappings exceeded the limit.";

        if (mapping.getMappings().size() > limit) {
            throw ConnectionConfigurationInvalidException.newBuilder(errorMessage)
                    .description(
                            "The number of configured payload mappings for the " + entity + " with address " + address +
                                    " is above the limit of " + limit + ".")
                    .dittoHeaders(dittoHeaders)
                    .build();
        }
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

    private void validateDeclaredAndIssuedAcknowledgements(final Connection connection) {
        final String idPrefix = connection.getId() + ":";
        getAcknowledgementLabelsToDeclare(connection)
                .map(Object::toString)
                .forEach(label -> {
                    if (!label.startsWith(idPrefix)) {
                        throw AcknowledgementLabelInvalidException.of(
                                label,
                                "Declared acknowledgement labels of a connection must have the form " +
                                        "<connection-id>:<alphanumeric-suffix>",
                                null,
                                DittoHeaders.empty()
                        );
                    }
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

}
