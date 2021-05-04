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
package org.eclipse.ditto.base.model.headers.contenttype;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

/**
 * Unit tests for {@link org.eclipse.ditto.base.model.headers.contenttype.ContentType}.
 */
public final class ContentTypeTest {

    @Test
    public void applicationJsonIsJson() {
        final ContentType applicationJson = ContentType.of("application/json");
        assertThat(applicationJson.getParsingStrategy()).isEqualTo(ContentType.ParsingStrategy.JSON);
    }

    @Test
    public void applicationJsonWithCharsetIsJson() {
        final ContentType applicationJson = ContentType.of("application/json;charset=UTF-8");
        assertThat(applicationJson.getParsingStrategy()).isEqualTo(ContentType.ParsingStrategy.JSON);
    }

    @Test
    public void vendorSpecificApplicationJsonIsJson() {
        final ContentType applicationJson = ContentType.of("application/vnd.eclipse.ditto+json");
        assertThat(applicationJson.getParsingStrategy()).isEqualTo(ContentType.ParsingStrategy.JSON);
    }

    @Test
    public void applicationJavascriptIsText() {
        final ContentType applicationJson = ContentType.of("application/javascript");
        assertThat(applicationJson.getParsingStrategy()).isEqualTo(ContentType.ParsingStrategy.TEXT);
    }

    @Test
    public void applicationOctetStreamIsBinary() {
        final ContentType applicationJson = ContentType.of("application/octet-stream");
        assertThat(applicationJson.getParsingStrategy()).isEqualTo(ContentType.ParsingStrategy.BINARY);
    }

    @Test
    public void imageJpegIsBinary() {
        final ContentType applicationJson = ContentType.of("image/jpeg");
        assertThat(applicationJson.getParsingStrategy()).isEqualTo(ContentType.ParsingStrategy.BINARY);
    }

    @Test
    public void applicationMergePatchJsonIsMergePatchJson() {
        final ContentType applicationMergePatchJson = ContentType.of("application/merge-patch+json");
        assertThat(applicationMergePatchJson.getParsingStrategy()).isEqualTo(ContentType.ParsingStrategy.JSON_MERGE_PATCH);
    }
}
