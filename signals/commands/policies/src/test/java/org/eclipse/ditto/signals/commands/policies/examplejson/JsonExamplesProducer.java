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
package org.eclipse.ditto.signals.commands.policies.examplejson;

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
import org.eclipse.ditto.model.policies.PolicyEntryInvalidException;
import org.eclipse.ditto.model.policies.PolicyIdInvalidException;
import org.eclipse.ditto.model.policies.PolicyRevision;
import org.eclipse.ditto.model.policies.Resource;
import org.eclipse.ditto.model.policies.ResourceKey;
import org.eclipse.ditto.model.policies.Resources;
import org.eclipse.ditto.model.policies.Subject;
import org.eclipse.ditto.model.policies.SubjectId;
import org.eclipse.ditto.model.policies.SubjectIdInvalidException;
import org.eclipse.ditto.model.policies.SubjectIssuer;
import org.eclipse.ditto.model.policies.SubjectType;
import org.eclipse.ditto.model.policies.Subjects;
import org.eclipse.ditto.signals.commands.policies.exceptions.PolicyConflictException;
import org.eclipse.ditto.signals.commands.policies.exceptions.PolicyEntryModificationInvalidException;
import org.eclipse.ditto.signals.commands.policies.exceptions.PolicyEntryNotAccessibleException;
import org.eclipse.ditto.signals.commands.policies.exceptions.PolicyEntryNotModifiableException;
import org.eclipse.ditto.signals.commands.policies.exceptions.PolicyModificationInvalidException;
import org.eclipse.ditto.signals.commands.policies.exceptions.PolicyNotAccessibleException;
import org.eclipse.ditto.signals.commands.policies.exceptions.PolicyNotModifiableException;
import org.eclipse.ditto.signals.commands.policies.exceptions.PolicyTooManyModifyingRequestsException;
import org.eclipse.ditto.signals.commands.policies.exceptions.PolicyUnavailableException;
import org.eclipse.ditto.signals.commands.policies.exceptions.ResourceNotAccessibleException;
import org.eclipse.ditto.signals.commands.policies.exceptions.ResourceNotModifiableException;
import org.eclipse.ditto.signals.commands.policies.exceptions.ResourcesNotAccessibleException;
import org.eclipse.ditto.signals.commands.policies.exceptions.ResourcesNotModifiableException;
import org.eclipse.ditto.signals.commands.policies.exceptions.SubjectNotAccessibleException;
import org.eclipse.ditto.signals.commands.policies.exceptions.SubjectNotModifiableException;
import org.eclipse.ditto.signals.commands.policies.exceptions.SubjectsNotAccessibleException;
import org.eclipse.ditto.signals.commands.policies.exceptions.SubjectsNotModifiableException;
import org.eclipse.ditto.signals.commands.policies.modify.CreatePolicy;
import org.eclipse.ditto.signals.commands.policies.modify.CreatePolicyResponse;
import org.eclipse.ditto.signals.commands.policies.modify.DeletePolicy;
import org.eclipse.ditto.signals.commands.policies.modify.DeletePolicyEntry;
import org.eclipse.ditto.signals.commands.policies.modify.DeletePolicyEntryResponse;
import org.eclipse.ditto.signals.commands.policies.modify.DeletePolicyResponse;
import org.eclipse.ditto.signals.commands.policies.modify.DeleteResource;
import org.eclipse.ditto.signals.commands.policies.modify.DeleteResourceResponse;
import org.eclipse.ditto.signals.commands.policies.modify.DeleteSubject;
import org.eclipse.ditto.signals.commands.policies.modify.DeleteSubjectResponse;
import org.eclipse.ditto.signals.commands.policies.modify.ModifyPolicy;
import org.eclipse.ditto.signals.commands.policies.modify.ModifyPolicyEntries;
import org.eclipse.ditto.signals.commands.policies.modify.ModifyPolicyEntriesResponse;
import org.eclipse.ditto.signals.commands.policies.modify.ModifyPolicyEntry;
import org.eclipse.ditto.signals.commands.policies.modify.ModifyPolicyEntryResponse;
import org.eclipse.ditto.signals.commands.policies.modify.ModifyPolicyResponse;
import org.eclipse.ditto.signals.commands.policies.modify.ModifyResource;
import org.eclipse.ditto.signals.commands.policies.modify.ModifyResourceResponse;
import org.eclipse.ditto.signals.commands.policies.modify.ModifyResources;
import org.eclipse.ditto.signals.commands.policies.modify.ModifyResourcesResponse;
import org.eclipse.ditto.signals.commands.policies.modify.ModifySubject;
import org.eclipse.ditto.signals.commands.policies.modify.ModifySubjectResponse;
import org.eclipse.ditto.signals.commands.policies.modify.ModifySubjects;
import org.eclipse.ditto.signals.commands.policies.modify.ModifySubjectsResponse;
import org.eclipse.ditto.signals.commands.policies.query.RetrievePolicy;
import org.eclipse.ditto.signals.commands.policies.query.RetrievePolicyEntries;
import org.eclipse.ditto.signals.commands.policies.query.RetrievePolicyEntriesResponse;
import org.eclipse.ditto.signals.commands.policies.query.RetrievePolicyEntry;
import org.eclipse.ditto.signals.commands.policies.query.RetrievePolicyEntryResponse;
import org.eclipse.ditto.signals.commands.policies.query.RetrievePolicyResponse;
import org.eclipse.ditto.signals.commands.policies.query.RetrieveResource;
import org.eclipse.ditto.signals.commands.policies.query.RetrieveResourceResponse;
import org.eclipse.ditto.signals.commands.policies.query.RetrieveResources;
import org.eclipse.ditto.signals.commands.policies.query.RetrieveResourcesResponse;
import org.eclipse.ditto.signals.commands.policies.query.RetrieveSubject;
import org.eclipse.ditto.signals.commands.policies.query.RetrieveSubjectResponse;
import org.eclipse.ditto.signals.commands.policies.query.RetrieveSubjects;
import org.eclipse.ditto.signals.commands.policies.query.RetrieveSubjectsResponse;


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
        producePolicyCommands(rootPath.resolve("policies"));
        producePolicyExceptions(rootPath.resolve("policies"));
    }

    private void producePolicyCommands(final Path rootPath) throws IOException {
        producePolicyQueryCommands(rootPath);
        producePolicyModifyCommands(rootPath);
        producePolicyQueryResponse(rootPath);
        producePolicyModifyResponse(rootPath);
    }

    private void producePolicyQueryCommands(final Path rootPath) throws IOException {
        final Path commandsDir = rootPath.resolve(Paths.get("commands", "query"));
        Files.createDirectories(commandsDir);

        final RetrievePolicy retrievePolicy = RetrievePolicy.of(POLICY_ID, DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("retrievePolicy.json")), retrievePolicy);

        final RetrievePolicyEntries retrievePolicyEntries = RetrievePolicyEntries.of(POLICY_ID, DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("retrievePolicyEntries.json")), retrievePolicyEntries);

        final RetrievePolicyEntry retrievePolicyEntry = RetrievePolicyEntry.of(POLICY_ID, LABEL, DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("retrievePolicyEntry.json")), retrievePolicyEntry);

        final RetrieveSubjects retrieveSubjects = RetrieveSubjects.of(POLICY_ID, LABEL, DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("retrieveSubjects.json")), retrieveSubjects);

        final RetrieveSubject retrieveSubject =
                RetrieveSubject.of(POLICY_ID, LABEL, SUBJECT_ID, DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("retrieveSubject.json")), retrieveSubject);

        final RetrieveResources retrieveResources = RetrieveResources.of(POLICY_ID, LABEL, DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("retrieveResources.json")), retrieveResources);

        final RetrieveResource retrieveResource = RetrieveResource.of(POLICY_ID, LABEL, RESOURCE.getResourceKey(),
                DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("retrieveResource.json")), retrieveResource);
    }

    private void producePolicyQueryResponse(final Path rootPath) throws IOException {
        final Path commandsDir = rootPath.resolve(Paths.get("commands", "query"));
        Files.createDirectories(commandsDir);

        final RetrievePolicyResponse retrievePolicyResponse =
                RetrievePolicyResponse.of(POLICY_ID, POLICY, DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("retrievePolicyResponse.json")), retrievePolicyResponse);

        final RetrievePolicyEntriesResponse retrievePolicyEntriesResponse =
                RetrievePolicyEntriesResponse.of(POLICY_ID, POLICY_ENTRIES, DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("retrievePolicyEntriesResponse.json")), retrievePolicyEntriesResponse);

        final RetrievePolicyEntryResponse retrievePolicyEntryResponse =
                RetrievePolicyEntryResponse.of(POLICY_ID, POLICY_ENTRY,
                DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("retrievePolicyEntryResponse.json")), retrievePolicyEntryResponse);

        final RetrieveSubjectsResponse retrieveSubjectsResponse =
                RetrieveSubjectsResponse.of(POLICY_ID, LABEL, SUBJECTS,
                DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("retrieveSubjectsResponse.json")), retrieveSubjectsResponse);

        final RetrieveSubjectResponse retrieveSubjectResponse = RetrieveSubjectResponse.of(POLICY_ID, LABEL, SUBJECT,
                DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("retrieveSubjectResponse.json")), retrieveSubjectResponse);

        final RetrieveResourcesResponse retrieveResourcesResponse =
                RetrieveResourcesResponse.of(POLICY_ID, LABEL, RESOURCES,
                DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("retrieveResourcesResponse.json")), retrieveResourcesResponse);

        final RetrieveResourceResponse retrieveResourceResponse =
                RetrieveResourceResponse.of(POLICY_ID, LABEL, RESOURCE,
                DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("retrieveResourceResponse.json")), retrieveResourceResponse);
    }

    private void producePolicyModifyCommands(final Path rootPath) throws IOException {
        final Path commandsDir = rootPath.resolve(Paths.get("commands", "modify"));
        Files.createDirectories(commandsDir);

        final CreatePolicy createPolicy = CreatePolicy.of(POLICY, DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("createPolicy.json")), createPolicy);

        final ModifyPolicy modifyPolicy = ModifyPolicy.of(POLICY_ID, POLICY, DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("modifyPolicy.json")), modifyPolicy);

        final DeletePolicy deletePolicy = DeletePolicy.of(POLICY_ID, DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("deletePolicy.json")), deletePolicy);

        final ModifyPolicyEntries modifyPolicyEntries = ModifyPolicyEntries.of(POLICY_ID, POLICY_ENTRIES,
                DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("modifyPolicyEntries.json")), modifyPolicyEntries);

        final ModifyPolicyEntry modifyPolicyEntry = ModifyPolicyEntry.of(POLICY_ID, POLICY_ENTRY, DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("modifyPolicyEntry.json")), modifyPolicyEntry);

        final DeletePolicyEntry deletePolicyEntry = DeletePolicyEntry.of(POLICY_ID, LABEL, DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("deletePolicyEntry.json")), deletePolicyEntry);

        final ModifySubjects modifySubjects = ModifySubjects.of(POLICY_ID, LABEL, SUBJECTS, DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("modifySubjects.json")), modifySubjects);

        final ModifySubject modifySubject = ModifySubject.of(POLICY_ID, LABEL, SUBJECT, DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("modifySubject.json")), modifySubject);

        final DeleteSubject deleteSubject = DeleteSubject.of(POLICY_ID, LABEL, SUBJECT_ID, DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("deleteSubject.json")), deleteSubject);

        final ModifyResources modifyResources = ModifyResources.of(POLICY_ID, LABEL, RESOURCES, DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("modifyResources.json")), modifyResources);

        final ModifyResource modifyResource = ModifyResource.of(POLICY_ID, LABEL, RESOURCE, DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("modifyResource.json")), modifyResource);

        final DeleteResource deleteResource = DeleteResource.of(POLICY_ID, LABEL, RESOURCE_KEY, DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("deleteResource.json")), deleteResource);
    }

    private void producePolicyModifyResponse(final Path rootPath) throws IOException {
        final Path commandsDir = rootPath.resolve(Paths.get("commands", "modify"));
        Files.createDirectories(commandsDir);

        final CreatePolicyResponse createPolicyResponse = CreatePolicyResponse.of(POLICY_ID, POLICY, DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("createPolicyResponse.json")), createPolicyResponse);

        final ModifyPolicyResponse modifyPolicyResponse = ModifyPolicyResponse.modified(POLICY_ID, DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("modifyPolicyResponse.json")), modifyPolicyResponse);

        final ModifyPolicyResponse modifyPolicyResponseCreated =
                ModifyPolicyResponse.created(POLICY_ID, POLICY, DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("modifyPolicyResponseCreated.json")), modifyPolicyResponseCreated);

        final DeletePolicyResponse deletePolicyResponse = DeletePolicyResponse.of(POLICY_ID, DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("deletePolicyResponse.json")), deletePolicyResponse);

        final ModifyPolicyEntriesResponse modifyPolicyEntriesResponse =
                ModifyPolicyEntriesResponse.of(POLICY_ID, DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("modifyPolicyEntriesResponse.json")), modifyPolicyEntriesResponse);

        final ModifyPolicyEntryResponse modifyPolicyEntryResponse =
                ModifyPolicyEntryResponse.modified(POLICY_ID, DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("modifyPolicyEntryResponse.json")), modifyPolicyEntryResponse);

        final ModifyPolicyEntryResponse modifyPolicyEntryResponseCreated =
                ModifyPolicyEntryResponse.created(POLICY_ID, POLICY_ENTRY, DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("modifyPolicyEntryResponseCreated.json")),
                modifyPolicyEntryResponseCreated);

        final DeletePolicyEntryResponse deletePolicyEntryResponse = DeletePolicyEntryResponse.of(POLICY_ID, LABEL,
                DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("deletePolicyEntryResponse.json")), deletePolicyEntryResponse);

        final ModifySubjectsResponse modifySubjectsResponse =
                ModifySubjectsResponse.of(POLICY_ID, LABEL, DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("modifySubjectsResponse.json")), modifySubjectsResponse);

        final ModifySubjectResponse modifySubjectResponse =
                ModifySubjectResponse.modified(POLICY_ID, LABEL, DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("modifySubjectResponse.json")), modifySubjectResponse);

        final ModifySubjectResponse modifySubjectResponseCreated =
                ModifySubjectResponse.created(POLICY_ID, LABEL, SUBJECT,
                DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("modifySubjectResponseCreated.json")), modifySubjectResponseCreated);

        final DeleteSubjectResponse deleteSubjectResponse = DeleteSubjectResponse.of(POLICY_ID, LABEL, SUBJECT_ID,
                DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("deleteSubjectResponse.json")), deleteSubjectResponse);

        final ModifyResourcesResponse modifyResourcesResponse =
                ModifyResourcesResponse.of(POLICY_ID, LABEL, DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("modifyResourcesResponse.json")), modifyResourcesResponse);

        final ModifyResourceResponse modifyResourceResponse =
                ModifyResourceResponse.modified(POLICY_ID, LABEL, DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("modifyResourceResponse.json")), modifyResourceResponse);

        final ModifyResourceResponse modifyResourceResponseCreated =
                ModifyResourceResponse.created(POLICY_ID, LABEL, RESOURCE,
                DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("modifyResourceResponseCreated.json")), modifyResourceResponseCreated);

        final DeleteResourceResponse deleteResourceResponse = DeleteResourceResponse.of(POLICY_ID, LABEL, RESOURCE_KEY,
                DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("deleteResourceResponse.json")), deleteResourceResponse);
    }

    private void producePolicyExceptions(final Path rootPath) throws IOException {
        final Path exceptionsDir = rootPath.resolve(Paths.get("errors"));
        Files.createDirectories(exceptionsDir);

        final PolicyConflictException policyConflictException = PolicyConflictException.newBuilder(POLICY_ID).build();
        writeJson(exceptionsDir.resolve(Paths.get("policyConflictException.json")), policyConflictException);

        final PolicyIdInvalidException policyIdInvalidException =
                PolicyIdInvalidException.newBuilder("invalid id").build();
        writeJson(exceptionsDir.resolve(Paths.get("policyIdInvalidException.json")), policyIdInvalidException);

        final PolicyEntryInvalidException policyEntryInvalidException =
                PolicyEntryInvalidException.newBuilder().build();
        writeJson(exceptionsDir.resolve(Paths.get("policyEntryInvalidException.json")), policyEntryInvalidException);

        final SubjectIdInvalidException subjectIdInvalidException =
                SubjectIdInvalidException.newBuilder("invalid subject").build();
        writeJson(exceptionsDir.resolve(Paths.get("subjectIdInvalidException.json")), subjectIdInvalidException);

        final PolicyNotAccessibleException policyNotAccessibleException =
                PolicyNotAccessibleException.newBuilder(POLICY_ID).build();
        writeJson(exceptionsDir.resolve(Paths.get("policyNotAccessibleException.json")), policyNotAccessibleException);

        final PolicyNotModifiableException policyNotModifiableException =
                PolicyNotModifiableException.newBuilder(POLICY_ID).build();
        writeJson(exceptionsDir.resolve(Paths.get("policyNotModifiableException.json")), policyNotModifiableException);

        final PolicyEntryNotAccessibleException policyEntryNotAccessibleException =
                PolicyEntryNotAccessibleException.newBuilder(POLICY_ID, LABEL.toString()).build();
        writeJson(exceptionsDir.resolve(Paths.get("policyEntryNotAccessibleException.json")),
                policyEntryNotAccessibleException);

        final PolicyEntryNotModifiableException policyEntryNotModifiableException =
                PolicyEntryNotModifiableException.newBuilder(POLICY_ID, LABEL.toString()).build();
        writeJson(exceptionsDir.resolve(Paths.get("policyEntryNotModifiableException.json")),
                policyEntryNotModifiableException);

        final ResourcesNotModifiableException resourcesNotModifiableException =
                ResourcesNotModifiableException.newBuilder(POLICY_ID, LABEL.toString()).build();
        writeJson(exceptionsDir.resolve(Paths.get("resourcesNotModifiableException.json")),
                resourcesNotModifiableException);

        final ResourcesNotAccessibleException resourcesNotAccessibleException =
                ResourcesNotAccessibleException.newBuilder(POLICY_ID, LABEL.toString()).build();
        writeJson(exceptionsDir.resolve(Paths.get("resourcesNotAccessibleException.json")),
                resourcesNotAccessibleException);

        final ResourceNotAccessibleException resourceNotAccessibleException =
                ResourceNotAccessibleException.newBuilder(POLICY_ID, LABEL.toString(), RESOURCE_PATH.toString())
                        .build();
        writeJson(exceptionsDir.resolve(Paths.get("resourceNotAccessibleException.json")),
                resourceNotAccessibleException);

        final ResourceNotModifiableException resourceNotModifiableException =
                ResourceNotModifiableException.newBuilder(POLICY_ID, LABEL.toString(), RESOURCE_PATH.toString())
                        .build();
        writeJson(exceptionsDir.resolve(Paths.get("resourceNotModifiableException.json")),
                resourceNotModifiableException);

        final SubjectsNotModifiableException subjectsNotModifiableException =
                SubjectsNotModifiableException.newBuilder(POLICY_ID, LABEL.toString()).build();
        writeJson(exceptionsDir.resolve(Paths.get("subjectsNotModifiableException.json")),
                subjectsNotModifiableException);

        final SubjectsNotAccessibleException subjectsNotAccessibleException =
                SubjectsNotAccessibleException.newBuilder(POLICY_ID, LABEL.toString()).build();
        writeJson(exceptionsDir.resolve(Paths.get("subjectsNotAccessibleException.json")),
                subjectsNotAccessibleException);

        final SubjectNotAccessibleException subjectNotAccessibleException =
                SubjectNotAccessibleException.newBuilder(POLICY_ID, LABEL.toString(), SUBJECT_ID.toString()).build();
        writeJson(exceptionsDir.resolve(Paths.get("subjectNotAccessibleException.json")),
                subjectNotAccessibleException);

        final SubjectNotModifiableException subjectNotModifiableException =
                SubjectNotModifiableException.newBuilder(POLICY_ID, LABEL.toString(), SUBJECT_ID.toString()).build();
        writeJson(exceptionsDir.resolve(Paths.get("subjectNotModifiableException.json")),
                subjectNotModifiableException);

        final PolicyTooManyModifyingRequestsException policyTooManyModifyingRequestsException =
                PolicyTooManyModifyingRequestsException.newBuilder(POLICY_ID)
                        .dittoHeaders(DITTO_HEADERS)
                        .build();
        writeJson(exceptionsDir.resolve(Paths.get("policyTooManyModifyingRequestsException.json")),
                policyTooManyModifyingRequestsException);

        final PolicyUnavailableException policyUnavailableException =
                PolicyUnavailableException.newBuilder(POLICY_ID).dittoHeaders(DITTO_HEADERS).build();
        writeJson(exceptionsDir.resolve(Paths.get("policyUnavailableException.json")), policyUnavailableException);

        final PolicyModificationInvalidException policyModificationInvalidException =
                PolicyModificationInvalidException.newBuilder(POLICY_ID)
                        .dittoHeaders(DITTO_HEADERS)
                        .build();
        writeJson(exceptionsDir.resolve(Paths.get("policyModificationInvalidException.json")),
                policyModificationInvalidException);

        final PolicyEntryModificationInvalidException policyEntryModificationInvalidException =
                PolicyEntryModificationInvalidException.newBuilder(POLICY_ID, LABEL.toString())
                        .dittoHeaders(DITTO_HEADERS).build();
        writeJson(exceptionsDir.resolve(Paths.get("policyEntryModificationInvalidException.json")),
                policyEntryModificationInvalidException);
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
