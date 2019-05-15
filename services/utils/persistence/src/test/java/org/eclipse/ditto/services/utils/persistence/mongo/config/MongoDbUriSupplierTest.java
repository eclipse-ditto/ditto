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
package org.eclipse.ditto.services.utils.persistence.mongo.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.entry;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

import org.eclipse.ditto.services.utils.config.DittoConfigError;
import org.eclipse.ditto.services.utils.persistence.mongo.config.MongoDbUriSupplier.QueryComponentParser;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

/**
 * Unit test for {@link org.eclipse.ditto.services.utils.persistence.mongo.config.MongoDbUriSupplier}.
 */
public final class MongoDbUriSupplierTest {

    private static final String SOURCE_URI = "mongodb://user-name-1234-5678-abcdefg:password12345@" +
            "first.hostname.com:10000,second.hostname.com:20000,third.hostname.com:30000,fourth.hostname" +
            "fifth.hostname.com:50000,sixth.hostname.com:60000,seventh:hostname.com:70000" +
            "/database-name?replicaSet=streched-0003&maxIdleTimeMS=240000&w=majority" +
            "&readPreference=primaryPreferred&ssl=true&sslInvalidHostNameAllowed=true";

    private static final String KEY_URI = MongoDbUriSupplier.URI_CONFIG_PATH;
    private static final String KEY_OPTIONS = DefaultOptionsConfig.CONFIG_PATH;

    @Test
    public void assertImmutability() {
        assertInstancesOf(MongoDbUriSupplier.class,
                areImmutable(),
                provided(Config.class).isAlsoImmutable());
    }

    @Test
    public void tryToGetInstanceWithNullConfig() {
        assertThatExceptionOfType(DittoConfigError.class)
                .isThrownBy(() -> MongoDbUriSupplier.of(null))
                .withCause(new NullPointerException("The MongoDB Config must not be null!"));
    }

    @Test
    public void tryToGetInstanceWithConfigWithoutUriPath() {
        assertThatExceptionOfType(DittoConfigError.class)
                .isThrownBy(() -> MongoDbUriSupplier.of(ConfigFactory.empty()))
                .withMessage("MongoDB Config did not have path <%s>!", KEY_URI)
                .withNoCause();
    }

    @Test
    public void preserveMongoDbSourceUriWithoutConfiguration() {
        final Config config = ConfigFactory.parseString(String.format("%s=\"%s\"", KEY_URI, SOURCE_URI));
        final MongoDbUriSupplier underTest = MongoDbUriSupplier.of(config);

        final String targetUri = underTest.get();

        assertThat(targetUri).isEqualTo(SOURCE_URI);
    }

    @Test
    public void parseQueryParameters() throws URISyntaxException {
        final String query = new URI(SOURCE_URI).getQuery();
        final Map<String, String> params = QueryComponentParser.getInstance().apply(query);

        assertThat(params).containsOnly(entry("replicaSet", "streched-0003"), entry("maxIdleTimeMS", "240000"),
                entry("w", "majority"), entry("readPreference", "primaryPreferred"), entry("ssl", "true"),
                entry("sslInvalidHostNameAllowed", "true"));
    }

    @Test
    public void preserveQueryParameterOrder() throws URISyntaxException {
        final String query = new URI(SOURCE_URI).getQuery();
        final Map<String, String> params = QueryComponentParser.getInstance().apply(query);

        params.put("ssl", "false");
        params.put("w", "hello world");

        assertThat(params).containsOnlyKeys("replicaSet", "maxIdleTimeMS", "w", "readPreference", "ssl",
                "sslInvalidHostNameAllowed");
    }
    @Test
    public void turnOffSsl() {
        final Config options = ConfigFactory.parseString("ssl=false");
        final Config config = ConfigFactory.parseString(
                String.format("%s=\"%s\"\n%s=%s", KEY_URI, SOURCE_URI, KEY_OPTIONS, options.root().render()));
        final MongoDbUriSupplier underTest = MongoDbUriSupplier.of(config);

        final String targetUri = underTest.get();

        assertThat(targetUri).isEqualTo(SOURCE_URI.replaceAll("ssl=true", "ssl=false"));
        assertThat(getSslEnabledConfig(config)).isFalse();
    }

    @Test
    public void turnOnSsl() {
        // GIVEN
        final Config options = ConfigFactory.parseString("ssl=true");
        final Config config = ConfigFactory.parseString(
                String.format("%s=\"%s\"\n%s=%s", KEY_URI, SOURCE_URI, KEY_OPTIONS, options.root().render()));

        // WHEN
        final String targetUri = getMongoUri(config);

        // THEN
        assertThat(targetUri).isEqualTo(SOURCE_URI);
        assertThat(getSslEnabledConfig(config)).isTrue();
    }

    @Test
    public void secondaryPreferred() {
        final Config options = ConfigFactory.parseString("readPreference=secondaryPreferred");
        final Config config = ConfigFactory.parseString(
                String.format("%s=\"%s\"\n%s=%s", KEY_URI, SOURCE_URI, KEY_OPTIONS, options.root().render()));
        final MongoDbUriSupplier underTest = MongoDbUriSupplier.of(config);

        final String targetUri = underTest.get();

        assertThat(targetUri).isEqualTo(SOURCE_URI.replaceAll("primary", "secondary"));
    }

    @Test
    public void acknowledgedWriteConcern() {
        final Config options = ConfigFactory.parseString("w=1");
        final Config config = ConfigFactory.parseString(
                String.format("%s=\"%s\"\n%s=%s", KEY_URI, SOURCE_URI, KEY_OPTIONS, options.root().render()));
        final MongoDbUriSupplier underTest = MongoDbUriSupplier.of(config);

        final String targetUri = underTest.get();

        assertThat(targetUri).isEqualTo(SOURCE_URI.replaceAll("w=majority", "w=1"));
    }

    @Test
    public void nullValuesAreNotSet() {
        final Config options = ConfigFactory.parseString("readPreference=null");
        final Config config = ConfigFactory.parseString(
                String.format("%s=\"%s\"\n%s=%s", KEY_URI, SOURCE_URI, KEY_OPTIONS, options.root().render()));
        final MongoDbUriSupplier underTest = MongoDbUriSupplier.of(config);

        final String targetUri = underTest.get();

        assertThat(targetUri).isEqualTo(SOURCE_URI);
    }

    @Test
    public void extraParametersAreAppended() {
        final Config options = ConfigFactory.parseString("hello=world");
        final Config config = ConfigFactory.parseString(
                String.format("%s=\"%s\"\n%s=%s", KEY_URI, SOURCE_URI, KEY_OPTIONS, options.root().render()));
        final MongoDbUriSupplier underTest = MongoDbUriSupplier.of(config);

        final String targetUri = underTest.get();

        assertThat(targetUri).isEqualTo(SOURCE_URI + "&hello=world");
    }

    @Test
    public void uriWithoutPathOrQueryIsOkay() {
        final String sourceUri = "mongodb://username:password@localhost:12345";
        final Config config = ConfigFactory.parseString(String.format("%s=\"%s\"", KEY_URI, sourceUri));
        final MongoDbUriSupplier underTest = MongoDbUriSupplier.of(config);

        final String targetUri = underTest.get();

        assertThat(targetUri).isEqualTo(sourceUri);
    }

    @Test
    public void uriWithoutQueryIsConfigurable() {
        final String sourceUri = "mongodb://username:password@localhost:12345";
        final Config options = ConfigFactory.parseString("ssl=false");
        final Config config = ConfigFactory.parseString(
                String.format("%s=\"%s\"\n%s=%s", KEY_URI, sourceUri, KEY_OPTIONS, options.root().render()));
        final MongoDbUriSupplier underTest = MongoDbUriSupplier.of(config);

        final String targetUri = underTest.get();

        assertThat(targetUri).isEqualTo(sourceUri + "?ssl=false");
    }

    private static String getMongoUri(final Config config) {
        final MongoDbConfig mongoDbConfig = getMongoDbConfig(config);
        return mongoDbConfig.getMongoDbUri();
    }

    private static boolean getSslEnabledConfig(final Config config) {
        final MongoDbConfig mongoConfig = getMongoDbConfig(config);
        final MongoDbConfig.OptionsConfig optionsConfig = mongoConfig.getOptionsConfig();
        return optionsConfig.isSslEnabled();
    }

    private static MongoDbConfig getMongoDbConfig(final Config config) {
        return DefaultMongoDbConfig.of(config);
    }

}
