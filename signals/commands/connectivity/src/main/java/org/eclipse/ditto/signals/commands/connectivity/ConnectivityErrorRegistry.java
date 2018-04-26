/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.signals.commands.connectivity;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.connectivity.ConnectionConfigurationInvalidException;
import org.eclipse.ditto.model.connectivity.ConnectionUriInvalidException;
import org.eclipse.ditto.model.connectivity.MessageMapperConfigurationFailedException;
import org.eclipse.ditto.model.connectivity.MessageMapperConfigurationInvalidException;
import org.eclipse.ditto.model.connectivity.MessageMappingFailedException;
import org.eclipse.ditto.signals.base.AbstractErrorRegistry;
import org.eclipse.ditto.signals.base.JsonParsable;
import org.eclipse.ditto.signals.commands.base.CommonErrorRegistry;
import org.eclipse.ditto.signals.commands.connectivity.exceptions.ConnectionConflictException;
import org.eclipse.ditto.signals.commands.connectivity.exceptions.ConnectionFailedException;
import org.eclipse.ditto.signals.commands.connectivity.exceptions.ConnectionNotAccessibleException;
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

        parseStrategies.put(ConnectionConfigurationInvalidException.ERROR_CODE,
                ConnectionConfigurationInvalidException::fromJson);
        parseStrategies.put(ConnectionUriInvalidException.ERROR_CODE, ConnectionUriInvalidException::fromJson);
        parseStrategies.put(MessageMappingFailedException.ERROR_CODE, MessageMappingFailedException::fromJson);
        parseStrategies.put(MessageMapperConfigurationInvalidException.ERROR_CODE,
                MessageMapperConfigurationInvalidException::fromJson);
        parseStrategies.put(MessageMapperConfigurationFailedException.ERROR_CODE,
                MessageMapperConfigurationFailedException::fromJson);
        parseStrategies.put(ConnectionNotAccessibleException.ERROR_CODE, ConnectionNotAccessibleException::fromJson);
        parseStrategies.put(ConnectionUnavailableException.ERROR_CODE, ConnectionUnavailableException::fromJson);
        parseStrategies.put(ConnectionFailedException.ERROR_CODE, ConnectionFailedException::fromJson);
        parseStrategies.put(ConnectionConflictException.ERROR_CODE, ConnectionConflictException::fromJson);

        return new ConnectivityErrorRegistry(parseStrategies);
    }
}
