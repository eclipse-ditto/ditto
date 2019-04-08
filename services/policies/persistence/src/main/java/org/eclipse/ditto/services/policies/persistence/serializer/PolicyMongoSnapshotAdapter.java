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
package org.eclipse.ditto.services.policies.persistence.serializer;

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
    protected Policy createJsonifiableFrom(final JsonObject jsonObject) {
        return PoliciesModelFactory.newPolicy(jsonObject);
    }

}
