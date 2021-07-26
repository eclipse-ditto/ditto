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
package org.eclipse.ditto.connectivity.service.messaging;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.eclipse.ditto.internal.utils.config.DittoConfigError;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;

/**
 * Allows to find out if a given {@link Throwable} matches this list.
 */
final class UserIndicatedErrors {

    private static final String USER_INDICATED_ERRORS = "ditto.connectivity.user-indicated-errors";
    private final Iterable<ErrorDefinition> errorDefinitions;

    private UserIndicatedErrors(final Iterable<ErrorDefinition> errorDefinitions) {
        this.errorDefinitions = errorDefinitions;
    }

    /**
     * Reads the configured list from the hocon config and creates a new {@link UserIndicatedErrors} based on this config.
     *
     * @param config the hocon config holding the list of known user indicated errors.
     * @return the new error list.
     */
    static UserIndicatedErrors of(final Config config) {
        final List<? extends Config> configList = getConfiguredListOrEmpty(config);
        final List<ErrorDefinition> definitionList = configList.stream()
                .map(Config.class::cast)
                .map(ErrorDefinition::of)
                .collect(Collectors.toList());
        return new UserIndicatedErrors(definitionList);
    }

    private static List<? extends Config> getConfiguredListOrEmpty(final Config config) {
        try {
            return config.getConfigList(USER_INDICATED_ERRORS);
        } catch (final ConfigException.Missing | ConfigException.WrongType e) {
            return List.of();
        }
    }

    /**
     * @param throwable the throwable that should be checked.
     * @return True if the throwable matches and {@link ErrorDefinition} contained in this list.
     */
    boolean matches(final Throwable throwable) {
        return StreamSupport.stream(errorDefinitions.spliterator(), true)
                .anyMatch(definition -> definition.matches(throwable));
    }

    private static final class ErrorDefinition {

        private static final String NAME = "exceptionName";
        private static final String PATTERN = "messagePattern";

        private final String exceptionName;
        private final Pattern messagePattern;

        private ErrorDefinition(final String exceptionName, final Pattern messagePattern) {
            this.exceptionName = exceptionName;
            this.messagePattern = messagePattern;
        }

        /**
         * Creates a new {@link UserIndicatedErrors.ErrorDefinition} from a config which is expected to have {@link UserIndicatedErrors.ErrorDefinition#NAME}
         * and {@link UserIndicatedErrors.ErrorDefinition#PATTERN} as key.
         *
         * @param config the config  which is expected to have {@link UserIndicatedErrors.ErrorDefinition#NAME}
         * and {@link UserIndicatedErrors.ErrorDefinition#PATTERN} as key.
         * @return the new blame definition
         */
        private static ErrorDefinition of(final Config config) {
            try {
                final var exceptionName = config.getString(NAME);
                final var regex = config.getString(PATTERN);
                final var pattern = Pattern.compile(regex);
                return new ErrorDefinition(exceptionName, pattern);
            } catch (final ConfigException.Missing | ConfigException.WrongType e) {
                throw new DittoConfigError(e);
            }
        }

        /**
         * @param throwable the throwable that should be checked.
         * @return True if the throwable matches this definition and false if not.
         */
        boolean matches(final Throwable throwable) {
            return exceptionName.equals(throwable.getClass().getName()) &&
                    messagePattern.matcher(String.valueOf(throwable.getMessage())).matches();
        }

    }

}
