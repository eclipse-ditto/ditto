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
import org.eclipse.ditto.signals.commands.base.CommandResponse;

/**
 * Aggregates all possible responses relating to a given {@link ThingSearchCommand}.
 *
 * @param <T> the type of the implementing class.
 */
public interface ThingSearchCommandResponse<T extends ThingSearchCommandResponse> extends CommandResponse<T> {

    /**
     * Type Prefix of Search command responses.
     */
    String TYPE_PREFIX = "thing-search." + TYPE_QUALIFIER + ":";

    @Override
    default JsonPointer getResourcePath() {
        return JsonPointer.empty();
    }

    @Override
    default String getResourceType() {
        return ThingSearchCommand.RESOURCE_TYPE;
    }

    @Override
    T setDittoHeaders(DittoHeaders dittoHeaders);

    /**
     * Search command responses do not have an ID. Thus this implementation always returns an empty string.
     *
     * @return an empty string.
     */
    @Override
    default String getId() {
        return "";
    }
}
