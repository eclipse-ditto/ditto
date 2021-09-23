/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.gateway.service.endpoints.actors;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.entity.type.EntityType;
import org.eclipse.ditto.base.model.signals.SignalWithEntityId;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.things.model.ThingId;
import org.junit.Test;

import akka.http.javadsl.model.HttpMethods;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.Uri;

/**
 * Unit test for {@link UriForLocationHeaderSupplier}.
 */
public final class UriForLocationHeaderSupplierTest {

    private static final EntityId KNOWN_ENTITY_ID = EntityId.of(EntityType.of("bumlux"), "Plumbus");
    private static final String KNOWN_RESOURCE_PATH = "Floob";

    private interface CommandResponseWithEntityIdTest extends CommandResponse<CommandResponseWithEntityIdTest>
            , SignalWithEntityId<CommandResponseWithEntityIdTest> {}

    ;

    @Test
    public void getUriForIdempotentRequest() {
        final Uri uri = Uri.create("https://example.com/plumbus");
        checkUriForIdempotentRequest(uri, uri);
    }

    @Test
    public void getUriForIdempotentRequestWithQueryParam() {
        final Uri expectedUri = Uri.create("https://example.com/plumbus");
        final Uri uri = Uri.create("https://example.com/plumbus?timeout=5s");
        checkUriForIdempotentRequest(expectedUri, uri);
    }

    private void checkUriForIdempotentRequest(final Uri expectedUri, final Uri uri) {
        final HttpRequest httpRequest = HttpRequest.create()
                .withUri(uri)
                .withMethod(HttpMethods.PUT);

        final UriForLocationHeaderSupplier underTest =
                new UriForLocationHeaderSupplier(httpRequest, ThingId.generateRandom(), JsonPointer.empty());

        assertThat(underTest.get()).isEqualTo(expectedUri);
    }

    @Test
    public void getUriForNonIdempotentRequestWithoutEntityIdInUri() {
        final Uri uri = Uri.create("https://example.com/things");
        checkUriForNonIdempotentRequestWithoutEntityIdInUri(uri, uri);
    }

    @Test
    public void getUriWithTrailingSlash() {
        final Uri uri = Uri.create("https://example.com/things/");
        final Uri locationUri = Uri.create("https://example.com/things");
        checkUriForNonIdempotentRequestWithoutEntityIdInUri(locationUri, uri);
    }

    @Test
    public void getUriForNonIdempotentRequestWithoutEntityIdInUriWithQueryParam() {
        final Uri expectedBaseUri = Uri.create("https://example.com/things");
        final Uri uri = Uri.create("https://example.com/things?timeout=42&response-required=false");
        checkUriForNonIdempotentRequestWithoutEntityIdInUri(expectedBaseUri, uri);
    }

    private void checkUriForNonIdempotentRequestWithoutEntityIdInUri(final Uri expectedBaseUri, final Uri uri) {
        final HttpRequest httpRequest = HttpRequest.create()
                .withUri(uri)
                .withMethod(HttpMethods.POST);
        final JsonPointer resourcePath = JsonPointer.of(KNOWN_RESOURCE_PATH);
        final Uri expectedUri =
                Uri.create(expectedBaseUri.toString() + "/" + KNOWN_ENTITY_ID + resourcePath);

        final UriForLocationHeaderSupplier underTest =
                new UriForLocationHeaderSupplier(httpRequest, KNOWN_ENTITY_ID, resourcePath);

        assertThat(underTest.get()).isEqualTo(expectedUri);
    }

    @Test
    public void getUriForNonIdempotentRequestWithEndingSlash() {
        final Uri uri = Uri.create("https://example.com/things");
        final HttpRequest httpRequest = HttpRequest.create()
                .withUri(uri)
                .withMethod(HttpMethods.POST);
        final Uri expectedUri =
                Uri.create(uri.toString() + "/" + KNOWN_ENTITY_ID + "/" + KNOWN_RESOURCE_PATH);

        final UriForLocationHeaderSupplier underTest = new UriForLocationHeaderSupplier(httpRequest, KNOWN_ENTITY_ID,
                JsonPointer.of(KNOWN_RESOURCE_PATH + "/"));

        assertThat(underTest.get()).isEqualTo(expectedUri);
    }

    @Test
    public void getUriForNonIdempotentRequestWithEntityIdInUri() {
        final String host = "https://example.com/things/";
        final Uri uri = Uri.create(host + KNOWN_ENTITY_ID + "will-be-discarded/");
        final HttpRequest httpRequest = HttpRequest.create()
                .withUri(uri)
                .withMethod(HttpMethods.POST);
        final JsonPointer resourcePath = JsonPointer.of(KNOWN_RESOURCE_PATH);
        final Uri expectedUri = Uri.create(host + KNOWN_ENTITY_ID + resourcePath);

        final UriForLocationHeaderSupplier underTest =
                new UriForLocationHeaderSupplier(httpRequest, KNOWN_ENTITY_ID, resourcePath);

        assertThat(underTest.get()).isEqualTo(expectedUri);
    }

}
