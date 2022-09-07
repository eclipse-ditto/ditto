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
package org.eclipse.ditto.thingsearch.api.commands.sudo;

import org.eclipse.ditto.base.api.commands.sudo.SudoCommand;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.json.JsonPointer;

/**
 * Interface for all Sudo Search Commands.
 *
 * @param <T> the type of the implementing class.
 */
public interface ThingSearchSudoCommand<T extends ThingSearchSudoCommand<T>> extends SudoCommand<T> {

    /**
     * Type Prefix of Sudo commands.
     */
    String TYPE_PREFIX = "thing-search.sudo." + TYPE_QUALIFIER + ":";

    /**
     * Thing search sudo resource type.
     */
    String RESOURCE_TYPE = "thing-search-sudo";

    @Override
    default JsonPointer getResourcePath() {
        // return empty resource path for SudoCommands as this path is currently not needed for SudoCommands:
        return JsonPointer.empty();
    }

    @Override
    default String getResourceType() {
        return RESOURCE_TYPE;
    }

    @Override
    default String getTypePrefix() {
        return TYPE_PREFIX;
    }

    @Override
    T setDittoHeaders(DittoHeaders dittoHeaders);

}
