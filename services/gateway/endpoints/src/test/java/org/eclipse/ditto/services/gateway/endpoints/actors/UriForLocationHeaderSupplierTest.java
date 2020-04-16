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
package org.eclipse.ditto.services.gateway.endpoints.actors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.entity.id.DefaultEntityId;
import org.eclipse.ditto.model.base.entity.id.EntityId;
import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.junit.Test;

import akka.http.javadsl.model.HttpMethods;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.Uri;

/**
 * Unit test for {@link UriForLocationHeaderSupplier}.
 */
public final class UriForLocationHeaderSupplierTest {

    private static final EntityId KNOWN_ENTITY_ID = DefaultEntityId.of("Plumbus");
    private static final String KNOWN_RESOURCE_PATH = "Floob";

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
                new UriForLocationHeaderSupplier(httpRequest, mock(CommandResponse.class));

        assertThat(underTest.get()).isEqualTo(expectedUri);
    }

    @Test
    public void getUriForNonIdempotentRequestWithoutEntityIdInUri() {
        final Uri uri = Uri.create("https://example.com/things");
        checkUriForNonIdempotentRequestWithoutEntityIdInUri(uri, uri);
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
        final CommandResponse<?> commandResponse = mock(CommandResponse.class);
        when(commandResponse.getEntityId()).thenReturn(KNOWN_ENTITY_ID);
        when(commandResponse.getResourcePath()).thenReturn(JsonPointer.of(KNOWN_RESOURCE_PATH));
        final Uri expectedUri =
                Uri.create(expectedBaseUri.toString() + "/" + commandResponse.getEntityId() +
                        commandResponse.getResourcePath());

        final UriForLocationHeaderSupplier underTest = new UriForLocationHeaderSupplier(httpRequest, commandResponse);

        assertThat(underTest.get()).isEqualTo(expectedUri);
    }

    @Test
    public void getUriForNonIdempotentRequestWithEndingSlash() {
        final Uri uri = Uri.create("https://example.com/things");
        final HttpRequest httpRequest = HttpRequest.create()
                .withUri(uri)
                .withMethod(HttpMethods.POST);
        final CommandResponse<?> commandResponse = mock(CommandResponse.class);
        when(commandResponse.getEntityId()).thenReturn(KNOWN_ENTITY_ID);
        when(commandResponse.getResourcePath()).thenReturn(JsonPointer.of(KNOWN_RESOURCE_PATH + "/"));
        final Uri expectedUri =
                Uri.create(uri.toString() + "/" + commandResponse.getEntityId() + "/" + KNOWN_RESOURCE_PATH);

        final UriForLocationHeaderSupplier underTest = new UriForLocationHeaderSupplier(httpRequest, commandResponse);

        assertThat(underTest.get()).isEqualTo(expectedUri);
    }

    @Test
    public void getUriForNonIdempotentRequestWithEntityIdInUri() {
        final String host = "https://example.com/things/";
        final Uri uri = Uri.create(host + KNOWN_ENTITY_ID + "will-be-discarded/");
        final HttpRequest httpRequest = HttpRequest.create()
                .withUri(uri)
                .withMethod(HttpMethods.POST);
        final CommandResponse<?> commandResponse = mock(CommandResponse.class);
        when(commandResponse.getEntityId()).thenReturn(KNOWN_ENTITY_ID);
        when(commandResponse.getResourcePath()).thenReturn(JsonPointer.of(KNOWN_RESOURCE_PATH));
        final Uri expectedUri = Uri.create(host + commandResponse.getEntityId() + commandResponse.getResourcePath());

        final UriForLocationHeaderSupplier underTest = new UriForLocationHeaderSupplier(httpRequest, commandResponse);

        assertThat(underTest.get()).isEqualTo(expectedUri);
    }

}