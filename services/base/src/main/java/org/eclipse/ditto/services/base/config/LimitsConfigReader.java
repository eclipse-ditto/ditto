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
     * Retrieve the maximum possible size of Ditto headers in bytes.
     *
     * @return max size of headers in bytes.
     */
    int headersMaxSize();

    /**
     * Retrieve the maximum number of authorization subjects.
     *
     * @return max number of authorization subjects.
     */
    int authSubjectsCount();

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
