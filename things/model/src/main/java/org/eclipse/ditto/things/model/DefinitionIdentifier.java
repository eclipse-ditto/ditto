/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.things.model;

import java.net.URL;
import java.util.Optional;

/**
 * This interface represents a single fully qualified Identifier of a {@code Definition} both used for
 * {@code Thing} and {@code Feature}.
 */
public interface DefinitionIdentifier extends CharSequence {

    /**
     * Returns the namespace of ths Identifier.
     * Might be an empty string when the Identifier contains an {@code url} instead.
     *
     * @return the namespace.
     */
    String getNamespace();

    /**
     * Returns the name of this Identifier.
     * Might be an empty string when the Identifier contains an {@code url} instead.
     *
     * @return the name.
     */
    String getName();

    /**
     * Returns the version of this Identifier.
     * Might be an empty string when the Identifier contains an {@code url} instead.
     *
     * @return the version.
     */
    String getVersion();

    /**
     * Returns the optional URL of this Identifier - if this is present, the {@code namespace}, {@code name} and
     * {@code version} strings of the definition are empty.
     * @return the optional URL.
     * @since 2.1.0
     */
    Optional<URL> getUrl();

    /**
     * Returns the string representation of this Identifier with the following structure:
     * <ul>
     * <li>either {@code "namespace:name:version"}</li>
     * <li>or {@code "http(s)://some.url:80/some/resource.json"}</li>
     * </ul>
     *
     * @return the string representation.
     */
    @Override
    String toString();

}
