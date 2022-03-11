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

import java.text.MessageFormat;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.common.Placeholders;
import org.eclipse.ditto.base.model.exceptions.DittoJsonException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionConfigurationInvalidException;
import org.eclipse.ditto.connectivity.model.ConnectionType;
import org.eclipse.ditto.connectivity.model.ConnectionUriInvalidException;
import org.eclipse.ditto.connectivity.model.FilteredTopic;
import org.eclipse.ditto.connectivity.model.HeaderMapping;
import org.eclipse.ditto.connectivity.model.Source;
import org.eclipse.ditto.connectivity.model.Target;
import org.eclipse.ditto.connectivity.service.config.ConnectivityConfig;
import org.eclipse.ditto.connectivity.service.mapping.DefaultMessageMapperFactory;
import org.eclipse.ditto.connectivity.service.mapping.DittoMessageMapper;
import org.eclipse.ditto.connectivity.service.mapping.MessageMapperFactory;
import org.eclipse.ditto.connectivity.service.mapping.MessageMapperRegistry;
import org.eclipse.ditto.connectivity.service.messaging.Resolvers;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.placeholders.Placeholder;
import org.eclipse.ditto.placeholders.PlaceholderFilter;

import akka.actor.ActorSystem;

/**
 * Protocol-specific specification for {@link org.eclipse.ditto.connectivity.model.Connection} objects.
 */
public abstract class AbstractProtocolValidator {

    /**
     * Type of connection for which this spec applies.
     *
     * @return the connection type.
     */
    public abstract ConnectionType type();

    /**
     * Check a connection of the declared type for errors and throw them if any exists.
     *
     * @param connection the connection to check for errors.
     * @param dittoHeaders headers of the command that triggered the connection validation.
     * @param actorSystem the ActorSystem to use for retrieving config.
     * @param connectivityConfig the connectivity config.
     * @throws DittoRuntimeException if the connection has errors.
     */
    public abstract void validate(Connection connection, DittoHeaders dittoHeaders, ActorSystem actorSystem,
            final ConnectivityConfig connectivityConfig);

    /**
     * Check whether the URI scheme of the connection belongs to an accepted scheme.
     *
     * @param connection the connection to check.
     * @param dittoHeaders headers of the command that triggered the connection validation.
     * @param acceptedSchemes valid URI schemes for the connection type.
     * @param secureSchemes subset of valid URI schemes that supports traffic encryption.
     * @param protocolName protocol name of the connection type.
     * @throws DittoRuntimeException if the URI scheme is not accepted.
     */
    protected static void validateUriScheme(final Connection connection,
            final DittoHeaders dittoHeaders,
            final Collection<String> acceptedSchemes,
            final Collection<String> secureSchemes,
            final String protocolName) {

        if (!acceptedSchemes.contains(connection.getProtocol())) {
            final String message =
                    MessageFormat.format("The URI scheme ''{0}'' is not valid for {1}.", connection.getProtocol(),
                            protocolName);
            final String description =
                    MessageFormat.format("Accepted URI schemes are: {0}", String.join(", ", acceptedSchemes));
            throw ConnectionUriInvalidException.newBuilder(connection.getUri())
                    .message(message)
                    .description(description)
                    .dittoHeaders(dittoHeaders)
                    .build();
        }

        // insecure protocol + certificates configured
        if (!secureSchemes.contains(connection.getProtocol()) && connection.getTrustedCertificates().isPresent()) {
            final String description = MessageFormat.format("Either switch to a secure protocol ({0}) or remove the " +
                    "trusted certificates.", secureSchemes);
            throw ConnectionUriInvalidException.newBuilder(connection.getUri())
                    .message("The connection has trusted certificates configured but uses an insecure protocol.")
                    .description(description)
                    .dittoHeaders(dittoHeaders)
                    .build();
        }
    }

    /**
     * Validate protocol-specific configurations of sources.
     *
     * @param connection the connection to check.
     * @param dittoHeaders headers of the command that triggered the connection validation.
     */
    protected void validateSourceConfigs(final Connection connection, final DittoHeaders dittoHeaders) {
        connection.getSources().forEach(source ->
                validateSource(source, dittoHeaders, sourceDescription(source, connection)));
    }

    /**
     * Validates the passed in {@code source} e.g. by validating its {@code enforcement} and {@code headerMapping}
     * for valid placeholder usage.
     *
     * @param source the source to validate
     * @param dittoHeaders the DittoHeaders to use in order for e.g. building DittoRuntimeExceptions
     * @param sourceDescription a descriptive text of the source
     * @throws ConnectionConfigurationInvalidException in case the Source configuration is invalid
     */
    protected abstract void validateSource(Source source, DittoHeaders dittoHeaders,
            Supplier<String> sourceDescription);

    /**
     * Validate protocol-specific configurations of targets.
     *
     * @param connection the connection to check.
     * @param dittoHeaders headers of the command that triggered the connection validation.
     */
    protected void validateTargetConfigs(final Connection connection, final DittoHeaders dittoHeaders) {
        connection.getTargets()
                .forEach(target -> validateTarget(target, dittoHeaders, targetDescription(target, connection)));
    }

    /**
     * Validate configurations of {@link org.eclipse.ditto.connectivity.model.PayloadMappingDefinition}.
     *
     * @param connection the connection to check the MappingContext in.
     * @param actorSystem the ActorSystem to use for retrieving config.
     * @param connectivityConfig the connectivity config.
     * @param dittoHeaders headers of the command that triggered the connection validation.
     */
    protected void validatePayloadMappings(final Connection connection, final ActorSystem actorSystem,
            final ConnectivityConfig connectivityConfig, final DittoHeaders dittoHeaders) {
        final MessageMapperFactory messageMapperFactory =
                DefaultMessageMapperFactory.of(connection, connectivityConfig, actorSystem, actorSystem.log());

        try {
            final MessageMapperRegistry messageMapperRegistry =
                    DittoJsonException.wrapJsonRuntimeException(connection.getPayloadMappingDefinition(), dittoHeaders,
                            (definition, theDittoHeaders) ->
                                    messageMapperFactory.registryOf(DittoMessageMapper.CONTEXT, definition)
                    );

            connection.getSources().stream()
                    .map(Source::getPayloadMapping).forEach(messageMapperRegistry::validatePayloadMapping);
            connection.getTargets().stream()
                    .map(Target::getPayloadMapping).forEach(messageMapperRegistry::validatePayloadMapping);

        } catch (final DittoRuntimeException e) {
            throw ConnectionConfigurationInvalidException.newBuilder(e.getMessage())
                    .description(e.getDescription().orElse(null))
                    .dittoHeaders(dittoHeaders)
                    .cause(e)
                    .build();
        } catch (final RuntimeException e) {
            throw ConnectionConfigurationInvalidException.newBuilder(e.getMessage())
                    .dittoHeaders(dittoHeaders)
                    .cause(e)
                    .build();
        }
    }

    /**
     * Validates the passed in {@code headerMapping} by validating valid placeholder usage.
     *
     * @param headerMapping the headerMapping to validate
     * @param dittoHeaders the DittoHeaders to use in order for e.g. building DittoRuntimeExceptions
     * @throws ConnectionConfigurationInvalidException in case the HeaderMapping configuration is invalid
     */
    protected void validateHeaderMapping(final HeaderMapping headerMapping, final DittoHeaders dittoHeaders) {
        headerMapping.getMapping().forEach((key, value)
                -> validateTemplate(value, dittoHeaders, Resolvers.getPlaceholders()));
    }

    protected static void validateExtraFields(final Target target) {
        target.getTopics().stream().map(FilteredTopic::getExtraFields)
                .forEach(extraFields -> extraFields.ifPresent(AbstractProtocolValidator::validateExtraFields));
    }

    private static void validateExtraFields(@Nullable final JsonFieldSelector extraFields) {
        if (extraFields == null) {
            return;
        }
        final String fieldSelector = extraFields.toString();
        if (Placeholders.containsAnyPlaceholder(fieldSelector)) {
            PlaceholderFilter.validate(fieldSelector, Resolvers.getPlaceholders());
        }
    }

    /**
     * Validates the passed in {@code target} e.g. by validating its {@code address} and {@code headerMapping}
     * for valid placeholder usage.
     *
     * @param target the target to validate
     * @param dittoHeaders the DittoHeaders to use in order for e.g. building DittoRuntimeExceptions
     * @param targetDescription a descriptive text of the target
     * @throws ConnectionConfigurationInvalidException in case the Target configuration is invalid
     */
    protected abstract void validateTarget(Target target, DittoHeaders dittoHeaders,
            Supplier<String> targetDescription);

    /**
     * Obtain a supplier of a description of a source of a connection.
     *
     * @param source the source.
     * @param connection the connection.
     * @return supplier of the description.
     */
    private static Supplier<String> sourceDescription(final Source source, final Connection connection) {
        return () -> MessageFormat.format("Source of index {0} of connection ''{1}''",
                source.getIndex(), connection.getId());
    }

    /**
     * Obtain a supplier of a description of a target of a connection.
     *
     * @param target the target.
     * @param connection the connection.
     * @return supplier of the description.
     */
    private static Supplier<String> targetDescription(final Target target, final Connection connection) {
        return () -> MessageFormat.format("Target of address ''{0}'' of connection ''{1}''",
                target.getAddress(), connection.getId());
    }

    /**
     * Validates that the passed {@code template} is both valid and that the placeholders in the passed {@code template}
     * are completely replaceable by the provided {@code placeholders}.
     *
     * @param template a string potentially containing placeholders to replace
     * @param headers the DittoHeaders to use in order for e.g. building DittoRuntimeExceptions
     * @param placeholders the {@link Placeholder}s to use for replacement
     * @throws ConnectionConfigurationInvalidException in case the template's placeholders could not completely be
     * resolved
     */
    protected void validateTemplate(final String template, final DittoHeaders headers,
            final Placeholder<?>... placeholders) {
        try {
            PlaceholderFilter.validate(template, placeholders);
        } catch (final DittoRuntimeException exception) {
            throw ConnectionConfigurationInvalidException
                    .newBuilder(exception.getMessage())
                    .description(exception.getDescription()
                            .orElse("Check the spelling and syntax of the placeholder.")
                    )
                    .cause(exception)
                    .dittoHeaders(headers)
                    .build();
        }
    }

    /**
     * Validates that the passed {@code template} is both valid and that the placeholders in the passed {@code
     * template}
     * are completely replaceable by the provided {@code placeholders}. Each placeholder will be replaced by
     * {@code stringUsedInPlaceholderReplacement} and the resolved template without any remaining placeholders will be
     * returned.
     *
     * @param template a string potentially containing placeholders to replace
     * @param headers the DittoHeaders to use in order for e.g. building DittoRuntimeExceptions
     * @param stringUsedInPlaceholderReplacement the dummy value used as a replacement for the found placeholders.
     * @param placeholders the {@link Placeholder}s to use for replacement
     * @return the {@code template} with every placeholder replaced by {@code stringUsedInPlaceholderReplacement}.
     * @throws ConnectionConfigurationInvalidException in case the template's placeholders could not completely be
     * resolved
     */
    protected List<String> validateTemplateAndReplace(final String template, final DittoHeaders headers,
            final String stringUsedInPlaceholderReplacement, final Placeholder<?>... placeholders) {
        try {
            return PlaceholderFilter.validateAndReplaceAll(template, stringUsedInPlaceholderReplacement, placeholders);
        } catch (final DittoRuntimeException exception) {
            throw ConnectionConfigurationInvalidException
                    .newBuilder(exception.getMessage())
                    .description(exception.getDescription()
                            .orElse("Check the spelling and syntax of the placeholder.")
                    )
                    .cause(exception)
                    .dittoHeaders(headers)
                    .build();
        }
    }

}
