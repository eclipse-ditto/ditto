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
package org.eclipse.ditto.things.model;

import org.eclipse.ditto.base.model.entity.Revision;

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
