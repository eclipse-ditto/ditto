/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.cluster;

import java.text.MessageFormat;
import java.util.ServiceLoader;

import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;

import org.eclipse.ditto.json.CborFactory;

/**
 * Loads a {@link CborFactory} via {@link ServiceLoader} API. If no CborFactory can be found, an IllegalStateException
 * is thrown.
 */
@ThreadSafe
final class CborFactoryLoader {

    @Nullable
    private static CborFactoryLoader instance = null;

    @SuppressWarnings("java:S3077")
    @Nullable
    private volatile CborFactory cborFactory;

    private CborFactoryLoader() {
        super();
    }

    static CborFactoryLoader getInstance() {
        var result = instance;
        if (null == result) {
            result = new CborFactoryLoader();
            instance = result;
        }
        return result;
    }

    CborFactory getCborFactoryOrThrow() {
        var result = cborFactory;

        // Double-Check-Idiom
        if (null == result) {
            synchronized (this) {
                result = cborFactory;
                if (null == result) {
                    result = checkIfCborAvailable(loadCborFactory());
                    cborFactory = result;
                }
            }
        }
        return result;
    }

    private static CborFactory loadCborFactory() {
        final var serviceLoader = ServiceLoader.load(CborFactory.class);
        return serviceLoader.findFirst()
                .orElseThrow(() -> {
                    final var pattern = "Failed to get <{0}> from ServiceLoader.";
                    return new IllegalStateException(MessageFormat.format(pattern, CborFactory.class.getSimpleName()));
                });
    }

    private static CborFactory checkIfCborAvailable(final CborFactory cborFactory) {
        if (!cborFactory.isCborAvailable()) {
            final var pattern = "<{0}> from Serviceloader cannot handle CBOR.";
            throw new IllegalStateException(MessageFormat.format(pattern, CborFactory.class.getSimpleName()));
        }
        return cborFactory;
    }

}
