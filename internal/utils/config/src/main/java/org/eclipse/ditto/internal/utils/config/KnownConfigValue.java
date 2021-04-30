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
package org.eclipse.ditto.internal.utils.config;

/**
 * This interface represents a well-known config value.
 * The purpose of this type is
 * <ul>
 *     <li>provide a well-known config path expression and</li>
 *     <li>provide a default value to fall back to in case no value was set in config for that path.</li>
 * </ul>
 * A path expression is a dot-separated expression such as {@code foo.bar.baz}.
 */
public interface KnownConfigValue extends WithConfigPath {

    /**
     * Returns the default value to fall back to if no value was set in configuration for the path.
     *
     * @return the default value.
     */
    Object getDefaultValue();

}
