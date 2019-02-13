/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
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
     *
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

    /**
     * Get the optional key to the next page.
     *
     * @return the optional key to the next page.
     */
    Optional<String> getNextPageKey();

    @Override
    default Category getCategory() {
        return Category.QUERY;
    }
}
