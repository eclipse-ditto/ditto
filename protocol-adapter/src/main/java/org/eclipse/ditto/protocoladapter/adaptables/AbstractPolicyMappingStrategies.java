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
package org.eclipse.ditto.protocoladapter.adaptables;

import java.util.Map;
import java.util.Optional;

import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.json.Jsonifiable;
import org.eclipse.ditto.model.policies.Label;
import org.eclipse.ditto.model.policies.PoliciesModelFactory;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.policies.PolicyEntry;
import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.model.policies.Resource;
import org.eclipse.ditto.model.policies.ResourceKey;
import org.eclipse.ditto.model.policies.Resources;
import org.eclipse.ditto.model.policies.Subject;
import org.eclipse.ditto.model.policies.SubjectId;
import org.eclipse.ditto.model.policies.Subjects;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.JsonifiableMapper;
import org.eclipse.ditto.protocoladapter.MessagePath;
import org.eclipse.ditto.protocoladapter.TopicPath;

/**
 * Provides helper methods to map from {@link Adaptable}s to policy commands.
 *
 * @param <T> the type of the mapped signals
 */
abstract class AbstractPolicyMappingStrategies<T extends Jsonifiable.WithPredicate<JsonObject, JsonField>>
        extends AbstractMappingStrategies<T> {

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
        return Optional.ofNullable(topicPath)
                .map(tp -> PolicyId.of(topicPath.getNamespace(), topicPath.getId()))
                .orElseThrow(() -> JsonParseException.newBuilder().build());
    }

    /**
     * Policy from policy.
     *
     * @param adaptable the adaptable
     * @return the policy
     */
    protected static Policy policyFrom(final Adaptable adaptable) {
        return adaptable.getPayload().getValue()
                .map(JsonValue::asObject)
                .map(PoliciesModelFactory::newPolicy)
                .orElseThrow(() -> JsonParseException.newBuilder().build());
    }

    /**
     * Policy entry from policy entry.
     *
     * @param adaptable the adaptable
     * @return the policy entry
     */
    protected static PolicyEntry policyEntryFrom(final Adaptable adaptable) {
        return adaptable.getPayload().getValue()
                .map(JsonValue::asObject)
                .map(entry -> PoliciesModelFactory.newPolicyEntry(labelFrom(adaptable), entry))
                .orElseThrow(() -> JsonParseException.newBuilder().build());
    }

    /**
     * Policy entries from iterable.
     *
     * @param adaptable the adaptable
     * @return the iterable
     */
    protected static Iterable<PolicyEntry> policyEntriesFrom(final Adaptable adaptable) {
        return adaptable.getPayload().getValue()
                .map(JsonValue::asObject)
                .map(PoliciesModelFactory::newPolicyEntries)
                .orElseThrow(() -> JsonParseException.newBuilder().build());
    }

    /**
     * Resource from resource.
     *
     * @param adaptable the adaptable
     * @return the resource
     */
    static Resource resourceFrom(final Adaptable adaptable) {
        return Resource.newInstance(entryResourceKeyFromPath(adaptable.getPayload().getPath()),
                adaptable.getPayload().getValue().orElseThrow(() -> JsonParseException.newBuilder().build()));
    }

    /**
     * Resources from resources.
     *
     * @param adaptable the adaptable
     * @return the resources
     */
    protected static Resources resourcesFrom(final Adaptable adaptable) {
        return PoliciesModelFactory.newResources(
                adaptable.getPayload()
                        .getValue().map(JsonValue::asObject)
                        .orElseThrow(() -> JsonParseException.newBuilder().build())
        );
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
     * Entry resource key from path resource key.
     *
     * @param path the path
     * @return the resource key
     */
    protected static ResourceKey entryResourceKeyFromPath(final MessagePath path) {
        // expected: entries/<entry>/resources/<type:/path1/path2>
        return path.getRoot()
                .filter(entries -> Policy.JsonFields.ENTRIES.getPointer().equals(entries.asPointer()))
                .flatMap(entries -> path.get(2))
                .filter(resources -> PolicyEntry.JsonFields.RESOURCES.getPointer().equals(resources.asPointer()))
                .flatMap(resources -> path.getSubPointer(3))
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
                .flatMap(entries -> path.get(2))
                .filter(resources -> PolicyEntry.JsonFields.SUBJECTS.getPointer().equals(resources.asPointer()))
                .flatMap(resources -> path.get(3))
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
        return adaptable.getPayload()
                .getValue()
                .map(JsonValue::asObject)
                .map(subject -> PoliciesModelFactory.newSubject(subjectIssuerWithId, subject))
                .orElseThrow(() -> JsonParseException.newBuilder().build());
    }

    /**
     * Subjects from subjects.
     *
     * @param adaptable the adaptable
     * @return the subjects
     */
    protected static Subjects subjectsFrom(final Adaptable adaptable) {
        return adaptable.getPayload()
                .getValue()
                .map(JsonValue::asObject)
                .map(PoliciesModelFactory::newSubjects)
                .orElseThrow(() -> JsonParseException.newBuilder().build());
    }

}
