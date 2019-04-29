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
package org.eclipse.ditto.services.utils.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

/**
 * Unit test for {@link MongoConfig}.
 */
public final class MongoConfigTest {

    private static final String SOURCE_URI = "mongodb://user-name-1234-5678-abcdefg:password12345@" +
            "first.hostname.com:10000,second.hostname.com:20000,third.hostname.com:30000,fourth.hostname" +
            "fifth.hostname.com:50000,sixth.hostname.com:60000,seventh:hostname.com:70000" +
            "/database-name?replicaSet=streched-0003&maxIdleTimeMS=240000&w=majority" +
            "&readPreference=primaryPreferred&ssl=true&sslInvalidHostNameAllowed=true";

    private static final String KEY_OPTIONS = MongoConfig.CONFIG_PATH + ".options";
    private static final String KEY_URI = MongoConfig.CONFIG_PATH + ".uri";

    @Test
    public void preservesSourceMongoUriWithoutConfiguration() {
        // GIVEN
        final Config config = ConfigFactory.parseString(String.format("%s=\"%s\"", KEY_URI, SOURCE_URI));

        // WHEN
        final String targetUri = getMongoUri(config);

        // THEN
        assertThat(targetUri).isEqualTo(SOURCE_URI);
    }

    @Test
    public void parsesQueryParameters() throws URISyntaxException {
        // WHEN
        final String query = new URI(SOURCE_URI).getQuery();
        final Map<String, String> params = MongoConfig.QueryComponentParser.getInstance().apply(query);

        // THEN
        assertThat(params).containsOnly(entry("replicaSet", "streched-0003"), entry("maxIdleTimeMS", "240000"),
                entry("w", "majority"), entry("readPreference", "primaryPreferred"), entry("ssl", "true"),
                entry("sslInvalidHostNameAllowed", "true"));
    }

    @Test
    public void preservesQueryParameterOrder() throws URISyntaxException {
        // GIVEN
        final String query = new URI(SOURCE_URI).getQuery();
        final Map<String, String> params = MongoConfig.QueryComponentParser.getInstance().apply(query);

        // WHEN
        params.put("ssl", "false");
        params.put("w", "hello world");

        // THEN
        assertThat(params).containsOnlyKeys("replicaSet", "maxIdleTimeMS", "w", "readPreference", "ssl",
                "sslInvalidHostNameAllowed");
    }

    @Test
    public void turnOffSsl() {
        // GIVEN
        final Config options = ConfigFactory.parseString("ssl=false");
        final Config config = ConfigFactory.parseString(
                String.format("%s=\"%s\"\n%s=%s", KEY_URI, SOURCE_URI, KEY_OPTIONS, options.root().render()));

        // WHEN
        final String targetUri = getMongoUri(config);

        // THEN
        assertThat(targetUri).isEqualTo(SOURCE_URI.replaceAll("ssl=true", "ssl=false"));
    }

    @Test
    public void secondaryPreferred() {
        // GIVEN
        final Config options = ConfigFactory.parseString("readPreference=secondaryPreferred");
        final Config config = ConfigFactory.parseString(
                String.format("%s=\"%s\"\n%s=%s", KEY_URI, SOURCE_URI, KEY_OPTIONS, options.root().render()));

        // WHEN
        final String targetUri = getMongoUri(config);

        // THEN
        assertThat(targetUri).isEqualTo(SOURCE_URI.replaceAll("primary", "secondary"));
    }

    @Test
    public void acknowledgedWriteConcern() {
        // GIVEN
        final Config options = ConfigFactory.parseString("w=1");
        final Config config = ConfigFactory.parseString(
                String.format("%s=\"%s\"\n%s=%s", KEY_URI, SOURCE_URI, KEY_OPTIONS, options.root().render()));

        // WHEN
        final String targetUri = getMongoUri(config);

        // THEN
        assertThat(targetUri).isEqualTo(SOURCE_URI.replaceAll("w=majority", "w=1"));
    }

    @Test
    public void nullValuesAreNotSet() {
        // GIVEN
        final Config options = ConfigFactory.parseString("readPreference=null");
        final Config config = ConfigFactory.parseString(
                String.format("%s=\"%s\"\n%s=%s", KEY_URI, SOURCE_URI, KEY_OPTIONS, options.root().render()));

        // WHEN
        final String targetUri = getMongoUri(config);

        // THEN
        assertThat(targetUri).isEqualTo(SOURCE_URI);
    }

    @Test
    public void extraParametersAreAppended() {
        // GIVEN
        final Config options = ConfigFactory.parseString("hello=world");
        final Config config = ConfigFactory.parseString(
                String.format("%s=\"%s\"\n%s=%s", KEY_URI, SOURCE_URI, KEY_OPTIONS, options.root().render()));

        // WHEN
        final String targetUri = getMongoUri(config);

        // THEN
        assertThat(targetUri).isEqualTo(SOURCE_URI + "&hello=world");
    }

    @Test
    public void uriWithoutPathOrQueryIsOkay() {
        // GIVEN
        final String sourceUri = "mongodb://username:password@localhost:12345";
        final Config config = ConfigFactory.parseString(String.format("%s=\"%s\"", KEY_URI, sourceUri));

        // WHEN
        final String targetUri = getMongoUri(config);

        // THEN
        assertThat(targetUri).isEqualTo(sourceUri);
    }

    @Test
    public void uriWithoutQueryIsConfigurable() {
        // GIVEN
        final String sourceUri = "mongodb://username:password@localhost:12345";
        final Config options = ConfigFactory.parseString("ssl=false");
        final Config config = ConfigFactory.parseString(
                String.format("%s=\"%s\"\n%s=%s", KEY_URI, sourceUri, KEY_OPTIONS, options.root().render()));

        // WHEN
        final String targetUri = getMongoUri(config);

        // THEN
        assertThat(targetUri).isEqualTo(sourceUri + "?ssl=false");
    }

    private static String getMongoUri(final Config config) {
        final MongoConfig mongoConfig = getMongoConfig(config);
        return mongoConfig.getMongoUri();
    }

    private static MongoConfig getMongoConfig(final Config config) {
        return MongoConfig.of(config);
    }

}
