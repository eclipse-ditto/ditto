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

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.function.Supplier;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.api.DittoServiceError;

/**
 * Returns the host name determined from the environment variable {@value #ENV_HOSTNAME}.
 * <p>
 * If the environment variable is not set, {@link #get()} tries to get the host address of the local host from
 * {@link InetAddress}.
 * If the local host name cannot be resolved into an address, a {@link DittoServiceError} is thrown.
 * </p>
 */
@Immutable
public final class HostNameSupplier implements Supplier<String> {

    /**
     * Name of the environment variable which contains the hostname.
     */
    static final String ENV_HOSTNAME = "HOSTNAME";

    private static final HostNameSupplier INSTANCE = new HostNameSupplier();

    private HostNameSupplier() {
        super();
    }

    /**
     * Returns an instance of HostNameSupplier.
     *
     * @return the instance.
     */
    public static HostNameSupplier getInstance() {
        return INSTANCE;
    }

    @Override
    public String get() {
        @Nullable final String hostnameFromEnv = System.getenv(ENV_HOSTNAME);
        if (null != hostnameFromEnv) {
            return hostnameFromEnv;
        }
        return tryToGetHostnameFromLocalHostAddress();
    }

    private static String tryToGetHostnameFromLocalHostAddress() {
        try {
            return getHostnameFromLocalHostAddress();
        } catch (final UnknownHostException e) {
            throw new DittoServiceError("Could not retrieve 'localhost' address!", e);
        }
    }

    private static String getHostnameFromLocalHostAddress() throws UnknownHostException {
        final InetAddress localHost = InetAddress.getLocalHost();
        return localHost.getHostAddress();
    }

}
