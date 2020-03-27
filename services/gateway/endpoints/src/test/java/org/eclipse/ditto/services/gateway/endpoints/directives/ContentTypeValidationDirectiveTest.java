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

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import akka.http.javadsl.model.ContentType;
import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.testkit.JUnitRouteTest;

@RunWith(Parameterized.class)
public class ContentTypeValidationDirectiveTest extends JUnitRouteTest {

    private static final List<ContentType> ONLY_JSON_ALLOWED = List.of(ContentTypes.APPLICATION_JSON);

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
                { false, ContentTypes.APPLICATION_JSON, true },
                { false, ContentTypes.APPLICATION_GRPC_PROTO, false },
                { true, ContentTypes.APPLICATION_JSON, true },
                { true, ContentTypes.APPLICATION_GRPC_PROTO, false },
                { true, null, true },
                { true, ContentTypes.NO_CONTENT_TYPE, true }
        });
    }

    final boolean allowNoneContentType;
    final ContentType contentType;
    final boolean expectedResult;

    public ContentTypeValidationDirectiveTest(
            final boolean allowNoneContentType,
            final ContentType contentType, final boolean expectedResult) {
        this.allowNoneContentType = allowNoneContentType;
        this.contentType = contentType;
        this.expectedResult = expectedResult;
    }

    @Test
    public void testIsContentTypeValid() {
        // Act
        final boolean result =
                ContentTypeValidationDirective.isContentTypeValid(ONLY_JSON_ALLOWED, allowNoneContentType, contentType);

        // Assert
        assertThat(result).isEqualTo(expectedResult);
    }

}