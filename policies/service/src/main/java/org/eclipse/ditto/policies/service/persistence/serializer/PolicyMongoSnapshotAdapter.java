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
package org.eclipse.ditto.policies.service.persistence.serializer;

import java.util.Optional;

import javax.annotation.concurrent.ThreadSafe;

import org.eclipse.ditto.internal.utils.persistence.mongo.AbstractMongoSnapshotAdapter;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyLifecycle;
import org.slf4j.LoggerFactory;

import com.typesafe.config.Config;

import akka.actor.ActorSystem;

/**
 * A {@link org.eclipse.ditto.internal.utils.persistence.SnapshotAdapter} for snapshotting a
 * {@link org.eclipse.ditto.policies.model.Policy}.
 */
@ThreadSafe
public final class PolicyMongoSnapshotAdapter extends AbstractMongoSnapshotAdapter<Policy> {

    /**
     * @param actorSystem the actor system in which to load the extension
     * @param config the config of the extension.
     */
    @SuppressWarnings("unused")
    public PolicyMongoSnapshotAdapter(final ActorSystem actorSystem, final Config config) {
        this();
    }

    /**
     * Constructs a new {@code PolicyMongoSnapshotAdapter}.
     */
    public PolicyMongoSnapshotAdapter() {
        super(LoggerFactory.getLogger(PolicyMongoSnapshotAdapter.class));
    }

    @Override
    protected boolean isDeleted(final Policy snapshotEntity) {
        return snapshotEntity.hasLifecycle(PolicyLifecycle.DELETED);
    }

    @Override
    protected JsonField getDeletedLifecycleJsonField() {
        final var field = Policy.JsonFields.LIFECYCLE;
        return JsonField.newInstance(field.getPointer().getRoot().orElseThrow(),
                JsonValue.of(PolicyLifecycle.DELETED.name()), field);
    }

    @Override
    protected Optional<JsonField> getRevisionJsonField(final Policy entity) {
        final var field = Policy.JsonFields.REVISION;
        return entity.getRevision().map(revision ->
                JsonField.newInstance(field.getPointer().getRoot().orElseThrow(), JsonValue.of(revision.toLong())));
    }

    @Override
    protected Policy createJsonifiableFrom(final JsonObject jsonObject) {
        return PoliciesModelFactory.newPolicy(jsonObject);
    }

}
