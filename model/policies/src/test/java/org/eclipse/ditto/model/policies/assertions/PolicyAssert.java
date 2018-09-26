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
package org.eclipse.ditto.model.policies.assertions;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import org.assertj.core.api.AbstractAssert;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.policies.EffectedPermissions;
import org.eclipse.ditto.model.policies.Label;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.policies.PolicyEntry;
import org.eclipse.ditto.model.policies.PolicyLifecycle;
import org.eclipse.ditto.model.policies.PolicyRevision;
import org.eclipse.ditto.model.policies.Resource;
import org.eclipse.ditto.model.policies.ResourceKey;
import org.eclipse.ditto.model.policies.Subject;
import org.eclipse.ditto.model.policies.SubjectId;
import org.eclipse.ditto.model.policies.SubjectType;


/**
 * Specific assertion for {@link Policy} objects.
 */
public final class PolicyAssert extends AbstractAssert<PolicyAssert, Policy> {

    /**
     * Constructs a new {@code PolicyAssert} object.
     *
     * @param actual the actual Policy.
     */
    PolicyAssert(final Policy actual) {
        super(actual, PolicyAssert.class);
    }

    public PolicyAssert hasId(final String expectedIdentifier) {
        isNotNull();

        final String actualId = actual.getId().orElse(null);

        assertThat(actualId).isEqualTo(expectedIdentifier) //
                .overridingErrorMessage("Expected Policy identifier to be \n<%s> but was \n<%s>", expectedIdentifier,
                        actualId);

        return this;
    }

    public PolicyAssert hasNamespace(final String expectedNamespace) {
        isNotNull();

        final String actualNamespace = actual.getNamespace().orElse(null);

        assertThat(actualNamespace).isEqualTo(expectedNamespace) //
                .overridingErrorMessage("Expected Thing namespace to be \n<%s> but was \n<%s>", expectedNamespace,
                        actualNamespace);

        return this;
    }

    public PolicyAssert hasLabel(final Label expectedLabel) {
        isNotNull();

        assertThat(actual.getLabels()).contains(expectedLabel) //
                .overridingErrorMessage("Expected Labels to contain \n<%s> but did not: \n<%s>", expectedLabel,
                        actual.getLabels());

        return this;
    }

    public PolicyAssert doesNotHaveLabel(final Label expectedLabel) {
        isNotNull();

        assertThat(actual.getLabels()).doesNotContain(expectedLabel) //
                .overridingErrorMessage("Expected Labels to NOT contain \n<%s> but it did: \n<%s>", expectedLabel,
                        actual.getLabels());

        return this;
    }

    public PolicyAssert hasSubjectFor(final Label label, final SubjectId subjectId) {
        isNotNull();
        hasLabel(label);

        final PolicyEntry policyEntry = actual.getEntryFor(label).get();

        assertThat(policyEntry.getSubjects().getSubject(subjectId)).isPresent() //
                .overridingErrorMessage(
                        "Expected Label <%s> to contain Subject for SubjectId \n<%s> " + "but did not: \n<%s>",
                        label, subjectId, policyEntry.getSubjects());

        return this;
    }

    public PolicyAssert doesNotHaveSubjectFor(final Label label, final SubjectId subjectId) {
        isNotNull();
        hasLabel(label);

        final PolicyEntry policyEntry = actual.getEntryFor(label).get();

        assertThat(policyEntry.getSubjects().getSubject(subjectId)).isEmpty() //
                .overridingErrorMessage(
                        "Expected Label <%s> to NOT contain Subject for SubjectId \n<%s> " + "but it did: \n<%s>",
                        label, subjectId,
                        policyEntry.getSubjects());

        return this;
    }

    public PolicyAssert hasSubjectTypeFor(final Label label, final SubjectId subjectId,
            final SubjectType expectedSubjectType) {
        isNotNull();
        hasSubjectFor(label, subjectId);

        final Subject subject = actual.getEntryFor(label).get().getSubjects().getSubject(subjectId).get();

        assertThat(subject.getType()).isEqualTo(expectedSubjectType) //
                .overridingErrorMessage(
                        "Expected Label <%s> to contain for SubjectId <%s> SubjectType " + "\n<%s> but did not: \n<%s>",
                        label,
                        subjectId, expectedSubjectType, subject.getType());

        return this;
    }

    public PolicyAssert hasResourceFor(final Label label, final String type, final JsonPointer path) {
        return hasResourceFor(label, ResourceKey.newInstance(type, path));
    }

    public PolicyAssert hasResourceFor(final Label label, final ResourceKey resourceKey) {
        isNotNull();
        hasLabel(label);

        final PolicyEntry policyEntry = actual.getEntryFor(label).get();

        assertThat(policyEntry.getResources().getResource(resourceKey)).isPresent() //
                .overridingErrorMessage(
                        "Expected Label <%s> to contain Resource for path \n<%s> " + "but did not: \n<%s>",
                        label, resourceKey, policyEntry.getResources());

        return this;
    }

    public PolicyAssert doesNotHaveResourceFor(final Label label, final String type, final JsonPointer path) {
        return doesNotHaveResourceFor(label, ResourceKey.newInstance(type, path));
    }

    public PolicyAssert doesNotHaveResourceFor(final Label label, final ResourceKey resourceKey) {
        isNotNull();
        hasLabel(label);

        final PolicyEntry policyEntry = actual.getEntryFor(label).get();

        assertThat(policyEntry.getResources().getResource(resourceKey)).isEmpty() //
                .overridingErrorMessage(
                        "Expected Label <%s> to NOT contain Resource for path \n<%s> " + "but it did: \n<%s>",
                        label, resourceKey, policyEntry.getResources());

        return this;
    }

    public PolicyAssert hasResourceEffectedPermissionsFor(final Label label, final String type, final JsonPointer path,
            final EffectedPermissions expectedEffectedPermissions) {
        return hasResourceEffectedPermissionsFor(label, ResourceKey.newInstance(type, path),
                expectedEffectedPermissions);
    }

    public PolicyAssert hasResourceEffectedPermissionsFor(final Label label, final ResourceKey resourceKey,
            final EffectedPermissions expectedEffectedPermissions) {
        isNotNull();
        hasResourceFor(label, resourceKey);

        final Resource resource = actual.getEntryFor(label).get().getResources().getResource(resourceKey).get();

        assertThat(resource.getEffectedPermissions()).isEqualTo(expectedEffectedPermissions) //
                .overridingErrorMessage(
                        "Expected Label <%s> to contain for Resource path <%s> EffectedPermissions " +
                                "\n<%s> but did not: \n<%s>",
                        label, resourceKey, expectedEffectedPermissions, resource.getEffectedPermissions());

        return this;
    }

    public PolicyAssert hasLifecycle(final PolicyLifecycle expectedLifecycle) {
        isNotNull();

        final Optional<PolicyLifecycle> lifecycleOptional = actual.getLifecycle();

        assertThat(lifecycleOptional.isPresent() && Objects.equals(lifecycleOptional.get(), expectedLifecycle)) //
                .overridingErrorMessage("Expected Policy lifecycle to have lifecycle \n<%s> but it had \n<%s>",
                        expectedLifecycle, lifecycleOptional.orElse(null)) //
                .isTrue();

        return this;
    }

    public PolicyAssert hasNoLifecycle() {
        isNotNull();

        final Optional<PolicyLifecycle> actualLifecycleOptional = actual.getLifecycle();

        assertThat(actualLifecycleOptional.isPresent()) //
                .overridingErrorMessage("Expected Policy not to have a lifecycle but it had <%s>",
                        actualLifecycleOptional.orElse(null)) //
                .isFalse();

        return this;
    }

    public PolicyAssert hasRevision(final PolicyRevision expectedRevision) {
        isNotNull();

        final Optional<PolicyRevision> revisionOptional = actual.getRevision();

        assertThat(revisionOptional) //
                .overridingErrorMessage("Expected Policy revision to be \n<%s> but it was \n<%s>", expectedRevision,
                        revisionOptional.orElse(null)) //
                .contains(expectedRevision);

        return this;
    }

    public PolicyAssert hasNoRevision() {
        isNotNull();

        final Optional<PolicyRevision> actualRevisionOptional = actual.getRevision();

        assertThat(actualRevisionOptional.isPresent()) //
                .overridingErrorMessage("Expected Policy not have a revision but it had <%s>",
                        actualRevisionOptional.orElse(null)) //
                .isFalse();

        return this;
    }

    public PolicyAssert hasModified(final Instant expectedmodified) {
        isNotNull();

        final Optional<Instant> modifiedOptional = actual.getModified();

        assertThat(modifiedOptional) //
                .overridingErrorMessage("Expected Policy modified to be \n<%s> but it was \n<%s>", expectedmodified,
                        modifiedOptional.orElse(null)) //
                .contains(expectedmodified);

        return this;
    }

    public PolicyAssert hasNoModified() {
        isNotNull();

        final Optional<Instant> actualmodifiedOptional = actual.getModified();

        assertThat(actualmodifiedOptional.isPresent()) //
                .overridingErrorMessage("Expected Policy not have a modified but it had <%s>",
                        actualmodifiedOptional.orElse(null)) //
                .isFalse();

        return this;
    }

    public PolicyAssert isModifiedAfter(final Instant Instant) {
        isNotNull();

        assertThat(actual.getModified()).isPresent();

        final Instant modified = actual.getModified().get();

        assertThat(modified.isAfter(Instant)) //
                .overridingErrorMessage("Expected <%s> to be after <%s> but it was not",
                        modified,
                        Instant) //
                .isTrue();

        return this;
    }

    public PolicyAssert isNotModifiedAfter(final Instant Instant) {
        isNotNull();

        assertThat(actual.getModified()).isPresent();

        final Instant modified = actual.getModified().get();

        assertThat(modified.isBefore(Instant)) //
                .overridingErrorMessage("Expected <%s> to be before <%s> but it was not",
                        modified,
                        Instant) //
                .isTrue();

        return this;
    }

    public PolicyAssert isEqualEqualToButModified(final Policy expected) {
        assertThat(expected).isNotNull();
        assertThat(actual).isNotNull();

        assertThat(actual.getModified()).isPresent();
        assertThat(actual.getId()).isEqualTo(expected.getId());
        assertThat(actual.getEntriesSet()).isEqualTo(expected.getEntriesSet());

        return this;
    }

}
