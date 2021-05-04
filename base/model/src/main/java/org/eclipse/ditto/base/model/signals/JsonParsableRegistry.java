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
package org.eclipse.ditto.base.model.signals;

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
