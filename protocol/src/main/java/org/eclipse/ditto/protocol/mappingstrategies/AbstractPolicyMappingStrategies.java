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
package org.eclipse.ditto.protocol.mappingstrategies;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Collection;
import java.util.Map;

import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.policies.model.Label;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyEntry;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.PolicyImport;
import org.eclipse.ditto.policies.model.PolicyImports;
import org.eclipse.ditto.policies.model.Resource;
import org.eclipse.ditto.policies.model.ResourceKey;
import org.eclipse.ditto.policies.model.Resources;
import org.eclipse.ditto.policies.model.Subject;
import org.eclipse.ditto.policies.model.SubjectId;
import org.eclipse.ditto.policies.model.Subjects;
import org.eclipse.ditto.policies.model.signals.events.SubjectsDeletedPartially;
import org.eclipse.ditto.policies.model.signals.events.SubjectsModifiedPartially;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.JsonifiableMapper;
import org.eclipse.ditto.protocol.MessagePath;
import org.eclipse.ditto.protocol.TopicPath;

/**
 * Provides helper methods to map from {@link Adaptable}s to policy commands.
 *
 * @param <T> the type of the mapped signals
 */
abstract class AbstractPolicyMappingStrategies<T extends Jsonifiable.WithPredicate<JsonObject, JsonField>>
        extends AbstractMappingStrategies<T> {

    private static final int RESOURCES_PATH_LEVEL = 2;
    private static final int SUBJECT_PATH_LEVEL = 3;
    private static final int RESOURCE_PATH_LEVEL = 3;

    protected AbstractPolicyMappingStrategies(final Map<String, JsonifiableMapper<T>> mappingStrategies) {
        super(mappingStrategies);
    }

    /**
     * Policy id from topic path policy id.
     *
     * @param topicPath the topic path
     * @return the policy id
     */
    protected static PolicyId policyIdFromTopicPath(final TopicPath topicPath) {
        checkNotNull(topicPath, "topicPath");
        return PolicyId.of(topicPath.getNamespace(), topicPath.getEntityName());
    }

    protected static PolicyId policyIdFrom(final Adaptable adaptable) {
        final TopicPath topicPath = adaptable.getTopicPath();
        return PolicyId.of(topicPath.getNamespace(), topicPath.getEntityName());
    }

    /**
     * Policy from policy.
     *
     * @param adaptable the adaptable
     * @return the policy
     */
    protected static Policy policyFrom(final Adaptable adaptable) {
        final JsonObject value = getValueFromPayload(adaptable);
        return PoliciesModelFactory.newPolicy(value);
    }

    protected static JsonObject policyJsonFrom(final Adaptable adaptable) {
        return adaptable.getPayload()
                .getValue()
                .filter(JsonValue::isObject)
                .map(JsonValue::asObject)
                .orElseThrow(
                        () -> new NullPointerException("Payload value must be a non-null object."));
    }

    /**
     * Policy entry from policy entry.
     *
     * @param adaptable the adaptable
     * @return the policy entry
     */
    protected static PolicyEntry policyEntryFrom(final Adaptable adaptable) {
        final JsonObject value = getValueFromPayload(adaptable);
        return PoliciesModelFactory.newPolicyEntry(labelFrom(adaptable), value);
    }

    /**
     * Policy entries from iterable.
     *
     * @param adaptable the adaptable
     * @return the iterable
     */
    protected static Iterable<PolicyEntry> policyEntriesFrom(final Adaptable adaptable) {
        final JsonObject value = getValueFromPayload(adaptable);
        return PoliciesModelFactory.newPolicyEntries(value);
    }

    /**
     * Policy imports from policy imports.
     *
     * @param adaptable the adaptable
     * @return the policy imports
     * @since 3.1.0
     */
    protected static PolicyImports policyImportsFrom(final Adaptable adaptable) {
        final JsonObject value = getValueFromPayload(adaptable);
        return PoliciesModelFactory.newPolicyImports(value);
    }

    /**
     * Policy entry from policy entry.
     *
     * @param adaptable the adaptable
     * @return the policy entry
    * @since 3.1.0
     */
    protected static PolicyImport policyImportFrom(final Adaptable adaptable) {
        final JsonObject value = getValueFromPayload(adaptable);
        return PoliciesModelFactory.newPolicyImport(importedPolicyIdFrom(adaptable), value);
    }

    /**
     * Resource from resource.
     *
     * @param adaptable the adaptable
     * @return the resource
     */
    static Resource resourceFrom(final Adaptable adaptable) {
        return Resource.newInstance(entryResourceKeyFromPath(adaptable.getPayload().getPath()),
                getValueFromPayload(adaptable));
    }

    /**
     * Resources from resources.
     *
     * @param adaptable the adaptable
     * @return the resources
     */
    protected static Resources resourcesFrom(final Adaptable adaptable) {
        return PoliciesModelFactory.newResources(getValueFromPayload(adaptable));
    }

    /**
     * Label from label.
     *
     * @param adaptable the adaptable
     * @return the label
     */
    protected static Label labelFrom(final Adaptable adaptable) {
        final MessagePath path = adaptable.getPayload().getPath();
        return path.getRoot()
                .filter(entries -> Policy.JsonFields.ENTRIES.getPointer().equals(entries.asPointer()))
                .map(entries -> path.nextLevel())
                .flatMap(JsonPointer::getRoot)
                .map(JsonKey::toString)
                .map(Label::of)
                .orElseThrow(() -> JsonParseException.newBuilder().build());
    }

    /**
     * Imported PolicyId.
     *
     * @param adaptable the adaptable
     * @return the imported policyId.
    * @since 3.1.0
     */
    protected static PolicyId importedPolicyIdFrom(final Adaptable adaptable) {
        final MessagePath path = adaptable.getPayload().getPath();
        return path.getRoot()
                .filter(entries -> Policy.JsonFields.IMPORTS.getPointer().equals(entries.asPointer()))
                .map(entries -> path.nextLevel())
                .flatMap(JsonPointer::getRoot)
                .map(JsonKey::toString)
                .map(PolicyId::of)
                .orElseThrow(() -> JsonParseException.newBuilder().build());
    }

    /**
     * Entry resource key from path resource key.
     *
     * @param path the path
     * @return the resource key
     */
    protected static ResourceKey entryResourceKeyFromPath(final MessagePath path) {
        // expected: entries/<entry>/resources/<type:/path1/path2>
        return path.getRoot()
                .filter(entries -> Policy.JsonFields.ENTRIES.getPointer().equals(entries.asPointer()))
                .flatMap(entries -> path.get(RESOURCES_PATH_LEVEL))
                .filter(resources -> PolicyEntry.JsonFields.RESOURCES.getPointer().equals(resources.asPointer()))
                .flatMap(resources -> path.getSubPointer(RESOURCE_PATH_LEVEL))
                .map(PoliciesModelFactory::newResourceKey)
                .orElseThrow(() -> JsonParseException.newBuilder().build());
    }

    /**
     * Entry subject id from path subject id.
     *
     * @param path the path
     * @return the subject id
     */
    protected static SubjectId entrySubjectIdFromPath(final MessagePath path) {
        // expected: entries/<entry>/resources/<issuer:subject>
        return path.getRoot()
                .filter(entries -> Policy.JsonFields.ENTRIES.getPointer().equals(entries.asPointer()))
                .flatMap(entries -> path.get(RESOURCES_PATH_LEVEL))
                .filter(resources -> PolicyEntry.JsonFields.SUBJECTS.getPointer().equals(resources.asPointer()))
                .flatMap(resources -> path.getSubPointer(SUBJECT_PATH_LEVEL))
                .map(AbstractPolicyMappingStrategies::stripLeadingSlash)
                .map(PoliciesModelFactory::newSubjectId)
                .orElseThrow(() -> JsonParseException.newBuilder().build());
    }

    /**
     * Subject from subject.
     *
     * @param adaptable the adaptable
     * @return the subject
     */
    protected static Subject subjectFrom(final Adaptable adaptable) {
        final SubjectId subjectIssuerWithId = entrySubjectIdFromPath(adaptable.getPayload().getPath());
        final JsonObject value = getValueFromPayload(adaptable);
        return PoliciesModelFactory.newSubject(subjectIssuerWithId, value);
    }

    /**
     * Subjects from subjects.
     *
     * @param adaptable the adaptable
     * @return the subjects
     */
    protected static Subjects subjectsFrom(final Adaptable adaptable) {
        final JsonObject value = getValueFromPayload(adaptable);
        return PoliciesModelFactory.newSubjects(value);
    }

    /**
     * Subjects that are modified indexed by their policy entry labels.
     *
     * @param adaptable the adaptable
     * @return the subjects
     */
    protected static Map<Label, Collection<Subject>> activatedSubjectsFrom(final Adaptable adaptable) {
        final JsonObject value = getValueFromPayload(adaptable);
        return SubjectsModifiedPartially.modifiedSubjectsFromJson(
                value.getValueOrThrow(SubjectsModifiedPartially.JSON_MODIFIED_SUBJECTS)
        );
    }

    /**
     * Subjects that are modified indexed by their policy entry labels.
     *
     * @param adaptable the adaptable
     * @return the subjects
     */
    protected static Map<Label, Collection<SubjectId>> deletedSubjectIdsFrom(final Adaptable adaptable) {
        final JsonObject value = getValueFromPayload(adaptable);
        return SubjectsDeletedPartially.deletedSubjectsFromJson(
                value.getValueOrThrow(SubjectsDeletedPartially.JSON_DELETED_SUBJECT_IDS)
        );
    }

    /**
     * @throws NullPointerException if the value is null.
     */
    protected static JsonObject getValueFromPayload(final Adaptable adaptable) {
        final JsonValue value = adaptable.getPayload().getValue()
                .filter(JsonValue::isObject)
                .orElseThrow(() -> new NullPointerException("Payload value must be a non-null object."));
        return value.asObject();
    }

    private static CharSequence stripLeadingSlash(final CharSequence charSequence) {
        if (charSequence.length() == 0 || charSequence.charAt(0) != '/') {
            return charSequence;
        } else {
            return charSequence.subSequence(1, charSequence.length());
        }
    }

}
