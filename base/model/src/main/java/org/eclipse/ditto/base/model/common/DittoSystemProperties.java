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
 * Holds Ditto wide system properties which must be read/applied in several services.
 *
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

    /**
     * System property name of the property defining the import limits of a given Policy.
     */
    public static final String DITTO_LIMITS_POLICY_IMPORTS_LIMIT = "ditto.limits.policy.imports-limit";

    private DittoSystemProperties() {
        throw new AssertionError();
    }
}
