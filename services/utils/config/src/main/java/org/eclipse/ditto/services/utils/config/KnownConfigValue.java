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
package org.eclipse.ditto.services.utils.config;

/**
 * This interface represents a well-known config value.
 * The purpose of this type is
 * <ul>
 *     <li>provide a well-known config path expression and</li>
 *     <li>provide a default value to fall back to in case no value was set in config for that path.</li>
 * </ul>
 * A path expression is a dot-separated expression such as {@code foo.bar.baz}.
 */
public interface KnownConfigValue {

    /**
     * Returns the path of this config value.
     *
     * @return the path expression.
     */
    String getPath();

    /**
     * Returns the default value to fall back to if no value was set in configuration for the path.
     *
     * @return the default value.
     */
    Object getDefaultValue();

}
