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
package org.eclipse.ditto.model.base.headers.contenttype;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

public final class ContentTypeTest {

    @Test
    public void applicationJsonIsJson() {
        final ContentType applicationJson = ContentType.of("application/json");
        assertThat(applicationJson.isText()).isFalse();
        assertThat(applicationJson.isJson()).isTrue();
        assertThat(applicationJson.isBinary()).isFalse();
    }

    @Test
    public void applicationJsonWithCharsetIsJson() {
        final ContentType applicationJson = ContentType.of("application/json;charset=UTF-8");
        assertThat(applicationJson.isText()).isFalse();
        assertThat(applicationJson.isJson()).isTrue();
        assertThat(applicationJson.isBinary()).isFalse();
    }

    @Test
    public void vendorSpecificApplicationJsonIsJson() {
        final ContentType applicationJson = ContentType.of("application/vnd.eclipse.ditto+json");
        assertThat(applicationJson.isText()).isFalse();
        assertThat(applicationJson.isJson()).isTrue();
        assertThat(applicationJson.isBinary()).isFalse();
    }

    @Test
    public void applicationJavascriptIsText() {
        final ContentType applicationJson = ContentType.of("application/javascript");
        assertThat(applicationJson.isText()).isTrue();
        assertThat(applicationJson.isJson()).isFalse();
        assertThat(applicationJson.isBinary()).isFalse();
    }

    @Test
    public void applicationOctetStreamIsBinary() {
        final ContentType applicationJson = ContentType.of("application/octet-stream");
        assertThat(applicationJson.isText()).isFalse();
        assertThat(applicationJson.isJson()).isFalse();
        assertThat(applicationJson.isBinary()).isTrue();
    }

    @Test
    public void imageJpegIsBinary() {
        final ContentType applicationJson = ContentType.of("image/jpeg");
        assertThat(applicationJson.isText()).isFalse();
        assertThat(applicationJson.isJson()).isFalse();
        assertThat(applicationJson.isBinary()).isTrue();
    }

}
