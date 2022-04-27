/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.base.model.common;

/**
 * TODO TJ add javadoc
 * @since 3.0.0
 */
public final class DittoSystemProperties {

    /**
     * System property name of the property defining the max policy size in bytes.
     */
    public static final String DITTO_LIMITS_POLICIES_MAX_SIZE_BYTES = "ditto.limits.policies.max-size";

    /**
     * System property name of the property defining the max Thing size in bytes.
     */
    public static final String DITTO_LIMITS_THINGS_MAX_SIZE_BYTES = "ditto.limits.things.max-size";

    /**
     * System property name of the property defining the max Message payload size in bytes.
     */
    public static final String DITTO_LIMITS_MESSAGES_MAX_SIZE_BYTES = "ditto.limits.messages.max-size";

    private DittoSystemProperties() {
        throw new AssertionError();
    }
}
