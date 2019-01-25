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
package org.eclipse.ditto.model.policies;

import javax.annotation.concurrent.Immutable;

/**
 * An enumeration of the available {@link ImportedLabels} effects.
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
