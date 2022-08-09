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
package org.eclipse.ditto.policies.model;

import static java.util.Objects.requireNonNull;
import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.text.MessageFormat;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonValue;

/**
 * An immutable implementation of {@link Subjects}.
 */
@Immutable
final class ImmutableSubjects implements Subjects {

    private final Map<SubjectId, Subject> subjects;

    private ImmutableSubjects(final Map<SubjectId, Subject> theSubjects) {
        requireNonNull(theSubjects, "The Subjects must not be null!");
        subjects = Collections.unmodifiableMap(new LinkedHashMap<>(theSubjects));
    }

    /**
     * Returns a new instance of {@code Subjects} with the given subjects.
     *
     * @param subjects the {@link Subject}s of the new Subjects.
     * @return the new {@code Subjects}.
     * @throws NullPointerException if {@code subjects} is {@code null}.
     */
    public static ImmutableSubjects of(final Iterable<Subject> subjects) {
        checkNotNull(subjects, "subjects");

        final Map<SubjectId, Subject> subjectsMap = new LinkedHashMap<>();
        subjects.forEach(subject -> {
            final Subject existingSubject = subjectsMap.put(subject.getId(), subject);
            if (null != existingSubject) {
                final String msgTemplate = "There is more than one Subject with the ID <{0}>!";
                throw new IllegalArgumentException(MessageFormat.format(msgTemplate, subject.getId()));
            }
        });

        return new ImmutableSubjects(subjectsMap);
    }

    /**
     * Creates a new {@code Subjects} from the specified JSON object.
     *
     * @param jsonObject the JSON object of which a new Subjects instance is to be created.
     * @return the {@code Subjects} which was created from the given JSON object.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * 'Subjects' format.
     */
    public static ImmutableSubjects fromJson(final JsonObject jsonObject) {
        final Function<JsonField, Subject> toSubject = jsonField -> {
            final JsonValue jsonValue = jsonField.getValue();
            if (!jsonValue.isObject()) {
                throw new JsonParseException(MessageFormat.format("<{0}> is not a JSON object!", jsonValue));
            }
            return PoliciesModelFactory.newSubject(jsonField.getKey(), jsonValue.asObject());
        };

        final List<Subject> theSubjects = jsonObject.stream()
                .filter(field -> !Objects.equals(field.getKey(), JsonSchemaVersion.getJsonKey()))
                .map(toSubject)
                .collect(Collectors.toList());

        return of(theSubjects);
    }

    @Override
    public Optional<Subject> getSubject(final SubjectId subjectId) {
        checkNotNull(subjectId, "subject identifier of the subject to retrieve");

        return Optional.ofNullable(subjects.get(subjectId));
    }

    @Override
    public Subjects setSubject(final Subject subject) {
        checkNotNull(subject, "subject to set");

        Subjects result = this;

        final Subject existingSubject = subjects.get(subject.getId());
        if (!Objects.equals(existingSubject, subject)) {
            result = createNewSubjectsWithNewSubject(subject);
        }
        return result;
    }

    @Override
    public Subjects setSubjects(final Subjects subjects) {
        Subjects result = this;
        for (final Subject subject : subjects) {
            result = result.setSubject(subject);
        }
        return result;
    }

    private Subjects createNewSubjectsWithNewSubject(final Subject newSubject) {
        final Map<SubjectId, Subject> subjectsCopy = copySubjects();
        subjectsCopy.put(newSubject.getId(), newSubject);
        return new ImmutableSubjects(subjectsCopy);
    }

    private Map<SubjectId, Subject> copySubjects() {
        return new LinkedHashMap<>(subjects);
    }

    @Override
    public Subjects removeSubject(final SubjectId subjectId) {
        checkNotNull(subjectId, "subject identifier of the subject to remove");

        if (!subjects.containsKey(subjectId)) {
            return this;
        }

        final Map<SubjectId, Subject> subjectsCopy = copySubjects();
        subjectsCopy.remove(subjectId);

        return new ImmutableSubjects(subjectsCopy);
    }

    @Override
    public int getSize() {
        return subjects.size();
    }

    @Override
    public boolean isEmpty() {
        return subjects.isEmpty();
    }

    @Override
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        return JsonFactory.newObjectBuilder()
                .setAll(subjectsToJson(schemaVersion, thePredicate), predicate)
                .build();
    }

    private JsonObject subjectsToJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        final JsonObjectBuilder jsonObjectBuilder = JsonFactory.newObjectBuilder();

        subjects.values().forEach(subject -> {
            final JsonKey key = JsonKey.of(subject.getId());
            final JsonValue value = subject.toJson(schemaVersion, thePredicate);
            final JsonFieldDefinition<JsonObject> fieldDefinition =
                    JsonFactory.newJsonObjectFieldDefinition(key, FieldType.REGULAR, JsonSchemaVersion.V_2);
            final JsonField field = JsonFactory.newField(key, value, fieldDefinition);

            jsonObjectBuilder.set(field, predicate);
        });

        return jsonObjectBuilder.build();
    }

    @Override
    public Iterator<Subject> iterator() {
        return new LinkedHashSet<>(subjects.values()).iterator();
    }

    @Override
    public Stream<Subject> stream() {
        return subjects.values().stream();
    }

    @Override
    public boolean isSemanticallySameAs(final Subjects otherSubjects) {
        return subjects.keySet().equals(otherSubjects.stream().map(Subject::getId).collect(Collectors.toSet()));
    }

    @SuppressWarnings({"squid:MethodCyclomaticComplexity", "squid:S1067"})
    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ImmutableSubjects)) {
            return false;
        }

        final ImmutableSubjects that = (ImmutableSubjects) o;
        return Objects.equals(subjects, that.subjects);
    }

    @Override
    public int hashCode() {
        return Objects.hash(subjects);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [subjects=" + subjects + "]";
    }

}
