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
package org.eclipse.ditto.services.utils.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

/**
 * Tests {@link MongoConfig}.
 */
public class MongoConfigTest {

    private static final String SOURCE_URI = "mongodb://user-name-1234-5678-abcdefg:password12345@" +
            "first.hostname.com:10000,second.hostname.com:20000,third.hostname.com:30000,fourth.hostname" +
            "fifth.hostname.com:50000,sixth.hostname.com:60000,seventh:hostname.com:70000" +
            "/database-name?replicaSet=streched-0003&maxIdleTimeMS=240000&w=majority" +
            "&readPreference=primaryPreferred&ssl=true&sslInvalidHostNameAllowed=true";

    @Test
    public void preservesSourceMongoUriWithoutConfiguration() {
        // GIVEN
        final Config config = ConfigFactory.parseString(String.format("%s=\"%s\"", MongoConfig.URI, SOURCE_URI));

        // WHEN
        final String targetUri = MongoConfig.getMongoUri(config);

        // THEN
        assertThat(targetUri).isEqualTo(SOURCE_URI);
    }

    @Test
    public void parsesQueryParameters() throws URISyntaxException {
        // WHEN
        final String query = new URI(SOURCE_URI).getQuery();
        final Map<String, String> params = MongoConfig.parseQuery(query);

        // THEN
        final Map<String, String> expectedParams = new HashMap<>();
        expectedParams.put("replicaSet", "streched-0003");
        expectedParams.put("maxIdleTimeMS", "240000");
        expectedParams.put("w", "majority");
        expectedParams.put("readPreference", "primaryPreferred");
        expectedParams.put("ssl", "true");
        expectedParams.put("sslInvalidHostNameAllowed", "true");
        assertThat(params).isEqualTo(expectedParams);
    }

    @Test
    public void preservesQueryParameterOrder() throws URISyntaxException {
        // GIVEN
        final String query = new URI(SOURCE_URI).getQuery();
        final Map<String, String> params = MongoConfig.parseQuery(query);

        // WHEN
        params.put("ssl", "false");
        params.put("w", "hello world");

        // THEN
        assertThat(new ArrayList<>(params.keySet())).isEqualTo(
                Arrays.asList("replicaSet", "maxIdleTimeMS", "w", "readPreference", "ssl",
                        "sslInvalidHostNameAllowed"));
    }

    @Test
    public void turnOffSsl() {
        // GIVEN
        final Config options = ConfigFactory.parseString("ssl=false");
        final Config config = ConfigFactory.parseString(String.format("%s=\"%s\"\n%s=%s",
                MongoConfig.URI, SOURCE_URI, MongoConfig.OPTIONS, options.root().render()));

        // WHEN
        final String targetUri = MongoConfig.getMongoUri(config);

        // THEN
        assertThat(targetUri).isEqualTo(SOURCE_URI.replaceAll("ssl=true", "ssl=false"));
    }

    @Test
    public void secondaryPreferred() {
        // GIVEN
        final Config options = ConfigFactory.parseString("readPreference=secondaryPreferred");
        final Config config = ConfigFactory.parseString(String.format("%s=\"%s\"\n%s=%s",
                MongoConfig.URI, SOURCE_URI, MongoConfig.OPTIONS, options.root().render()));

        // WHEN
        final String targetUri = MongoConfig.getMongoUri(config);

        // THEN
        assertThat(targetUri).isEqualTo(SOURCE_URI.replaceAll("primary", "secondary"));
    }

    @Test
    public void acknowledgedWriteConcern() {
        // GIVEN
        final Config options = ConfigFactory.parseString("w=1");
        final Config config = ConfigFactory.parseString(String.format("%s=\"%s\"\n%s=%s",
                MongoConfig.URI, SOURCE_URI, MongoConfig.OPTIONS, options.root().render()));

        // WHEN
        final String targetUri = MongoConfig.getMongoUri(config);

        // THEN
        assertThat(targetUri).isEqualTo(SOURCE_URI.replaceAll("w=majority", "w=1"));
    }

    @Test
    public void nullValuesAreNotSet() {
        // GIVEN
        final Config options = ConfigFactory.parseString("readPreference=null");
        final Config config = ConfigFactory.parseString(String.format("%s=\"%s\"\n%s=%s",
                MongoConfig.URI, SOURCE_URI, MongoConfig.OPTIONS, options.root().render()));

        // WHEN
        final String targetUri = MongoConfig.getMongoUri(config);

        // THEN
        assertThat(targetUri).isEqualTo(SOURCE_URI);
    }

    @Test
    public void extraParametersAreAppended() {
        // GIVEN
        final Config options = ConfigFactory.parseString("hello=world");
        final Config config = ConfigFactory.parseString(String.format("%s=\"%s\"\n%s=%s",
                MongoConfig.URI, SOURCE_URI, MongoConfig.OPTIONS, options.root().render()));

        // WHEN
        final String targetUri = MongoConfig.getMongoUri(config);

        // THEN
        assertThat(targetUri).isEqualTo(SOURCE_URI + "&hello=world");
    }

    @Test
    public void uriWithoutPathOrQueryIsOkay() {
        // GIVEN
        final String sourceUri = "mongodb://username:password@localhost:12345";
        final Config config = ConfigFactory.parseString(String.format("%s=\"%s\"", MongoConfig.URI, sourceUri));

        // WHEN
        final String targetUri = MongoConfig.getMongoUri(config);

        // THEN
        assertThat(targetUri).isEqualTo(sourceUri);
    }

    @Test
    public void uriWithoutQueryIsConfigurable() {
        // GIVEN
        final String sourceUri = "mongodb://username:password@localhost:12345";
        final Config options = ConfigFactory.parseString("ssl=false");
        final Config config = ConfigFactory.parseString(String.format("%s=\"%s\"\n%s=%s",
                MongoConfig.URI, sourceUri, MongoConfig.OPTIONS, options.root().render()));

        // WHEN
        final String targetUri = MongoConfig.getMongoUri(config);

        // THEN
        assertThat(targetUri).isEqualTo(sourceUri + "?ssl=false");
    }
}
