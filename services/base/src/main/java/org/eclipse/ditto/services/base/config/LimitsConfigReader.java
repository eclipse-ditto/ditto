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
package org.eclipse.ditto.services.base.config;

/**
 * Limits configurations.
 */
public interface LimitsConfigReader {

    /**
     * The configuration path of the Ditto limits.
     */
    String PATH = "ditto.limits";

    /**
     * Retrieve the maximum possible size of "Thing" entities in bytes.
     *
     * @return max size in bytes.
     */
    long thingsMaxSize();

    /**
     * Retrieve the maximum possible size of "Policies" entities in bytes.
     *
     * @return max size in bytes.
     */
    long policiesMaxSize();

    /**
     * Retrieve the maximum possible size of "Policies" entities in bytes.
     *
     * @return max size in bytes.
     */
    long messagesMaxSize();

    /**
     * Retrieve the default pagination size to apply when searching for "Things" via "things-search".
     *
     * @return default pagination size.
     */
    int thingsSearchDefaultPageSize();

    /**
     * Retrieve the maximum pagination size to apply when searching for "Things" via "things-search".
     *
     * @return max pagination size.
     */
    int thingsSearchMaxPageSize();

}
