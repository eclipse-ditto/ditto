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
package org.eclipse.ditto.timeseries.mongodb;

/**
 * Builds the MongoDB connection string the integration tests hand to {@code MongoClientWrapper}.
 * <p>
 * Necessary because the database name has to travel <em>inside the URI</em>. Ditto's
 * {@code DefaultMongoDbConfig} reads only {@code ditto.mongodb.uri}; there is no
 * {@code ditto.mongodb.database} setting, and {@code MongoClientWrapper.getDefaultDatabase()}
 * resolves the database purely from the URI's path segment. Supplying the name as a separate config
 * key silently does nothing, and the adapter then fails with {@code IllegalArgumentException: name
 * can not be null} on the first database access.
 */
final class MongoDbItUris {

    private MongoDbItUris() {
        throw new AssertionError();
    }

    /**
     * Returns {@code uri} with its database path segment replaced by {@code database}, preserving the
     * scheme, host list and any query string.
     *
     * @param uri a MongoDB connection string, with or without a database path and query string.
     * @param database the database the test should use.
     * @return the connection string pointing at {@code database}.
     */
    static String withDatabase(final String uri, final String database) {
        final int queryStart = uri.indexOf('?');
        final String beforeQuery = queryStart < 0 ? uri : uri.substring(0, queryStart);
        final String query = queryStart < 0 ? "" : uri.substring(queryStart);

        final int schemeEnd = beforeQuery.indexOf("://");
        if (schemeEnd < 0) {
            throw new IllegalArgumentException("Not a MongoDB connection string: <" + uri + ">");
        }
        final int hostsStart = schemeEnd + "://".length();
        // Everything up to the first '/' after the scheme is the (possibly comma-separated) host
        // list; anything after it is the database path this method replaces.
        final int pathStart = beforeQuery.indexOf('/', hostsStart);
        final String schemeAndHosts =
                pathStart < 0 ? beforeQuery : beforeQuery.substring(0, pathStart);

        return schemeAndHosts + "/" + database + query;
    }
}
