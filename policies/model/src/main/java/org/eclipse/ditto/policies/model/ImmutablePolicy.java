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

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;
import static org.eclipse.ditto.policies.model.PoliciesModelFactory.emptyResources;
import static org.eclipse.ditto.policies.model.PoliciesModelFactory.newPolicyEntry;
import static org.eclipse.ditto.policies.model.PoliciesModelFactory.newResources;

import java.text.MessageFormat;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.entity.metadata.MetadataModelFactory;
import org.eclipse.ditto.base.model.exceptions.DittoJsonException;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;

/**
 * Immutable implementation of {@link Policy}.
 */
@Immutable
final class ImmutablePolicy implements Policy {

    @Nullable private final PolicyId policyId;
    private final Map<Label, PolicyEntry> entries;
    @Nullable private final String namespace;
    @Nullable private final PolicyLifecycle lifecycle;
    @Nullable private final PolicyRevision revision;
    @Nullable private final Instant modified;
    @Nullable private final Instant created;
    @Nullable private final Metadata metadata;

    private ImmutablePolicy(@Nullable final PolicyId policyId,
            final Map<Label, PolicyEntry> theEntries,
            @Nullable final PolicyLifecycle lifecycle,
            @Nullable final PolicyRevision revision,
            @Nullable final Instant modified,
            @Nullable final Instant created,
            @Nullable final Metadata metadata) {

        this.policyId = policyId;
        entries = Collections.unmodifiableMap(new LinkedHashMap<>(theEntries));
        namespace = policyId == null ? null : policyId.getNamespace();
        this.lifecycle = lifecycle;
        this.revision = revision;
        this.modified = modified;
        this.created = created;
        this.metadata = metadata;
    }

    /**
     * Returns a new Policy which is initialised with the specified entries.
     *
     * @param policyId the ID of the new Policy.
     * @param lifecycle the lifecycle of the Policy to be created.
     * @param revision the revision of the Policy to be created.
     * @param modified the modified timestamp of the Policy to be created.
     * @param created the created timestamp of the Policy to be created.
     * @param metadata the metadata of the Thing to be created.
     * @param entries the entries of the Policy to be created.
     * @return a new initialised Policy.
     * @throws NullPointerException if {@code entries} is {@code null}.
     */
    public static Policy of(@Nullable final PolicyId policyId,
            @Nullable final PolicyLifecycle lifecycle,
            @Nullable final PolicyRevision revision,
            @Nullable final Instant modified,
            @Nullable final Instant created,
            @Nullable final Metadata metadata,
            final Iterable<PolicyEntry> entries) {

        checkNotNull(entries, "Policy entries");

        final Map<Label, PolicyEntry> entryMap = new LinkedHashMap<>();
        entries.forEach(policyEntry -> entryMap.put(policyEntry.getLabel(), policyEntry));

        return new ImmutablePolicy(policyId, entryMap, lifecycle, revision, modified, created, metadata);
    }

    /**
     * Creates a new {@code Policy} object from the specified JSON object.
     *
     * @param jsonObject a JSON object which provides the data for the Policy to be created.
     * @return a new Policy which is initialised with the extracted data from {@code jsonObject}.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws PolicyEntryInvalidException if an Policy entry does not contain any known permission which evaluates to
     * {@code true} or {@code false}.
     * @throws PolicyIdInvalidException if the parsed policy ID did not comply to
     * {@link org.eclipse.ditto.base.model.entity.id.RegexPatterns#ID_REGEX}.
     */
    public static Policy fromJson(final JsonObject jsonObject) {
        final PolicyId policyId = jsonObject.getValue(JsonFields.ID).map(PolicyId::of).orElse(null);

        final PolicyLifecycle readLifecycle = jsonObject.getValue(JsonFields.LIFECYCLE)
                .flatMap(PolicyLifecycle::forName)
                .orElse(null);

        final PolicyRevision readRevision = jsonObject.getValue(JsonFields.REVISION)
                .map(PolicyRevision::newInstance)
                .orElse(null);

        final Instant readModified = jsonObject.getValue(JsonFields.MODIFIED)
                .map(ImmutablePolicy::tryToParseModified)
                .orElse(null);

        final Instant readCreated = jsonObject.getValue(JsonFields.CREATED)
                .map(ImmutablePolicy::tryToParseModified)
                .orElse(null);

        final JsonObject readEntries = jsonObject.getValue(JsonFields.ENTRIES).orElseGet(JsonObject::empty);

        final Function<JsonField, PolicyEntry> toPolicyEntry = jsonField -> {
            final JsonValue jsonValue = jsonField.getValue();
            if (!jsonValue.isObject()) {
                throw new DittoJsonException(JsonParseException.newBuilder()
                        .message(MessageFormat.format("<{0}> is not a JSON object!", jsonValue))
                        .build());
            }
            return ImmutablePolicyEntry.fromJson(jsonField.getKey(), jsonValue.asObject());
        };

        final Collection<PolicyEntry> policyEntries = readEntries.stream()
                .filter(jsonField -> !Objects.equals(jsonField.getKey(), JsonSchemaVersion.getJsonKey()))
                .map(toPolicyEntry)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        final Metadata readMetadata = jsonObject.getValue(JsonFields.METADATA)
                .map(MetadataModelFactory::newMetadata)
                .orElse(null);

        return of(policyId, readLifecycle, readRevision, readModified, readCreated, readMetadata, policyEntries);
    }

    private static Instant tryToParseModified(final CharSequence dateTime) {
        try {
            return Instant.parse(dateTime);
        } catch (final DateTimeParseException e) {
            throw new JsonParseException("The JSON object's field '" + JsonFields.MODIFIED.getPointer() + "' " +
                    "is not in ISO-8601 format as expected");
        }
    }

    @Override
    public Optional<PolicyId> getEntityId() {
        return Optional.ofNullable(policyId);
    }

    @Override
    public Optional<String> getNamespace() {
        return Optional.ofNullable(namespace);
    }

    @Override
    public Optional<PolicyLifecycle> getLifecycle() {
        return Optional.ofNullable(lifecycle);
    }

    @Override
    public Optional<PolicyRevision> getRevision() {
        return Optional.ofNullable(revision);
    }

    @Override
    public Optional<Instant> getModified() {
        return Optional.ofNullable(modified);
    }

    @Override
    public Optional<Instant> getCreated() {
        return Optional.ofNullable(created);
    }

    @Override
    public Optional<Metadata> getMetadata() {
        return Optional.ofNullable(metadata);
    }

    @Override
    public boolean isDeleted() {
        return PolicyLifecycle.DELETED.equals(lifecycle);
    }

    @Override
    public Policy setEntry(final PolicyEntry policyEntry) {
        checkNotNull(policyEntry, "entry to be set to this Policy");

        final Policy result;

        final PolicyEntry existingPolicyEntry = entries.get(policyEntry.getLabel());
        if (null != existingPolicyEntry) {
            if (existingPolicyEntry.equals(policyEntry)) {
                result = this;
            } else {
                final Map<Label, PolicyEntry> entriesCopy = copyEntries();
                entriesCopy.put(policyEntry.getLabel(), policyEntry);
                result = new ImmutablePolicy(policyId, entriesCopy, lifecycle, revision, modified, created, metadata);
            }
        } else {
            final Map<Label, PolicyEntry> entriesCopy = copyEntries();
            entriesCopy.put(policyEntry.getLabel(), policyEntry);
            result = new ImmutablePolicy(policyId, entriesCopy, lifecycle, revision, modified, created, metadata);
        }

        return result;
    }

    @Override
    public Set<Label> getLabels() {
        return entries.keySet();
    }

    @Override
    public boolean contains(final CharSequence label) {
        return entries.containsKey(Label.of(label));
    }

    @Override
    public Optional<PolicyEntry> getEntryFor(final CharSequence label) {
        return Optional.ofNullable(entries.get(Label.of(label)));
    }

    @Override
    public Policy removeEntry(final CharSequence label) {
        final Label lbl = Label.of(label);

        if (!entries.containsKey(lbl)) {
            return this;
        }

        final Map<Label, PolicyEntry> entriesCopy = copyEntries();
        entriesCopy.remove(lbl);

        return new ImmutablePolicy(policyId, entriesCopy, lifecycle, revision, modified, created, metadata);
    }

    @Override
    public Policy removeEntry(final PolicyEntry entry) {
        checkNotNull(entry, "Policy entry to be removed");

        return removeEntry(entry.getLabel());
    }

    @Override
    public Policy setSubjectsFor(final CharSequence label, final Subjects subjects) {
        final Label lbl = Label.of(label);
        checkNotNull(subjects, "subjects to set to the Policy entry");

        final Map<Label, PolicyEntry> entriesCopy = copyEntries();
        final PolicyEntry modifiedEntry;

        if (!entriesCopy.containsKey(lbl)) {
            modifiedEntry = newPolicyEntry(lbl, subjects, emptyResources());
        } else {
            final PolicyEntry policyEntry = entriesCopy.get(lbl);
            modifiedEntry = newPolicyEntry(lbl, subjects, policyEntry.getResources());
        }

        entriesCopy.put(lbl, modifiedEntry);
        return new ImmutablePolicy(policyId, entriesCopy, lifecycle, revision, modified, created, metadata);
    }

    @Override
    public Policy setSubjectFor(final CharSequence label, final Subject subject) {
        final Label lbl = Label.of(label);
        checkNotNull(subject, "subject to set to the Policy entry");

        final Policy result;
        final PolicyEntry existingPolicyEntry = entries.get(lbl);
        if (null != existingPolicyEntry) {
            final Subjects existingSubjects = existingPolicyEntry.getSubjects();
            final Subjects newSubjects = existingSubjects.setSubject(subject);
            if (!Objects.equals(existingSubjects, newSubjects)) {
                final Map<Label, PolicyEntry> entriesCopy = copyEntries();
                entriesCopy.put(lbl, newPolicyEntry(lbl, newSubjects, existingPolicyEntry.getResources()));
                result = new ImmutablePolicy(policyId, entriesCopy, lifecycle, revision, modified, created, metadata);
            } else {
                result = this;
            }
        } else {
            result = setSubjectsFor(label, Subjects.newInstance(subject));
        }

        return result;
    }

    @Override
    public Policy removeSubjectFor(final CharSequence label, final SubjectId subjectId) {
        final Label lbl = Label.of(label);

        Policy result = this;
        final PolicyEntry existingPolicyEntry = entries.get(lbl);
        if (null != existingPolicyEntry) {
            final Subjects existingSubjects = existingPolicyEntry.getSubjects();
            final Subjects newSubjects = existingSubjects.removeSubject(subjectId);
            if (!Objects.equals(existingSubjects, newSubjects)) {
                final Map<Label, PolicyEntry> entriesCopy = copyEntries();
                entriesCopy.put(lbl, newPolicyEntry(lbl, newSubjects, existingPolicyEntry.getResources()));
                result = new ImmutablePolicy(policyId, entriesCopy, lifecycle, revision, modified, created, metadata);
            }
        }

        return result;
    }

    @Override
    public Policy setResourcesFor(final CharSequence label, final Resources resources) {
        final Label lbl = Label.of(label);
        checkNotNull(resources, "resources to set to the Policy entry");

        final Map<Label, PolicyEntry> entriesCopy = copyEntries();
        final PolicyEntry policyEntry = entriesCopy.get(lbl);
        final PolicyEntry modifiedEntry;
        if (null == policyEntry) {
            modifiedEntry = newPolicyEntry(lbl, PoliciesModelFactory.emptySubjects(), resources);
        } else {
            modifiedEntry = newPolicyEntry(lbl, policyEntry.getSubjects(), resources);
        }
        entriesCopy.put(lbl, modifiedEntry);

        return new ImmutablePolicy(policyId, entriesCopy, lifecycle, revision, modified, created, metadata);
    }

    @Override
    public Policy setResourceFor(final CharSequence label, final Resource resource) {
        final Label lbl = Label.of(label);
        checkNotNull(resource, "resource to set to the Policy entry");

        final Map<Label, PolicyEntry> entriesCopy = copyEntries();
        final PolicyEntry modifiedEntry;

        if (!entriesCopy.containsKey(lbl)) {
            modifiedEntry = newPolicyEntry(label, PoliciesModelFactory.emptySubjects(), newResources(resource));
        } else {
            final PolicyEntry policyEntry = entriesCopy.get(lbl);
            final Resources modifiedResources = policyEntry.getResources().setResource(resource);
            modifiedEntry = newPolicyEntry(label, policyEntry.getSubjects(), modifiedResources);
        }

        entriesCopy.put(lbl, modifiedEntry);
        return new ImmutablePolicy(policyId, entriesCopy, lifecycle, revision, modified, created, metadata);
    }

    @Override
    public Policy removeResourceFor(final CharSequence label, final ResourceKey resourceKey) {
        final Label lbl = Label.of(label);

        Policy result = this;

        final PolicyEntry existingEntry = entries.get(lbl);
        if (null != existingEntry) {
            final Resources existingResources = existingEntry.getResources();
            final Resources newResources = existingResources.removeResource(resourceKey);
            if (!Objects.equals(existingResources, newResources)) {
                final Map<Label, PolicyEntry> entriesCopy = copyEntries();
                entriesCopy.put(lbl, newPolicyEntry(lbl, existingEntry.getSubjects(), newResources));
                result = new ImmutablePolicy(policyId, entriesCopy, lifecycle, revision, modified, created, metadata);
            }
        }

        return result;
    }

    @Override
    public Optional<EffectedPermissions> getEffectedPermissionsFor(final CharSequence label, final SubjectId subjectId,
            final ResourceKey resourceKey) {

        final Label lbl = Label.of(label);

        Optional<EffectedPermissions> result = Optional.empty();

        final PolicyEntry policyEntry = entries.get(lbl);
        if (null != policyEntry) {
            final Subjects subjects = policyEntry.getSubjects();
            final Optional<Subject> subjectOptional = subjects.getSubject(subjectId);
            if (subjectOptional.isPresent()) {
                final Resources resources = policyEntry.getResources();
                result = resources.getResource(resourceKey).map(Resource::getEffectedPermissions);
            }
        }

        return result;
    }

    @Override
    public boolean isEmpty() {
        return entries.isEmpty();
    }

    @Override
    public int getSize() {
        return entries.size();
    }

    @Override
    public Set<PolicyEntry> getEntriesSet() {
        return stream().collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @Override
    public Stream<PolicyEntry> stream() {
        return entries.values().stream();
    }

    @Override
    public Iterator<PolicyEntry> iterator() {
        final Set<PolicyEntry> policyEntries = getEntriesSet();
        return policyEntries.iterator();
    }

    @Override
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {
        checkNotNull(schemaVersion, "schema version");
        checkNotNull(thePredicate, "predicate");

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);

        final JsonObjectBuilder jsonObjectBuilder = JsonFactory.newObjectBuilder();
        if (null != lifecycle) {
            jsonObjectBuilder.set(JsonFields.LIFECYCLE, lifecycle.name(), predicate);
        }
        if (null != revision) {
            jsonObjectBuilder.set(JsonFields.REVISION, revision.toLong(), predicate);
        }
        if (null != modified) {
            jsonObjectBuilder.set(JsonFields.MODIFIED, modified.toString(), predicate);
        }
        if (null != created) {
            jsonObjectBuilder.set(JsonFields.CREATED, created.toString(), predicate);
        }
        if (null != policyId) {
            jsonObjectBuilder.set(JsonFields.NAMESPACE, namespace, predicate);
            jsonObjectBuilder.set(JsonFields.ID, String.valueOf(policyId), predicate);
        }
        jsonObjectBuilder.set(JsonFields.ENTRIES, stream()
                .map(policyEntry -> JsonFactory.newObjectBuilder()
                        .set(policyEntry.getLabel().getJsonFieldDefinition(),
                                policyEntry.toJson(schemaVersion, thePredicate.and(FieldType.notHidden())),
                                predicate) // notice: only "not HIDDEN" sub-fields of PolicyEntry are included
                        .build())
                .collect(JsonCollectors.objectsToObject()), predicate);

        if (null != metadata) {
            jsonObjectBuilder.set(JsonFields.METADATA, metadata.toJson(schemaVersion, thePredicate), predicate);
        }
        return jsonObjectBuilder.build();
    }

    private Map<Label, PolicyEntry> copyEntries() {
        return new LinkedHashMap<>(entries);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ImmutablePolicy that = (ImmutablePolicy) o;
        return Objects.equals(policyId, that.policyId) &&
                Objects.equals(namespace, that.namespace) &&
                lifecycle == that.lifecycle &&
                Objects.equals(revision, that.revision) &&
                Objects.equals(modified, that.modified) &&
                Objects.equals(created, that.created) &&
                Objects.equals(metadata, that.metadata) &&
                Objects.equals(entries, that.entries);
    }

    @Override
    public int hashCode() {
        return Objects.hash(policyId, namespace, lifecycle, revision, modified, created, metadata, entries);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "policyId=" + policyId +
                ", namespace=" + namespace +
                ", lifecycle=" + lifecycle +
                ", revision=" + revision +
                ", modified=" + modified +
                ", created=" + created +
                ", metadata=" + metadata +
                ", entries=" + entries +
                "]";
    }

}
