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
package org.eclipse.ditto.signals.commands.thingsearch;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.signals.commands.base.Command;

/**
 * Base Interface for all commands which are understood by the Search service. Is aware of a command name (e.g.:
 * "queryThings") and a command version. The version is used to support multiple command versions in one Search service
 * runtime.
 *
 * @param <T> the type of the implementing class.
 */
public interface ThingSearchCommand<T extends ThingSearchCommand> extends Command<T> {

    /**
     * Type Prefix of Search commands.
     */
    String TYPE_PREFIX = "thing-search." + TYPE_QUALIFIER + ":";

    /**
     * Thing Search resource type.
     */
    String RESOURCE_TYPE = "thing-search";

    @Override
    default String getTypePrefix() {
        return TYPE_PREFIX;
    }

    @Override
    default JsonPointer getResourcePath() {
        return JsonPointer.empty();
    }

    @Override
    default String getResourceType() {
        return RESOURCE_TYPE;
    }

    @Override
    T setDittoHeaders(DittoHeaders dittoHeaders);

    /**
     * Search commands do not have an ID. Thus this implementation always returns an empty string.
     *
     * @return an empty string.
     */
    @Override
    default String getId() {
        return "";
    }

}
