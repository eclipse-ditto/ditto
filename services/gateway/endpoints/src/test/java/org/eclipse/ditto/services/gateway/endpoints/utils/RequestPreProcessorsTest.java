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
package org.eclipse.ditto.services.gateway.endpoints.utils;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.policies.SubjectId;
import org.eclipse.ditto.model.policies.SubjectIssuer;
import org.junit.Test;

/**
 * Tests for {@link RequestPreProcessors}.
 */
public class RequestPreProcessorsTest {

    @Test
    public void ensureSubjectIdPlaceholderIsReplaced() {
        // GIVEN
        final String stringWithPlaceholder = "${request.subjectId}";
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder().authorizationSubjects(
                SubjectId.newInstance(SubjectIssuer.GOOGLE, "hans").toString(),
                SubjectId.newInstance(SubjectIssuer.GOOGLE, "lisa").toString(),
                SubjectId.newInstance(SubjectIssuer.GOOGLE, "larry").toString()
        ).build();

        // WHEN
        final String replacedString = RequestPreProcessors.replacePlaceholders(stringWithPlaceholder, dittoHeaders);

        // THEN
        assertThat(replacedString)
                .isEqualTo(SubjectIssuer.GOOGLE + SubjectId.ISSUER_DELIMITER + "hans");
    }

    @Test
    public void ensureMultipleSubjectIdPlaceholdersAreReplaced() {
        // GIVEN
        final String stringWithPlaceholder = "First:\n${request.subjectId}\nSecond:\n${request.subjectId}";
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder().authorizationSubjects(
                SubjectId.newInstance(SubjectIssuer.GOOGLE, "hans").toString(),
                SubjectId.newInstance(SubjectIssuer.GOOGLE, "lisa").toString(),
                SubjectId.newInstance(SubjectIssuer.GOOGLE, "larry").toString()
        ).build();

        // WHEN
        final String replacedString = RequestPreProcessors.replacePlaceholders(stringWithPlaceholder, dittoHeaders);

        // THEN
        final String expectedReplacement = SubjectIssuer.GOOGLE + SubjectId.ISSUER_DELIMITER + "hans";
        final String expectedReplacedString = "First:\n" + expectedReplacement + "\nSecond:\n" + expectedReplacement;
        assertThat(replacedString).isEqualTo(expectedReplacedString);
    }

}
