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
package org.eclipse.ditto.base.model.headers;

import javax.annotation.Nonnull;

/**
 * Common interface for all classes which have a manifest string available.
 */
@FunctionalInterface
public interface WithManifest {

    /**
     * Returns the manifest (type hint).
     *
     * @return the manifest.
     */
    @Nonnull
    String getManifest();

}
