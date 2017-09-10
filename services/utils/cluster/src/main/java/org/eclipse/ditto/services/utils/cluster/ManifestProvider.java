/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.utils.cluster;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.function.Function;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.headers.WithManifest;

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

    private ManifestProvider() {
        super();
    }

    /**
     * Returns an instance of {@code ManifestProvider}.
     *
     * @return the instance.
     */
    public static ManifestProvider getInstance() {
        return new ManifestProvider();
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
