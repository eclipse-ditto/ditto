/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.signals.commands.common;


import org.eclipse.ditto.json.JsonPointer;

/**
 * Matches and maps the path to an internal command name.
 *
 * TODO adapt @since annotation @since 1.6.0
 */
public interface PathMatcher<T> {

    /**
     * Tries to match the given payload path against a set of patterns and returns the associated command name.
     *
     * @param path the path from a Ditto Protocol message
     * @return the internal command name
     * @throws org.eclipse.ditto.model.base.exceptions.DittoRuntimeException if the path does not match any of the known patterns.
     */
    T match(JsonPointer path);

}
