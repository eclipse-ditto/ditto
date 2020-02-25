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
package org.eclipse.ditto.signals.commands.thingsearch;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.thingsearch.Option;
import org.eclipse.ditto.signals.base.WithIdButActuallyNot;
import org.eclipse.ditto.signals.commands.base.Command;

/**
 * Base Interface for all commands which are understood by the Search service. Is aware of a command name (e.g.:
 * "queryThings") and a command version. The version is used to support multiple command versions in one Search service
 * runtime.
 *
 * @param <T> the type of the implementing class.
 */
public interface ThingSearchCommand<T extends ThingSearchCommand> extends Command<T>, WithIdButActuallyNot {

    /**
     * Type Prefix of Search commands.
     */
    String TYPE_PREFIX = "thing-search." + TYPE_QUALIFIER + ":";

    /**
     * Thing Search resource type.
     */
    String RESOURCE_TYPE = "thing-search";

    /**
     * Returns the selected fields which are to be included in the JSON of the retrieved entity.
     *
     * @return the selected fields.
     */
    default Optional<JsonFieldSelector> getSelectedFields() {
        return Optional.empty();
    }

    /**
     * Get the optional set of namespaces.
     *
     * @return the optional set of namespaces.
     */
    default Optional<Set<String>> getNamespaces() {return Optional.empty();}

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

}
