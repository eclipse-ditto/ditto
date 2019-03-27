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
package org.eclipse.ditto.signals.commands.thingsearch.query;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

import javax.annotation.Nullable;

import org.eclipse.ditto.signals.commands.thingsearch.ThingSearchCommand;

/**
 * Aggregates all {@link ThingSearchCommand}s which query the Search service.
 *
 * @param <T> the type of the implementing class.
 */
public interface ThingSearchQueryCommand<T extends ThingSearchQueryCommand> extends ThingSearchCommand<T> {

    /**
     * Get the optional set of namespaces.
     *
     * @return the optional set of namespaces.
     */
    Optional<Set<String>> getNamespaces();

    /**
     * Sets the given namespaces on a copy of this command and returns it.
     * @param namespaces the namespaces.
     * @return the created command.
     */
    T setNamespaces(@Nullable Collection<String> namespaces);

    /**
     * Get the optional filter string.
     *
     * @return the optional filter string.
     */
    Optional<String> getFilter();

    @Override
    default Category getCategory() {
        return Category.QUERY;
    }
}
