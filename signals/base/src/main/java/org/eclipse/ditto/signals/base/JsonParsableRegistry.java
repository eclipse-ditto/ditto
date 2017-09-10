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
package org.eclipse.ditto.signals.base;

import java.util.Set;

/**
 * A registry is aware of a set of {@link JsonParsable}s which the registry can parse from a {@link
 * org.eclipse.ditto.json.JsonObject}.
 *
 * @param <T> the type to parse.
 */
public interface JsonParsableRegistry<T> extends JsonParsable<T> {

    /**
     * Returns the types (command, response, event, error) this registry supports.
     *
     * @return the types this registry supports.
     */
    Set<String> getTypes();

}
