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

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.signals.commands.base.CommandResponse;

/**
 * Common interface of all possible responses which are related to a given {@link ConnectivityCommand}. Implementations of
 * this interface are required to be immutable.
 *
 * @param <T> the type of the implementing class.
 */
public interface ConnectivityCommandResponse<T extends ConnectivityCommandResponse> extends CommandResponse<T> {

    /**
     * Type Prefix of Connectivity command responses.
     */
    String TYPE_PREFIX = "connectivity." + TYPE_QUALIFIER + ":";

    /**
     * Returns the identifier of the Connection.
     *
     * @return the identifier of the Connection.
     */
    String getConnectionId();

    @Override
    default String getId() {
        return getConnectionId();
    }

    @Override
    default JsonPointer getResourcePath() {
        return JsonPointer.empty();
    }

    @Override
    default String getResourceType() {
        return ConnectivityCommand.RESOURCE_TYPE;
    }

}
