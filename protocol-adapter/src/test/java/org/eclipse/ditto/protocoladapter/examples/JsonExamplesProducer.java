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
package org.eclipse.ditto.protocoladapter.examples;

import static org.eclipse.ditto.model.base.auth.AuthorizationModelFactory.newAuthSubject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonFieldSelectorInvalidException;
import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonParseOptions;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonPointerInvalidException;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.exceptions.DittoJsonException;
import org.eclipse.ditto.model.base.headers.DittoHeaderDefinition;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.base.json.Jsonifiable;
import org.eclipse.ditto.model.messages.MessageTimeoutException;
import org.eclipse.ditto.model.messages.SubjectInvalidException;
import org.eclipse.ditto.model.messages.TimeoutInvalidException;
import org.eclipse.ditto.model.policies.EffectedPermissions;
import org.eclipse.ditto.model.policies.Label;
import org.eclipse.ditto.model.policies.LabelInvalidException;
import org.eclipse.ditto.model.policies.PoliciesModelFactory;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.policies.PolicyEntry;
import org.eclipse.ditto.model.policies.PolicyEntryInvalidException;
import org.eclipse.ditto.model.policies.PolicyId;
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
import org.eclipse.ditto.model.things.AccessControlList;
import org.eclipse.ditto.model.things.AclEntry;
import org.eclipse.ditto.model.things.AclEntryInvalidException;
import org.eclipse.ditto.model.things.AclInvalidException;
import org.eclipse.ditto.model.things.AclNotAllowedException;
import org.eclipse.ditto.model.things.Attributes;
import org.eclipse.ditto.model.things.DefinitionIdentifierInvalidException;
import org.eclipse.ditto.model.things.Feature;
import org.eclipse.ditto.model.things.FeatureDefinition;
import org.eclipse.ditto.model.things.FeatureDefinitionEmptyException;
import org.eclipse.ditto.model.things.FeatureProperties;
import org.eclipse.ditto.model.things.Features;
import org.eclipse.ditto.model.things.Permission;
import org.eclipse.ditto.model.things.PolicyIdMissingException;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingDefinition;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.model.things.ThingIdInvalidException;
import org.eclipse.ditto.model.things.ThingLifecycle;
import org.eclipse.ditto.model.things.ThingRevision;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.model.thingsearch.SearchModelFactory;
import org.eclipse.ditto.model.thingsearch.SearchQuery;
import org.eclipse.ditto.model.thingsearch.SearchResult;
import org.eclipse.ditto.protocoladapter.JsonifiableAdaptable;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayAuthenticationFailedException;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayAuthenticationProviderUnavailableException;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayMethodNotAllowedException;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayServiceTimeoutException;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayServiceTooManyRequestsException;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayServiceUnavailableException;
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
import org.eclipse.ditto.signals.commands.things.ThingErrorResponse;
import org.eclipse.ditto.signals.commands.things.exceptions.AclModificationInvalidException;
import org.eclipse.ditto.signals.commands.things.exceptions.AclNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.exceptions.AclNotModifiableException;
import org.eclipse.ditto.signals.commands.things.exceptions.AttributeNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.exceptions.AttributeNotModifiableException;
import org.eclipse.ditto.signals.commands.things.exceptions.AttributesNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.exceptions.AttributesNotModifiableException;
import org.eclipse.ditto.signals.commands.things.exceptions.FeatureDefinitionNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.exceptions.FeatureDefinitionNotModifiableException;
import org.eclipse.ditto.signals.commands.things.exceptions.FeatureNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.exceptions.FeatureNotModifiableException;
import org.eclipse.ditto.signals.commands.things.exceptions.FeaturePropertiesNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.exceptions.FeaturePropertiesNotModifiableException;
import org.eclipse.ditto.signals.commands.things.exceptions.FeaturePropertyNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.exceptions.FeaturePropertyNotModifiableException;
import org.eclipse.ditto.signals.commands.things.exceptions.FeaturesNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.exceptions.FeaturesNotModifiableException;
import org.eclipse.ditto.signals.commands.things.exceptions.PolicyIdNotAllowedException;
import org.eclipse.ditto.signals.commands.things.exceptions.PolicyIdNotDeletableException;
import org.eclipse.ditto.signals.commands.things.exceptions.PolicyIdNotModifiableException;
import org.eclipse.ditto.signals.commands.things.exceptions.PolicyNotAllowedException;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingConflictException;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingIdNotDeletableException;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingIdNotExplicitlySettableException;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingNotCreatableException;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingNotDeletableException;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingNotModifiableException;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingTooManyModifyingRequestsException;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingUnavailableException;
import org.eclipse.ditto.signals.commands.things.modify.CreateThing;
import org.eclipse.ditto.signals.commands.things.modify.CreateThingResponse;
import org.eclipse.ditto.signals.commands.things.modify.DeleteAclEntry;
import org.eclipse.ditto.signals.commands.things.modify.DeleteAttribute;
import org.eclipse.ditto.signals.commands.things.modify.DeleteAttributeResponse;
import org.eclipse.ditto.signals.commands.things.modify.DeleteAttributes;
import org.eclipse.ditto.signals.commands.things.modify.DeleteAttributesResponse;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeature;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeatureDefinition;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeatureDefinitionResponse;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeatureProperties;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeaturePropertiesResponse;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeatureProperty;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeaturePropertyResponse;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeatureResponse;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeatures;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeaturesResponse;
import org.eclipse.ditto.signals.commands.things.modify.DeleteThing;
import org.eclipse.ditto.signals.commands.things.modify.DeleteThingDefinition;
import org.eclipse.ditto.signals.commands.things.modify.DeleteThingDefinitionResponse;
import org.eclipse.ditto.signals.commands.things.modify.DeleteThingResponse;
import org.eclipse.ditto.signals.commands.things.modify.MergeThing;
import org.eclipse.ditto.signals.commands.things.modify.MergeThingResponse;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAcl;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAclEntry;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAttribute;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAttributeResponse;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAttributes;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAttributesResponse;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeature;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeatureDefinition;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeatureDefinitionResponse;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeatureProperties;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeaturePropertiesResponse;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeatureProperty;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeaturePropertyResponse;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeatureResponse;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeatures;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeaturesResponse;
import org.eclipse.ditto.signals.commands.things.modify.ModifyPolicyId;
import org.eclipse.ditto.signals.commands.things.modify.ModifyPolicyIdResponse;
import org.eclipse.ditto.signals.commands.things.modify.ModifyThing;
import org.eclipse.ditto.signals.commands.things.modify.ModifyThingDefinition;
import org.eclipse.ditto.signals.commands.things.modify.ModifyThingDefinitionResponse;
import org.eclipse.ditto.signals.commands.things.modify.ModifyThingResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAcl;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAclEntry;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAclEntryResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAclResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAttribute;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAttributeResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAttributes;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAttributesResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeature;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeatureDefinition;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeatureDefinitionResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeatureProperties;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeaturePropertiesResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeatureProperty;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeaturePropertyResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeatureResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeatures;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeaturesResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrievePolicyId;
import org.eclipse.ditto.signals.commands.things.query.RetrievePolicyIdResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThing;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThingResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThings;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThingsResponse;
import org.eclipse.ditto.signals.commands.thingsearch.exceptions.InvalidOptionException;
import org.eclipse.ditto.signals.commands.thingsearch.exceptions.SubscriptionProtocolErrorException;
import org.eclipse.ditto.signals.commands.thingsearch.query.CountThings;
import org.eclipse.ditto.signals.commands.thingsearch.query.CountThingsResponse;
import org.eclipse.ditto.signals.commands.thingsearch.query.QueryThings;
import org.eclipse.ditto.signals.commands.thingsearch.query.QueryThingsResponse;
import org.eclipse.ditto.signals.commands.thingsearch.subscription.CancelSubscription;
import org.eclipse.ditto.signals.commands.thingsearch.subscription.CreateSubscription;
import org.eclipse.ditto.signals.commands.thingsearch.subscription.RequestFromSubscription;
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
import org.eclipse.ditto.signals.events.things.AclEntryCreated;
import org.eclipse.ditto.signals.events.things.AclEntryDeleted;
import org.eclipse.ditto.signals.events.things.AclEntryModified;
import org.eclipse.ditto.signals.events.things.AclModified;
import org.eclipse.ditto.signals.events.things.AttributeCreated;
import org.eclipse.ditto.signals.events.things.AttributeDeleted;
import org.eclipse.ditto.signals.events.things.AttributeModified;
import org.eclipse.ditto.signals.events.things.AttributesCreated;
import org.eclipse.ditto.signals.events.things.AttributesDeleted;
import org.eclipse.ditto.signals.events.things.AttributesModified;
import org.eclipse.ditto.signals.events.things.FeatureCreated;
import org.eclipse.ditto.signals.events.things.FeatureDefinitionCreated;
import org.eclipse.ditto.signals.events.things.FeatureDefinitionDeleted;
import org.eclipse.ditto.signals.events.things.FeatureDefinitionModified;
import org.eclipse.ditto.signals.events.things.FeatureDeleted;
import org.eclipse.ditto.signals.events.things.FeatureModified;
import org.eclipse.ditto.signals.events.things.FeaturePropertiesCreated;
import org.eclipse.ditto.signals.events.things.FeaturePropertiesDeleted;
import org.eclipse.ditto.signals.events.things.FeaturePropertiesModified;
import org.eclipse.ditto.signals.events.things.FeaturePropertyCreated;
import org.eclipse.ditto.signals.events.things.FeaturePropertyDeleted;
import org.eclipse.ditto.signals.events.things.FeaturePropertyModified;
import org.eclipse.ditto.signals.events.things.FeaturesCreated;
import org.eclipse.ditto.signals.events.things.FeaturesDeleted;
import org.eclipse.ditto.signals.events.things.FeaturesModified;
import org.eclipse.ditto.signals.events.things.PolicyIdCreated;
import org.eclipse.ditto.signals.events.things.PolicyIdModified;
import org.eclipse.ditto.signals.events.things.ThingCreated;
import org.eclipse.ditto.signals.events.things.ThingDefinitionCreated;
import org.eclipse.ditto.signals.events.things.ThingDefinitionDeleted;
import org.eclipse.ditto.signals.events.things.ThingDefinitionModified;
import org.eclipse.ditto.signals.events.things.ThingDeleted;
import org.eclipse.ditto.signals.events.things.ThingMerged;
import org.eclipse.ditto.signals.events.things.ThingModified;
import org.eclipse.ditto.signals.events.thingsearch.SubscriptionComplete;
import org.eclipse.ditto.signals.events.thingsearch.SubscriptionCreated;
import org.eclipse.ditto.signals.events.thingsearch.SubscriptionFailed;
import org.eclipse.ditto.signals.events.thingsearch.SubscriptionHasNextPage;

class JsonExamplesProducer {

    public static final String FEATURE_ID = "accelerometer";
    public static final String NAMESPACE = "com.acme";

    /*
     * Policy
     */
    private static final PolicyId POLICY_ID = PolicyId.of(NAMESPACE, "the_policy_id");
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
    /*
     * Thing
     */
    private static final ThingId THING_ID = ThingId.of(NAMESPACE, "xdk_53");
    private static final ThingDefinition THING_DEFINITION = ThingsModelFactory.newDefinition("com.acme:XDKmodel:1.0.0");
    private static final ThingLifecycle LIFECYCLE = ThingLifecycle.ACTIVE;
    private static final AuthorizationSubject AUTH_SUBJECT_1 =
            newAuthSubject("the_auth_subject");
    private static final AclEntry ACL_ENTRY_1 =
            ThingsModelFactory.newAclEntry(AUTH_SUBJECT_1, Permission.READ, Permission.WRITE, Permission.ADMINISTRATE);
    private static final AuthorizationSubject AUTH_SUBJECT_2 =
            newAuthSubject("the_auth_subject_2");
    private static final AclEntry ACL_ENTRY_2 = ThingsModelFactory.newAclEntry(AUTH_SUBJECT_2, Permission.READ);
    private static final AccessControlList ACL = ThingsModelFactory.newAcl(ACL_ENTRY_1, ACL_ENTRY_2);
    private static final JsonObject ATTRIBUTE_VALUE = JsonFactory.newObjectBuilder()
            .set("latitude", 44.673856)
            .set("longitude", 8.261719)
            .build();
    private static final JsonPointer ATTRIBUTE_POINTER = JsonFactory.newPointer("location");
    private static final Attributes ATTRIBUTES = ThingsModelFactory.newAttributesBuilder()
            .set(ATTRIBUTE_POINTER, ATTRIBUTE_VALUE)
            .build();
    private static final ThingRevision THING_REVISION = ThingsModelFactory.newThingRevision(REVISION_NUMBER);
    public static final String PROPERTY_X = "x";
    private static final JsonPointer PROPERTY_POINTER = JsonFactory.newPointer(PROPERTY_X);
    private static final JsonValue PROPERTY_VALUE = JsonFactory.newValue(42);
    private static final FeatureDefinition FEATURE_DEFINITION =
            FeatureDefinition.fromIdentifier(NAMESPACE + ":" + FEATURE_ID + ":1.0.0");
    private static final FeatureProperties FEATURE_PROPERTIES = ThingsModelFactory.newFeaturePropertiesBuilder()
            .set("x", 3.141)
            .set("y", 2.718)
            .set("z", 1)
            .set("unit", "g")
            .build();

    private static final Feature FLUX_CAPACITOR = ThingsModelFactory.newFeatureBuilder()
            .definition(FEATURE_DEFINITION)
            .properties(FEATURE_PROPERTIES)
            .withId(FEATURE_ID)
            .build();
    private static final Features FEATURES = ThingsModelFactory.newFeatures(FLUX_CAPACITOR);
    private static final Thing THING = ThingsModelFactory.newThingBuilder()
            .setId(THING_ID)
            .setDefinition(THING_DEFINITION)
            .setRevision(THING_REVISION)
            .setAttributes(ATTRIBUTES)
            .setFeatures(FEATURES)
            .setLifecycle(LIFECYCLE)
            .setPolicyId(POLICY_ID)
            .build();

    private static final JsonFieldSelector JSON_FIELD_SELECTOR_ATTRIBUTES_WITH_THING_ID = JsonFactory.newFieldSelector(
            "thingId,attributes(location)", JsonFactory
                    .newParseOptionsBuilder().withoutUrlDecoding()
                    .build());
    private static final JsonFieldSelector JSON_FIELD_SELECTOR_ATTRIBUTES = JsonFactory.newFieldSelector(
            "attributes", JsonFactory
                    .newParseOptionsBuilder().withoutUrlDecoding()
                    .build());

    private static final JsonFieldSelector JSON_FIELD_SELECTOR_FEATURE_PROPERTIES =
            JsonFactory.newFieldSelector("properties/target_year_1",
                    JsonParseOptions.newBuilder().withoutUrlDecoding().build());

    private static final DittoHeaders DITTO_HEADERS = DittoHeaders.empty();

    private static final List<DittoHeaderDefinition> EXCLUDED_RESPONSE_HEADERS =
            Arrays.asList(DittoHeaderDefinition.RESPONSE_REQUIRED, DittoHeaderDefinition.CONTENT_TYPE);
    private static final List<DittoHeaderDefinition> EXCLUDED_EVENT_HEADERS =
            Collections.singletonList(DittoHeaderDefinition.RESPONSE_REQUIRED);

    /**
     * Generates *.json examples. If you want to create examples for ditto-documentation use {@link
     * PublicJsonExamplesProducer}.
     *
     * @param args expected one argument: the folder where the examples should be stored, e.g. "generated-examples"
     */
    public static void main(final String... args) throws IOException {
        run(args, new JsonExamplesProducer());
    }

    protected static void run(final String[] args, final JsonExamplesProducer producer) throws
            IOException {
        if (args.length != 1) {
            System.err.println("Exactly 1 argument required: the target folder in which to generate the JSON files");
            System.exit(-1);
        }
        producer.produce(Paths.get(args[0]));
    }

    private void produce(final Path rootPath) throws IOException {

        producePolicyCommands(rootPath.resolve("policies"));
        producePolicyEvents(rootPath.resolve("policies"));
        producePolicyExceptions(rootPath.resolve("policies"));

        produceThingCommands(rootPath.resolve("things"));
        produceThingEvents(rootPath.resolve("things"));
        produceThingMergedEvents(rootPath.resolve("things"));
        produceThingExceptions(rootPath.resolve("things"));

        produceSearchModel(rootPath.resolve("search"));
        produceSearchCommands(rootPath.resolve("search"));
        produceSearchCommandResponses(rootPath.resolve("search"));
        produceSearchEvents(rootPath.resolve("search"));
        produceSearchExceptions(rootPath.resolve("search"));

        produceMessageExceptions(rootPath.resolve("messages"));
        produceGatewayExceptions(rootPath.resolve("gateway"));

        produceJsonExceptions(rootPath.resolve("json"));
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
                ModifyPolicyEntryResponse.modified(POLICY_ID, LABEL, DITTO_HEADERS);
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
                ModifySubjectResponse.modified(POLICY_ID, LABEL, SUBJECT_ID, DITTO_HEADERS);
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
                ModifyResourceResponse.modified(POLICY_ID, LABEL, RESOURCE_KEY, DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("modifyResourceResponse.json")), modifyResourceResponse);

        final ModifyResourceResponse modifyResourceResponseCreated =
                ModifyResourceResponse.created(POLICY_ID, LABEL, RESOURCE,
                        DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("modifyResourceResponseCreated.json")), modifyResourceResponseCreated);

        final DeleteResourceResponse deleteResourceResponse = DeleteResourceResponse.of(POLICY_ID, LABEL, RESOURCE_KEY,
                DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("deleteResourceResponse.json")), deleteResourceResponse);
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

    private void producePolicyExceptions(final Path rootPath) throws IOException {
        final Path exceptionsDir = rootPath.resolve(Paths.get("exceptions"));
        Files.createDirectories(exceptionsDir);

        final PolicyConflictException policyConflictException = PolicyConflictException.newBuilder(POLICY_ID).build();
        writeJson(exceptionsDir.resolve(Paths.get("policyConflictException.json")), policyConflictException);

        final PolicyIdInvalidException policyIdInvalidException =
                PolicyIdInvalidException.newBuilder("invalid id").build();
        writeJson(exceptionsDir.resolve(Paths.get("policyIdInvalidException.json")), policyIdInvalidException);

        final LabelInvalidException labelInvalidException =
                LabelInvalidException.newBuilder("imported-label").build();
        writeJson(exceptionsDir.resolve(Paths.get("labelInvalidException.json")), labelInvalidException);

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


    private void produceThingCommands(final Path rootPath) throws IOException {
        produceThingQueryCommands(rootPath);
        produceThingModifyCommands(rootPath);
        produceThingMergeCommands(rootPath);
        produceThingQueryResponses(rootPath);
        produceThingModifyResponses(rootPath);
        produceThingMergeCommandResponses(rootPath);
    }

    private void produceThingQueryCommands(final Path rootPath) throws IOException {
        System.out.println("--- produce thing query commands --------------------------------------------------------");

        final Path commandsDir = rootPath.resolve(Paths.get("commands", "query"));
        Files.createDirectories(commandsDir);

        final RetrieveThing retrieveThing = RetrieveThing.of(THING_ID, DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("retrieveThing.json")), retrieveThing);

        final RetrieveThing retrieveThingWithFieldSelector = RetrieveThing.getBuilder(THING_ID, DITTO_HEADERS)
                .withSelectedFields(JSON_FIELD_SELECTOR_ATTRIBUTES_WITH_THING_ID)
                .build();
        writeJson(commandsDir.resolve(Paths.get("retrieveThing-withFieldSelector.json")),
                retrieveThingWithFieldSelector);

        final RetrieveThing retrieveThingWithSnapshotRevision = RetrieveThing.getBuilder(THING_ID, DITTO_HEADERS)
                .withSnapshotRevision(42L)
                .build();
        writeJson(commandsDir.resolve(Paths.get("retrieveThing-withSnapshotRevision.json")),
                retrieveThingWithSnapshotRevision);

        final ThingId[] thingIds =
                {ThingId.of(NAMESPACE, "xdk_53"), ThingId.of(NAMESPACE, "xdk_58"), ThingId.of(NAMESPACE, "xdk_67")};
        final RetrieveThings retrieveThings =
                RetrieveThings.getBuilder(thingIds).dittoHeaders(DITTO_HEADERS).build();
        writeJson(commandsDir.resolve(Paths.get("retrieveThings.json")), retrieveThings);

        final RetrieveThings retrieveThingsWithFieldSelector =
                RetrieveThings.getBuilder(thingIds).dittoHeaders(DITTO_HEADERS)
                        .selectedFields(JSON_FIELD_SELECTOR_ATTRIBUTES_WITH_THING_ID).build();
        writeJson(commandsDir.resolve(Paths.get("retrieveThings-withFieldSelector.json")),
                retrieveThingsWithFieldSelector);

        final RetrieveAttributes retrieveAttributes = RetrieveAttributes.of(THING_ID, DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("retrieveAttributes.json")), retrieveAttributes);

        final RetrieveAttributes retrieveAttributesSelectedFields =
                RetrieveAttributes.of(THING_ID, JSON_FIELD_SELECTOR_ATTRIBUTES,
                        DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("retrieveAttributes-selectedFields.json")),
                retrieveAttributesSelectedFields);

        final RetrieveAttribute retrieveAttributeWithJsonPointer =
                RetrieveAttribute.of(THING_ID, ATTRIBUTE_POINTER, DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("retrieveAttribute.json")), retrieveAttributeWithJsonPointer);

        final RetrieveAcl retrieveAcl = RetrieveAcl.of(THING_ID, DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("retrieveAcl.json")), retrieveAcl, JsonSchemaVersion.V_1);

        final RetrieveAclEntry retrieveAclEntry = RetrieveAclEntry.of(THING_ID, AUTH_SUBJECT_1, DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("retrieveAclEntry.json")), retrieveAclEntry, JsonSchemaVersion.V_1);

        final RetrievePolicyId retrievePolicyId = RetrievePolicyId.of(THING_ID, DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("retrievePolicyId.json")), retrievePolicyId);

        final RetrieveFeatures retrieveFeatures = RetrieveFeatures.of(THING_ID, DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("retrieveFeatures.json")), retrieveFeatures);

        final RetrieveFeature retrieveFeature = RetrieveFeature.of(THING_ID, FEATURE_ID, DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("retrieveFeature.json")), retrieveFeature);

        final RetrieveFeatureDefinition retrieveFeatureDefinition =
                RetrieveFeatureDefinition.of(THING_ID, FEATURE_ID, DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("retrieveFeatureDefinition.json")), retrieveFeatureDefinition);

        final RetrieveFeatureProperties retrieveFeatureProperties =
                RetrieveFeatureProperties.of(THING_ID, FEATURE_ID, DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("retrieveFeatureProperties.json")), retrieveFeatureProperties);

        final RetrieveFeatureProperties retrieveFeaturePropertiesSelectedFields = RetrieveFeatureProperties.of(THING_ID,
                FEATURE_ID, JSON_FIELD_SELECTOR_FEATURE_PROPERTIES, DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("retrieveFeatureProperties-selectedFields.json")),
                retrieveFeaturePropertiesSelectedFields);

        final RetrieveFeatureProperty retrieveFeatureProperty =
                RetrieveFeatureProperty.of(THING_ID, FEATURE_ID, PROPERTY_POINTER, DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("retrieveFeatureProperty.json")), retrieveFeatureProperty);
    }

    private void produceThingQueryResponses(final Path rootPath) throws IOException {
        System.out.println("--- produce thing query responses -------------------------------------------------------");

        final Path commandsDir = rootPath.resolve(Paths.get("commands", "query"));
        Files.createDirectories(commandsDir);

        final RetrieveThingsResponse retrieveThingsResponse =
                RetrieveThingsResponse.of(Collections.singletonList(THING), FieldType.notHidden(),
                        JsonExamplesProducer.NAMESPACE, DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("retrieveThingsResponse.json")), retrieveThingsResponse);

        final RetrieveThingResponse retrieveThingResponse = RetrieveThingResponse.of(THING_ID, THING, DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("retrieveThingResponse.json")), retrieveThingResponse);

        final RetrieveAttributesResponse retrieveAttributesResponse =
                RetrieveAttributesResponse.of(THING_ID, ATTRIBUTES,
                        DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("retrieveAttributesResponse.json")), retrieveAttributesResponse);

        final RetrieveAttributeResponse retrieveAttributeResponse =
                RetrieveAttributeResponse.of(THING_ID, ATTRIBUTE_POINTER, ATTRIBUTE_VALUE, DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("retrieveAttributeResponse.json")), retrieveAttributeResponse);

        final RetrieveAclResponse retrieveAclResponse = RetrieveAclResponse.of(THING_ID, ACL, DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("retrieveAclResponse.json")), retrieveAclResponse,
                JsonSchemaVersion.V_1);

        final RetrieveAclEntryResponse retrieveAclEntryResponse = RetrieveAclEntryResponse.of(THING_ID, ACL_ENTRY_1,
                DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("retrieveAclEntryResponse.json")), retrieveAclEntryResponse,
                JsonSchemaVersion.V_1);

        final RetrievePolicyIdResponse retrievePolicyIdResponse = RetrievePolicyIdResponse.of(THING_ID, POLICY_ID,
                DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("retrievePolicyIdResponse.json")), retrievePolicyIdResponse);

        final RetrieveFeaturesResponse retrieveFeaturesResponse = RetrieveFeaturesResponse.of(THING_ID, FEATURES,
                DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("retrieveFeaturesResponse.json")), retrieveFeaturesResponse);

        final RetrieveFeatureResponse retrieveFeatureResponse = RetrieveFeatureResponse.of(THING_ID, FLUX_CAPACITOR,
                DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("retrieveFeatureResponse.json")), retrieveFeatureResponse);

        final RetrieveFeatureDefinitionResponse retrieveFeatureDefinitionResponse =
                RetrieveFeatureDefinitionResponse.of(THING_ID, FEATURE_ID, FEATURE_DEFINITION, DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("retrieveFeatureDefinitionResponse.json")),
                retrieveFeatureDefinitionResponse);

        final RetrieveFeaturePropertiesResponse retrieveFeaturePropertiesResponse =
                RetrieveFeaturePropertiesResponse.of(THING_ID, FEATURE_ID, FEATURE_PROPERTIES, DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("retrieveFeaturePropertiesResponse.json")),
                retrieveFeaturePropertiesResponse);

        final RetrieveFeaturePropertyResponse retrieveFeaturePropertyResponse =
                RetrieveFeaturePropertyResponse.of(THING_ID, FEATURE_ID, PROPERTY_POINTER, PROPERTY_VALUE,
                        DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("retrieveFeaturePropertyResponse.json")),
                retrieveFeaturePropertyResponse);
    }

    private void produceThingModifyCommands(final Path rootPath) throws IOException {
        System.out.println("--- produce thing modify commands -------------------------------------------------------");

        final Path commandsDir = rootPath.resolve(Paths.get("commands", "modify"));
        Files.createDirectories(commandsDir);

        final CreateThing createThing = CreateThing.of(THING, POLICY.toJson(), DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("createThing.json")), createThing);

        final ModifyThing modifyThing = ModifyThing.of(THING_ID, THING, POLICY.toJson(), DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("modifyThing.json")), modifyThing);

        final DeleteThing deleteThing = DeleteThing.of(THING_ID, DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("deleteThing.json")), deleteThing);

        final ModifyThingDefinition modifyThingDefinition =
                ModifyThingDefinition.of(THING_ID, ThingsModelFactory.newDefinition(THING_DEFINITION), DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("modifyThingDefinition.json")), modifyThingDefinition);

        final DeleteThingDefinition deleteThingDefinition = DeleteThingDefinition.of(THING_ID, DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("deleteThingDefinition.json")), deleteThingDefinition);

        final ModifyAclEntry modifyAclEntry = ModifyAclEntry.of(THING_ID, ACL_ENTRY_1, DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("modifyAclEntry.json")), modifyAclEntry, JsonSchemaVersion.V_1);

        final ModifyAcl modifyAcl = ModifyAcl.of(THING_ID, ACL, DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("modifyAcl.json")), modifyAcl, JsonSchemaVersion.V_1);

        final DeleteAclEntry deleteAclEntry = DeleteAclEntry.of(THING_ID, AUTH_SUBJECT_1, DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("deleteAclEntry.json")), deleteAclEntry, JsonSchemaVersion.V_1);

        final ModifyPolicyId modifyPolicyId = ModifyPolicyId.of(THING_ID, POLICY_ID, DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("modifyPolicyId.json")), modifyPolicyId);

        final ModifyAttributes modifyAttributes = ModifyAttributes.of(THING_ID, ATTRIBUTES, DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("modifyAttributes.json")), modifyAttributes);

        final ModifyAttribute modifyAttribute = ModifyAttribute.of(THING_ID, ATTRIBUTE_POINTER, ATTRIBUTE_VALUE,
                DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("modifyAttribute.json")), modifyAttribute);

        final DeleteAttributes deleteAttributes = DeleteAttributes.of(THING_ID, DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("deleteAttributes.json")), deleteAttributes);

        final DeleteAttribute deleteAttribute = DeleteAttribute.of(THING_ID, ATTRIBUTE_POINTER, DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("deleteAttribute.json")), deleteAttribute);

        final ModifyFeature modifyFeature = ModifyFeature.of(THING_ID, FLUX_CAPACITOR, DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("modifyFeature.json")), modifyFeature);

        final DeleteFeature deleteFeature = DeleteFeature.of(THING_ID, FEATURE_ID, DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("deleteFeature.json")), deleteFeature);

        final ModifyFeatures modifyFeatures = ModifyFeatures.of(THING_ID, FEATURES, DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("modifyFeatures.json")), modifyFeatures);

        final DeleteFeatures deleteFeatures = DeleteFeatures.of(THING_ID, DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("deleteFeatures.json")), deleteFeatures);

        final ModifyFeatureDefinition modifyFeatureDefinition = ModifyFeatureDefinition.of(THING_ID, FEATURE_ID,
                FEATURE_DEFINITION, DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("modifyFeatureDefinition.json")), modifyFeatureDefinition);

        final ModifyFeatureProperties modifyFeatureProperties = ModifyFeatureProperties.of(THING_ID, FEATURE_ID,
                FEATURE_PROPERTIES, DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("modifyFeatureProperties.json")), modifyFeatureProperties);

        final ModifyFeatureProperty modifyFeatureProperty = ModifyFeatureProperty.of(THING_ID, FEATURE_ID,
                PROPERTY_POINTER, PROPERTY_VALUE, DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("modifyFeatureProperty.json")), modifyFeatureProperty);

        final DeleteFeatureDefinition deleteFeatureDefinition = DeleteFeatureDefinition.of(THING_ID, FEATURE_ID,
                DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("deleteFeatureDefinition.json")), deleteFeatureDefinition);

        final DeleteFeatureProperties deleteFeatureProperties = DeleteFeatureProperties.of(THING_ID, FEATURE_ID,
                DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("deleteFeatureProperties.json")), deleteFeatureProperties);

        final DeleteFeatureProperty deleteFeatureProperty = DeleteFeatureProperty.of(THING_ID, FEATURE_ID,
                PROPERTY_POINTER, DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("deleteFeatureProperty.json")), deleteFeatureProperty);
    }

    private void produceThingModifyResponses(final Path rootPath) throws IOException {
        System.out.println("--- produce thing modify responses ------------------------------------------------------");

        final Path commandsDir = rootPath.resolve(Paths.get("commands", "modify"));
        Files.createDirectories(commandsDir);

        final CreateThingResponse createThingResponse = CreateThingResponse.of(THING, DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("createThingResponse.json")), createThingResponse);

        final ModifyThingResponse modifyThingResponse = ModifyThingResponse.modified(THING_ID, DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("modifyThingResponse.json")), modifyThingResponse);

        final ModifyThingResponse modifyThingResponseCreated = ModifyThingResponse.created(THING, DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("modifyThingResponseCreated.json")), modifyThingResponseCreated);

        final DeleteThingResponse deleteThingResponse = DeleteThingResponse.of(THING_ID, DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("deleteThingResponse.json")), deleteThingResponse);

        final ModifyThingDefinitionResponse modifyThingDefinitionResponse =
                ModifyThingDefinitionResponse.modified(THING_ID, DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("modifyThingDefinitionResponse.json")), modifyThingDefinitionResponse);

        final ModifyThingDefinitionResponse modifyThingDefinitionResponseCreated =
                ModifyThingDefinitionResponse.created(THING_ID, THING_DEFINITION, DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("modifyThingDefinitionResponseCreated.json")),
                modifyThingDefinitionResponseCreated);

        final DeleteThingDefinitionResponse deleteThingDefinitionResponse =
                DeleteThingDefinitionResponse.of(THING_ID, DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("deleteThingDefinitionResponse.json")),
                deleteThingDefinitionResponse);

        final ModifyPolicyIdResponse modifyPolicyIdResponseCreated =
                ModifyPolicyIdResponse.created(THING_ID, POLICY_ID, DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("modifyPolicyIdResponseCreated.json")), modifyPolicyIdResponseCreated);

        final ModifyPolicyIdResponse modifyPolicyIdResponseModified =
                ModifyPolicyIdResponse.modified(THING_ID, DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("modifyPolicyIdResponseModified.json")),
                modifyPolicyIdResponseModified);

        final ModifyAttributesResponse modifyAttributesResponse =
                ModifyAttributesResponse.modified(THING_ID, DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("modifyAttributesResponse.json")), modifyAttributesResponse);

        final ModifyAttributesResponse modifyAttributesResponseCreated =
                ModifyAttributesResponse.created(THING_ID, ATTRIBUTES,
                        DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("modifyAttributesResponseCreated.json")),
                modifyAttributesResponseCreated);

        final DeleteAttributesResponse deleteAttributesResponse =
                DeleteAttributesResponse.of(THING_ID, DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("deleteAttributesResponse.json")), deleteAttributesResponse);

        final ModifyAttributeResponse modifyAttributeResponse =
                ModifyAttributeResponse.modified(THING_ID, ATTRIBUTE_POINTER,
                        DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("modifyAttributeResponse.json")), modifyAttributeResponse);

        final ModifyAttributeResponse modifyAttributeResponseCreated =
                ModifyAttributeResponse.created(THING_ID, ATTRIBUTE_POINTER, ATTRIBUTE_VALUE, DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("modifyAttributeResponseCreated.json")),
                modifyAttributeResponseCreated);

        final DeleteAttributeResponse deleteAttributeResponse = DeleteAttributeResponse.of(THING_ID, ATTRIBUTE_POINTER,
                DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("deleteAttributeResponse.json")), deleteAttributeResponse);

        final ModifyFeaturesResponse modifyFeaturesResponse =
                ModifyFeaturesResponse.modified(THING_ID, DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("modifyFeaturesResponse.json")), modifyFeaturesResponse);

        final ModifyFeaturesResponse modifyFeaturesResponseCreated = ModifyFeaturesResponse.created(THING_ID, FEATURES,
                DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("modifyFeaturesResponseCreated.json")), modifyFeaturesResponseCreated);

        final DeleteFeaturesResponse deleteFeaturesResponse = DeleteFeaturesResponse.of(THING_ID, DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("deleteFeaturesResponse.json")), deleteFeaturesResponse);

        final ModifyFeatureResponse modifyFeatureResponse =
                ModifyFeatureResponse.modified(THING_ID, FEATURE_ID, DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("modifyFeatureResponse.json")), modifyFeatureResponse);

        final ModifyFeatureResponse modifyFeatureResponseCreated =
                ModifyFeatureResponse.created(THING_ID, FLUX_CAPACITOR,
                        DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("modifyFeatureResponseCreated.json")), modifyFeatureResponseCreated);

        final DeleteFeatureResponse deleteFeatureResponse =
                DeleteFeatureResponse.of(THING_ID, FEATURE_ID, DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("deleteFeatureResponse.json")), deleteFeatureResponse);

        final ModifyFeatureDefinitionResponse modifyFeatureDefinitionResponse =
                ModifyFeatureDefinitionResponse.modified(THING_ID, FEATURE_ID, DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("modifyFeatureDefinitionResponse.json")),
                modifyFeatureDefinitionResponse);

        final ModifyFeatureDefinitionResponse modifyFeatureDefinitionResponseCreated =
                ModifyFeatureDefinitionResponse.created(THING_ID, FEATURE_ID, FEATURE_DEFINITION, DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("modifyFeatureDefinitionResponseCreated.json")),
                modifyFeatureDefinitionResponseCreated);

        final ModifyFeaturePropertiesResponse modifyFeaturePropertiesResponse =
                ModifyFeaturePropertiesResponse.modified(THING_ID, FEATURE_ID, DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("modifyFeaturePropertiesResponse.json")),
                modifyFeaturePropertiesResponse);

        final ModifyFeaturePropertiesResponse modifyFeaturePropertiesResponseCreated =
                ModifyFeaturePropertiesResponse.created(THING_ID, FEATURE_ID, FEATURE_PROPERTIES, DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("modifyFeaturePropertiesResponseCreated.json")),
                modifyFeaturePropertiesResponseCreated);

        final DeleteFeatureDefinitionResponse deleteFeatureDefinitionResponse =
                DeleteFeatureDefinitionResponse.of(THING_ID, FEATURE_ID, DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("deleteFeatureDefinitionResponse.json")),
                deleteFeatureDefinitionResponse);

        final DeleteFeaturePropertiesResponse deleteFeaturePropertiesResponse =
                DeleteFeaturePropertiesResponse.of(THING_ID, FEATURE_ID, DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("deleteFeaturePropertiesResponse.json")),
                deleteFeaturePropertiesResponse);

        final ModifyFeaturePropertyResponse modifyFeaturePropertyResponse =
                ModifyFeaturePropertyResponse.modified(THING_ID, FEATURE_ID, PROPERTY_POINTER, DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("modifyFeaturePropertyResponse.json")), modifyFeaturePropertyResponse);

        final ModifyFeaturePropertyResponse modifyFeaturePropertyResponseCreated = ModifyFeaturePropertyResponse
                .created(THING_ID, FEATURE_ID, PROPERTY_POINTER, PROPERTY_VALUE, DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("modifyFeaturePropertyResponseCreated.json")),
                modifyFeaturePropertyResponseCreated);

        final DeleteFeaturePropertyResponse deleteFeaturePropertyResponse =
                DeleteFeaturePropertyResponse.of(THING_ID, FEATURE_ID, PROPERTY_POINTER, DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("deleteFeaturePropertyResponse.json")), deleteFeaturePropertyResponse);
    }

    private MergeThingResponse toResponse(final MergeThing mergeThing) {
        return MergeThingResponse.of(mergeThing.getEntityId(), mergeThing.getResourcePath(),
                mergeThing.getDittoHeaders());
    }

    private void produceThingMergeCommands(final Path rootPath) throws IOException {
        System.out.println("--- produce thing merge commands --------------------------------------------------------");

        final Path commandsDir = rootPath.resolve(Paths.get("commands", "merge"));
        Files.createDirectories(commandsDir);

        final MergeThing mergeThing = MergeThing.withThing(THING_ID, THING, DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("mergeThing.json")), mergeThing);

        final MergeThing mergeThingDefinition =
                MergeThing.withThingDefinition(THING_ID, ThingsModelFactory.newDefinition(THING_DEFINITION),
                        DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("mergeThingDefinition.json")), mergeThingDefinition);

        final MergeThing mergeDeleteThingDefinition =
                MergeThing.withThingDefinition(THING_ID, ThingsModelFactory.nullDefinition(), DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("mergeDeleteThingDefinition.json")), mergeDeleteThingDefinition);

        final MergeThing mergePolicyId = MergeThing.withPolicyId(THING_ID, POLICY_ID, DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("mergePolicyId.json")), mergePolicyId);

        final MergeThing mergeAttributes = MergeThing.withAttributes(THING_ID, ATTRIBUTES, DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("mergeAttributes.json")), mergeAttributes);

        final MergeThing mergeDeleteAttributes =
                MergeThing.withAttributes(THING_ID, ThingsModelFactory.nullAttributes(), DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("mergeDeleteAttributes.json")), mergeDeleteAttributes);

        final MergeThing mergeAttribute =
                MergeThing.withAttribute(THING_ID, ATTRIBUTE_POINTER, ATTRIBUTE_VALUE, DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("mergeAttribute.json")), mergeAttribute);

        final MergeThing mergeDeleteAttribute =
                MergeThing.withAttribute(THING_ID, ATTRIBUTE_POINTER, JsonValue.nullLiteral(), DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("mergeDeleteAttribute.json")), mergeDeleteAttribute);

        final MergeThing mergeFeature = MergeThing.withFeature(THING_ID, FLUX_CAPACITOR, DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("mergeFeature.json")), mergeFeature);

        final MergeThing mergeDeleteFeature =
                MergeThing.withFeature(THING_ID, ThingsModelFactory.nullFeature(FEATURE_ID), DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("mergeDeleteFeature.json")), mergeDeleteFeature);

        final MergeThing mergeFeatures = MergeThing.withFeatures(THING_ID, FEATURES, DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("mergeFeatures.json")), mergeFeatures);

        final MergeThing mergeDeleteFeatures =
                MergeThing.withFeatures(THING_ID, ThingsModelFactory.nullFeatures(), DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("mergeDeleteFeatures.json")), mergeDeleteFeatures);

        final MergeThing mergeFeatureDefinition =
                MergeThing.withFeatureDefinition(THING_ID, FEATURE_ID, FEATURE_DEFINITION, DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("mergeFeatureDefinition.json")), mergeFeatureDefinition);

        final MergeThing mergeDeleteFeatureDefinition =
                MergeThing.withFeatureDefinition(THING_ID, FEATURE_ID, ThingsModelFactory.nullFeatureDefinition(),
                        DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("mergeDeleteFeatureDefinition.json")), mergeDeleteFeatureDefinition);

        final MergeThing mergeFeatureProperties =
                MergeThing.withFeatureProperties(THING_ID, FEATURE_ID, FEATURE_PROPERTIES, DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("mergeFeatureProperties.json")), mergeFeatureProperties);

        final MergeThing mergeDeleteFeatureProperties =
                MergeThing.withFeatureProperties(THING_ID, FEATURE_ID, ThingsModelFactory.nullFeatureProperties(),
                        DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("mergeDeleteFeatureProperties.json")), mergeDeleteFeatureProperties);

        final MergeThing mergeFeatureProperty =
                MergeThing.withFeatureProperty(THING_ID, FEATURE_ID, PROPERTY_POINTER, PROPERTY_VALUE, DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("mergeFeatureProperty.json")), mergeFeatureProperty);

        final MergeThing mergeDeleteFeatureProperty =
                MergeThing.withFeatureProperty(THING_ID, FEATURE_ID, PROPERTY_POINTER, JsonValue.nullLiteral(),
                        DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("mergeDeleteFeatureProperty.json")), mergeDeleteFeatureProperty);

        final MergeThing mergeFeatureDesiredProperties =
                MergeThing.withFeatureDesiredProperties(THING_ID, FEATURE_ID, FEATURE_PROPERTIES, DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("mergeFeatureDesiredProperties.json")), mergeFeatureDesiredProperties);

        final MergeThing mergeDeleteFeatureDesiredProperties =
                MergeThing.withFeatureDesiredProperties(THING_ID, FEATURE_ID,
                        ThingsModelFactory.nullFeatureProperties(), DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("mergeDeleteFeatureDesiredProperties.json")),
                mergeDeleteFeatureDesiredProperties);

        final MergeThing mergeFeatureDesiredProperty =
                MergeThing.withFeatureDesiredProperty(THING_ID, FEATURE_ID, PROPERTY_POINTER, PROPERTY_VALUE,
                        DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("mergeFeatureDesiredProperty.json")), mergeFeatureDesiredProperty);

        final MergeThing mergeDeleteFeatureDesiredProperty =
                MergeThing.withFeatureDesiredProperty(THING_ID, FEATURE_ID, PROPERTY_POINTER, JsonValue.nullLiteral(),
                        DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("mergeDeleteFeatureDesiredProperty.json")),
                mergeDeleteFeatureDesiredProperty);
    }

    private void produceThingMergeCommandResponses(final Path rootPath) throws IOException {
        System.out.println("--- produce thing merge responses -------------------------------------------------------");

        final Path commandsDir = rootPath.resolve(Paths.get("commands", "merge"));
        Files.createDirectories(commandsDir);

        final MergeThingResponse mergeThingResponse = toResponse(MergeThing.withThing(THING_ID, THING, DITTO_HEADERS));
        writeResponseJson(commandsDir.resolve(Paths.get("mergeThingResponse.json")), mergeThingResponse);

        final MergeThingResponse mergeThingDefinitionResponse = toResponse(
                MergeThing.withThingDefinition(THING_ID, ThingsModelFactory.newDefinition(THING_DEFINITION),
                        DITTO_HEADERS));
        writeResponseJson(commandsDir.resolve(Paths.get("mergeThingDefinitionResponse.json")),
                mergeThingDefinitionResponse);

        final MergeThingResponse mergeDeleteThingDefinitionResponse = toResponse(
                MergeThing.withThingDefinition(THING_ID, ThingsModelFactory.nullDefinition(), DITTO_HEADERS));
        writeResponseJson(commandsDir.resolve(Paths.get("mergeDeleteThingDefinitionResponse.json")),
                mergeDeleteThingDefinitionResponse);

        final MergeThingResponse mergePolicyIdResponse =
                toResponse(MergeThing.withPolicyId(THING_ID, POLICY_ID, DITTO_HEADERS));
        writeResponseJson(commandsDir.resolve(Paths.get("mergePolicyIdResponse.json")), mergePolicyIdResponse);

        final MergeThingResponse mergeAttributesResponse =
                toResponse(MergeThing.withAttributes(THING_ID, ATTRIBUTES, DITTO_HEADERS));
        writeResponseJson(commandsDir.resolve(Paths.get("mergeAttributesResponse.json")), mergeAttributesResponse);

        final MergeThingResponse mergeDeleteAttributesResponse =
                toResponse(MergeThing.withAttributes(THING_ID, ThingsModelFactory.nullAttributes(), DITTO_HEADERS));
        writeResponseJson(commandsDir.resolve(Paths.get("mergeDeleteAttributesResponse.json")),
                mergeDeleteAttributesResponse);

        final MergeThingResponse mergeAttributeResponse =
                toResponse(MergeThing.withAttribute(THING_ID, ATTRIBUTE_POINTER, ATTRIBUTE_VALUE, DITTO_HEADERS));
        writeResponseJson(commandsDir.resolve(Paths.get("mergeAttributeResponse.json")), mergeAttributeResponse);

        final MergeThingResponse mergeDeleteAttributeResponse = toResponse(
                MergeThing.withAttribute(THING_ID, ATTRIBUTE_POINTER, JsonValue.nullLiteral(), DITTO_HEADERS));
        writeResponseJson(commandsDir.resolve(Paths.get("mergeDeleteAttributeResponse.json")),
                mergeDeleteAttributeResponse);

        final MergeThingResponse mergeFeatureResponse =
                toResponse(MergeThing.withFeature(THING_ID, FLUX_CAPACITOR, DITTO_HEADERS));
        writeResponseJson(commandsDir.resolve(Paths.get("mergeFeatureResponse.json")), mergeFeatureResponse);

        final MergeThingResponse mergeDeleteFeatureResponse =
                toResponse(MergeThing.withFeature(THING_ID, ThingsModelFactory.nullFeature(FEATURE_ID), DITTO_HEADERS));
        writeResponseJson(commandsDir.resolve(Paths.get("mergeDeleteFeatureResponse.json")),
                mergeDeleteFeatureResponse);

        final MergeThingResponse mergeFeaturesResponse =
                toResponse(MergeThing.withFeatures(THING_ID, FEATURES, DITTO_HEADERS));
        writeResponseJson(commandsDir.resolve(Paths.get("mergeFeaturesResponse.json")), mergeFeaturesResponse);

        final MergeThingResponse mergeDeleteFeaturesResponse =
                toResponse(MergeThing.withFeatures(THING_ID, ThingsModelFactory.nullFeatures(), DITTO_HEADERS));
        writeResponseJson(commandsDir.resolve(Paths.get("mergeDeleteFeaturesResponse.json")),
                mergeDeleteFeaturesResponse);

        final MergeThingResponse mergeFeatureDefinitionResponse =
                toResponse(MergeThing.withFeatureDefinition(THING_ID, FEATURE_ID, FEATURE_DEFINITION, DITTO_HEADERS));
        writeResponseJson(commandsDir.resolve(Paths.get("mergeFeatureDefinitionResponse.json")),
                mergeFeatureDefinitionResponse);

        final MergeThingResponse mergeDeleteFeatureDefinitionResponse = toResponse(
                MergeThing.withFeatureDefinition(THING_ID, FEATURE_ID, ThingsModelFactory.nullFeatureDefinition(),
                        DITTO_HEADERS));
        writeResponseJson(commandsDir.resolve(Paths.get("mergeDeleteFeatureDefinitionResponse.json")),
                mergeDeleteFeatureDefinitionResponse);

        final MergeThingResponse mergeFeaturePropertiesResponse =
                toResponse(MergeThing.withFeatureProperties(THING_ID, FEATURE_ID, FEATURE_PROPERTIES, DITTO_HEADERS));
        writeResponseJson(commandsDir.resolve(Paths.get("mergeFeaturePropertiesResponse.json")),
                mergeFeaturePropertiesResponse);

        final MergeThingResponse mergeDeleteFeaturePropertiesResponse = toResponse(
                MergeThing.withFeatureProperties(THING_ID, FEATURE_ID, ThingsModelFactory.nullFeatureProperties(),
                        DITTO_HEADERS));
        writeResponseJson(commandsDir.resolve(Paths.get("mergeDeleteFeaturePropertiesResponse.json")),
                mergeDeleteFeaturePropertiesResponse);

        final MergeThingResponse mergeFeatureDesiredPropertiesResponse = toResponse(
                MergeThing.withFeatureDesiredProperties(THING_ID, FEATURE_ID, FEATURE_PROPERTIES, DITTO_HEADERS));
        writeResponseJson(commandsDir.resolve(Paths.get("mergeFeatureDesiredPropertiesResponse.json")),
                mergeFeatureDesiredPropertiesResponse);

        final MergeThingResponse mergeDeleteFeatureDesiredPropertiesResponse = toResponse(
                MergeThing.withFeatureDesiredProperties(THING_ID, FEATURE_ID,
                        ThingsModelFactory.nullFeatureProperties(), DITTO_HEADERS));
        writeResponseJson(commandsDir.resolve(Paths.get("mergeDeleteFeatureDesiredPropertiesResponse.json")),
                mergeDeleteFeatureDesiredPropertiesResponse);

        final MergeThingResponse mergeFeaturePropertyResponse = toResponse(
                MergeThing.withFeatureProperty(THING_ID, FEATURE_ID, PROPERTY_POINTER, PROPERTY_VALUE, DITTO_HEADERS));
        writeResponseJson(commandsDir.resolve(Paths.get("mergeFeaturePropertyResponse.json")),
                mergeFeaturePropertyResponse);

        final MergeThingResponse mergeDeleteFeaturePropertyResponse = toResponse(
                MergeThing.withFeatureProperty(THING_ID, FEATURE_ID, PROPERTY_POINTER, JsonValue.nullLiteral(),
                        DITTO_HEADERS));
        writeResponseJson(commandsDir.resolve(Paths.get("mergeDeleteFeaturePropertyResponse.json")),
                mergeDeleteFeaturePropertyResponse);

        final MergeThingResponse mergeFeatureDesiredPropertyResponse = toResponse(
                MergeThing.withFeatureDesiredProperty(THING_ID, FEATURE_ID, PROPERTY_POINTER, PROPERTY_VALUE,
                        DITTO_HEADERS));
        writeResponseJson(commandsDir.resolve(Paths.get("mergeFeatureDesiredPropertyResponse.json")),
                mergeFeatureDesiredPropertyResponse);

        final MergeThingResponse mergeDeleteFeatureDesiredPropertyResponse = toResponse(
                MergeThing.withFeatureDesiredProperty(THING_ID, FEATURE_ID, PROPERTY_POINTER, JsonValue.nullLiteral(),
                        DITTO_HEADERS));
        writeResponseJson(commandsDir.resolve(Paths.get("mergeDeleteFeatureDesiredPropertyResponse.json")),
                mergeDeleteFeatureDesiredPropertyResponse);
    }

    private void produceThingEvents(final Path rootPath) throws IOException {
        System.out.println("--- produce thing events ---------------------------------------------------------");
        final Path eventsDir = rootPath.resolve(Paths.get("events"));
        Files.createDirectories(eventsDir);

        final ThingCreated thingCreated = ThingCreated.of(THING, REVISION_NUMBER, DITTO_HEADERS);
        writeJson(eventsDir.resolve(Paths.get("thingCreated.json")), thingCreated);

        final ThingModified thingModified = ThingModified.of(THING, REVISION_NUMBER, DITTO_HEADERS);
        writeJson(eventsDir.resolve(Paths.get("thingModified.json")), thingModified);

        final ThingDeleted thingDeleted = ThingDeleted.of(THING_ID, REVISION_NUMBER, DITTO_HEADERS);
        writeJson(eventsDir.resolve(Paths.get("thingDeleted.json")), thingDeleted);

        final ThingDefinitionCreated thingDefinitionCreated =
                ThingDefinitionCreated.of(THING_ID, THING_DEFINITION, REVISION_NUMBER, DITTO_HEADERS);
        writeJson(eventsDir.resolve(Paths.get("thingDefinitionCreated.json")), thingDefinitionCreated);

        final ThingDefinitionModified thingDefinitionModified =
                ThingDefinitionModified.of(THING_ID, THING_DEFINITION, REVISION_NUMBER, DITTO_HEADERS);
        writeJson(eventsDir.resolve(Paths.get("thingDefinitionModified.json")), thingDefinitionModified);

        final ThingDefinitionDeleted thingDefinitionDeleted =
                ThingDefinitionDeleted.of(THING_ID, REVISION_NUMBER, DITTO_HEADERS);
        writeJson(eventsDir.resolve(Paths.get("thingDefinitionDeleted.json")), thingDefinitionDeleted);

        final AclEntryCreated aclEntryCreated = AclEntryCreated.of(THING_ID, ACL_ENTRY_1, REVISION_NUMBER,
                DITTO_HEADERS);
        writeJson(eventsDir.resolve(Paths.get("aclEntryCreated.json")), aclEntryCreated, JsonSchemaVersion.V_1);

        final AclEntryModified aclEntryModified = AclEntryModified.of(THING_ID, ACL_ENTRY_1, REVISION_NUMBER,
                DITTO_HEADERS);
        writeJson(eventsDir.resolve(Paths.get("aclEntryModified.json")), aclEntryModified, JsonSchemaVersion.V_1);

        final AclModified aclModified = AclModified.of(THING_ID, ACL, REVISION_NUMBER, DITTO_HEADERS);
        writeJson(eventsDir.resolve(Paths.get("aclModified.json")), aclModified, JsonSchemaVersion.V_1);

        final AclEntryDeleted aclEntryDeleted = AclEntryDeleted.of(THING_ID, AUTH_SUBJECT_1, REVISION_NUMBER,
                DITTO_HEADERS);
        writeJson(eventsDir.resolve(Paths.get("aclEntryDeleted.json")), aclEntryDeleted, JsonSchemaVersion.V_1);

        final PolicyIdCreated policyIdCreated =
                PolicyIdCreated.of(THING_ID, POLICY_ID, REVISION_NUMBER,
                        DITTO_HEADERS);
        writeJson(eventsDir.resolve(Paths.get("policyIdCreated.json")), policyIdCreated);

        final PolicyIdModified policyIdModified =
                PolicyIdModified.of(THING_ID, POLICY_ID, REVISION_NUMBER, DITTO_HEADERS);
        writeJson(eventsDir.resolve(Paths.get("policyIdModified.json")), policyIdModified);

        final AttributesCreated attributesCreated = AttributesCreated.of(THING_ID, ATTRIBUTES, REVISION_NUMBER,
                DITTO_HEADERS);
        writeJson(eventsDir.resolve(Paths.get("attributesCreated.json")), attributesCreated);

        final AttributesModified attributesModified = AttributesModified.of(THING_ID, ATTRIBUTES, REVISION_NUMBER,
                DITTO_HEADERS);
        writeJson(eventsDir.resolve(Paths.get("attributesModified.json")), attributesModified);

        final AttributesDeleted attributesDeleted = AttributesDeleted.of(THING_ID, REVISION_NUMBER, DITTO_HEADERS);
        writeJson(eventsDir.resolve(Paths.get("attributesDeleted.json")), attributesDeleted);

        final AttributeCreated attributeCreated = AttributeCreated.of(THING_ID, ATTRIBUTE_POINTER, ATTRIBUTE_VALUE,
                REVISION_NUMBER, DITTO_HEADERS);
        writeJson(eventsDir.resolve(Paths.get("attributeCreated.json")), attributeCreated);

        final AttributeModified attributeModified = AttributeModified.of(THING_ID, ATTRIBUTE_POINTER, ATTRIBUTE_VALUE,
                REVISION_NUMBER, DITTO_HEADERS);
        writeJson(eventsDir.resolve(Paths.get("attributeModified.json")), attributeModified);

        final AttributeDeleted attributeDeleted = AttributeDeleted.of(THING_ID, ATTRIBUTE_POINTER, REVISION_NUMBER,
                DITTO_HEADERS);
        writeJson(eventsDir.resolve(Paths.get("attributeDeleted.json")), attributeDeleted);

        final FeatureCreated featureCreated = FeatureCreated.of(THING_ID, FLUX_CAPACITOR, REVISION_NUMBER,
                DITTO_HEADERS);
        writeJson(eventsDir.resolve(Paths.get("featureCreated.json")), featureCreated);

        final FeatureModified featureModified = FeatureModified.of(THING_ID, FLUX_CAPACITOR, REVISION_NUMBER,
                DITTO_HEADERS);
        writeJson(eventsDir.resolve(Paths.get("featureModified.json")), featureModified);

        final FeatureDeleted featureDeleted = FeatureDeleted.of(THING_ID, FEATURE_ID, REVISION_NUMBER, DITTO_HEADERS);
        writeJson(eventsDir.resolve(Paths.get("featureDeleted.json")), featureDeleted);

        final FeaturesDeleted featuresDeleted = FeaturesDeleted.of(THING_ID, REVISION_NUMBER, DITTO_HEADERS);
        writeJson(eventsDir.resolve(Paths.get("featuresDeleted.json")), featuresDeleted);

        final FeaturesCreated featuresCreated = FeaturesCreated.of(THING_ID,
                ThingsModelFactory.newFeatures(FLUX_CAPACITOR), REVISION_NUMBER, DITTO_HEADERS);
        writeJson(eventsDir.resolve(Paths.get("featuresCreated.json")), featuresCreated);

        final FeaturesModified featuresModified = FeaturesModified.of(THING_ID, FEATURES, REVISION_NUMBER,
                DITTO_HEADERS);
        writeJson(eventsDir.resolve(Paths.get("featuresModified.json")), featuresModified);

        final FeatureDefinitionCreated featureDefinitionCreated = FeatureDefinitionCreated.of(THING_ID, FEATURE_ID,
                FEATURE_DEFINITION, REVISION_NUMBER, DITTO_HEADERS);
        writeJson(eventsDir.resolve(Paths.get("featureDefinitionCreated.json")), featureDefinitionCreated);

        final FeatureDefinitionModified featureDefinitionModified = FeatureDefinitionModified.of(THING_ID, FEATURE_ID,
                FEATURE_DEFINITION, REVISION_NUMBER, DITTO_HEADERS);
        writeJson(eventsDir.resolve(Paths.get("featureDefinitionModified.json")), featureDefinitionModified);

        final FeaturePropertiesCreated featurePropertiesCreated = FeaturePropertiesCreated.of(THING_ID, FEATURE_ID,
                FEATURE_PROPERTIES, REVISION_NUMBER, DITTO_HEADERS);
        writeJson(eventsDir.resolve(Paths.get("featurePropertiesCreated.json")), featurePropertiesCreated);

        final FeaturePropertiesModified featurePropertiesModified = FeaturePropertiesModified.of(THING_ID, FEATURE_ID,
                FEATURE_PROPERTIES, REVISION_NUMBER, DITTO_HEADERS);
        writeJson(eventsDir.resolve(Paths.get("featurePropertiesModified.json")), featurePropertiesModified);

        final FeaturePropertyCreated featurePropertyCreated = FeaturePropertyCreated.of(THING_ID, FEATURE_ID,
                PROPERTY_POINTER, PROPERTY_VALUE, REVISION_NUMBER, DITTO_HEADERS);
        writeJson(eventsDir.resolve(Paths.get("featurePropertyCreated.json")), featurePropertyCreated);

        final FeaturePropertyModified featurePropertyModified = FeaturePropertyModified.of(THING_ID, FEATURE_ID,
                PROPERTY_POINTER, PROPERTY_VALUE, REVISION_NUMBER, DITTO_HEADERS);
        writeJson(eventsDir.resolve(Paths.get("featurePropertyModified.json")), featurePropertyModified);

        final FeatureDefinitionDeleted featureDefinitionDeleted = FeatureDefinitionDeleted.of(THING_ID, FEATURE_ID,
                REVISION_NUMBER, DITTO_HEADERS);
        writeJson(eventsDir.resolve(Paths.get("featureDefinitionDeleted.json")), featureDefinitionDeleted);

        final FeaturePropertiesDeleted featurePropertiesDeleted = FeaturePropertiesDeleted.of(THING_ID, FEATURE_ID,
                REVISION_NUMBER, DITTO_HEADERS);
        writeJson(eventsDir.resolve(Paths.get("featurePropertiesDeleted.json")), featurePropertiesDeleted);

        final FeaturePropertyDeleted featurePropertyDeleted = FeaturePropertyDeleted.of(THING_ID, FEATURE_ID,
                PROPERTY_POINTER, REVISION_NUMBER, DITTO_HEADERS);
        writeJson(eventsDir.resolve(Paths.get("featurePropertyDeleted.json")), featurePropertyDeleted);
    }

    private ThingMerged toThingMergedEvent(final MergeThing mergeThing) {
        return ThingMerged.of(mergeThing.getEntityId(), mergeThing.getResourcePath(), mergeThing.getValue(), 42L,
                Instant.ofEpochSecond(Instant.now().getEpochSecond()), mergeThing.getDittoHeaders(), null);
    }

    private void produceThingMergedEvents(final Path rootPath) throws IOException {

        System.out.println("--- produce thing merged events ---------------------------------------------------------");

        final Path eventsDir = rootPath.resolve(Paths.get("events"));
        Files.createDirectories(eventsDir);

        final ThingMerged mergedThing = toThingMergedEvent(MergeThing.withThing(THING_ID, THING, DITTO_HEADERS));
        writeEventJson(eventsDir.resolve(Paths.get("mergedThing.json")), mergedThing);

        final ThingMerged mergedThingDefinition = toThingMergedEvent(
                MergeThing.withThingDefinition(THING_ID, ThingsModelFactory.newDefinition(THING_DEFINITION),
                        DITTO_HEADERS));
        writeEventJson(eventsDir.resolve(Paths.get("mergedThingDefinition.json")), mergedThingDefinition);

        final ThingMerged mergedDeleteThingDefinition = toThingMergedEvent(
                MergeThing.withThingDefinition(THING_ID, ThingsModelFactory.nullDefinition(), DITTO_HEADERS));
        writeEventJson(eventsDir.resolve(Paths.get("mergedDeleteThingDefinition.json")), mergedDeleteThingDefinition);

        final ThingMerged mergedPolicyId =
                toThingMergedEvent(MergeThing.withPolicyId(THING_ID, POLICY_ID, DITTO_HEADERS));
        writeEventJson(eventsDir.resolve(Paths.get("mergedPolicyId.json")), mergedPolicyId);

        final ThingMerged mergedAttributes =
                toThingMergedEvent(MergeThing.withAttributes(THING_ID, ATTRIBUTES, DITTO_HEADERS));
        writeEventJson(eventsDir.resolve(Paths.get("mergedAttributes.json")), mergedAttributes);

        final ThingMerged mergedDeleteAttributes = toThingMergedEvent(
                MergeThing.withAttributes(THING_ID, ThingsModelFactory.nullAttributes(), DITTO_HEADERS));
        writeEventJson(eventsDir.resolve(Paths.get("mergedDeleteAttributes.json")), mergedDeleteAttributes);

        final ThingMerged mergedAttribute = toThingMergedEvent(
                MergeThing.withAttribute(THING_ID, ATTRIBUTE_POINTER, ATTRIBUTE_VALUE, DITTO_HEADERS));
        writeEventJson(eventsDir.resolve(Paths.get("mergedAttribute.json")), mergedAttribute);

        final ThingMerged mergedDeleteAttribute = toThingMergedEvent(
                MergeThing.withAttribute(THING_ID, ATTRIBUTE_POINTER, JsonValue.nullLiteral(), DITTO_HEADERS));
        writeEventJson(eventsDir.resolve(Paths.get("mergedDeleteAttribute.json")), mergedDeleteAttribute);

        final ThingMerged mergedFeature =
                toThingMergedEvent(MergeThing.withFeature(THING_ID, FLUX_CAPACITOR, DITTO_HEADERS));
        writeEventJson(eventsDir.resolve(Paths.get("mergedFeature.json")), mergedFeature);

        final ThingMerged mergedDeleteFeature = toThingMergedEvent(
                MergeThing.withFeature(THING_ID, ThingsModelFactory.nullFeature(FEATURE_ID), DITTO_HEADERS));
        writeEventJson(eventsDir.resolve(Paths.get("mergedDeleteFeature.json")), mergedDeleteFeature);

        final ThingMerged mergedFeatures =
                toThingMergedEvent(MergeThing.withFeatures(THING_ID, FEATURES, DITTO_HEADERS));
        writeEventJson(eventsDir.resolve(Paths.get("mergedFeatures.json")), mergedFeatures);

        final ThingMerged mergedDeleteFeatures =
                toThingMergedEvent(MergeThing.withFeatures(THING_ID, ThingsModelFactory.nullFeatures(), DITTO_HEADERS));
        writeEventJson(eventsDir.resolve(Paths.get("mergedDeleteFeatures.json")), mergedDeleteFeatures);

        final ThingMerged mergedFeatureDefinition = toThingMergedEvent(
                MergeThing.withFeatureDefinition(THING_ID, FEATURE_ID, FEATURE_DEFINITION, DITTO_HEADERS));
        writeEventJson(eventsDir.resolve(Paths.get("mergedFeatureDefinition.json")), mergedFeatureDefinition);

        final ThingMerged mergedDeleteFeatureDefinition = toThingMergedEvent(
                MergeThing.withFeatureDefinition(THING_ID, FEATURE_ID, ThingsModelFactory.nullFeatureDefinition(),
                        DITTO_HEADERS));
        writeEventJson(eventsDir.resolve(Paths.get("mergedDeleteFeatureDefinition.json")),
                mergedDeleteFeatureDefinition);

        final ThingMerged mergedFeatureProperties = toThingMergedEvent(
                MergeThing.withFeatureProperties(THING_ID, FEATURE_ID, FEATURE_PROPERTIES, DITTO_HEADERS));
        writeEventJson(eventsDir.resolve(Paths.get("mergedFeatureProperties.json")), mergedFeatureProperties);

        final ThingMerged mergedDeleteFeatureProperties = toThingMergedEvent(
                MergeThing.withFeatureProperties(THING_ID, FEATURE_ID, ThingsModelFactory.nullFeatureProperties(),
                        DITTO_HEADERS));
        writeEventJson(eventsDir.resolve(Paths.get("mergedDeleteFeatureProperties.json")),
                mergedDeleteFeatureProperties);

        final ThingMerged mergedFeatureDesiredProperties = toThingMergedEvent(
                MergeThing.withFeatureDesiredProperties(THING_ID, FEATURE_ID, FEATURE_PROPERTIES, DITTO_HEADERS));
        writeEventJson(eventsDir.resolve(Paths.get("mergedFeatureDesiredProperties.json")),
                mergedFeatureDesiredProperties);

        final ThingMerged mergedDeleteFeatureDesiredProperties = toThingMergedEvent(
                MergeThing.withFeatureDesiredProperties(THING_ID, FEATURE_ID,
                        ThingsModelFactory.nullFeatureProperties(), DITTO_HEADERS));
        writeEventJson(eventsDir.resolve(Paths.get("mergedDeleteFeatureDesiredProperties.json")),
                mergedDeleteFeatureDesiredProperties);

        final ThingMerged mergedFeatureProperty = toThingMergedEvent(
                MergeThing.withFeatureProperty(THING_ID, FEATURE_ID, PROPERTY_POINTER, PROPERTY_VALUE, DITTO_HEADERS));
        writeEventJson(eventsDir.resolve(Paths.get("mergedFeatureProperty.json")), mergedFeatureProperty);

        final ThingMerged mergedDeleteFeatureProperty = toThingMergedEvent(
                MergeThing.withFeatureProperty(THING_ID, FEATURE_ID, PROPERTY_POINTER, JsonValue.nullLiteral(),
                        DITTO_HEADERS));
        writeEventJson(eventsDir.resolve(Paths.get("mergedDeleteFeatureProperty.json")), mergedDeleteFeatureProperty);

        final ThingMerged mergedFeatureDesiredProperty = toThingMergedEvent(
                MergeThing.withFeatureDesiredProperty(THING_ID, FEATURE_ID, PROPERTY_POINTER, PROPERTY_VALUE,
                        DITTO_HEADERS));
        writeEventJson(eventsDir.resolve(Paths.get("mergedFeatureDesiredProperty.json")), mergedFeatureDesiredProperty);

        final ThingMerged mergedDeleteFeatureDesiredProperty = toThingMergedEvent(
                MergeThing.withFeatureDesiredProperty(THING_ID, FEATURE_ID, PROPERTY_POINTER, JsonValue.nullLiteral(),
                        DITTO_HEADERS));
        writeEventJson(eventsDir.resolve(Paths.get("mergedDeleteFeatureDesiredProperty.json")),
                mergedDeleteFeatureDesiredProperty);
    }

    private void produceThingExceptions(final Path rootPath) throws IOException {
        final Path exceptionsDir = rootPath.resolve(Paths.get("exceptions"));
        Files.createDirectories(exceptionsDir);

        final ThingIdInvalidException thingIdInvalidException = ThingIdInvalidException.newBuilder("invalid id")
                .dittoHeaders(DITTO_HEADERS)
                .build();
        writeJson(exceptionsDir.resolve(Paths.get("thingIdInvalidException.json")), thingIdInvalidException);

        final PolicyIdMissingException policyIdMissingException = PolicyIdMissingException
                .fromThingIdOnUpdate(THING_ID, DITTO_HEADERS);
        writeJson(exceptionsDir.resolve(Paths.get("policyIdMissingException.json")), policyIdMissingException);

        final AttributesNotAccessibleException attributesNotAccessibleException =
                AttributesNotAccessibleException.newBuilder(THING_ID)
                        .dittoHeaders(DITTO_HEADERS)
                        .build();
        writeJson(exceptionsDir.resolve(Paths.get("attributesNotAccessibleException.json")),
                attributesNotAccessibleException);

        final AttributesNotModifiableException attributesNotModifiableException =
                AttributesNotModifiableException.newBuilder(THING_ID)
                        .dittoHeaders(DITTO_HEADERS)
                        .build();
        writeJson(exceptionsDir.resolve(Paths.get("attributesNotModifiableException.json")),
                attributesNotModifiableException);

        final AttributeNotAccessibleException attributeNotAccessibleException = AttributeNotAccessibleException
                .newBuilder(THING_ID, ATTRIBUTE_POINTER).dittoHeaders(DITTO_HEADERS).build();
        writeJson(exceptionsDir.resolve(Paths.get("attributeNotAccessibleException.json")),
                attributeNotAccessibleException);

        final AttributeNotModifiableException attributeNotModifiableException = AttributeNotModifiableException
                .newBuilder(THING_ID, ATTRIBUTE_POINTER).dittoHeaders(DITTO_HEADERS).build();
        writeJson(exceptionsDir.resolve(Paths.get("attributeNotModifiableException.json")),
                attributeNotModifiableException);

        final FeaturesNotAccessibleException featuresNotAccessibleException =
                FeaturesNotAccessibleException.newBuilder(THING_ID)
                        .dittoHeaders(DITTO_HEADERS)
                        .build();
        writeJson(exceptionsDir.resolve(Paths.get("featuresNotAccessibleException.json")),
                featuresNotAccessibleException);

        final FeaturesNotModifiableException featuresNotModifiableException =
                FeaturesNotModifiableException.newBuilder(THING_ID)
                        .dittoHeaders(DITTO_HEADERS)
                        .build();
        writeJson(exceptionsDir.resolve(Paths.get("featuresNotModifiableException.json")),
                featuresNotModifiableException);

        final FeatureNotAccessibleException featureNotAccessibleException = FeatureNotAccessibleException
                .newBuilder(THING_ID, FEATURE_ID).dittoHeaders(DITTO_HEADERS).build();
        writeJson(exceptionsDir.resolve(Paths.get("featureNotAccessibleException.json")),
                featureNotAccessibleException);

        final FeatureNotModifiableException featureNotModifiableException = FeatureNotModifiableException
                .newBuilder(THING_ID, FEATURE_ID).dittoHeaders(DITTO_HEADERS).build();
        writeJson(exceptionsDir.resolve(Paths.get("featureNotModifiableException.json")),
                featureNotModifiableException);

        final FeatureDefinitionEmptyException featureDefinitionEmptyException =
                FeatureDefinitionEmptyException.newBuilder().dittoHeaders(DITTO_HEADERS).build();
        writeJson(exceptionsDir.resolve(Paths.get("featureDefinitionEmptyException.json")),
                featureDefinitionEmptyException);

        final DefinitionIdentifierInvalidException definitionIdentifierInvalidException =
                DefinitionIdentifierInvalidException.newBuilder("foo:bar")
                        .dittoHeaders(DITTO_HEADERS).build();
        writeJson(exceptionsDir.resolve(Paths.get("definitionIdentifierInvalidException.json")),
                definitionIdentifierInvalidException);

        final FeatureDefinitionNotAccessibleException featureDefinitionNotAccessibleException =
                FeatureDefinitionNotAccessibleException.newBuilder(THING_ID, FEATURE_ID)
                        .dittoHeaders(DITTO_HEADERS).build();
        writeJson(exceptionsDir.resolve(Paths.get("featureDefinitionNotAccessibleException.json")),
                featureDefinitionNotAccessibleException);

        final FeatureDefinitionNotModifiableException featureDefinitionNotModifiableException =
                FeatureDefinitionNotModifiableException.newBuilder(THING_ID, FEATURE_ID)
                        .dittoHeaders(DITTO_HEADERS).build();
        writeJson(exceptionsDir.resolve(Paths.get("featureDefinitionNotModifiableException.json")),
                featureDefinitionNotModifiableException);

        final FeaturePropertiesNotAccessibleException featurePropertiesNotAccessibleException =
                FeaturePropertiesNotAccessibleException.newBuilder(THING_ID, FEATURE_ID)
                        .dittoHeaders(DITTO_HEADERS).build();
        writeJson(exceptionsDir.resolve(Paths.get("featurePropertiesNotAccessibleException.json")),
                featurePropertiesNotAccessibleException);

        final FeaturePropertiesNotModifiableException featurePropertiesNotModifiableException =
                FeaturePropertiesNotModifiableException.newBuilder(THING_ID, FEATURE_ID)
                        .dittoHeaders(DITTO_HEADERS).build();
        writeJson(exceptionsDir.resolve(Paths.get("featurePropertiesNotModifiableException.json")),
                featurePropertiesNotModifiableException);

        final FeaturePropertyNotAccessibleException featurePropertyNotAccessibleException =
                FeaturePropertyNotAccessibleException.newBuilder(THING_ID, FEATURE_ID, PROPERTY_POINTER)
                        .dittoHeaders(DITTO_HEADERS).build();
        writeJson(exceptionsDir.resolve(Paths.get("featurePropertyNotAccessibleException.json")),
                featurePropertyNotAccessibleException);

        final FeaturePropertyNotModifiableException featurePropertyNotModifiableException =
                FeaturePropertyNotModifiableException.newBuilder(THING_ID, FEATURE_ID, PROPERTY_POINTER)
                        .dittoHeaders(DITTO_HEADERS).build();
        writeJson(exceptionsDir.resolve(Paths.get("featurePropertyNotModifiableException.json")),
                featurePropertyNotModifiableException);

        final ThingConflictException thingConflictException = ThingConflictException.newBuilder(THING_ID).build();
        writeJson(exceptionsDir.resolve(Paths.get("thingConflictException.json")), thingConflictException);

        final ThingIdNotExplicitlySettableException thingIdNotExplicitlySettableExceptionPost =
                ThingIdNotExplicitlySettableException.forPostMethod().build();
        writeJson(exceptionsDir.resolve(Paths.get("thingIdNotExplicitlySettableException_post.json")),
                thingIdNotExplicitlySettableExceptionPost);
        final ThingIdNotExplicitlySettableException thingIdNotExplicitlySettableExceptionPut =
                ThingIdNotExplicitlySettableException.forPutMethod().build();
        writeJson(exceptionsDir.resolve(Paths.get("thingIdNotExplicitlySettableException_put.json")),
                thingIdNotExplicitlySettableExceptionPut);

        final ThingNotAccessibleException thingNotAccessibleException =
                ThingNotAccessibleException.newBuilder(THING_ID).dittoHeaders(DITTO_HEADERS).build();
        writeJson(exceptionsDir.resolve(Paths.get("thingNotAccessibleException.json")), thingNotAccessibleException);

        final ThingNotCreatableException thingNotCreatableException =
                ThingNotCreatableException.newBuilderForPolicyMissing(THING_ID, POLICY_ID)
                        .dittoHeaders(DITTO_HEADERS)
                        .build();
        writeJson(exceptionsDir.resolve(Paths.get("thingNotCreatableException.json")), thingNotCreatableException);

        final ThingNotModifiableException thingNotModifiableException =
                ThingNotModifiableException.newBuilder(THING_ID).dittoHeaders(DITTO_HEADERS).build();
        writeJson(exceptionsDir.resolve(Paths.get("thingNotModifiableException.json")), thingNotModifiableException);

        final PolicyIdNotModifiableException policyIdNotModifiableException =
                PolicyIdNotModifiableException.newBuilder(THING_ID).dittoHeaders(DITTO_HEADERS).build();
        writeJson(exceptionsDir.resolve(Paths.get("policyIdNotModifiableException.json")),
                policyIdNotModifiableException);

        final PolicyIdNotAllowedException policyIdNotAllowedException =
                PolicyIdNotAllowedException.newBuilder(THING_ID).dittoHeaders(DITTO_HEADERS).build();
        writeJson(exceptionsDir.resolve(Paths.get("policyIdNotAllowedException.json")),
                policyIdNotAllowedException);

        final PolicyNotAllowedException policyNotAllowedException =
                PolicyNotAllowedException.newBuilder(THING_ID).dittoHeaders(DITTO_HEADERS).build();
        writeJson(exceptionsDir.resolve(Paths.get("policyNotAllowedException.json")),
                policyNotAllowedException);

        final ThingNotDeletableException thingNotDeletableException =
                ThingNotDeletableException.newBuilder(THING_ID).dittoHeaders(DITTO_HEADERS).build();
        writeJson(exceptionsDir.resolve(Paths.get("thingNotDeletableException.json")), thingNotDeletableException);

        final AclInvalidException aclInvalidException =
                AclInvalidException.newBuilder(THING_ID).dittoHeaders(DITTO_HEADERS).build();
        writeJson(exceptionsDir.resolve(Paths.get("aclInvalidException.json")), aclInvalidException,
                JsonSchemaVersion.V_1);

        final AclEntryInvalidException aclEntryInvalidException = AclEntryInvalidException.newBuilder()
                .dittoHeaders(DITTO_HEADERS)
                .build();
        writeJson(exceptionsDir.resolve(Paths.get("aclEntryInvalidException.json")), aclEntryInvalidException,
                JsonSchemaVersion.V_1);

        final AclNotAllowedException aclNotAllowedException = AclNotAllowedException.newBuilder(THING_ID)
                .dittoHeaders(DITTO_HEADERS)
                .build();
        writeJson(exceptionsDir.resolve(Paths.get("aclNotAllowedException.json")), aclNotAllowedException,
                JsonSchemaVersion.V_1);

        final AclModificationInvalidException aclModificationInvalidException =
                AclModificationInvalidException.newBuilder(THING_ID)
                        .dittoHeaders(DITTO_HEADERS)
                        .build();
        writeJson(exceptionsDir.resolve(Paths.get("aclModificationInvalidException.json")),
                aclModificationInvalidException, JsonSchemaVersion.V_1);

        final AuthorizationSubject authorizationSubject = newAuthSubject("the_acl_subject");
        final AclNotAccessibleException aclNotAccessibleException = AclNotAccessibleException
                .newBuilder(THING_ID, authorizationSubject).dittoHeaders(DITTO_HEADERS).build();
        writeJson(exceptionsDir.resolve(Paths.get("aclNotAccessibleException.json")), aclNotAccessibleException,
                JsonSchemaVersion.V_1);

        final AclNotModifiableException aclNotModifiableException =
                AclNotModifiableException.newBuilder(THING_ID).dittoHeaders(DITTO_HEADERS).build();
        writeJson(exceptionsDir.resolve(Paths.get("aclNotModifiableException.json")), aclNotModifiableException,
                JsonSchemaVersion.V_1);

        final ThingTooManyModifyingRequestsException thingTooManyModifyingRequestsException =
                ThingTooManyModifyingRequestsException.newBuilder(THING_ID)
                        .dittoHeaders(DITTO_HEADERS)
                        .build();
        writeJson(exceptionsDir.resolve(Paths.get("thingTooManyModifyingRequestsException.json")),
                thingTooManyModifyingRequestsException);

        final ThingUnavailableException thingUnavailableException =
                ThingUnavailableException.newBuilder(THING_ID).dittoHeaders(DITTO_HEADERS).build();
        writeJson(exceptionsDir.resolve(Paths.get("thingUnavailableException.json")), thingUnavailableException);

        final ThingIdNotDeletableException thingIdNotDeletableException =
                ThingIdNotDeletableException.newBuilder().dittoHeaders(DITTO_HEADERS).build();
        writeJson(exceptionsDir.resolve(Paths.get("thingIdNotDeletableException.json")), thingIdNotDeletableException);

        final PolicyIdNotDeletableException policyIdNotDeletableException =
                PolicyIdNotDeletableException.newBuilder().dittoHeaders(DITTO_HEADERS).build();
        writeJson(exceptionsDir.resolve(Paths.get("thingPolicyIdNotDeletableException.json")),
                policyIdNotDeletableException);

        final ThingErrorResponse thingErrorResponse = ThingErrorResponse.of(thingNotAccessibleException);
        writeJson(exceptionsDir.resolve(Paths.get("thingErrorResponse.json")), thingErrorResponse);
    }

    private void produceSearchModel(final Path rootPath) throws IOException {
        final Path modelDir = rootPath.resolve(Paths.get("model"));
        Files.createDirectories(modelDir);

        final Thing thing = ThingsModelFactory.newThingBuilder().setId(ThingId.of("default", "thing1"))
                .setAttribute(JsonFactory.newPointer("temperature"), JsonFactory.newValue(35L)).build();
        final Thing thing2 = ThingsModelFactory.newThingBuilder().setId(ThingId.of("default", "thing2"))
                .setAttribute(JsonFactory.newPointer("temperature"), JsonFactory.newValue(35L)).build();
        final JsonArray items = JsonFactory.newArrayBuilder().add(thing.toJson(), thing2.toJson()).build();
        writeJson(modelDir.resolve(Paths.get("search-model.json")),
                SearchModelFactory.newSearchResultBuilder().addAll(items).build());
    }

    private void produceSearchCommands(final Path rootPath) throws IOException {
        final Path commandsDir = rootPath.resolve(Paths.get("commands"));
        Files.createDirectories(commandsDir);

        final Set<String> knownNamespaces = new HashSet<>(Collections.singletonList("org.eclipse.ditto"));

        final SearchQuery searchQuery =
                SearchModelFactory.newSearchQueryBuilder(SearchModelFactory.property("attributes/temperature").eq(32))
                        .build();

        final String optionString = "size(10),sort(+thingId)";

        final QueryThings queryThingsCommand = QueryThings.of(searchQuery.getFilterAsString(),
                Collections.singletonList(optionString),
                JsonFactory.newFieldSelector("attributes", JsonFactory.newParseOptionsBuilder()
                        .withoutUrlDecoding()
                        .build()),
                knownNamespaces,
                DittoHeaders.empty());

        writeJson(commandsDir.resolve(Paths.get("query-things-command.json")), queryThingsCommand);

        final CountThings countThingsCommand = CountThings.of(searchQuery.getFilterAsString(), knownNamespaces,
                DittoHeaders.empty());

        writeJson(commandsDir.resolve(Paths.get("count-things-command.json")), countThingsCommand);

        final CreateSubscription createSubscriptionCommand = CreateSubscription.of(searchQuery.getFilterAsString(),
                optionString,
                JsonFactory.newFieldSelector("attributes", JsonFactory.newParseOptionsBuilder()
                        .withoutUrlDecoding()
                        .build()),
                knownNamespaces,
                headersWithCorrelationIdFor(CreateSubscription.TYPE));
        writeJson(commandsDir.resolve(Paths.get("create-subscription-command.json")), createSubscriptionCommand);
        final RequestFromSubscription requestFromSubscriptionCommand =
                RequestFromSubscription.of("24601", 3, DittoHeaders.empty());
        writeJson(commandsDir.resolve(Paths.get("request-subscription-command.json")), requestFromSubscriptionCommand);
        final CancelSubscription cancelSubscriptionCommand = CancelSubscription.of("24601", DittoHeaders.empty());
        writeJson(commandsDir.resolve(Paths.get("cancel-subscription-command.json")), cancelSubscriptionCommand);
    }

    private void produceSearchCommandResponses(final Path rootPath) throws IOException {
        final Path commandsDir = rootPath.resolve(Paths.get("responses"));
        Files.createDirectories(commandsDir);


        final Thing thing = ThingsModelFactory.newThingBuilder()
                .setId(ThingId.of("default", "thing1"))
                .setAttribute(JsonFactory.newPointer("temperature"), JsonFactory.newValue(35L))
                .build();
        final Thing thing2 = ThingsModelFactory.newThingBuilder()
                .setId(ThingId.of("default", "thing2"))
                .setAttribute(JsonFactory.newPointer("temperature"), JsonFactory.newValue(35L))
                .build();
        final JsonArray array = JsonFactory.newArrayBuilder()
                .add(thing.toJson())
                .add(thing2.toJson())
                .build();

        final SearchResult result = SearchModelFactory.newSearchResult(array, -1L);
        final QueryThingsResponse queryThingsResponse = QueryThingsResponse.of(result, DittoHeaders.empty());
        writeJson(commandsDir.resolve(Paths.get("query-things-response.json")), queryThingsResponse);

        final CountThingsResponse countThingsResponse = CountThingsResponse.of(42, DittoHeaders.empty());
        writeJson(commandsDir.resolve(Paths.get("count-things-response.json")), countThingsResponse);
    }

    private void produceSearchEvents(final Path rootPath) throws IOException {
        final Path commandsDir = rootPath.resolve(Paths.get("events"));
        Files.createDirectories(commandsDir);

        final Thing thing = ThingsModelFactory.newThingBuilder()
                .setId(ThingId.of("default", "thing1"))
                .setAttribute(JsonFactory.newPointer("temperature"), JsonFactory.newValue(35L))
                .build();
        final Thing thing2 = ThingsModelFactory.newThingBuilder()
                .setId(ThingId.of("default", "thing2"))
                .setAttribute(JsonFactory.newPointer("temperature"), JsonFactory.newValue(35L))
                .build();
        final JsonArray array = JsonFactory.newArrayBuilder()
                .add(thing.toJson())
                .add(thing2.toJson())
                .build();

        final SubscriptionCreated subscriptionCreatedEvent =
                SubscriptionCreated.of("24601", headersWithCorrelationIdFor(CreateSubscription.TYPE));
        writeJson(commandsDir.resolve(Paths.get("subscription-created-event.json")), subscriptionCreatedEvent);

        final SubscriptionHasNextPage subscriptionHasNextPageEvent =
                SubscriptionHasNextPage.of("24601", array, DittoHeaders.empty());
        writeJson(commandsDir.resolve(Paths.get("subscription-has-next-event.json")), subscriptionHasNextPageEvent);

        final SubscriptionComplete subscriptionCompleteEvent = SubscriptionComplete.of("24601", DittoHeaders.empty());
        writeJson(commandsDir.resolve(Paths.get("subscription-complete-event.json")), subscriptionCompleteEvent);

        final SubscriptionFailed subscriptionFailedEvent = SubscriptionFailed.of(
                "24601",
                SubscriptionProtocolErrorException.newBuilder()
                        .message("Rule 3.9: While the Subscription is not cancelled, Subscription.request(long n) " +
                                "MUST signal onError with a java.lang.IllegalArgumentException if the argument is " +
                                "<= 0. The cause message SHOULD explain that non-positive request signals are illegal.")
                        .description("The intent of this rule is to prevent faulty implementations to proceed " +
                                "operation without any exceptions being raised. Requesting a negative or 0 number of " +
                                "elements, since requests are additive, most likely to be the result of an erroneous " +
                                "calculation on the behalf of the Subscriber.")
                        .build(),
                DittoHeaders.empty()
        );
        writeJson(commandsDir.resolve(Paths.get("subscription-failed-event.json")), subscriptionFailedEvent);
    }

    private void produceSearchExceptions(final Path rootPath) throws IOException {
        final Path exceptionsDir = rootPath.resolve(Paths.get("exceptions"));
        Files.createDirectories(exceptionsDir);

        final InvalidOptionException invalidOptionException = InvalidOptionException.newBuilder().build();
        writeJson(exceptionsDir.resolve(Paths.get("invalidOptionException.json")), invalidOptionException);
    }

    private void produceMessageExceptions(final Path rootPath) throws IOException {
        final Path exceptionsDir = rootPath.resolve(Paths.get("exceptions"));
        Files.createDirectories(exceptionsDir);

        final MessageTimeoutException messageTimeoutException = MessageTimeoutException.newBuilder(60L).build();
        writeJson(exceptionsDir.resolve(Paths.get("messageTimeoutException.json")), messageTimeoutException);

        final SubjectInvalidException subjectInvalidException = SubjectInvalidException.newBuilder("invalid subject")
                .build();
        writeJson(exceptionsDir.resolve(Paths.get("subjectInvalidException.json")), subjectInvalidException);

        final TimeoutInvalidException timeoutInvalidException = TimeoutInvalidException.newBuilder(120L, 60L).build();
        writeJson(exceptionsDir.resolve(Paths.get("timeoutInvalidException.json")), timeoutInvalidException);
    }

    private void produceGatewayExceptions(final Path rootPath) throws IOException {
        final Path exceptionsDir = rootPath.resolve(Paths.get("exceptions"));
        Files.createDirectories(exceptionsDir);

        final GatewayAuthenticationFailedException gatewayAuthenticationFailedException =
                GatewayAuthenticationFailedException.newBuilder("devops authentication failed!").build();
        writeJson(exceptionsDir.resolve(Paths.get("gatewayAuthenticationFailedException.json")),
                gatewayAuthenticationFailedException);

        final GatewayAuthenticationProviderUnavailableException gatewayAuthenticationProviderUnavailableException =
                GatewayAuthenticationProviderUnavailableException.newBuilder().build();
        writeJson(exceptionsDir.resolve(Paths.get("gatewayAuthenticationProviderUnavailableException.json")),
                gatewayAuthenticationProviderUnavailableException);

        final GatewayMethodNotAllowedException gatewayMethodNotAllowedException =
                GatewayMethodNotAllowedException.newBuilder("POST").build();
        writeJson(exceptionsDir.resolve(Paths.get("gatewayMethodNotAllowedException.json")),
                gatewayMethodNotAllowedException);

        final GatewayServiceTimeoutException gatewayServiceTimeoutException =
                GatewayServiceTimeoutException.newBuilder().build();
        writeJson(exceptionsDir.resolve(Paths.get("gatewayServiceTimeoutException.json")),
                gatewayServiceTimeoutException);

        final GatewayServiceUnavailableException gatewayServiceUnavailableException =
                GatewayServiceUnavailableException.newBuilder().build();
        writeJson(exceptionsDir.resolve(Paths.get("gatewayServiceUnavailableException.json")),
                gatewayServiceUnavailableException);

        final GatewayServiceTooManyRequestsException gatewayServiceTooManyRequestsException =
                GatewayServiceTooManyRequestsException.newBuilder().build();
        writeJson(exceptionsDir.resolve(Paths.get("gatewayServiceTooManyRequestsException.json")),
                gatewayServiceTooManyRequestsException);
    }

    private void produceJsonExceptions(final Path rootPath) throws IOException {
        final Path exceptionsDir = rootPath.resolve(Paths.get("exceptions"));
        Files.createDirectories(exceptionsDir);

        final DittoJsonException jsonFieldSelectorInvalidException =
                new DittoJsonException(JsonFieldSelectorInvalidException.newBuilder().fieldSelector("foo(bar").build(),
                        DITTO_HEADERS);
        writeJson(exceptionsDir.resolve(Paths.get("jsonFieldSelectorInvalidException.json")),
                jsonFieldSelectorInvalidException);

        final DittoJsonException jsonPointerInvalidException = new DittoJsonException(
                JsonPointerInvalidException.newBuilder().jsonPointer("").build(), DITTO_HEADERS);
        writeJson(exceptionsDir.resolve(Paths.get("jsonPointerInvalidException.json")), jsonPointerInvalidException);

        final DittoJsonException jsonMissingFieldException = new DittoJsonException(
                JsonMissingFieldException.newBuilder().fieldName("attributes").build(), DITTO_HEADERS);
        writeJson(exceptionsDir.resolve(Paths.get("jsonMissingFieldException.json")), jsonMissingFieldException);

        final DittoJsonException jsonParseException = new DittoJsonException(
                JsonParseException.newBuilder().message("Could not read 'foo'").build(), DITTO_HEADERS);
        writeJson(exceptionsDir.resolve(Paths.get("jsonParseException.json")), jsonParseException);
    }

    private void writeResponseJson(final Path path, final Jsonifiable.WithPredicate<JsonObject, JsonField> jsonifiable)
            throws IOException {
        writeJson(path, jsonifiable, EXCLUDED_RESPONSE_HEADERS);
    }

    private void writeEventJson(final Path path, final Jsonifiable.WithPredicate<JsonObject, JsonField> jsonifiable)
            throws IOException {
        writeJson(path, jsonifiable, EXCLUDED_EVENT_HEADERS);
    }

    protected void writeJson(final Path path, final Jsonifiable.WithPredicate<JsonObject, JsonField> jsonifiable)
            throws IOException {
        writeJson(path, jsonifiable, JsonSchemaVersion.LATEST);
    }

    protected void writeJson(final Path path, final Jsonifiable.WithPredicate<JsonObject, JsonField> jsonifiable,
            final JsonSchemaVersion schemaVersion) throws IOException {
        writeJson(path, jsonifiable, schemaVersion, Collections.emptyList());
    }

    protected void writeJson(final Path path, final Jsonifiable.WithPredicate<JsonObject, JsonField> jsonifiable,
            final List<DittoHeaderDefinition> excludedHeaders) throws IOException {
        writeJson(path, jsonifiable, JsonSchemaVersion.LATEST, excludedHeaders);
    }

    protected void writeJson(final Path path, final Jsonifiable.WithPredicate<JsonObject, JsonField> jsonifiable,
            final JsonSchemaVersion schemaVersion, final List<DittoHeaderDefinition> excludedHeaders)
            throws IOException {
        final JsonObject jsonObject = jsonifiable.toJson(schemaVersion);
        final JsonObject headersExcluded = excludeHeaders(jsonObject, excludedHeaders);
        System.out.println("Writing file: " + path.toAbsolutePath());
        Files.write(path, headersExcluded.toString().getBytes());
    }

    private DittoHeaders headersWithCorrelationIdFor(final String commandType) {
        return DittoHeaders.newBuilder()
                .correlationId(UUID.nameUUIDFromBytes(commandType.getBytes()).toString())
                .build();
    }

    JsonObject excludeHeaders(final JsonObject initialObject, final List<DittoHeaderDefinition> excludedHeaders) {
        final JsonObject headers = initialObject.getValue(JsonifiableAdaptable.JsonFields.HEADERS)
                .filter(JsonValue::isObject)
                .map(JsonValue::asObject)
                .orElse(JsonObject.empty());
        final JsonObject filteredHeaders = headers.stream()
                .filter(filterHeader(excludedHeaders))
                .collect(JsonCollectors.fieldsToObject());
        return initialObject.set(JsonifiableAdaptable.JsonFields.HEADERS, filteredHeaders);
    }

    static Predicate<JsonField> filterHeader(final List<DittoHeaderDefinition> definitions) {
        return field -> definitions.stream().noneMatch(d -> d.getKey().equals(field.getKeyName()));
    }
}
