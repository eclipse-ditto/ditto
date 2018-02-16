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
package org.eclipse.ditto.services.policies.persistence.serializer;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.ThreadSafe;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.policies.PoliciesModelFactory;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.services.utils.persistence.mongo.AbstractMongoSnapshotAdapter;
import org.slf4j.LoggerFactory;

/**
 * A {@link org.eclipse.ditto.services.utils.persistence.SnapshotAdapter} for snapshotting a
 * {@link org.eclipse.ditto.model.policies.Policy}.
 */
@ThreadSafe
public final class PolicyMongoSnapshotAdapter extends AbstractMongoSnapshotAdapter<Policy> {

    /**
     * Constructs a new {@code PolicyMongoSnapshotAdapter}.
     */
    public PolicyMongoSnapshotAdapter() {
        super(LoggerFactory.getLogger(PolicyMongoSnapshotAdapter.class));
    }

    @Override
    protected Policy createJsonifiableFrom(@Nonnull final JsonObject jsonObject) {
        return PoliciesModelFactory.newPolicy(jsonObject);
    }

}
