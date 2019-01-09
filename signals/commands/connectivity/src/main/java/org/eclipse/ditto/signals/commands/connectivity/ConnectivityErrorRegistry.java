/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.signals.commands.connectivity;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.connectivity.ConnectionConfigurationInvalidException;
import org.eclipse.ditto.model.connectivity.ConnectionSignalIdEnforcementFailedException;
import org.eclipse.ditto.model.connectivity.ConnectionUriInvalidException;
import org.eclipse.ditto.model.connectivity.MessageMapperConfigurationFailedException;
import org.eclipse.ditto.model.connectivity.MessageMapperConfigurationInvalidException;
import org.eclipse.ditto.model.connectivity.MessageMappingFailedException;
import org.eclipse.ditto.model.connectivity.MessageSendingFailedException;
import org.eclipse.ditto.model.connectivity.TopicParseException;
import org.eclipse.ditto.model.connectivity.UnresolvedPlaceholderException;
import org.eclipse.ditto.signals.base.AbstractErrorRegistry;
import org.eclipse.ditto.signals.base.JsonParsable;
import org.eclipse.ditto.signals.commands.base.CommonErrorRegistry;
import org.eclipse.ditto.signals.commands.connectivity.exceptions.ConnectionConflictException;
import org.eclipse.ditto.signals.commands.connectivity.exceptions.ConnectionFailedException;
import org.eclipse.ditto.signals.commands.connectivity.exceptions.ConnectionIdNotExplicitlySettableException;
import org.eclipse.ditto.signals.commands.connectivity.exceptions.ConnectionNotAccessibleException;
import org.eclipse.ditto.signals.commands.connectivity.exceptions.ConnectionSignalIllegalException;
import org.eclipse.ditto.signals.commands.connectivity.exceptions.ConnectionTimeoutException;
import org.eclipse.ditto.signals.commands.connectivity.exceptions.ConnectionUnavailableException;

/**
 * A {@link org.eclipse.ditto.signals.base.ErrorRegistry} aware of all
 * {@link org.eclipse.ditto.model.connectivity.ConnectivityException}s.
 */
@Immutable
public final class ConnectivityErrorRegistry extends AbstractErrorRegistry<DittoRuntimeException> {

    private ConnectivityErrorRegistry(final Map<String, JsonParsable<DittoRuntimeException>> parseStrategies) {
        super(parseStrategies);
    }

    /**
     * Returns a new {@code ConnectivityErrorRegistry}.
     *
     * @return the error registry.
     */
    public static ConnectivityErrorRegistry newInstance() {
        return newInstance(Collections.emptyMap());
    }

    /**
     * Returns a new {@code ConnectivityErrorRegistry} providing {@code additionalParseStrategies} as argument - that way
     * the user of this ConnectivityErrorRegistry can register additional parsers for his own extensions of {@link
     * org.eclipse.ditto.model.base.exceptions.DittoRuntimeException}.
     *
     * @param additionalParseStrategies a map relating a connectivity ERROR_CODE to a specific JsonParsable of
     * DittoRuntimeException.
     * @return the error registry.
     */
    public static ConnectivityErrorRegistry newInstance(
            final Map<String, JsonParsable<DittoRuntimeException>> additionalParseStrategies) {
        final Map<String, JsonParsable<DittoRuntimeException>> parseStrategies =
                new HashMap<>(additionalParseStrategies);

        final CommonErrorRegistry commonErrorRegistry = CommonErrorRegistry.newInstance();
        commonErrorRegistry.getTypes().forEach(type -> parseStrategies.put(type, commonErrorRegistry));

        // exceptions in package org.eclipse.ditto.signals.commands.connectivity.exceptions
        parseStrategies.put(ConnectionConflictException.ERROR_CODE,
                ConnectionConflictException::fromJson);

        parseStrategies.put(ConnectionFailedException.ERROR_CODE,
                ConnectionFailedException::fromJson);

        parseStrategies.put(ConnectionIdNotExplicitlySettableException.ERROR_CODE,
                ConnectionIdNotExplicitlySettableException::fromJson);

        parseStrategies.put(ConnectionNotAccessibleException.ERROR_CODE,
                ConnectionNotAccessibleException::fromJson);

        parseStrategies.put(ConnectionSignalIllegalException.ERROR_CODE,
                ConnectionSignalIllegalException::fromJson);

        parseStrategies.put(ConnectionUnavailableException.ERROR_CODE,
                ConnectionUnavailableException::fromJson);

        // exceptions in package org.eclipse.ditto.model.connectivity
        parseStrategies.put(ConnectionConfigurationInvalidException.ERROR_CODE,
                ConnectionConfigurationInvalidException::fromJson);

        parseStrategies.put(ConnectionSignalIdEnforcementFailedException.ERROR_CODE,
                ConnectionSignalIdEnforcementFailedException::fromJson);

        parseStrategies.put(ConnectionTimeoutException.ERROR_CODE,
                ConnectionTimeoutException::fromJson);

        parseStrategies.put(ConnectionUriInvalidException.ERROR_CODE,
                ConnectionUriInvalidException::fromJson);

        parseStrategies.put(MessageMapperConfigurationFailedException.ERROR_CODE,
                MessageMapperConfigurationFailedException::fromJson);

        parseStrategies.put(MessageMapperConfigurationInvalidException.ERROR_CODE,
                MessageMapperConfigurationInvalidException::fromJson);

        parseStrategies.put(MessageMappingFailedException.ERROR_CODE,
                MessageMappingFailedException::fromJson);

        parseStrategies.put(MessageSendingFailedException.ERROR_CODE,
                MessageSendingFailedException::fromJson);

        parseStrategies.put(TopicParseException.ERROR_CODE,
                TopicParseException::fromJson);

        parseStrategies.put(UnresolvedPlaceholderException.ERROR_CODE,
                UnresolvedPlaceholderException::fromJson);

        return new ConnectivityErrorRegistry(parseStrategies);
    }
}
