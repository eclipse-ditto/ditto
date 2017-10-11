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
package org.eclipse.ditto.services.models.thingsearch.commands.sudo;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.signals.commands.base.Command;

/**
 * Interface for all Sudo Search Commands.
 *
 * @param <T> the type of the implementing class.
 */
public interface ThingSearchSudoCommand<T extends ThingSearchSudoCommand> extends Command<T> {

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

    /**
     * Sudo commands do not have an ID. Thus this implementation always returns an empty string.
     *
     * @return an empty string.
     */
    @Override
    default String getId() {
        return "";
    }
}
