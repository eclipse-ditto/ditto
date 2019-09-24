package org.eclipse.ditto.services.connectivity.mapping.custom;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.placeholders.PlaceholderFactory;
import org.eclipse.ditto.model.things.Feature;
import org.eclipse.ditto.model.things.FeatureDefinition;
import org.eclipse.ditto.model.things.FeatureProperties;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.DittoProtocolAdapter;
import org.eclipse.ditto.services.connectivity.mapping.MappingConfig;
import org.eclipse.ditto.services.connectivity.mapping.MessageMapper;
import org.eclipse.ditto.services.connectivity.mapping.MessageMapperConfiguration;
import org.eclipse.ditto.services.models.connectivity.ExternalMessage;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConnectionStatusMessageMapper implements MessageMapper {

    public static final String HEADER_HUB_DEVICE_ID = "device_id";
    public static final String HEADER_HUB_TTD = "ttd";
    public static final String HEADER_HUB_CREATION_TIME = "creation-time";

    public static final String DEFAULT_FEATURE_ID = "ConnectionStatus";
    public static final String FEATURE_DEFINITION = "com.bosch.iot.suite.standard:ConnectionStatus:1.0.0";
    public static final String FEATURE_PROPERTIE_READY_SINCE = "readySince";
    public static final String FEATURE_PROPERTIE_READY_UNTIL = "readyUntil";

    public static final Logger LOGGER = LoggerFactory.getLogger(ConnectionStatusMessageMapper.class);

    private boolean noErrorOccurred;
    private String featureId;
    private Optional<String> mappingOptionThingId;

    private HashMap<String, String> extractedHeader;

    /**
     * Constructs a new {@code CsmPresenceSensorMessageMapper} object.
     * This constructor is required as the the instance is created via reflection.
     */
    public ConnectionStatusMessageMapper() {
        noErrorOccurred = true;
    }


    @Override
    public void configure(final MappingConfig mappingConfig,
            final MessageMapperConfiguration messageMapperConfiguration) {
        featureId = messageMapperConfiguration.findProperty("featureId").orElse(DEFAULT_FEATURE_ID);

        mappingOptionThingId = PlaceholderFactory.newHeadersPlaceholder()
                .resolve(messageMapperConfiguration.getProperties(), "thingId");
        if (mappingOptionThingId.equals(Optional.empty())) {
            noErrorOccurred = false;
            LOGGER.info("Could not find thingId in your mapping options");
        }
    }

    @Override
    public Optional<Adaptable> map(final ExternalMessage externalMessage) {
        extractedHeader =
                extractHeader(externalMessage, HEADER_HUB_TTD, HEADER_HUB_CREATION_TIME, HEADER_HUB_DEVICE_ID);

        checkIfEntriesSet(extractedHeader);

        checkMappingOption(mappingOptionThingId);

        //Check if time is convertible
        long creationTime = 0;
        long ttd = 0;
        try {
            creationTime = Long.parseLong(extractedHeader.get(HEADER_HUB_CREATION_TIME));
            ttd = Long.parseLong(extractedHeader.get(HEADER_HUB_TTD));
        } catch (NumberFormatException e) {
            LOGGER.info("Header {} or {} is not convertible to type long", HEADER_HUB_CREATION_TIME, HEADER_HUB_TTD);
            noErrorOccurred = false;
        }

        if (creationTime < 0 || ttd < -1) {
            LOGGER.info("Undefined value in {} or {}", HEADER_HUB_CREATION_TIME, HEADER_HUB_TTD);
            noErrorOccurred = false;
        }

        if (noErrorOccurred) {
            final ThingId thingId = ThingId.of(mappingConnectionOptions(mappingOptionThingId));

            //Set time to ISO-8601 UTC
            String readyUntil;
            if (ttd != -1) {
                readyUntil = Instant.ofEpochSecond(creationTime + ttd).toString();
            } else {
                //(unix time) 253402300799 = (ISO-8601) 9999-12-31T23:59:59
                readyUntil = Instant.ofEpochSecond(Long.parseLong("253402300799")).toString();
            }

            String readySince = Instant.ofEpochSecond(creationTime).toString();

            //Build propertyPath of featureId
            final FeatureProperties featureProperties = FeatureProperties.newBuilder()
                    .set(FEATURE_PROPERTIE_READY_SINCE, readySince)
                    .set(FEATURE_PROPERTIE_READY_UNTIL, readyUntil)
                    .build();

            final FeatureDefinition featureDefinition = FeatureDefinition.fromIdentifier(FEATURE_DEFINITION);

            final Feature feature = Feature.newBuilder()
                    .definition(featureDefinition)
                    .properties(featureProperties)
                    .withId(featureId)
                    .build();

            final ModifyFeature modifyFeature = ModifyFeature.of(thingId, feature, DittoHeaders.empty());
            LOGGER.info("modifyFeature: {} ", modifyFeature);
            final Adaptable adaptable = DittoProtocolAdapter.newInstance().toAdaptable(modifyFeature);

            LOGGER.info("Feature {} of Thing {} is modified", featureId, extractedHeader.get(HEADER_HUB_DEVICE_ID));

            return Optional.of(adaptable);
        } else { return Optional.empty();}
    }

    private void checkMappingOption(final Optional<String> mappingOption) {
        final String extractedValue = mappingOption.get();

        if (extractedValue.startsWith("{{") && extractedValue.endsWith("}}")) {

            final String mappingContext = extractedValue
                    .substring(extractedValue.indexOf(' ') + 1, extractedValue.indexOf(':'));

            if (!mappingContext.equals(PlaceholderFactory.newHeadersPlaceholder().getPrefix())) {
                noErrorOccurred = false;
            }
        }
    }

    private CharSequence mappingConnectionOptions(final Optional<String> mappingOption) {
        final String extractedValue = mappingOption.get();

        if (extractedValue.startsWith("{{") && extractedValue.endsWith("}}")) {

            final String mappingValue =
                    extractedValue.substring(extractedValue.indexOf(":") + 1, extractedValue.lastIndexOf(" "));
            return extractedHeader.get(mappingValue);
        }
        return extractedValue;
    }


    private void checkIfEntriesSet(final HashMap<String, String> extractedHeader) {
        for (Map.Entry<String, String> entry : extractedHeader.entrySet()) {
            if (entry.getValue() == null || "".equals(entry.getValue())) {
                LOGGER.info("Header {} is not set", entry.getKey());
                noErrorOccurred = false;
            }
        }
    }

    private HashMap<String, String> extractHeader(final ExternalMessage externalMessage,
            final String... headerToExtract) {

        HashMap<String, String> extractedHeader = new HashMap<>();

        for (String header : headerToExtract) {
            extractedHeader.put(
                    header,
                    externalMessage.findHeader(header).orElse(""));
        }
        return extractedHeader;
    }

    @Override
    public Optional<ExternalMessage> map(final Adaptable adaptable) {
        return Optional.empty();
    }
}
