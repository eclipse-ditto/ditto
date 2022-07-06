/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.api.commands.sudo;

import org.eclipse.ditto.base.api.commands.sudo.SudoQueryCommandResponse;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.json.JsonValue;

/**
 * Aggregates all ConnectivitySudoCommand Responses.
 *
 * @param <T> the type of the implementing class.
 */
public interface ConnectivitySudoQueryCommandResponse<T extends ConnectivitySudoQueryCommandResponse<T>> extends
        SudoQueryCommandResponse<T> {

    /**
     * Type Prefix of Connectivity sudo command responses.
     */
    String TYPE_PREFIX = "connectivity." + SUDO_TYPE_QUALIFIER;

    @Override
    default String getResourceType() {
        return ConnectivitySudoCommand.RESOURCE_TYPE;
    }

    @Override
    T setEntity(JsonValue entity);

    @Override
    T setDittoHeaders(DittoHeaders dittoHeaders);

    /**
     * An enumeration of the known {@link org.eclipse.ditto.json.JsonField}s of a Sudo Connectivity command response.
     */
    class JsonFields extends CommandResponse.JsonFields {

        private JsonFields() {
            throw new AssertionError();
        }
    }

}
