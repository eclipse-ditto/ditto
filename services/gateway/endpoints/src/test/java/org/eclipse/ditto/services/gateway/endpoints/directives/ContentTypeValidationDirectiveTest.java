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
package org.eclipse.ditto.services.gateway.endpoints.directives;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

import akka.http.javadsl.model.ContentType;
import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.testkit.JUnitRouteTest;

public class ContentTypeValidationDirectiveTest extends JUnitRouteTest {

    @Test
    public void testEnsureValidContentTypeJsonAlwaysValid() {
        // Arrange
        final ContentType contentType = ContentTypes.APPLICATION_JSON;
        final String unmatchedPath = "";

        // Act
        final boolean result = ContentTypeValidationDirective.isContentTypeValidForThatPath(contentType, unmatchedPath);

        // Assert
        assertThat(result).isTrue();
    }

    @Test
    public void testEnsureValidContentTypeNoContentTypeIsAlwaysValid() {
        // Arrange
        final ContentType contentType = null;
        final String unmatchedPath = "imNotEvenARealPath";

        // Act
        final boolean result = ContentTypeValidationDirective.isContentTypeValidForThatPath(contentType, unmatchedPath);

        // Assert
        assertThat(result).isTrue();
    }

    @Test
    public void testEnsureValidContentTypeAkkasNoContentTypeIsAlwaysValid() {
        // Arrange
        final ContentType contentType = ContentTypes.NO_CONTENT_TYPE;
        final String unmatchedPath = "imNotEvenARealPath";

        // Act
        final boolean result = ContentTypeValidationDirective.isContentTypeValidForThatPath(contentType, unmatchedPath);

        // Assert
        assertThat(result).isTrue();
    }

    @Test
    public void testEnsureValidContentTypeAnyContentTypeForMessageEndpointIsValid() {
        // Arrange
        final ContentType contentType = ContentTypes.APPLICATION_OCTET_STREAM;
        final String unmatchedPath = "/api/2/things/messages";

        // Act
        final boolean result = ContentTypeValidationDirective.isContentTypeValidForThatPath(contentType, unmatchedPath);

        // Assert
        assertThat(result).isTrue();
    }

    @Test
    public void testEnsureValidContentTypeOtherThanJsonIsNotValid() {
        // Arrange
        final ContentType contentType = ContentTypes.APPLICATION_OCTET_STREAM;
        final String unmatchedPath = "/api/2/things/abc";

        // Act
        final boolean result = ContentTypeValidationDirective.isContentTypeValidForThatPath(contentType, unmatchedPath);

        // Assert
        assertThat(result).isFalse();
    }
}