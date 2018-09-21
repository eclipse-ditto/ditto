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
package org.eclipse.ditto.services.connectivity.messaging.validation;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

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
import org.eclipse.ditto.services.connectivity.messaging.internal.SSLContextCreator;

/**
 * Validate a connection according to its type.
 */
@Immutable
public final class ConnectionValidator {

    private final Map<ConnectionType, AbstractProtocolValidator> specMap;
    private final QueryFilterCriteriaFactory queryFilterCriteriaFactory;

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
     * @throws org.eclipse.ditto.model.base.exceptions.DittoRuntimeException if the connection has errors.
     * @throws java.lang.IllegalStateException if the connection type is not known.
     */
    void validate(final Connection connection, final DittoHeaders dittoHeaders) {
        final AbstractProtocolValidator spec = specMap.get(connection.getConnectionType());
        validateSourceAndTargetAddressesAreNonempty(connection, dittoHeaders);
        validateFormatOfCertificates(connection, dittoHeaders);
        if (spec != null) {
            // throw error at validation site for clarity of stack trace
            spec.validate(connection, dittoHeaders);
        } else {
            throw new IllegalStateException("Unknown connection type: " + connection);
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
