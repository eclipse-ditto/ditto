/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.concierge.enforcement.placeholders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.junit.Test;

public class SubjectIdReplacementDefinitionTest {
    private SubjectIdReplacementDefinition replacementDefinition = SubjectIdReplacementDefinition.getInstance();

    @Test
    public void succeedsWhenHeadersContainSubjectId() {
        final String subjectId = "nginx:first";
        final DittoHeaders validHeaders = DittoHeaders.newBuilder()
                .authorizationContext(AuthorizationContext.newInstance(AuthorizationSubject.newInstance(subjectId),
                        AuthorizationSubject.newInstance("nginx:second"))).build();

        final String actualSubjectId = replacementDefinition.apply(validHeaders);

        assertThat(actualSubjectId).isEqualTo(subjectId);
    }

    @Test
    public void failsWhenHeadersDoNotContainSubjectId() {
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> replacementDefinition.apply(DittoHeaders.empty()));
    }
}
