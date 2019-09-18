package org.eclipse.ditto.services.connectivity.mapping.custom;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
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

    public static final String FEATURE_ID = "ConnectionStatus";
    public static final String FEATURE_DEFINITION = "com.bosch.iot.suite.standard:ConnectionStatus:1.0.0";
    public static final String FEATURE_PROPERTIE_CONNECTED_SINCE = "connectedSince";
    public static final String FEATURE_PROPERTIE_CONNECTED_UNTIL = "connectedUntil";

    public static final Logger LOGGER = LoggerFactory.getLogger(ConnectionStatusMessageMapper.class);
    public static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mmX")
            .withZone(ZoneOffset.UTC);

    private boolean noErrorOccurred;

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

    }

    @Override
    public Optional<Adaptable> map(final ExternalMessage externalMessage) {
        HashMap<String, String> extractedHeader =
                extractHeader(externalMessage, HEADER_HUB_TTD, HEADER_HUB_CREATION_TIME, HEADER_HUB_DEVICE_ID);

        checkIfEntriesSet(extractedHeader);

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

        if (noErrorOccurred) {
            final ThingId thingId = ThingId.of(extractedHeader.get(HEADER_HUB_DEVICE_ID));

            //Set time to ISO-8601 UTC
            String connectedSince = FORMATTER.format(Instant.ofEpochSecond(creationTime));
            String connectedUntil = FORMATTER.format(Instant.ofEpochSecond(creationTime + ttd));

            //Build propertyPath of featureId
            final FeatureProperties featureProperties = FeatureProperties.newBuilder()
                    .set(FEATURE_PROPERTIE_CONNECTED_SINCE, connectedSince)
                    .set(FEATURE_PROPERTIE_CONNECTED_UNTIL, connectedUntil)
                    .build();

            final FeatureDefinition featureDefinition = FeatureDefinition.fromIdentifier(FEATURE_DEFINITION);

            final Feature feature = Feature.newBuilder()
                    .definition(featureDefinition)
                    .properties(featureProperties)
                    .withId(FEATURE_ID)
                    .build();

            final ModifyFeature modifyFeature = ModifyFeature.of(thingId, feature, DittoHeaders.empty());

            final Adaptable adaptable = DittoProtocolAdapter.newInstance().toAdaptable(modifyFeature);
            LOGGER.info("Feature {} of Thing {} is modified", FEATURE_ID, extractedHeader.get(HEADER_HUB_DEVICE_ID));
            return Optional.of(adaptable);
        } else { return Optional.empty();}
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
