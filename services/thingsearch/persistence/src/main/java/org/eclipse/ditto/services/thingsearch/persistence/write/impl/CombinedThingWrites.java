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
package org.eclipse.ditto.services.thingsearch.persistence.write.impl;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;

import javax.annotation.concurrent.NotThreadSafe;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.policiesenforcers.PolicyEnforcer;
import org.eclipse.ditto.model.things.AccessControlList;
import org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants;
import org.eclipse.ditto.services.thingsearch.persistence.write.IndexLengthRestrictionEnforcer;
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
import org.eclipse.ditto.signals.events.things.ThingCreated;
import org.eclipse.ditto.signals.events.things.ThingDeleted;
import org.eclipse.ditto.signals.events.things.ThingEvent;
import org.eclipse.ditto.signals.events.things.ThingModified;

import akka.event.LoggingAdapter;

/**
 * Wraps an ordered list of update operations for one distinct {@link org.eclipse.ditto.model.things.Thing} to prepare
 * an ordered bulk write.
 */
@NotThreadSafe
public final class CombinedThingWrites {

    private final List<Bson> combinedWriteDocuments;
    private final List<PolicyUpdate> combinedPolicyUpdates;
    private final long targetSequenceNumber;
    private final long sourceSequenceNumber;

    private CombinedThingWrites(final List<Bson> combinedWriteDocuments, final List<PolicyUpdate> combinedPolicyUpdates,
            final long sourceSequenceNumber, final long targetSequenceNumber) {
        this.combinedWriteDocuments = Collections.unmodifiableList(new ArrayList<>(combinedWriteDocuments));
        this.combinedPolicyUpdates = Collections.unmodifiableList(new ArrayList<>(combinedPolicyUpdates));
        this.targetSequenceNumber = targetSequenceNumber;
        this.sourceSequenceNumber = sourceSequenceNumber;
    }

    /**
     * Returns a mutable builder with a fluent API for creating a {@code CombinedThingWrites}.
     *
     * @param sourceSequenceNumber the sequence number to be set finally if the bulk update operation succeeded.
     * @param policyEnforcer this enforcer is used to evaluate the policy - if relevant - when adding a ThingEvent.
     * @return the new builder.
     * @throws NullPointerException if {@code policyEnforcer} is {@code null}.
     */
    public static Builder newBuilder(final LoggingAdapter log, final long sourceSequenceNumber, final PolicyEnforcer
            policyEnforcer) {
        return new Builder(log, sourceSequenceNumber, policyEnforcer);
    }

    /**
     * Returns an unmodifiable list containing the combined {@link PolicyUpdate}s to be executed FIFO.
     *
     * @return the combined policy updates.
     */
    public List<PolicyUpdate> getCombinedPolicyUpdates() {
        return combinedPolicyUpdates;
    }

    /**
     * Returns an unmodifiable list containing the combined write documents to be executed FIFO.
     *
     * @return the combined write documents.
     */
    public List<Bson> getCombinedWriteDocuments() {
        return combinedWriteDocuments;
    }

    /**
     * Returns the source sequence number.
     *
     * @return the source sequence number.
     */
    public long getSourceSequenceNumber() {
        return sourceSequenceNumber;
    }

    /**
     * Returns the target sequence number.
     *
     * @return the target sequence number.
     */
    public long getTargetSequenceNumber() {
        return targetSequenceNumber;
    }

    /**
     * Returns the BSON document describing the update of the (target) sequence number.
     *
     * @return the document.
     */
    public Bson getSequenceNumberUpdate() {
        final Document result = new Document();
        result.append(PersistenceConstants.SET,
                new Document(PersistenceConstants.FIELD_REVISION, targetSequenceNumber));
        return result;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final CombinedThingWrites that = (CombinedThingWrites) o;
        return targetSequenceNumber == that.targetSequenceNumber &&
                sourceSequenceNumber == that.sourceSequenceNumber &&
                Objects.equals(combinedWriteDocuments, that.combinedWriteDocuments) &&
                Objects.equals(combinedPolicyUpdates, that.combinedPolicyUpdates);
    }

    @Override
    public int hashCode() {
        return Objects.hash(combinedWriteDocuments, combinedPolicyUpdates, targetSequenceNumber, sourceSequenceNumber);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "combinedWriteDocuments=" + combinedWriteDocuments +
                ", combinedPolicyUpdates=" + combinedPolicyUpdates +
                ", targetSequenceNumber=" + targetSequenceNumber +
                ", sourceSequenceNumber=" + sourceSequenceNumber +
                "]";
    }

    /**
     * Builds a new {@link CombinedThingWrites}.
     */
    @NotThreadSafe
    public static final class Builder {

        private static final Map<String, Class<? extends CreationStrategy>> CREATION_STRATEGIES;

        static {
            final Map<String, Class<? extends CreationStrategy>> creationStrategies = new HashMap<>();
            creationStrategies.put(AclEntryCreated.TYPE, AclEntryCreatedStrategy.class);
            creationStrategies.put(AclEntryModified.TYPE, AclEntryModifiedStrategy.class);
            creationStrategies.put(AclEntryDeleted.TYPE, AclEntryDeletedStrategy.class);
            creationStrategies.put(AclModified.TYPE, AclModifiedStrategy.class);
            creationStrategies.put(AttributeCreated.TYPE, AttributeCreatedStrategy.class);
            creationStrategies.put(AttributeModified.TYPE, AttributeModifiedStrategy.class);
            creationStrategies.put(AttributeDeleted.TYPE, AttributeDeletedStrategy.class);
            creationStrategies.put(AttributesCreated.TYPE, AttributesCreatedStrategy.class);
            creationStrategies.put(AttributesModified.TYPE, AttributesModifiedStrategy.class);
            creationStrategies.put(AttributesDeleted.TYPE, AttributesDeletedStrategy.class);
            creationStrategies.put(FeatureCreated.TYPE, FeatureCreatedStrategy.class);
            creationStrategies.put(FeatureModified.TYPE, FeatureModifiedStrategy.class);
            creationStrategies.put(FeatureDeleted.TYPE, FeatureDeletedStrategy.class);
            creationStrategies.put(FeaturesCreated.TYPE, FeaturesCreatedStrategy.class);
            creationStrategies.put(FeaturesModified.TYPE, FeaturesModifiedStrategy.class);
            creationStrategies.put(FeaturesDeleted.TYPE, FeaturesDeletedStrategy.class);
            creationStrategies.put(FeaturePropertyCreated.TYPE, FeaturePropertyCreatedStrategy.class);
            creationStrategies.put(FeaturePropertyModified.TYPE, FeaturePropertyModifiedStrategy.class);
            creationStrategies.put(FeaturePropertyDeleted.TYPE, FeaturePropertyDeletedStrategy.class);
            creationStrategies.put(FeaturePropertiesCreated.TYPE, FeaturePropertiesCreatedStrategy.class);
            creationStrategies.put(FeaturePropertiesModified.TYPE, FeaturePropertiesModifiedStrategy.class);
            creationStrategies.put(FeaturePropertiesDeleted.TYPE, FeaturePropertiesDeletedStrategy.class);
            creationStrategies.put(ThingCreated.TYPE, ThingCreatedStrategy.class);
            creationStrategies.put(ThingModified.TYPE, ThingModifiedStrategy.class);
            creationStrategies.put(ThingDeleted.TYPE, ThingDeletedStrategy.class);
            CREATION_STRATEGIES = Collections.unmodifiableMap(creationStrategies);
        }

        private final IndexLengthRestrictionEnforcer indexLengthRestrictionEnforcer;
        private final List<Bson> combinedWriteDocuments;
        private final List<PolicyUpdate> combinedPolicyUpdates;
        private final long sourceSequenceNumber;

        private long targetSequenceNumber;
        private final PolicyEnforcer policyEnforcer;

        private Builder(final LoggingAdapter log, final long sourceSequenceNumber,
                final PolicyEnforcer policyEnforcer) {
            this.indexLengthRestrictionEnforcer = IndexLengthRestrictionEnforcer.newBuilder(log).build();
            this.sourceSequenceNumber = sourceSequenceNumber;
            targetSequenceNumber = sourceSequenceNumber;
            this.policyEnforcer = policyEnforcer;
            combinedWriteDocuments = new ArrayList<>();
            combinedPolicyUpdates = new ArrayList<>();
        }

        /**
         * Returns the size of the combined write documents.
         *
         * @return the size.
         */
        public int getSize() {
            return combinedWriteDocuments.size();
        }

        /**
         * Creates and adds update documents for the specified ThingEvent. Write update documents are created for each
         * ThingEvent. Dependent on the specified JSON schema version policy update documents are created additionally.
         *
         * @param thingEvent the event to create update documents for.
         * @param jsonSchemaVersion this JSON schema version determines whether to create policy update documents or
         * not.
         * @return this builder instance to allow method chaining.
         * @throws NullPointerException if any argument is {@code null}.
         * @throws IllegalStateException if the type of {@code thingEvent} is unknown or if the internal strategy for
         * dealing with {@code thingEvent} cannot be instantiated.
         * @throws IllegalArgumentException if the revision of {@code thingEvent} is not equal to the expected target
         * sequence number.
         */
        public Builder addEvent(final ThingEvent thingEvent, final JsonSchemaVersion jsonSchemaVersion) {
            checkNotNull(thingEvent, "ThingEvent");
            checkNotNull(jsonSchemaVersion, "JSON schema version");

            final Class<? extends CreationStrategy> strategyClass = CREATION_STRATEGIES.get(thingEvent.getType());
            if (null != strategyClass) {
                /* Use reflection to create the appropriate CreationStrategy object.
                 * Why reflection? Because we want to keep the memory footprint of ThingUpdater as small as possible.
                 * Each instance of ThingUpdater holds an instance of CombinedThingWrites.Builder which could be a lot.
                 * By using reflection we do not need to store fully-fledged objects in CREATION_STRATEGIES but merely
                 * a means to create those objects right when needed. This saves memory at the cost of CPU - a good
                 * deal in this case.
                 */
                final Constructor<? extends CreationStrategy> constructor = tryToGetConstructor(strategyClass);
                final CreationStrategy<?> strategy = tryToCreateInstance(constructor);
                strategy.accept(thingEvent, jsonSchemaVersion);
            } else {
                final String pattern = "Event type <{0}> is unknown!";
                throw new IllegalStateException(MessageFormat.format(pattern, thingEvent.getType()));
            }

            return this;
        }

        private Constructor<? extends CreationStrategy> tryToGetConstructor(
                final Class<? extends CreationStrategy> clazz) {
            try {
                // As CreationStrategy represents an inner class declared in a non-static context, the formal parameter
                // types have to include the explicit enclosing instance as the first parameter.
                return clazz.getDeclaredConstructor(Builder.class);
            } catch (final NoSuchMethodException e) {
                final String pattern = "Failed to get constructor for <{0}>!";
                throw new IllegalStateException(MessageFormat.format(pattern, clazz.getSimpleName()), e);
            }
        }

        private CreationStrategy<?> tryToCreateInstance(final Constructor<? extends CreationStrategy> constructor) {
            try {
                constructor.setAccessible(true);
                return constructor.newInstance(this);
            } catch (final InstantiationException | IllegalAccessException | InvocationTargetException e) {
                final String pattern = "Failed to create instance of <{0}>!";
                final String msg = MessageFormat.format(pattern, constructor.getDeclaringClass().getSimpleName());
                throw new IllegalStateException(msg, e);
            }
        }

        /**
         * Builds the {@link CombinedThingWrites} object.
         *
         * @return the built object.
         */
        public CombinedThingWrites build() {
            return new CombinedThingWrites(combinedWriteDocuments, combinedPolicyUpdates, sourceSequenceNumber,
                    targetSequenceNumber);
        }

        /**
         * Common base implementation for strategies which create update documents for ThingEvents. This class exists to
         * comprise code which would be redundant for each strategy else. A main feature of {@code CreationStrategy} is
         * to accept a generic ThingEvent and casting it to a specific sub-type thereof. This works as each strategy
         * knows the particular sub-type it handles. The cast is safe because of the mapping from event type to strategy
         * implementation class.
         *
         * @param <T> the specific sub-type of ThingEvent this strategy accepts.
         */
        private abstract class CreationStrategy<T extends ThingEvent>
                implements BiConsumer<ThingEvent<?>, JsonSchemaVersion> {

            @Override
            public void accept(final ThingEvent<?> thingEvent, final JsonSchemaVersion jsonSchemaVersion) {
                incrementTargetSequenceNumber();
                checkProvidedSequenceNumber(thingEvent.getRevision());

                @SuppressWarnings("unchecked") final T specificEvent = (T) thingEvent;
                combinedWriteDocuments.addAll(createUpdates(specificEvent));
                if (isPolicyRelevant(jsonSchemaVersion)) {
                    final PolicyUpdate policyUpdate = createPolicyUpdate(specificEvent);
                    if (null != policyUpdate) {
                        combinedPolicyUpdates.add(policyUpdate);
                    }
                }
            }

            private void incrementTargetSequenceNumber() {
                targetSequenceNumber++;
            }

            private void checkProvidedSequenceNumber(final long sequenceNumber) {
                if (targetSequenceNumber != sequenceNumber) {
                    final String pattern = "Expected sequence number <{0}> but got provided <{1}>!";
                    final String msg = MessageFormat.format(pattern, sequenceNumber, targetSequenceNumber);
                    throw new IllegalArgumentException(msg);
                }
            }

            protected abstract List<Bson> createUpdates(T specificEvent);

            private boolean isPolicyRelevant(final JsonSchemaVersion jsonSchemaVersion) {
                return jsonSchemaVersion.toInt() > JsonSchemaVersion.V_1.toInt();
            }

            @SuppressWarnings("squid:S1172")
            protected PolicyUpdate createPolicyUpdate(final T event) {
                return null;
            }

        }

        private final class AclEntryCreatedStrategy extends CreationStrategy<AclEntryCreated> {

            @Override
            protected List<Bson> createUpdates(final AclEntryCreated event) {
                final CombinedUpdates updateAclEntry = AclUpdatesFactory.createUpdateAclEntry(event.getAclEntry());
                return updateAclEntry.getUpdates();
            }
        }

        private final class AclEntryModifiedStrategy extends CreationStrategy<AclEntryModified> {

            @Override
            protected List<Bson> createUpdates(final AclEntryModified event) {
                final CombinedUpdates combinedUpdates = AclUpdatesFactory.createUpdateAclEntry(event.getAclEntry());
                return combinedUpdates.getUpdates();
            }
        }

        private final class AclEntryDeletedStrategy extends CreationStrategy<AclEntryDeleted> {

            @Override
            protected List<Bson> createUpdates(final AclEntryDeleted event) {
                final AuthorizationSubject authorizationSubject = event.getAuthorizationSubject();
                return Collections.singletonList(AclUpdatesFactory.deleteAclEntry(authorizationSubject.getId()));
            }
        }

        private final class AclModifiedStrategy extends CreationStrategy<AclModified> {

            @Override
            protected List<Bson> createUpdates(final AclModified event) {
                final AccessControlList acl = event.getAccessControlList();
                final CombinedUpdates combinedUpdates = AclUpdatesFactory.createUpdateAclEntries(acl);
                return combinedUpdates.getUpdates();
            }
        }

        private final class AttributeCreatedStrategy extends CreationStrategy<AttributeCreated> {

            @Override
            protected List<Bson> createUpdates(final AttributeCreated event) {
                final JsonPointer pointer = event.getAttributePointer();
                final JsonValue value = event.getAttributeValue();
                final CombinedUpdates combinedUpdates =
                        AttributesUpdateFactory.createAttributesUpdates(indexLengthRestrictionEnforcer, pointer, value);
                return combinedUpdates.getUpdates();
            }

            @Override
            protected PolicyUpdate createPolicyUpdate(final AttributeCreated event) {
                final JsonPointer p = event.getAttributePointer();
                final JsonValue v = event.getAttributeValue();
                return PolicyUpdateFactory.createAttributePolicyIndexUpdate(event.getThingId(), p, v, policyEnforcer);
            }
        }

        private final class AttributeModifiedStrategy extends CreationStrategy<AttributeModified> {

            @Override
            protected List<Bson> createUpdates(final AttributeModified event) {
                final JsonPointer pointer = event.getAttributePointer();
                final JsonValue value = event.getAttributeValue();
                final CombinedUpdates combinedUpdates =
                        AttributesUpdateFactory.createAttributesUpdates(indexLengthRestrictionEnforcer, pointer, value);
                return combinedUpdates.getUpdates();
            }

            @Override
            protected PolicyUpdate createPolicyUpdate(final AttributeModified event) {
                final JsonPointer p = event.getAttributePointer();
                final JsonValue v = event.getAttributeValue();
                return PolicyUpdateFactory.createAttributePolicyIndexUpdate(event.getThingId(), p, v, policyEnforcer);
            }
        }

        private final class AttributeDeletedStrategy extends CreationStrategy<AttributeDeleted> {

            @Override
            protected List<Bson> createUpdates(final AttributeDeleted event) {
                return Collections.singletonList(
                        AttributesUpdateFactory.createAttributeDeletionUpdate(event.getAttributePointer()));
            }

            @Override
            protected PolicyUpdate createPolicyUpdate(final AttributeDeleted event) {
                return PolicyUpdateFactory.createAttributeDeletion(event.getThingId(), event.getAttributePointer());
            }
        }

        private final class AttributesCreatedStrategy extends CreationStrategy<AttributesCreated> {

            @Override
            protected List<Bson> createUpdates(final AttributesCreated event) {
                final CombinedUpdates combinedUpdates =
                        AttributesUpdateFactory.createAttributesUpdate(indexLengthRestrictionEnforcer,
                                event.getCreatedAttributes());
                return combinedUpdates.getUpdates();
            }

            @Override
            protected PolicyUpdate createPolicyUpdate(final AttributesCreated event) {
                return PolicyUpdateFactory.createAttributesUpdate(event.getThingId(), event.getCreatedAttributes(),
                        policyEnforcer);
            }
        }

        private final class AttributesModifiedStrategy extends CreationStrategy<AttributesModified> {

            @Override
            protected List<Bson> createUpdates(final AttributesModified event) {
                final CombinedUpdates combinedUpdates =
                        AttributesUpdateFactory.createAttributesUpdate(indexLengthRestrictionEnforcer,
                                event.getModifiedAttributes());
                return combinedUpdates.getUpdates();
            }

            @Override
            protected PolicyUpdate createPolicyUpdate(final AttributesModified event) {
                return PolicyUpdateFactory.createAttributesUpdate(event.getThingId(), event.getModifiedAttributes(),
                        policyEnforcer);
            }
        }

        private final class AttributesDeletedStrategy extends CreationStrategy<AttributesDeleted> {

            @Override
            protected List<Bson> createUpdates(final AttributesDeleted event) {
                return Collections.singletonList(AttributesUpdateFactory.deleteAttributes());
            }

            @Override
            protected PolicyUpdate createPolicyUpdate(final AttributesDeleted event) {
                return PolicyUpdateFactory.createAttributesDeletion(event.getThingId());
            }
        }

        private final class FeatureCreatedStrategy extends CreationStrategy<FeatureCreated> {

            @Override
            protected List<Bson> createUpdates(final FeatureCreated event) {
                final CombinedUpdates combinedUpdates =
                        FeaturesUpdateFactory.createUpdateForFeature(indexLengthRestrictionEnforcer, event.getFeature(),
                                true);
                return combinedUpdates.getUpdates();
            }

            @Override
            protected PolicyUpdate createPolicyUpdate(final FeatureCreated event) {
                return PolicyUpdateFactory.createFeatureUpdate(event.getThingId(), event.getFeature(), policyEnforcer);
            }
        }

        private final class FeatureModifiedStrategy extends CreationStrategy<FeatureModified> {

            @Override
            protected List<Bson> createUpdates(final FeatureModified event) {
                final CombinedUpdates combinedUpdates =
                        FeaturesUpdateFactory.createUpdateForFeature(indexLengthRestrictionEnforcer, event.getFeature(),
                                false);
                return combinedUpdates.getUpdates();
            }

            @Override
            protected PolicyUpdate createPolicyUpdate(final FeatureModified event) {
                return PolicyUpdateFactory.createFeatureUpdate(event.getThingId(), event.getFeature(), policyEnforcer);
            }
        }

        private final class FeatureDeletedStrategy extends CreationStrategy<FeatureDeleted> {

            @Override
            protected List<Bson> createUpdates(final FeatureDeleted event) {
                return Collections.singletonList(FeaturesUpdateFactory.createDeleteFeatureUpdate(event.getFeatureId()));
            }

            @Override
            protected PolicyUpdate createPolicyUpdate(final FeatureDeleted event) {
                return PolicyUpdateFactory.createFeatureDeletion(event.getThingId(), event.getFeatureId());
            }
        }

        private final class FeaturesCreatedStrategy extends CreationStrategy<FeaturesCreated> {

            @Override
            protected List<Bson> createUpdates(final FeaturesCreated event) {
                final CombinedUpdates combinedUpdates = FeaturesUpdateFactory.updateFeatures(
                        indexLengthRestrictionEnforcer, event
                                .getFeatures());
                return combinedUpdates.getUpdates();
            }

            @Override
            protected PolicyUpdate createPolicyUpdate(final FeaturesCreated event) {
                return PolicyUpdateFactory.createFeaturesUpdate(event.getThingId(), event.getFeatures(),
                        policyEnforcer);
            }
        }

        private final class FeaturesModifiedStrategy extends CreationStrategy<FeaturesModified> {

            @Override
            protected List<Bson> createUpdates(final FeaturesModified event) {
                final CombinedUpdates combinedUpdates =
                        FeaturesUpdateFactory.updateFeatures(indexLengthRestrictionEnforcer, event.getFeatures());
                return combinedUpdates.getUpdates();
            }

            @Override
            protected PolicyUpdate createPolicyUpdate(final FeaturesModified event) {
                return PolicyUpdateFactory.createFeaturesUpdate(event.getThingId(), event.getFeatures(),
                        policyEnforcer);
            }
        }

        private final class FeaturesDeletedStrategy extends CreationStrategy<FeaturesDeleted> {

            @Override
            protected List<Bson> createUpdates(final FeaturesDeleted event) {
                return Collections.singletonList(FeaturesUpdateFactory.deleteFeatures());
            }

            @Override
            protected PolicyUpdate createPolicyUpdate(final FeaturesDeleted event) {
                return PolicyUpdateFactory.createFeaturesDeletion(event.getThingId());
            }
        }

        private final class FeaturePropertyCreatedStrategy extends CreationStrategy<FeaturePropertyCreated> {

            @Override
            protected List<Bson> createUpdates(final FeaturePropertyCreated event) {
                final CombinedUpdates combinedUpdates =
                        FeaturesUpdateFactory.createUpdateForFeatureProperty(indexLengthRestrictionEnforcer,
                                event.getFeatureId(),
                                event.getPropertyPointer(), event.getPropertyValue());
                return combinedUpdates.getUpdates();
            }

            @Override
            protected PolicyUpdate createPolicyUpdate(final FeaturePropertyCreated event) {
                return PolicyUpdateFactory.createFeaturePropertyUpdate(event.getThingId(), event.getFeatureId(),
                        event.getPropertyPointer(), event.getPropertyValue(), policyEnforcer);
            }
        }

        private final class FeaturePropertyModifiedStrategy extends CreationStrategy<FeaturePropertyModified> {

            @Override
            protected List<Bson> createUpdates(final FeaturePropertyModified event) {
                final CombinedUpdates combinedUpdates =
                        FeaturesUpdateFactory.createUpdateForFeatureProperty(indexLengthRestrictionEnforcer,
                                event.getFeatureId(),
                                event.getPropertyPointer(), event.getPropertyValue());
                return combinedUpdates.getUpdates();
            }

            @Override
            protected PolicyUpdate createPolicyUpdate(final FeaturePropertyModified event) {
                return PolicyUpdateFactory.createFeaturePropertyUpdate(event.getThingId(), event.getFeatureId(),
                        event.getPropertyPointer(), event.getPropertyValue(), policyEnforcer);
            }
        }

        private final class FeaturePropertyDeletedStrategy extends CreationStrategy<FeaturePropertyDeleted> {

            @Override
            protected List<Bson> createUpdates(final FeaturePropertyDeleted event) {
                return Collections.singletonList(
                        FeaturesUpdateFactory.createDeleteFeaturePropertyUpdate(event.getFeatureId(),
                                event.getPropertyPointer()));
            }

            @Override
            protected PolicyUpdate createPolicyUpdate(final FeaturePropertyDeleted event) {
                return PolicyUpdateFactory.createFeaturePropertyDeletion(event.getThingId(), event.getFeatureId(),
                        event.getPropertyPointer());
            }
        }

        private final class FeaturePropertiesCreatedStrategy extends CreationStrategy<FeaturePropertiesCreated> {

            @Override
            protected List<Bson> createUpdates(final FeaturePropertiesCreated event) {
                final CombinedUpdates combinedUpdates =
                        FeaturesUpdateFactory.createUpdateForFeatureProperties(indexLengthRestrictionEnforcer,
                                event.getFeatureId(),
                                event.getProperties());
                return combinedUpdates.getUpdates();
            }

            @Override
            protected PolicyUpdate createPolicyUpdate(final FeaturePropertiesCreated event) {
                return PolicyUpdateFactory.createFeaturePropertiesUpdate(event.getThingId(), event.getFeatureId(),
                        event.getProperties(), policyEnforcer);
            }
        }

        private final class FeaturePropertiesModifiedStrategy extends CreationStrategy<FeaturePropertiesModified> {

            @Override
            protected List<Bson> createUpdates(final FeaturePropertiesModified event) {
                final CombinedUpdates combinedUpdates =
                        FeaturesUpdateFactory.createUpdateForFeatureProperties(indexLengthRestrictionEnforcer,
                                event.getFeatureId(),
                                event.getProperties());
                return combinedUpdates.getUpdates();
            }

            @Override
            protected PolicyUpdate createPolicyUpdate(final FeaturePropertiesModified event) {
                return PolicyUpdateFactory.createFeaturePropertiesUpdate(event.getThingId(), event.getFeatureId(),
                        event.getProperties(), policyEnforcer);
            }
        }

        private final class FeaturePropertiesDeletedStrategy extends CreationStrategy<FeaturePropertiesDeleted> {

            @Override
            protected List<Bson> createUpdates(final FeaturePropertiesDeleted event) {
                return Collections.singletonList(
                        FeaturesUpdateFactory.createDeleteFeaturePropertiesUpdate(event.getFeatureId()));
            }

            @Override
            protected PolicyUpdate createPolicyUpdate(final FeaturePropertiesDeleted event) {
                return PolicyUpdateFactory.createFeaturePropertiesDeletion(event.getThingId(), event.getFeatureId());
            }
        }

        private final class ThingCreatedStrategy extends CreationStrategy<ThingCreated> {

            @Override
            protected List<Bson> createUpdates(final ThingCreated event) {
                return Collections.singletonList(ThingUpdateFactory.createUpdateThingUpdate(
                        indexLengthRestrictionEnforcer, event
                                .getThing()));
            }

            @Override
            protected PolicyUpdate createPolicyUpdate(final ThingCreated event) {
                return PolicyUpdateFactory.createPolicyIndexUpdate(event.getThing(), policyEnforcer);
            }
        }

        private final class ThingModifiedStrategy extends CreationStrategy<ThingModified> {

            @Override
            protected List<Bson> createUpdates(final ThingModified event) {
                return Collections.singletonList(ThingUpdateFactory.createUpdateThingUpdate(
                        indexLengthRestrictionEnforcer, event
                                .getThing()));
            }

            @Override
            protected PolicyUpdate createPolicyUpdate(final ThingModified event) {
                return PolicyUpdateFactory.createPolicyIndexUpdate(event.getThing(), policyEnforcer);
            }
        }

        private final class ThingDeletedStrategy extends CreationStrategy<ThingDeleted> {

            @Override
            protected List<Bson> createUpdates(final ThingDeleted event) {
                return Collections.singletonList(ThingUpdateFactory.createDeleteThingUpdate());
            }

            @Override
            protected PolicyUpdate createPolicyUpdate(final ThingDeleted event) {
                return PolicyUpdateFactory.createDeleteThingUpdate(event.getThingId());
            }
        }

    }

}
