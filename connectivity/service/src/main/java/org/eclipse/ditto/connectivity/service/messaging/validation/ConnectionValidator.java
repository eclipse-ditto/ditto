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
package org.eclipse.ditto.connectivity.service.messaging.validation;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.acks.AcknowledgementLabel;
import org.eclipse.ditto.base.model.acks.AcknowledgementLabelInvalidException;
import org.eclipse.ditto.base.model.acks.AcknowledgementLabelNotUniqueException;
import org.eclipse.ditto.base.model.acks.DittoAcknowledgementLabel;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.service.placeholders.ConnectivityPlaceholders;
import org.eclipse.ditto.connectivity.model.ClientCertificateCredentials;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionConfigurationInvalidException;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.ConnectionType;
import org.eclipse.ditto.connectivity.model.Credentials;
import org.eclipse.ditto.connectivity.model.CredentialsVisitor;
import org.eclipse.ditto.connectivity.model.HmacCredentials;
import org.eclipse.ditto.connectivity.model.OAuthClientCredentials;
import org.eclipse.ditto.connectivity.model.PayloadMapping;
import org.eclipse.ditto.connectivity.model.Source;
import org.eclipse.ditto.connectivity.model.SshPublicKeyCredentials;
import org.eclipse.ditto.connectivity.model.Target;
import org.eclipse.ditto.connectivity.model.UserPasswordCredentials;
import org.eclipse.ditto.connectivity.service.config.ConnectionConfig;
import org.eclipse.ditto.connectivity.service.config.ConnectivityConfig;
import org.eclipse.ditto.connectivity.service.config.mapping.MapperLimitsConfig;
import org.eclipse.ditto.connectivity.service.messaging.internal.ssl.SSLContextCreator;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.logs.ConnectionLogger;
import org.eclipse.ditto.placeholders.ExpressionResolver;
import org.eclipse.ditto.placeholders.PlaceholderFactory;
import org.eclipse.ditto.placeholders.TimePlaceholder;
import org.eclipse.ditto.protocol.placeholders.ResourcePlaceholder;
import org.eclipse.ditto.protocol.placeholders.TopicPathPlaceholder;
import org.eclipse.ditto.rql.parser.RqlPredicateParser;
import org.eclipse.ditto.rql.query.filter.QueryFilterCriteriaFactory;

import akka.actor.ActorSystem;
import akka.event.LoggingAdapter;

/**
 * Validate a connection according to its type.
 */
@Immutable
public final class ConnectionValidator {

    private final Map<ConnectionType, AbstractProtocolValidator> specMap;
    private final QueryFilterCriteriaFactory queryFilterCriteriaFactory;
    private final LoggingAdapter loggingAdapter;
    private final ConnectivityConfig connectivityConfig;

    private ConnectionValidator(LoggingAdapter loggingAdapter,
            final ConnectivityConfig connectivityConfig,
            final AbstractProtocolValidator... connectionSpecs) {
        this.loggingAdapter = loggingAdapter;
        this.connectivityConfig = connectivityConfig;
        final Map<ConnectionType, AbstractProtocolValidator> theSpecMap = Arrays.stream(connectionSpecs)
                .collect(Collectors.toMap(AbstractProtocolValidator::type, Function.identity()));
        this.specMap = Collections.unmodifiableMap(theSpecMap);
        queryFilterCriteriaFactory = QueryFilterCriteriaFactory.modelBased(RqlPredicateParser.getInstance(),
                TopicPathPlaceholder.getInstance(), ResourcePlaceholder.getInstance(), TimePlaceholder.getInstance());
    }

    /**
     * Create a connection validator from connection specs.
     *
     * @param loggingAdapter a logging adapter
     * @param connectivityConfig the connectivity config
     * @param connectionSpecs specs of supported connection types.
     * @return a connection validator.
     */
    public static ConnectionValidator of(LoggingAdapter loggingAdapter, final ConnectivityConfig connectivityConfig,
            final AbstractProtocolValidator... connectionSpecs) {
        return new ConnectionValidator(loggingAdapter, connectivityConfig, connectionSpecs);
    }

    /**
     * Read the declared acknowledgement labels of sources and the issued acknowledgement labels of targets
     * and compute the set of acknowledgement labels the connection needs to declare.
     *
     * @param connection the connection.
     * @return the set of acknowledgement labels to declare.
     */
    public static Stream<AcknowledgementLabel> getAcknowledgementLabelsToDeclare(final Connection connection) {
        final Stream<AcknowledgementLabel> sourceDeclaredAcks =
                getSourceDeclaredAcknowledgementLabels(connection.getId(), connection.getSources());
        final Stream<AcknowledgementLabel> targetIssuedAcks = getTargetIssuedAcknowledgementLabels(connection);
        return Stream.concat(sourceDeclaredAcks, targetIssuedAcks);
    }

    private static Stream<AcknowledgementLabel> getTargetIssuedAcknowledgementLabels(final Connection connection) {
        return getTargetIssuedAcknowledgementLabels(connection.getId(), connection.getTargets())
                // live-response is permitted as issued acknowledgement without declaration
                .filter(label -> !DittoAcknowledgementLabel.LIVE_RESPONSE.equals(label));
    }

    /**
     * Read the declared acknowledgement labels of sources after placeholder resolution.
     *
     * @param connectionId the connection ID.
     * @param sources the sources.
     * @return the source declared acknowledgement labels.
     */
    public static Stream<AcknowledgementLabel> getSourceDeclaredAcknowledgementLabels(
            final ConnectionId connectionId,
            final Collection<Source> sources) {
        final ExpressionResolver connectionIdResolver = PlaceholderFactory.newExpressionResolver(
                ConnectivityPlaceholders.newConnectionIdPlaceholder(), connectionId);
        return sources.stream()
                .flatMap(source -> source.getDeclaredAcknowledgementLabels().stream())
                .map(ackLabel -> resolveConnectionIdPlaceholder(connectionIdResolver, ackLabel))
                .filter(Optional::isPresent)
                .map(Optional::get);
    }

    /**
     * Read the issued acknowledgement labels of targets after placeholder resolution.
     *
     * @param connectionId the connection ID.
     * @param targets the targets.
     * @return the target issued acknowledgement labels.
     */
    public static Stream<AcknowledgementLabel> getTargetIssuedAcknowledgementLabels(
            final ConnectionId connectionId,
            final Collection<Target> targets) {

        final ExpressionResolver connectionIdResolver = PlaceholderFactory.newExpressionResolver(
                ConnectivityPlaceholders.newConnectionIdPlaceholder(), connectionId);
        return targets.stream()
                .map(Target::getIssuedAcknowledgementLabel)
                .flatMap(Optional::stream)
                .map(ackLabel -> resolveConnectionIdPlaceholder(connectionIdResolver, ackLabel))
                .flatMap(Optional::stream);
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
                    .findFirst()
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
     * @throws org.eclipse.ditto.base.model.exceptions.DittoRuntimeException if the connection has errors.
     * @throws java.lang.IllegalStateException if the connection type is not known.
     */
    void validate(final Connection connection, final DittoHeaders dittoHeaders, final ActorSystem actorSystem) {
        // validate sources and targets
        validateSourcesAndTargets(connection, dittoHeaders, connectivityConfig);

        // validate configured acknowledgements
        validateDeclaredAndIssuedAcknowledgements(connection);

        // validate configured certificate
        final ConnectionLogger connectionLogger = ConnectionLogger.getInstance(connection.getId(),
                connectivityConfig.getMonitoringConfig().logger());
        validateFormatOfCertificates(connection, dittoHeaders, connectionLogger);
        final ConnectionType connectionType = connection.getConnectionType();
        // validate configured host
        final HostValidator hostValidator = new DefaultHostValidator(connectivityConfig, loggingAdapter);

        if(connectionType != ConnectionType.HONO) {
            hostValidator.validateHostname(connection.getHostname(), dittoHeaders);
        }

        // tunneling not supported for kafka
        if (ConnectionType.KAFKA == connectionType && connection.getSshTunnel().isPresent()) {
            throw ConnectionConfigurationInvalidException
                    .newBuilder("SSH tunneling not supported.")
                    .description(
                            "SSH tunneling is not supported for the connection type <" + ConnectionType.KAFKA + ">.")
                    .dittoHeaders(dittoHeaders)
                    .build();
        }

        // validate ssh tunnel
        connection.getSshTunnel()
                .ifPresent(tunnel -> SshTunnelValidator.getInstance(dittoHeaders, hostValidator).validate(tunnel));

        // validate credentials
        connection.getCredentials()
                .ifPresent(credentials -> credentials.accept(
                        CredentialsValidationVisitor.of(connection, dittoHeaders, connectivityConfig, hostValidator)));

        // protocol specific validations
        final AbstractProtocolValidator spec = specMap.get(connection.getConnectionType());
        if (spec != null) {
            // throw error at validation site for clarity of stack trace
            spec.validate(connection, dittoHeaders, actorSystem, connectivityConfig);
        } else {
            throw new IllegalStateException("Unknown connection type: " + connection);
        }
    }

    private void validateSourcesAndTargets(final Connection connection,
            final DittoHeaders dittoHeaders, final ConnectivityConfig connectivityConfig) {
        final ConnectionConfig connectionConfig = connectivityConfig.getConnectionConfig();
        final MapperLimitsConfig mapperLimitsConfig = connectivityConfig.getMappingConfig().getMapperLimitsConfig();
        checkNumberOfSourcesAndTargets(connection, dittoHeaders, connectionConfig.getMaxNumberOfSources(),
                connectionConfig.getMaxNumberOfTargets());
        validateSourceAndTargetAddressesAreNonempty(connection, dittoHeaders);
        checkMappingNumberOfSourcesAndTargets(connection, dittoHeaders, mapperLimitsConfig.getMaxSourceMappers(),
                mapperLimitsConfig.getMaxTargetMappers());
    }

    /**
     * Check if number of sources and targets within a connection is valid
     *
     * @param connection the connection to validate.
     * @param dittoHeaders headers of the command that triggered the connection validation.
     * @param maxNumberOfSources maximum number of allowed sources
     * @param maxNumberOfTargets maximum number of allowed targets
     * @throws ConnectionConfigurationInvalidException if number is over predefined limit
     */
    private void checkNumberOfSourcesAndTargets(final Connection connection,
            final DittoHeaders dittoHeaders, final int maxNumberOfSources, final int maxNumberOfTargets) {
        final String errorMessage = "The number of configured sources or targets within a connection exceeded.";
        if (connection.getSources().size() > maxNumberOfSources) {
            throw ConnectionConfigurationInvalidException.newBuilder(errorMessage)
                    .description(
                            "The number of configured sources for connection: " + connection.getId() +
                                    " is above the limit of " + maxNumberOfSources + ".")
                    .dittoHeaders(dittoHeaders)
                    .build();
        } else if (connection.getTargets().size() > maxNumberOfTargets) {
            throw ConnectionConfigurationInvalidException.newBuilder(errorMessage)
                    .description("The number of configured targets for connection: " + connection.getId() +
                            " is above the limit of " + maxNumberOfTargets + ".")
                    .dittoHeaders(dittoHeaders)
                    .build();
        }
    }

    /**
     * Check if number of mappings are valid
     *
     * @param connection the connection to validate.
     * @param dittoHeaders headers of the command that triggered the connection validation.
     * @param maxMappingsPerSource maximum number of payload mappings per source
     * @param maxMappingsPerTarget maximum number of payload mappings per target
     * @throws ConnectionConfigurationInvalidException if payload number is over predefined limit
     */
    private void checkMappingNumberOfSourcesAndTargets(final Connection connection, final DittoHeaders dittoHeaders,
            final int maxMappingsPerSource, final int maxMappingsPerTarget) {
        connection.getSources().forEach(source -> checkPayloadMappingLimit(source.getPayloadMapping(),
                maxMappingsPerSource, "source", String.join(",", source.getAddresses()), dittoHeaders));
        connection.getTargets().forEach(target -> checkPayloadMappingLimit(target.getPayloadMapping(),
                maxMappingsPerTarget, "target", target.getAddress(), dittoHeaders));
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

    /**
     * Check if source and target address is not empty
     *
     * @param connection the connection to validate.
     * @param dittoHeaders headers of the command that triggered the connection validation.
     * @throws ConnectionConfigurationInvalidException if address is empty
     */
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
            target.getTopics().forEach(topic -> topic.getFilter().ifPresent(filter ->
                    // will throw an InvalidRqlExpressionException if the RQL expression was not valid:
                    queryFilterCriteriaFactory.filterCriteria(filter, dittoHeaders)
            ));
        });
    }

    private void validateDeclaredAndIssuedAcknowledgements(final Connection connection) {
        final String idPrefix = connection.getId() + ":";

        getAcknowledgementLabelsToDeclare(connection)
                .map(Object::toString)
                .forEach(label -> {
                    if (!label.startsWith(idPrefix)) {
                        final String message = String.format("Declared acknowledgement labels of a connection must " +
                                "have the form %s<alphanumeric-suffix>", idPrefix);
                        throw AcknowledgementLabelInvalidException.of(
                                label,
                                message,
                                null,
                                DittoHeaders.empty()
                        );
                    }
                });

        // check uniqueness of target issued acks inside one connection after checking for the validity of the ack
        // labels in order to give the AcknowledgementLabelInvalidException priority
        final List<String> targetAckLabels = getTargetIssuedAcknowledgementLabels(connection)
                .map(Object::toString)
                .toList();
        final Set<String> distinctTargetAckLabels = new HashSet<>(targetAckLabels);
        if (targetAckLabels.size() > distinctTargetAckLabels.size()) {
            throw AcknowledgementLabelNotUniqueException.newBuilder()
                    .message("An issued acknowledgement label may only be used for one target of the connection.")
                    .description("Please choose unique suffixes for your configured issued acknowledgement labels.")
                    .build();
        }
    }

    private static void validateFormatOfCertificates(final Connection connection, final DittoHeaders dittoHeaders,
            final ConnectionLogger connectionLogger) {
        final Optional<String> trustedCertificates = connection.getTrustedCertificates();
        final Optional<Credentials> credentials =
                connection.getCredentials().filter(c -> c.accept(new IsClientCertificateCredentialsVisitor()));
        // check if there are certificates to check
        if (trustedCertificates.isPresent() || credentials.isPresent()) {
            credentials.orElseGet(ClientCertificateCredentials::empty)
                    .accept(SSLContextCreator.fromConnection(connection, dittoHeaders, connectionLogger));
        }
    }

    private static DittoRuntimeException emptyAddressesError(final String location, final DittoHeaders dittoHeaders) {
        final String message = location + ": addresses may not be empty.";
        return ConnectionConfigurationInvalidException.newBuilder(message)
                .dittoHeaders(dittoHeaders)
                .build();
    }

    private static final class IsClientCertificateCredentialsVisitor implements CredentialsVisitor<Boolean> {

        @Override
        public Boolean clientCertificate(final ClientCertificateCredentials credentials) {
            return true;
        }

        @Override
        public Boolean usernamePassword(final UserPasswordCredentials credentials) {
            return false;
        }

        @Override
        public Boolean sshPublicKeyAuthentication(final SshPublicKeyCredentials credentials) {
            return false;
        }

        @Override
        public Boolean hmac(final HmacCredentials credentials) {
            return false;
        }

        @Override
        public Boolean oauthClientCredentials(final OAuthClientCredentials credentials) {
            return false;
        }
    }
}
