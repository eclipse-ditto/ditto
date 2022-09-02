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
package org.eclipse.ditto.policies.service.persistence.actors.strategies.commands;

import static java.text.MessageFormat.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.eclipse.ditto.base.model.signals.commands.Command.Category.DELETE;
import static org.eclipse.ditto.base.model.signals.commands.Command.Category.MODIFY;
import static org.eclipse.ditto.base.model.signals.commands.Command.Category.QUERY;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.Optional;

import javax.annotation.Nullable;

import org.assertj.core.api.ThrowableAssertAlternative;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTag;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTagMatchers;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.base.model.signals.commands.Command.Category;
import org.eclipse.ditto.internal.utils.headers.conditional.ConditionalHeadersValidator;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyPreconditionFailedException;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyPreconditionNotModifiedException;
import org.junit.Test;

/**
 * Tests the {@link ConditionalHeadersValidator} provided by {@link PoliciesConditionalHeadersValidatorProvider}.
 */
public class PoliciesConditionalHeadersValidatorTest {

    private static final String IF_MATCH_PRECONDITION_FAILED_MESSAGE_PATTERN =
            "The comparison of precondition header ''if-match'' for the requested Policy resource evaluated to false." +
                    " Header value: ''{0}'', actual entity-tag: ''{1}''.";
    private static final String IF_NONE_MATCH_PRECONDITION_FAILED_MESSAGE_PATTERN =
            "The comparison of precondition header ''if-none-match'' for the requested Policy resource evaluated to " +
                    "false. Header value: ''{0}'', actual entity-tag: ''{1}''.";
    private static final String IF_NONE_MATCH_NOT_MODIFIED_MESSAGE_PATTERN =
            "The comparison of precondition header ''if-none-match'' for the requested Policy resource evaluated to " +
                    "false. Expected: ''{0}'' not to match actual: ''{1}''.";

    private static final ConditionalHeadersValidator SUT = PoliciesConditionalHeadersValidatorProvider.getInstance();

    @Test
    public void assertImmutability() {
        assertInstancesOf(PoliciesConditionalHeadersValidatorProvider.class, areImmutable());
    }

    @Test
    public void doesNotThrowExceptionsIfAllChecksSucceed() {
        final String ifMatchHeaderValue = "\"rev:1\"";
        final String ifNoneMatchHeaderValue = "\"rev:2\"";
        final EntityTag actualEntityTag = EntityTag.fromString("\"rev:1\"");
        final Command commandMock = createCommandMock(MODIFY, ifMatchHeaderValue, ifNoneMatchHeaderValue, null);

        SUT.checkConditionalHeaders(commandMock, actualEntityTag);
    }

    @Test
    public void doesNotThrowExceptionsIfCheckIsSkippedBecauseOfNullAndQuery() {
        assertSkipForCommandCategory(QUERY);
    }

    @Test
    public void doesNotThrowExceptionsIfCheckIsSkippedBecauseOfNullAndDelete() {
        assertSkipForCommandCategory(DELETE);
    }

    @Test
    public void doesNotSkipIfEntityTagIsNullAndCategoryIsModify() {
        final String ifMatchHeaderValue = "\"rev:2\"";
        final String ifNoneMatchHeaderValue = "\"rev:3\"";
        final EntityTag actualEntityTag = null;
        final String expectedMessage =
                format(IF_MATCH_PRECONDITION_FAILED_MESSAGE_PATTERN, ifMatchHeaderValue,
                        String.valueOf(actualEntityTag));

        assertPreconditionFailed(ifMatchHeaderValue, ifNoneMatchHeaderValue, actualEntityTag, MODIFY, expectedMessage);
    }

    @Test
    public void throwsPolicyPreconditionFailedExceptionWhenIfMatchFails() {
        final String ifMatchHeaderValue = "\"rev:2\"";
        final String ifNoneMatchHeaderValue = "\"rev:3\"";
        final EntityTag actualEntityTag = EntityTag.fromString("\"rev:1\"");
        final String expectedMessage =
                format(IF_MATCH_PRECONDITION_FAILED_MESSAGE_PATTERN, ifMatchHeaderValue, actualEntityTag);

        assertPreconditionFailed(ifMatchHeaderValue, ifNoneMatchHeaderValue, actualEntityTag, DELETE, expectedMessage);
        assertPreconditionFailed(ifMatchHeaderValue, ifNoneMatchHeaderValue, actualEntityTag, MODIFY, expectedMessage);
        assertPreconditionFailed(ifMatchHeaderValue, ifNoneMatchHeaderValue, actualEntityTag, QUERY, expectedMessage);
    }

    @Test
    public void throwsPolicyPreconditionFailedExceptionWhenIfNoneMatchFailsAndCategoryIsNotQuery() {
        final String ifMatchHeaderValue = "\"rev:1\"";
        final String ifNoneMatchHeaderValue = "\"rev:1\"";
        final EntityTag actualEntityTag = EntityTag.fromString("\"rev:1\"");
        final String expectedMessage =
                format(IF_NONE_MATCH_PRECONDITION_FAILED_MESSAGE_PATTERN, ifNoneMatchHeaderValue, actualEntityTag);

        assertPreconditionFailed(ifMatchHeaderValue, ifNoneMatchHeaderValue, actualEntityTag, DELETE, expectedMessage);
        assertPreconditionFailed(ifMatchHeaderValue, ifNoneMatchHeaderValue, actualEntityTag, MODIFY, expectedMessage);
    }

    @Test
    public void throwsPolicyPreconditionNotModifiedExceptionWhenIfNoneMatchFailsAndCategoryIsQuery() {
        final String ifMatchHeaderValue = "\"rev:1\"";
        final String ifNoneMatchHeaderValue = "\"rev:1\"";
        final EntityTag actualEntityTag = EntityTag.fromString("\"rev:1\"");
        final String expectedMessage =
                format(IF_NONE_MATCH_NOT_MODIFIED_MESSAGE_PATTERN, ifNoneMatchHeaderValue, actualEntityTag);

        assertNotModified(ifMatchHeaderValue, ifNoneMatchHeaderValue, actualEntityTag, expectedMessage, null);
    }

    private Command createCommandMock(final Category commandCategory, final String ifMatchHeaderValue,
            final String ifNoneMatchHeaderValue, final @Nullable JsonObject selectedFields) {
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                .ifMatch(EntityTagMatchers.fromCommaSeparatedString(ifMatchHeaderValue))
                .ifNoneMatch(EntityTagMatchers.fromCommaSeparatedString(ifNoneMatchHeaderValue))
                .build();
        final Command commandMock = mock(Command.class);
        when(commandMock.getDittoHeaders()).thenReturn(dittoHeaders);
        when(commandMock.getCategory()).thenReturn(commandCategory);

        if (Optional.ofNullable(selectedFields).isPresent()) {
            when(commandMock.toJson()).thenReturn(Optional.of(selectedFields).get());
        } else {
            when(commandMock.toJson()).thenReturn(JsonObject.empty());
        }

        return commandMock;
    }

    private void assertSkipForCommandCategory(final Category commandCategory) {
        final String ifMatchHeaderValue = "\"rev:2\"";
        final String ifNoneMatchHeaderValue = "\"rev:1\"";
        final EntityTag actualEntityTag = null;
        final Command commandMock =
                createCommandMock(commandCategory, ifMatchHeaderValue, ifNoneMatchHeaderValue, null);

        SUT.checkConditionalHeaders(commandMock, actualEntityTag);
    }

    private void assertETagHeaderInDre(final DittoRuntimeException dre, final EntityTag expectedEntityTag) {
        final Optional<EntityTag> eTag = dre.getDittoHeaders().getETag();

        assertThat(eTag).isPresent();
        assertThat(eTag).contains(expectedEntityTag);
    }

    private void assertPreconditionFailed(final String ifMatchHeaderValue,
            final String ifNoneMatchHeaderValue, @Nullable final EntityTag actualEntityTag,
            final Category commandCategory, final String expectedMessage) {
        final Command commandMock =
                createCommandMock(commandCategory, ifMatchHeaderValue, ifNoneMatchHeaderValue, null);

        final ThrowableAssertAlternative<PolicyPreconditionFailedException> assertion =
                assertThatExceptionOfType(PolicyPreconditionFailedException.class)
                        .isThrownBy(() -> SUT.checkConditionalHeaders(commandMock, actualEntityTag))
                        .withMessage(expectedMessage);

        if (actualEntityTag == null) {
            assertion.satisfies(exception -> assertThat(exception.getDittoHeaders().getETag()).isNotPresent());
        } else {
            assertion.satisfies(exception -> assertETagHeaderInDre(exception, actualEntityTag));
        }
    }

    private void assertNotModified(final String ifMatchHeaderValue,
            final String ifNoneMatchHeaderValue, @Nullable final EntityTag actualEntityTag,
            final String expectedMessage, final @Nullable JsonObject selectedFields) {
        final Command commandMock =
                createCommandMock(QUERY, ifMatchHeaderValue, ifNoneMatchHeaderValue, selectedFields);

        final ThrowableAssertAlternative<PolicyPreconditionNotModifiedException> assertion =
                assertThatExceptionOfType(PolicyPreconditionNotModifiedException.class)
                        .isThrownBy(() -> SUT.checkConditionalHeaders(commandMock, actualEntityTag))
                        .withMessage(expectedMessage);

        if (actualEntityTag == null) {
            assertion.satisfies(exception -> assertThat(exception.getDittoHeaders().getETag()).isNotPresent());
        } else {
            assertion.satisfies(exception -> assertETagHeaderInDre(exception, actualEntityTag));
        }
    }
}
