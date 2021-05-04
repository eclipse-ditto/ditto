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

import java.util.function.Supplier;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * Returns the instance identifier based on the environment the service runs in. E.g.:
 * <ul>
 * <li>for Docker Swarm environment the suffix would be the Swarm Instance Index (starting from "1")</li>
 * <li>as fallback the "HOSTNAME" environment variable is used.</li>
 * </ul>
 * <p>
 * If the instance identifier cannot be resolved a {@link org.eclipse.ditto.base.api.DittoServiceError} is thrown.
 * </p>
 */
@Immutable
public final class InstanceIdentifierSupplier implements Supplier<String> {

    /**
     * Name of the environment variable which contains the instance index.
     */
    static final String ENV_INSTANCE_INDEX = "INSTANCE_INDEX";

    // The singleton instance of this class.
    @Nullable private static InstanceIdentifierSupplier instance;

    @Nullable private String instanceIdentifier;

    private InstanceIdentifierSupplier() {
        instanceIdentifier = null;
    }

    /**
     * Returns an instance of InstanceIdentifierSupplier.
     *
     * @return the instance.
     */
    public static InstanceIdentifierSupplier getInstance() {
        InstanceIdentifierSupplier result = instance;
        if (null == result) {
            result = new InstanceIdentifierSupplier();
            instance = result;
        }
        return result;
    }

    @Override
    public String get() {
        String result = instanceIdentifier;
        if (null == result) {

            // cache the once found instance identifier
            result = getInstanceIdentifier();
            instanceIdentifier = result;
        }
        return result;
    }

    private static String getInstanceIdentifier() {
        @Nullable final String result = System.getenv(ENV_INSTANCE_INDEX);
        if (null != result) {
            return result;
        }
        final HostNameSupplier hostNameSupplier = HostNameSupplier.getInstance();
        return hostNameSupplier.get();
    }

}
