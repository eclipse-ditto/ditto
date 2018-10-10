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
package org.eclipse.ditto.model.things;

import org.eclipse.ditto.model.base.entity.Revision;

/**
 * Represents the current revision of a Thing.
 */
public interface ThingRevision extends Revision<ThingRevision> {

    /**
     * Returns a new immutable {@link ThingRevision} which is initialised with the given revision number.
     *
     * @param revisionNumber the {@code long} value of the revision.
     * @return the new immutable {@code ThingRevision}.
     */
    static ThingRevision newInstance(final long revisionNumber) {
        return ThingsModelFactory.newThingRevision(revisionNumber);
    }
}
