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
package org.eclipse.ditto.signals.commands.things.examplejson;

import static org.eclipse.ditto.model.base.auth.AuthorizationModelFactory.newAuthSubject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.base.json.Jsonifiable;
import org.eclipse.ditto.model.things.AccessControlList;
import org.eclipse.ditto.model.things.AclEntry;
import org.eclipse.ditto.model.things.AclEntryInvalidException;
import org.eclipse.ditto.model.things.AclInvalidException;
import org.eclipse.ditto.model.things.AclNotAllowedException;
import org.eclipse.ditto.model.things.Attributes;
import org.eclipse.ditto.model.things.Feature;
import org.eclipse.ditto.model.things.FeatureDefinition;
import org.eclipse.ditto.model.things.FeatureProperties;
import org.eclipse.ditto.model.things.Features;
import org.eclipse.ditto.model.things.Permission;
import org.eclipse.ditto.model.things.PolicyIdMissingException;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingIdInvalidException;
import org.eclipse.ditto.model.things.ThingLifecycle;
import org.eclipse.ditto.model.things.ThingRevision;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.signals.commands.things.TestConstants;
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
import org.eclipse.ditto.signals.commands.things.exceptions.PolicyIdNotModifiableException;
import org.eclipse.ditto.signals.commands.things.exceptions.PolicyNotAllowedException;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingConflictException;
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
import org.eclipse.ditto.signals.commands.things.modify.DeleteThingResponse;
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


public class JsonExamplesProducer {

    private static final String FEATURE_ID = "accelerometer";
    private static final String NAMESPACE = "com.acme";

    /*
     * Thing
     */
    private static final String THING_ID = NAMESPACE + ":xdk_53";
    private static final String POLICY_ID = NAMESPACE + ":policy0815";
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
    private static final long REVISION_NUMBER = 1L;
    private static final ThingRevision THING_REVISION = ThingsModelFactory.newThingRevision(REVISION_NUMBER);
    private static final String PROPERTY_X = "x";
    private static final JsonPointer PROPERTY_POINTER = JsonFactory.newPointer(PROPERTY_X);
    private static final JsonValue PROPERTY_VALUE = JsonFactory.newValue(42);
    private static final FeatureDefinition FEATURE_DEFINITION =
            FeatureDefinition.fromIdentifier("org.eclipse.ditto:fluxcapacitor:1.0.0");
    private static final FeatureProperties FEATURE_PROPERTIES = ThingsModelFactory.newFeaturePropertiesBuilder()
            .set("x", 3.141)
            .set("y", 2.718)
            .set("z", 1)
            .set("unit", "g")
            .build();
    private static final Feature FLUX_CAPACITOR = ThingsModelFactory.newFeatureBuilder()
            .properties(FEATURE_PROPERTIES)
            .withId(FEATURE_ID)
            .build();
    private static final Features FEATURES = ThingsModelFactory.newFeatures(FLUX_CAPACITOR);
    private static final Thing THING = ThingsModelFactory.newThingBuilder()
            .setId(THING_ID)
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
        produceThingCommands(rootPath.resolve("things"));
        produceThingExceptions(rootPath.resolve("things"));
    }


    private void produceThingCommands(final Path rootPath) throws IOException {
        produceThingQueryCommands(rootPath);
        produceThingModifyCommands(rootPath);
        produceThingQueryResponses(rootPath);
        produceThingModifyResponses(rootPath);
    }

    private void produceThingQueryCommands(final Path rootPath) throws IOException {
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

        final String[] thingIds =
                {NAMESPACE + ":xdk_53", NAMESPACE + ":xdk_58", NAMESPACE + ":xdk_67"};
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
                RetrieveAttributes.of(THING_ID, TestConstants.JSON_FIELD_SELECTOR_ATTRIBUTES,
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

        final RetrieveFeatureProperties retrieveFeatureProperties =
                RetrieveFeatureProperties.of(THING_ID, FEATURE_ID, DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("retrieveFeatureProperties.json")), retrieveFeatureProperties);

        final RetrieveFeatureProperties retrieveFeaturePropertiesSelectedFields = RetrieveFeatureProperties.of(THING_ID,
                FEATURE_ID, TestConstants.JSON_FIELD_SELECTOR_FEATURE_PROPERTIES, DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("retrieveFeatureProperties-selectedFields.json")),
                retrieveFeaturePropertiesSelectedFields);

        final RetrieveFeatureProperty retrieveFeatureProperty =
                RetrieveFeatureProperty.of(THING_ID, FEATURE_ID, PROPERTY_POINTER, DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("retrieveFeatureProperty.json")), retrieveFeatureProperty);
    }

    private void produceThingQueryResponses(final Path rootPath) throws IOException {
        final Path commandsDir = rootPath.resolve(Paths.get("commands", "query"));
        Files.createDirectories(commandsDir);

        final RetrieveThingsResponse retrieveThingsResponse =
                RetrieveThingsResponse.of(Collections.singletonList(THING), FieldType.notHidden(), "com.acme",
                        DITTO_HEADERS);
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
        final Path commandsDir = rootPath.resolve(Paths.get("commands", "modify"));
        Files.createDirectories(commandsDir);

        final CreateThing createThing = CreateThing.of(THING, null, DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("createThing.json")), createThing);

        final ModifyThing modifyThing = ModifyThing.of(THING_ID, THING, null, DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("modifyThing.json")), modifyThing);

        final DeleteThing deleteThing = DeleteThing.of(THING_ID, DITTO_HEADERS);
        writeJson(commandsDir.resolve(Paths.get("deleteThing.json")), deleteThing);

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

        final ModifyPolicyIdResponse modifyPolicyIdResponseCreated =
                ModifyPolicyIdResponse.modified(THING_ID, DITTO_HEADERS);
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
                ModifyFeatureResponse.modified(THING_ID, TestConstants.Feature.FLUX_CAPACITOR_ID, DITTO_HEADERS);
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

    private void produceThingExceptions(final Path rootPath) throws IOException {
        final Path exceptionsDir = rootPath.resolve(Paths.get("errors"));
        Files.createDirectories(exceptionsDir);

        final ThingIdInvalidException thingIdInvalidException = ThingIdInvalidException.newBuilder("invalid id")
                .dittoHeaders(DITTO_HEADERS)
                .build();
        writeJson(exceptionsDir.resolve(Paths.get("thingIdInvalidException.json")), thingIdInvalidException);

        final PolicyIdMissingException policyIdMissingException = PolicyIdMissingException
                .fromThingId(THING_ID, DITTO_HEADERS);
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
                ThingIdNotExplicitlySettableException.newBuilder(true).build();
        writeJson(exceptionsDir.resolve(Paths.get("thingIdNotExplicitlySettableException_post.json")),
                thingIdNotExplicitlySettableExceptionPost);
        final ThingIdNotExplicitlySettableException thingIdNotExplicitlySettableExceptionPut =
                ThingIdNotExplicitlySettableException.newBuilder(false).build();
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

        final ThingErrorResponse thingErrorResponse = ThingErrorResponse.of(thingNotAccessibleException);
        writeJson(exceptionsDir.resolve(Paths.get("thingErrorResponse.json")), thingErrorResponse);
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
