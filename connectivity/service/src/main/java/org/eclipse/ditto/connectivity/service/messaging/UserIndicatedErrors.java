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
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.eclipse.ditto.internal.utils.config.DittoConfigError;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigList;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueType;

/**
 * Allows finding out if a given {@link Throwable} matches the configured list of user indicated errors.
 */
final class UserIndicatedErrors {

    private static final String USER_INDICATED_ERRORS = "ditto.connectivity.user-indicated-errors";
    private final List<ErrorDefinition> errorDefinitions;

    private UserIndicatedErrors(final List<ErrorDefinition> errorDefinitions) {
        this.errorDefinitions = errorDefinitions;
    }

    /**
     * Reads the configured list from the hocon config and creates a new {@link UserIndicatedErrors} based on this config.
     *
     * @param config the hocon config holding the list of known user indicated errors.
     * @return the new error list.
     */
    static UserIndicatedErrors of(final Config config) {
        final List<ErrorDefinition> definitionList = getConfiguredListOrEmpty(config);
        return new UserIndicatedErrors(definitionList);
    }

    private static List<ErrorDefinition> getConfiguredListOrEmpty(final Config config) {
        try {
            final ConfigList list = config.getList(USER_INDICATED_ERRORS);
            final Stream<ErrorDefinition> errorDefinitionsFromString = list.stream()
                    .filter(value -> ConfigValueType.STRING.equals(value.valueType()))
                    .map(ConfigValue::unwrapped)
                    .map(Object::toString)
                    .map(ConfigFactory::parseString)
                    .map(ErrorDefinition::of);
            final Stream<ErrorDefinition> errorDefinitionsFromMap = list.stream()
                    .filter(value -> ConfigValueType.OBJECT.equals(value.valueType()))
                    .map(ConfigValue::render)
                    .map(ConfigFactory::parseString)
                    .map(ErrorDefinition::of);
            return Stream.concat(errorDefinitionsFromMap, errorDefinitionsFromString)
                    .toList();
        } catch (final ConfigException.Missing | ConfigException.WrongType e) {
            return List.of();
        }
    }

    /**
     * Checks whether the passed {@code throwable} matches against any of the configured error definitions indicating
     * that the Throwable is an error indicated by a user and not an internal one.
     *
     * @param throwable the throwable that should be checked.
     * @return True if the throwable matches and {@link ErrorDefinition} contained in this list.
     */
    boolean matches(@Nullable final Throwable throwable) {
        if (throwable == null) {
            return false;
        }
        return errorDefinitions.stream()
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
         * @return the new error definition
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
         * Matches the passed {@code throwable}'s class name and message against the configured ones of this instace
         * and returns {@code true} if they match.
         *
         * @param throwable the throwable that should be checked.
         * @return {@code true} if the throwable matches this definition and false if not.
         */
        boolean matches(final Throwable throwable) {
            boolean matches = exceptionName.equals(throwable.getClass().getName()) &&
                    messagePattern.matcher(String.valueOf(throwable.getMessage())).matches();
            final Throwable cause = throwable.getCause();
            if (matches) {
                return true;
            } else if (cause != null && cause != throwable) {
                return matches(cause);
            }
            return false;
        }

    }

}
