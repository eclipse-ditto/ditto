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
package org.eclipse.ditto.signals.commands.thingsearch.query;

import java.util.Optional;
import java.util.Set;

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
