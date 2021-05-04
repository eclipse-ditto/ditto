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
package org.eclipse.ditto.internal.utils.cluster;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.function.Function;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.headers.WithManifest;

/**
 * This function provides the manifest for a given Object. If the given object is {@code null}, a
 * NullPointerException is thrown. Otherwise the algorithm for obtaining the manifest string from a given Object
 * {@code o} is as follows:
 * <p>
 * <ol>
 * <li>is {@code o} an instance of {@link WithManifest} &#8594; {@link WithManifest#getManifest()},</li>
 * <li>else for the class of {@code o} &#8594; {@link Class#getSimpleName()}.</li>
 * </ol>
 */
@Immutable
final class ManifestProvider implements Function<Object, String> {

    private static final ManifestProvider INSTANCE = new ManifestProvider();

    private ManifestProvider() {
        super();
    }

    /**
     * Returns an instance of {@code ManifestProvider}.
     *
     * @return the instance.
     */
    public static ManifestProvider getInstance() {
        return INSTANCE;
    }

    @Override
    public String apply(final Object o) {
        checkNotNull(o, "object");
        final Class<?> oClass = o.getClass();
        if (WithManifest.class.isAssignableFrom(oClass)) {
            final WithManifest withManifest = (WithManifest) o;
            return withManifest.getManifest();
        }
        // Important: use the simple name without package!
        return oClass.getSimpleName();
    }

}
