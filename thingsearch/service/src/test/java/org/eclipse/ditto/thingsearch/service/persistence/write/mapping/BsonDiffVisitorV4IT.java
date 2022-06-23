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
package org.eclipse.ditto.thingsearch.service.persistence.write.mapping;

import org.eclipse.ditto.internal.utils.test.mongo.MongoDbResource;
import org.junit.ClassRule;

/**
 * Tests incremental update against default MongoDB version.
 */
public final class BsonDiffVisitorV4IT extends BsonDiffVisitorIT {

    @ClassRule
    public static final MongoDbResource MONGO_RESOURCE = new MongoDbResource();

    @Override
    protected MongoDbResource getMongoDbResource() {
        return MONGO_RESOURCE;
    }

}
