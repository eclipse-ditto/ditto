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
package org.eclipse.ditto.model.policies;

import java.util.Optional;
import java.util.stream.Stream;

import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.base.json.Jsonifiable;

/**
 * A collection of {@link Subject}s contained in a single {@link PolicyEntry}.
 */
public interface Subjects extends Iterable<Subject>, Jsonifiable.WithFieldSelectorAndPredicate<JsonField> {

    /**
     * Returns a new {@code Subjects} containing the given subjects.
     *
     * @param subjects the {@link Subject}s to be contained in the new Subjects.
     * @return the new {@code Subjects}.
     * @throws NullPointerException if {@code subjects} is {@code null}.
     */
    static Subjects newInstance(final Iterable<Subject> subjects) {
        return PoliciesModelFactory.newSubjects(subjects);
    }

    /**
     * Returns a new {@code Subjects} containing the given subjects.
     *
     * @param subject the {@link Subject} to be contained in the new Subjects.
     * @param furtherSubjects further {@link Subject}s to be contained in the new Subjects.
     * @return the new {@code Subjects}.
     * @throws NullPointerException if any argument is {@code null}.
     */
    static Subjects newInstance(final Subject subject, final Subject... furtherSubjects) {
        return PoliciesModelFactory.newSubjects(subject, furtherSubjects);
    }

    /**
     * Subjects is only available in JsonSchemaVersion V_2.
     *
     * @return the supported JsonSchemaVersions of Subjects.
     */
    @Override
    default JsonSchemaVersion[] getSupportedSchemaVersions() {
        return new JsonSchemaVersion[]{JsonSchemaVersion.V_2};
    }

    /**
     * Returns the Subject with the given subjectIssuerWithId or an empty optional.
     *
     * @param subjectIssuerWithId the Subject issuer + Subject ID (separated with a "{@value
     * SubjectId#ISSUER_DELIMITER}") of the Subject to be retrieved.
     * @return the Subject with the given subjectIssuerWithId or an empty optional.
     * @throws NullPointerException if {@code subjectIssuerWithId} is {@code null}.
     * @throws IllegalArgumentException if {@code subjectIssuerWithId} is empty.
     */
    default Optional<Subject> getSubject(final CharSequence subjectIssuerWithId) {
        return getSubject(SubjectId.newInstance(subjectIssuerWithId));
    }

    /**
     * Returns the Subject with the given {@code issuer} and {@code subject} or an empty optional.
     *
     * @param issuer the SubjectId's {@code issuer} of the Subject to be retrieved.
     * @param subject the character sequence for the SubjectId's {@code subject} of the Subject to be retrieved.
     * @return the Subject with the given subjectId or an empty optional.
     * @throws NullPointerException if {@code subjectId} is {@code null}.
     * @throws IllegalArgumentException if {@code subject} is empty.
     */
    default Optional<Subject> getSubject(final SubjectIssuer issuer, final CharSequence subject) {
        return getSubject(SubjectId.newInstance(issuer, subject));
    }

    /**
     * Returns the Subject with the given subjectId or an empty optional.
     *
     * @param subjectId the subjectId of the Subject to be retrieved.
     * @return the Subject with the given subjectId or an empty optional.
     * @throws NullPointerException if {@code subjectId} is {@code null}.
     */
    Optional<Subject> getSubject(SubjectId subjectId);

    /**
     * Sets the given Subject to a copy of this Subjects. A previous Subject with the same {@link SubjectId} will be
     * overwritten.
     *
     * @param subject the Subject to be set.
     * @return a copy of this Subjects with {@code subject} set.
     * @throws NullPointerException if {@code subject} is {@code null}.
     */
    Subjects setSubject(Subject subject);

    /**
     * Removes the Subject with the given identifier from a copy of this Subjects.
     *
     * @param subjectIssuerWithId the Subject issuer + Subject ID (separated with a "{@value
     * SubjectId#ISSUER_DELIMITER}") of the Subject to be removed.
     * @return a copy of this Subjects with {@code subject} removed.
     * @throws NullPointerException if {@code subjectIssuerWithId} is {@code null}.
     * @throws IllegalArgumentException if {@code subjectIssuerWithId} is empty.
     */
    default Subjects removeSubject(final CharSequence subjectIssuerWithId) {
        return removeSubject(SubjectId.newInstance(subjectIssuerWithId));
    }

    /**
     * Removes the Subject with the given {@code issuer} and {@code subject} from a copy of this Subjects.
     *
     * @param issuer the SubjectId's {@code issuer} of the Subject to be removed.
     * @param subject the character sequence for the SubjectId's {@code subject} of the Subject to be removed.
     * @return a copy of this Subjects with {@code subject} removed.
     * @throws NullPointerException if {@code subjectId} is {@code null}.
     * @throws IllegalArgumentException if {@code subject} is empty.
     */
    default Subjects removeSubject(final SubjectIssuer issuer, final CharSequence subject) {
        return removeSubject(SubjectId.newInstance(issuer, subject));
    }

    /**
     * Removes the Subject with the given identifier from a copy of this Subjects.
     *
     * @param subjectId the subjectId of the Subject to be removed.
     * @return a copy of this Subjects with {@code subject} removed.
     * @throws NullPointerException if {@code subjectId} is {@code null}.
     */
    Subjects removeSubject(SubjectId subjectId);

    /**
     * Returns the size of this Subjects, i. e. the number of contained values.
     *
     * @return the number of Subjects.
     */
    int getSize();

    /**
     * Indicates whether this Subjects is empty.
     *
     * @return {@code true} if this Subjects does not contain any values, {@code false} else.
     */
    boolean isEmpty();

    /**
     * Returns a sequential {@code Stream} with the values of this Subjects as its source.
     *
     * @return a sequential stream of the Subjects of this container.
     */
    Stream<Subject> stream();

    /**
     * Returns all non hidden marked fields of this subjects.
     *
     * @return a JSON object representation of this subjects including only non hidden marked fields.
     */
    @Override
    default JsonObject toJson() {
        return toJson(FieldType.notHidden());
    }

    @Override
    default JsonObject toJson(final JsonSchemaVersion schemaVersion, final JsonFieldSelector fieldSelector) {
        return toJson(schemaVersion, FieldType.regularOrSpecial()).get(fieldSelector);
    }

}
