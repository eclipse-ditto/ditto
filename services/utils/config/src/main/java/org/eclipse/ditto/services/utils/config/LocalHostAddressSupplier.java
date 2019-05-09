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

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.MessageFormat;
import java.util.function.Supplier;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.base.DittoServiceError;

/**
 * Returns the address of the host name which was determined by the environment variable
 * {@value HostNameSupplier#ENV_HOSTNAME}.
 * <p>
 * If the local host address cannot be resolved a {@link DittoServiceError} is thrown.
 * </p>
 */
@Immutable
public final class LocalHostAddressSupplier implements Supplier<String> {

    // The singleton instance of this class.
    @Nullable private static LocalHostAddressSupplier instance;

    @Nullable private String localHostAddress;

    private LocalHostAddressSupplier() {
        localHostAddress = null;
    }

    /**
     * Returns an instance of the local host address supplier.
     *
     * @return the instance.
     */
    public static LocalHostAddressSupplier getInstance() {
        LocalHostAddressSupplier result = instance;
        if (null == result) {
            result = new LocalHostAddressSupplier();
            instance = result;
        }
        return result;
    }

    @Override
    public String get() {
        String result = localHostAddress;
        if (null == result) {

            // cache the once found local host address
            final HostNameSupplier hostNameSupplier = HostNameSupplier.getInstance();
            result = tryToGetHostAddress(hostNameSupplier.get());
            localHostAddress = result;
        }
        return result;
    }

    private static String tryToGetHostAddress(final String hostName) {
        try {
            return getHostAddress(hostName);
        } catch (final UnknownHostException e) {
            throw new DittoServiceError(MessageFormat.format("Could not resolve hostname <{0}>!", hostName), e);
        }
    }

    private static String getHostAddress(final String hostName) throws UnknownHostException {
        final InetAddress inetAddress = InetAddress.getByName(hostName);
        return inetAddress.getHostAddress();
    }

}
