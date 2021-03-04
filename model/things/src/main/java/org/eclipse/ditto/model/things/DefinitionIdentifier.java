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
package org.eclipse.ditto.model.things;

/**
 * This interface represents a single fully qualified Identifier of a {@code Definition} both used for
 * {@code Thing} and {@code Feature}.
 */
public interface DefinitionIdentifier extends CharSequence {

    /**
     * Returns the namespace of ths Identifier.
     *
     * @return the namespace.
     */
    String getNamespace();

    /**
     * Returns the name of this Identifier.
     *
     * @return the name.
     */
    String getName();

    /**
     * Returns the version of this Identifier.
     *
     * @return the version.
     */
    String getVersion();

    /**
     * Returns the string representation of this Identifier with the following structure:
     * {@code "namespace:name:version"}
     *
     * @return the string representation.
     */
    @Override
    String toString();

}
