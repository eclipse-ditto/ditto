/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.persistence.mongo.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.entry;
import static org.mutabilitydetector.unittesting.AllowedReason.assumingFields;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.Map;

import org.eclipse.ditto.internal.utils.config.DittoConfigError;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

/**
 * Unit test for {@link MongoDbUriSupplier}.
 */
public final class MongoDbUriSupplierTest {

    private static final String SOURCE_URI = "mongodb://user-name-1234-5678-abcdefg:password12345@" +
            "first.hostname.com:10000,second.hostname.com:20000,third.hostname.com:30000,fourth.hostname" +
            "fifth.hostname.com:50000,sixth.hostname.com:60000,seventh.hostname.com:65000" +
            "/database-name?replicaSet=streched-0003&maxIdleTimeMS=240000&w=majority" +
            "&readPreference=primaryPreferred&ssl=true&sslInvalidHostNameAllowed=true";

    private static final String KEY_URI = MongoDbUriSupplier.URI_CONFIG_PATH;

    @Test
    public void assertImmutability() {
        assertInstancesOf(MongoDbUriSupplier.class,
                areImmutable(),
                provided(Config.class).isAlsoImmutable(),
                assumingFields("extraUriOptions")
                        .areSafelyCopiedUnmodifiableCollectionsWithImmutableElements());
    }

    @Test
    public void tryToGetInstanceWithNullUri() {
        assertThatExceptionOfType(DittoConfigError.class)
                .isThrownBy(() -> MongoDbUriSupplier.of(null, Collections.emptyMap()))
                .withCause(new NullPointerException("The configuredMongoUri must not be null!"));
    }

    @Test
    public void tryToGetInstanceWithNullExtraOptions() {
        assertThatExceptionOfType(DittoConfigError.class)
                .isThrownBy(() -> MongoDbUriSupplier.of(SOURCE_URI, null))
                .withMessage("The extraUriOptions must not be null!", KEY_URI)
                .withCause(new NullPointerException("The extraUriOptions must not be null!"));
    }

    @Test
    public void preserveMongoDbSourceUriWithoutConfiguration() {
        final MongoDbUriSupplier underTest = MongoDbUriSupplier.of(SOURCE_URI, Collections.emptyMap());

        final String targetUri = underTest.get();

        assertThat(targetUri).isEqualTo(SOURCE_URI);
    }

    @Test
    public void parseQueryParameters() throws URISyntaxException {
        final String query = new URI(SOURCE_URI).getQuery();
        final Map<String, String> params = MongoDbUriSupplier.QueryComponentParser.getInstance().apply(query);

        assertThat(params).containsOnly(entry("replicaSet", "streched-0003"), entry("maxIdleTimeMS", "240000"),
                entry("w", "majority"), entry("readPreference", "primaryPreferred"), entry("ssl", "true"),
                entry("sslInvalidHostNameAllowed", "true"));
    }

    @Test
    public void preserveQueryParameterOrder() throws URISyntaxException {
        final String query = new URI(SOURCE_URI).getQuery();
        final Map<String, String> params = MongoDbUriSupplier.QueryComponentParser.getInstance().apply(query);

        params.put("ssl", "false");
        params.put("w", "hello world");

        assertThat(params).containsOnlyKeys("replicaSet", "maxIdleTimeMS", "w", "readPreference", "ssl",
                "sslInvalidHostNameAllowed");
    }
    @Test
    public void turnOffSsl() {
        final Config options = ConfigFactory.parseString("ssl=false");
        final MongoDbUriSupplier underTest = MongoDbUriSupplier.of(SOURCE_URI, options.root().unwrapped());

        final String targetUri = underTest.get();

        assertThat(targetUri).isEqualTo(SOURCE_URI.replaceAll("ssl=true", "ssl=false"));
    }

    @Test
    public void turnOnSsl() {
        // GIVEN
        final Config options = ConfigFactory.parseString("ssl=true");
        final MongoDbUriSupplier underTest = MongoDbUriSupplier.of(SOURCE_URI, options.root().unwrapped());

        // WHEN
        final String targetUri = underTest.get();

        // THEN
        assertThat(targetUri).isEqualTo(SOURCE_URI);
    }

    @Test
    public void secondaryPreferred() {
        final Config options = ConfigFactory.parseString("readPreference=secondaryPreferred");
        final MongoDbUriSupplier underTest = MongoDbUriSupplier.of(SOURCE_URI, options.root().unwrapped());

        final String targetUri = underTest.get();

        assertThat(targetUri).isEqualTo(SOURCE_URI.replaceAll("primary", "secondary"));
    }

    @Test
    public void acknowledgedWriteConcern() {
        final Config options = ConfigFactory.parseString("w=1");
        final MongoDbUriSupplier underTest = MongoDbUriSupplier.of(SOURCE_URI, options.root().unwrapped());

        final String targetUri = underTest.get();

        assertThat(targetUri).isEqualTo(SOURCE_URI.replaceAll("w=majority", "w=1"));
    }

    @Test
    public void extraParametersAreAppended() {
        final Config options = ConfigFactory.parseString("hello=world");
        final MongoDbUriSupplier underTest = MongoDbUriSupplier.of(SOURCE_URI, options.root().unwrapped());

        final String targetUri = underTest.get();

        assertThat(targetUri).isEqualTo(SOURCE_URI + "&hello=world");
    }

    @Test
    public void uriWithoutPathOrQueryIsOkay() {
        final String sourceUri = "mongodb://username:password@localhost:12345";
        final MongoDbUriSupplier underTest = MongoDbUriSupplier.of(sourceUri, Collections.emptyMap());

        final String targetUri = underTest.get();

        assertThat(targetUri).isEqualTo(sourceUri);
    }

    @Test
    public void uriWithoutQueryIsConfigurable() {
        final String sourceUri = "mongodb://username:password@localhost:12345";
        final Config options = ConfigFactory.parseString("ssl=false");
        final MongoDbUriSupplier underTest = MongoDbUriSupplier.of(sourceUri, options.root().unwrapped());

        final String targetUri = underTest.get();

        assertThat(targetUri).isEqualTo(sourceUri + "?ssl=false");
    }

}
