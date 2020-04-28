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
package org.eclipse.ditto.services.concierge.enforcement.placeholders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.junit.Before;
import org.junit.Test;

public final class SubjectIdReplacementDefinitionTest {

    private SubjectIdReplacementDefinition underTest;

    @Before
    public void setUp() {
        underTest = SubjectIdReplacementDefinition.getInstance();
    }

    @Test
    public void succeedsWhenHeadersContainSubjectId() {
        final String subjectId = "nginx:first";
        final DittoHeaders validHeaders = DittoHeaders.newBuilder()
                .authorizationContext(AuthorizationContext.newInstance(DittoAuthorizationContextType.UNSPECIFIED,
                        AuthorizationSubject.newInstance(subjectId), AuthorizationSubject.newInstance("nginx:second")))
                .build();

        final String actualSubjectId = underTest.apply(validHeaders);

        assertThat(actualSubjectId).isEqualTo(subjectId);
    }

    @Test
    public void failsWhenHeadersDoNotContainSubjectId() {
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> underTest.apply(DittoHeaders.empty()));
    }

}
