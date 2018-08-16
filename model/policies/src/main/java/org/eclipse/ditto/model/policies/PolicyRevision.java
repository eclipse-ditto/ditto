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
package org.eclipse.ditto.model.policies;

import org.eclipse.ditto.model.base.entity.Revision;

/**
 * Represents the current revision of a Policy.
 */
public interface PolicyRevision extends Revision<PolicyRevision> {

    /**
     * Returns a new immutable {@code PolicyRevision} which is initialised with the given revision number.
     *
     * @param revisionNumber the {@code long} value of the revision.
     * @return the new immutable {@code PolicyRevision}.
     */
    static PolicyRevision newInstance(final long revisionNumber) {
        return PoliciesModelFactory.newPolicyRevision(revisionNumber);
    }

}
