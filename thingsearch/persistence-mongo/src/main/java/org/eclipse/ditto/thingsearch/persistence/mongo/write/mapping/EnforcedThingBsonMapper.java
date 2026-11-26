/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.thingsearch.persistence.mongo.write.mapping;

import org.bson.BsonDocument;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.thingsearch.persistence.api.mapping.SearchIndexDocumentFactory;
import org.eclipse.ditto.thingsearch.persistence.api.model.Metadata;

/**
 * Map a Thing with its enforcing policy and given {@link Metadata} to the MongoDB search index {@link BsonDocument}.
 * <p>
 * The mapping first produces the backend-neutral search index document via {@link SearchIndexDocumentFactory} and
 * then encodes it with {@link SearchIndexDocumentMongoEncoder} into the BSON wire format of the search index.
 */
final class EnforcedThingBsonMapper {

    private EnforcedThingBsonMapper() {
        throw new AssertionError();
    }

    static BsonDocument toBsonDocument(final JsonObject thing, final Policy policy, final Metadata metadata) {
        return toBsonDocument(thing, policy, metadata, -1);
    }

    static BsonDocument toBsonDocument(final JsonObject thing, final Policy policy, final Metadata metadata,
            final int maxArraySize) {

        return SearchIndexDocumentMongoEncoder.encode(
                SearchIndexDocumentFactory.create(thing, policy, metadata, maxArraySize));
    }

}
