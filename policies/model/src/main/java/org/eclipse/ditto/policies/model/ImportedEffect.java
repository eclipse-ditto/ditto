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
package org.eclipse.ditto.policies.model;

import javax.annotation.concurrent.Immutable;

/**
 * An enumeration of the available {@link ImportedLabels} effects.
 *
 * @since 3.x.0 TODO ditto#298
 */
@Immutable
public enum ImportedEffect {

    /**
     * Effect including the contained {@link ImportedLabels}.
     */
    INCLUDED("included"),

    /**
     * Effect excluding the contained {@link ImportedLabels}.
     */
    EXCLUDED("excluded");

    private final String id;

    ImportedEffect(final String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    @Override
    public String toString() {
        return id;
    }

}
