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
package org.eclipse.ditto.signals.events.policies.examplejson;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.base.json.Jsonifiable;
import org.eclipse.ditto.model.policies.EffectedPermissions;
import org.eclipse.ditto.model.policies.Label;
import org.eclipse.ditto.model.policies.PoliciesModelFactory;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.policies.PolicyEntry;
import org.eclipse.ditto.model.policies.PolicyRevision;
import org.eclipse.ditto.model.policies.Resource;
import org.eclipse.ditto.model.policies.ResourceKey;
import org.eclipse.ditto.model.policies.Resources;
import org.eclipse.ditto.model.policies.Subject;
import org.eclipse.ditto.model.policies.SubjectId;
import org.eclipse.ditto.model.policies.SubjectIssuer;
import org.eclipse.ditto.model.policies.SubjectType;
import org.eclipse.ditto.model.policies.Subjects;
import org.eclipse.ditto.signals.events.policies.PolicyCreated;
import org.eclipse.ditto.signals.events.policies.PolicyDeleted;
import org.eclipse.ditto.signals.events.policies.PolicyEntriesModified;
import org.eclipse.ditto.signals.events.policies.PolicyEntryCreated;
import org.eclipse.ditto.signals.events.policies.PolicyEntryDeleted;
import org.eclipse.ditto.signals.events.policies.PolicyEntryModified;
import org.eclipse.ditto.signals.events.policies.PolicyModified;
import org.eclipse.ditto.signals.events.policies.ResourceCreated;
import org.eclipse.ditto.signals.events.policies.ResourceDeleted;
import org.eclipse.ditto.signals.events.policies.ResourceModified;
import org.eclipse.ditto.signals.events.policies.ResourcesModified;
import org.eclipse.ditto.signals.events.policies.SubjectCreated;
import org.eclipse.ditto.signals.events.policies.SubjectDeleted;
import org.eclipse.ditto.signals.events.policies.SubjectModified;
import org.eclipse.ditto.signals.events.policies.SubjectsModified;


public class JsonExamplesProducer {

    private static final String NAMESPACE = "com.acme";

    /*
     * Policy
     */
    private static final String POLICY_ID = NAMESPACE + ":the_policy_id";
    private static final Label LABEL = PoliciesModelFactory.newLabel("the_label");
    private static final SubjectId SUBJECT_ID =
            PoliciesModelFactory.newSubjectId(SubjectIssuer.GOOGLE, "the_subjectid");
    private static final Subject SUBJECT =
            PoliciesModelFactory.newSubject(SUBJECT_ID, SubjectType.newInstance("yourSubjectTypeDescription"));
    private static final Subjects SUBJECTS = PoliciesModelFactory.newSubjects(SUBJECT);
    private static final EffectedPermissions EFFECTED_PERMISSIONS = PoliciesModelFactory
            .newEffectedPermissions(Arrays.asList("READ", "WRITE"), PoliciesModelFactory.noPermissions());
    private static final String RESOURCE_TYPE = "thing";
    private static final JsonPointer RESOURCE_PATH = JsonFactory.newPointer("/the_resource_path");
    private static final ResourceKey RESOURCE_KEY = ResourceKey.newInstance(RESOURCE_TYPE, RESOURCE_PATH);
    private static final Resource RESOURCE =
            PoliciesModelFactory.newResource(RESOURCE_TYPE, RESOURCE_PATH, EFFECTED_PERMISSIONS);
    private static final Resources RESOURCES = PoliciesModelFactory.newResources(RESOURCE);
    private static final PolicyEntry POLICY_ENTRY = PoliciesModelFactory.newPolicyEntry(LABEL, SUBJECTS, RESOURCES);
    private static final Iterable<PolicyEntry> POLICY_ENTRIES =
            new HashSet<>(Arrays.asList(PoliciesModelFactory.newPolicyEntry(LABEL, SUBJECTS, RESOURCES),
                    PoliciesModelFactory.newPolicyEntry(Label.of("another_label"), SUBJECTS, RESOURCES)));
    private static final long REVISION_NUMBER = 1;
    private static final PolicyRevision REVISION = PoliciesModelFactory.newPolicyRevision(REVISION_NUMBER);
    private static final Policy POLICY = PoliciesModelFactory.newPolicyBuilder(POLICY_ID)
            .set(POLICY_ENTRY)
            .setRevision(REVISION)
            .build();

    private static final DittoHeaders DITTO_HEADERS = DittoHeaders.empty();

    public static void main(final String... args) throws IOException {
        run(args, new JsonExamplesProducer());
    }

    private static void run(final String[] args, final JsonExamplesProducer producer) throws
            IOException {
        if (args.length != 1) {
            System.err.println("Exactly 1 argument required: the target folder in which to generate the JSON files");
            System.exit(-1);
        }
        producer.produce(Paths.get(args[0]));
    }

    private void produce(final Path rootPath) throws IOException {
        producePolicyEvents(rootPath.resolve("policies"));
    }

    private void producePolicyEvents(final Path rootPath) throws IOException {
        final Path eventsDir = rootPath.resolve(Paths.get("events"));
        Files.createDirectories(eventsDir);

        final PolicyCreated policyCreated = PolicyCreated.of(POLICY, REVISION_NUMBER, DITTO_HEADERS);
        writeJson(eventsDir.resolve(Paths.get("policyCreated.json")), policyCreated);

        final PolicyModified policyModified = PolicyModified.of(POLICY, REVISION_NUMBER, DITTO_HEADERS);
        writeJson(eventsDir.resolve(Paths.get("policyModified.json")), policyModified);

        final PolicyDeleted policyDeleted = PolicyDeleted.of(POLICY_ID, REVISION_NUMBER, DITTO_HEADERS);
        writeJson(eventsDir.resolve(Paths.get("policyDeleted.json")), policyDeleted);

        final PolicyEntriesModified policyEntriesModified =
                PolicyEntriesModified.of(POLICY_ID, POLICY_ENTRIES, REVISION_NUMBER, DITTO_HEADERS);
        writeJson(eventsDir.resolve(Paths.get("policyEntriesModified.json")), policyEntriesModified);

        final PolicyEntryCreated policyEntryCreated =
                PolicyEntryCreated.of(POLICY_ID, POLICY_ENTRY, REVISION_NUMBER, DITTO_HEADERS);
        writeJson(eventsDir.resolve(Paths.get("policyEntryCreated.json")), policyEntryCreated);

        final PolicyEntryModified policyEntryModified =
                PolicyEntryModified.of(POLICY_ID, POLICY_ENTRY, REVISION_NUMBER, DITTO_HEADERS);
        writeJson(eventsDir.resolve(Paths.get("policyEntryModified.json")), policyEntryModified);

        final PolicyEntryDeleted policyEntryDeleted =
                PolicyEntryDeleted.of(POLICY_ID, LABEL, REVISION_NUMBER, DITTO_HEADERS);
        writeJson(eventsDir.resolve(Paths.get("policyEntryDeleted.json")), policyEntryDeleted);

        final SubjectsModified subjectsModified =
                SubjectsModified.of(POLICY_ID, LABEL, SUBJECTS, REVISION_NUMBER, DITTO_HEADERS);
        writeJson(eventsDir.resolve(Paths.get("subjectsModified.json")), subjectsModified);

        final SubjectCreated subjectCreated =
                SubjectCreated.of(POLICY_ID, LABEL, SUBJECT, REVISION_NUMBER, DITTO_HEADERS);
        writeJson(eventsDir.resolve(Paths.get("subjectCreated.json")), subjectCreated);

        final SubjectModified subjectModified =
                SubjectModified.of(POLICY_ID, LABEL, SUBJECT, REVISION_NUMBER, DITTO_HEADERS);
        writeJson(eventsDir.resolve(Paths.get("subjectModified.json")), subjectModified);

        final SubjectDeleted subjectDeleted =
                SubjectDeleted.of(POLICY_ID, LABEL, SUBJECT_ID, REVISION_NUMBER, DITTO_HEADERS);
        writeJson(eventsDir.resolve(Paths.get("subjectDeleted.json")), subjectDeleted);

        final ResourcesModified resourcesModified =
                ResourcesModified.of(POLICY_ID, LABEL, RESOURCES, REVISION_NUMBER, DITTO_HEADERS);
        writeJson(eventsDir.resolve(Paths.get("resourcesModified.json")), resourcesModified);

        final ResourceCreated resourceCreated =
                ResourceCreated.of(POLICY_ID, LABEL, RESOURCE, REVISION_NUMBER, DITTO_HEADERS);
        writeJson(eventsDir.resolve(Paths.get("resourceCreated.json")), resourceCreated);

        final ResourceModified resourceModified =
                ResourceModified.of(POLICY_ID, LABEL, RESOURCE, REVISION_NUMBER, DITTO_HEADERS);
        writeJson(eventsDir.resolve(Paths.get("resourceModified.json")), resourceModified);

        final ResourceDeleted resourceDeleted =
                ResourceDeleted.of(POLICY_ID, LABEL, RESOURCE_KEY, REVISION_NUMBER, DITTO_HEADERS);
        writeJson(eventsDir.resolve(Paths.get("resourceDeleted.json")), resourceDeleted);
    }

    private void writeJson(final Path path, final Jsonifiable.WithPredicate<JsonObject, JsonField> jsonifiable)
            throws IOException {
        writeJson(path, jsonifiable, JsonSchemaVersion.LATEST);
    }

    private void writeJson(final Path path, final Jsonifiable.WithPredicate<JsonObject, JsonField> jsonifiable,
            final JsonSchemaVersion schemaVersion) throws IOException {
        final String jsonString = jsonifiable.toJsonString(schemaVersion);
        System.out.println("Writing file: " + path.toAbsolutePath());
        Files.write(path, jsonString.getBytes());
    }
}
