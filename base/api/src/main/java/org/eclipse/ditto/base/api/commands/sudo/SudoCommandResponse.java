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
package org.eclipse.ditto.base.api.commands.sudo;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.json.JsonPointer;

/**
 * Aggregates all sudo command responses in Ditto.
 *
 * @param <T> the type of the implementing class.
 */
public interface SudoCommandResponse<T extends SudoCommandResponse<T>> extends CommandResponse<T> {

    /**
     * Type qualifier of sudo command responses.
     */
    String SUDO_TYPE_QUALIFIER = "sudo." + TYPE_QUALIFIER + ":";

    @Override
    default JsonPointer getResourcePath() {
        // return empty resource path for SudoCommandResponses as this path is currently not needed for
        // SudoCommandResponses:
        return JsonPointer.empty();
    }

    @Override
    T setDittoHeaders(DittoHeaders dittoHeaders);

}
