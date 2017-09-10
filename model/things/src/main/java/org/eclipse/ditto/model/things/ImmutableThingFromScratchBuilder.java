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
package org.eclipse.ditto.model.things;

import java.time.Instant;
import java.util.function.Consumer;

import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.common.ConditionChecker;
import org.eclipse.ditto.model.base.common.IdValidator;
import org.eclipse.ditto.model.base.common.Validator;


/**
 * A mutable builder for an immutable {@link Thing} from scratch.
 */
@NotThreadSafe
final class ImmutableThingFromScratchBuilder implements ThingBuilder, ThingBuilder.FromScratch {

   /*
    * This builder is used by ImmutableThingFromCopyBuilder to reduce implementation overhead.
    * Thus some fields and methods have to be package private - unfortunately.
    */

    String id;
    ThingLifecycle lifecycle;
    ThingRevision revision;
    Instant modified;
    private String policyId;
    private AccessControlListBuilder aclBuilder;
    private AttributesBuilder attributesBuilder;
    private Attributes attributes;
    private FeaturesBuilder featuresBuilder;
    private Features features;

    private ImmutableThingFromScratchBuilder() {
        id = null;
        policyId = null;
        aclBuilder = null;
        attributesBuilder = null;
        attributes = null;
        featuresBuilder = null;
        features = null;
        lifecycle = null;
        revision = null;
        modified = null;
    }

    /**
     * Returns a new instance of {@code ImmutableThingFromScratchBuilder}.
     *
     * @return the new builder.
     */
    public static ImmutableThingFromScratchBuilder newInstance() {
        return new ImmutableThingFromScratchBuilder();
    }

    @Override
    public FromScratch setAttributes(final Attributes attributes) {
        ConditionChecker.checkNotNull(attributes, "Attributes to be set");

        if (attributes.isNull()) {
            return setNullAttributes();
        } else {
            invokeOnAttributesBuilder(ab -> ab.removeAll().setAll(attributes));
            return this;
        }
    }

    @Override
    public FromScratch setAttributes(final JsonObject attributesJsonObject) {
        ConditionChecker.checkNotNull(attributesJsonObject, "JSON object representation of Attributes to be set");

        if (attributesJsonObject.isNull()) {
            return setNullAttributes();
        } else if (attributesJsonObject.isEmpty()) {
            return setEmptyAttributes();
        }
        return setAttributes(ThingsModelFactory.newAttributes(attributesJsonObject));
    }

    @Override
    public FromScratch setAttributes(final String attributesJsonString) {
        return setAttributes(ThingsModelFactory.newAttributes(attributesJsonString));
    }

    @Override
    public FromScratch removeAllAttributes() {
        attributesBuilder = null;
        return this;
    }

    @Override
    public FromScratch setEmptyAttributes() {
        attributesBuilder = null;
        attributes = ThingsModelFactory.emptyAttributes();
        return this;
    }

    @Override
    public FromScratch setNullAttributes() {
        attributesBuilder = null;
        attributes = ThingsModelFactory.nullAttributes();
        return this;
    }

    @Override
    public FromScratch setAttribute(final JsonPointer attributePath, final JsonValue attributeValue) {
        ConditionChecker.checkNotNull(attributeValue, "attribute value to be set");
        invokeOnAttributesBuilder(ab -> ab.set(attributePath, attributeValue));
        return this;
    }

    @Override
    public FromScratch removeAttribute(final JsonPointer attributePath) {
        if (null != attributesBuilder) {
            invokeOnAttributesBuilder(ab -> ab.remove(attributePath));
        }
        return this;
    }

    @Override
    public FromScratch setFeature(final Feature feature) {
        invokeOnFeaturesBuilder(fb -> fb.set(feature));
        return this;
    }

    @Override
    public FromScratch setFeature(final String featureId) {
        return setFeature(ThingsModelFactory.newFeature(featureId));
    }

    @Override
    public FromScratch setFeature(final String featureId, final FeatureProperties featureProperties) {
        return setFeature(ThingsModelFactory.newFeature(featureId, featureProperties));
    }

    @Override
    public FromScratch removeFeature(final String featureId) {
        invokeOnFeaturesBuilder(fb -> fb.remove(featureId));
        if (getFeatures().isEmpty()) {
            featuresBuilder = null;
        }
        return this;
    }

    @Override
    public FromScratch setFeatureProperty(final String featureId, final JsonPointer propertyPath,
            final JsonValue propertyValue) {
        ConditionChecker.checkNotNull(propertyValue, "property value to be set");

        final Features existingFeatures = getFeatures();
        if (null != existingFeatures) {
            return setFeatures(existingFeatures.setProperty(featureId, propertyPath, propertyValue));
        } else {
            final FeatureProperties featureProperties = ThingsModelFactory.newFeaturePropertiesBuilder()
                    .set(propertyPath, propertyValue)
                    .build();
            return setFeature(featureId, featureProperties);
        }
    }

    @Override
    public FromScratch removeFeatureProperty(final String featureId, final JsonPointer propertyPath) {
        ConditionChecker.checkNotNull(featureId, "identifier of the Feature from which the property to be removed");
        ConditionChecker.checkNotNull(propertyPath, "path to the property to be removed");

        if (null != featuresBuilder) {
            final Features existingFeatures = getFeatures();
            return setFeatures(existingFeatures.removeProperty(featureId, propertyPath));
        }
        return this;
    }

    @Override
    public FromScratch setFeatures(final Iterable<Feature> features) {
        ConditionChecker.checkNotNull(features, "Features to be set");

        if (features instanceof Features) {
            final Features featuresToSet = (Features) features;
            if (featuresToSet.isNull()) {
                return setNullFeatures();
            }
        }

        invokeOnFeaturesBuilder(fb -> fb.removeAll().setAll(features));
        return this;
    }

    @Override
    public FromScratch removeAllFeatures() {
        featuresBuilder = null;
        return this;
    }

    @Override
    public FromScratch setEmptyFeatures() {
        featuresBuilder = null;
        features = ThingsModelFactory.emptyFeatures();
        return this;
    }

    @Override
    public FromScratch setNullFeatures() {
        featuresBuilder = null;
        features = ThingsModelFactory.nullFeatures();
        return this;
    }

    @Override
    public FromScratch setFeatures(final JsonObject featuresJsonObject) {
        ConditionChecker.checkNotNull(featuresJsonObject, "JSON object representation of Features to be set");

        if (featuresJsonObject.isNull()) {
            return setNullFeatures();
        }
        return setFeatures(ThingsModelFactory.newFeatures(featuresJsonObject));
    }

    @Override
    public FromScratch setFeatures(final String featuresJsonString) {
        return setFeatures(ThingsModelFactory.newFeatures(featuresJsonString));
    }

    @Override
    public FromScratch setLifecycle(final ThingLifecycle lifecycle) {
        this.lifecycle = lifecycle;
        return this;
    }

    @Override
    public FromScratch setRevision(final ThingRevision revision) {
        this.revision = revision;
        return this;
    }

    @Override
    public FromScratch setRevision(final long revisionNumber) {
        return setRevision(ThingsModelFactory.newThingRevision(revisionNumber));
    }

    @Override
    public FromScratch setModified(final Instant modified) {
        this.modified = modified;
        return this;
    }

    @Override
    public FromScratch setPermissions(final JsonObject accessControlListJsonObject) {
        final AccessControlList accessControlList = ThingsModelFactory.newAcl(accessControlListJsonObject);
        return setPermissions(accessControlList);
    }

    @Override
    public FromScratch setPermissions(final String accessControlListJsonString) {
        final AccessControlList accessControlList = ThingsModelFactory.newAcl(accessControlListJsonString);
        return setPermissions(accessControlList);
    }

    @Override
    public FromScratch setPermissions(final AuthorizationSubject authorizationSubject, final Permissions permissions) {
        final AclEntry aclEntry = ThingsModelFactory.newAclEntry(authorizationSubject, permissions);
        return setPermissions(aclEntry);
    }

    @Override
    public FromScratch setPermissions(final AuthorizationSubject authorizationSubject, final Permission permission,
            final Permission... furtherPermissions) {
        final AclEntry aclEntry = ThingsModelFactory.newAclEntry(authorizationSubject, permission, furtherPermissions);
        return setPermissions(aclEntry);
    }

    @Override
    public FromScratch setPermissions(final AclEntry aclEntry, final AclEntry... furtherAclEntries) {
        invokeOnAclBuilder(ab -> ab.set(aclEntry));
        for (final AclEntry furtherAclEntry : furtherAclEntries) {
            invokeOnAclBuilder(ab -> ab.set(furtherAclEntry));
        }
        return this;
    }

    @Override
    public FromScratch setPermissions(final Iterable<AclEntry> aclEntries) {
        invokeOnAclBuilder(ab -> ab.setAll(aclEntries));

        return this;
    }

    @Override
    public FromScratch removePermissionsOf(final AuthorizationSubject authorizationSubject) {
        ConditionChecker.checkNotNull(authorizationSubject,
                "authorization subject of which all permissions are to be removed");

        if (null != aclBuilder) {
            invokeOnAclBuilder(ab -> ab.remove(authorizationSubject));
        }
        return this;
    }

    @Override
    public FromScratch removeAllPermissions() {
        aclBuilder = null;
        return this;
    }

    @Override
    public FromScratch setPolicyId(final String policyId) {
        this.policyId = ConditionChecker.checkNotNull(policyId, "Policy ID");
        return this;
    }

    @Override
    public FromScratch removePolicyId() {
        this.policyId = null;
        return this;
    }

    @Override
    public FromScratch setId(final String thingId) {
        if (null != thingId) {
            final Validator thingIdValidator = IdValidator.newInstance(thingId, Thing.ID_REGEX);
            if (!thingIdValidator.isValid()) {
                throw ThingIdInvalidException.newBuilder(thingId)
                        .message(thingIdValidator.getReason().orElse(null))
                        .build();
            }
        }
        id = thingId;
        return this;
    }

    @Override
    public FromScratch setGeneratedId() {
        id = ThingBuilder.generateRandomThingId();
        return this;
    }

    @Override
    public Thing build() {
        if (null != policyId) {
            return ImmutableThing.of(id, policyId, getAttributes(), getFeatures(), lifecycle, revision, modified);
        } else {
            return ImmutableThing.of(id, getAcl(), getAttributes(), getFeatures(), lifecycle, revision, modified);
        }
    }

    private void invokeOnAclBuilder(final Consumer<AccessControlListBuilder> aclBuilderConsumer) {
        AccessControlListBuilder result = aclBuilder;
        if (null == result) {
            result = ThingsModelFactory.newAclBuilder();
            aclBuilder = result;
        }
        aclBuilderConsumer.accept(result);
    }

    private void invokeOnAttributesBuilder(final Consumer<AttributesBuilder> attributesBuilderConsumer) {
        AttributesBuilder result = attributesBuilder;
        if (null == result) {
            result = ThingsModelFactory.newAttributesBuilder();
            attributesBuilder = result;
        }
        attributesBuilderConsumer.accept(result);
        attributes = null;
    }

    private void invokeOnFeaturesBuilder(final Consumer<FeaturesBuilder> featuresBuilderConsumer) {
        FeaturesBuilder result = featuresBuilder;
        if (null == result) {
            result = ThingsModelFactory.newFeaturesBuilder();
            featuresBuilder = result;
        }
        featuresBuilderConsumer.accept(result);
        features = null;
    }

    AccessControlList getAcl() {
        AccessControlList result = null;
        if (null != aclBuilder) {
            result = aclBuilder.build();
        }
        return result;
    }

    Attributes getAttributes() {
        Attributes result = attributes;
        if (null != attributesBuilder) {
            result = attributesBuilder.build();
        }
        return result;
    }

    Features getFeatures() {
        Features result = features;
        if (null != featuresBuilder) {
            result = featuresBuilder.build();
        }
        return result;
    }

}
