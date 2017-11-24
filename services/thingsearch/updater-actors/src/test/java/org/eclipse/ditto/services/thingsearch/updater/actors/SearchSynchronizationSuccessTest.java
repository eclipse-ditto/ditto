package org.eclipse.ditto.services.thingsearch.updater.actors;


import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

public class SearchSynchronizationSuccessTest {

    @Test
    public void newInstance() throws Exception {
        final LocalDateTime ts = LocalDateTime.now().minus(2, ChronoUnit.SECONDS);
        final SearchSynchronizationSuccess searchSynchronizationSuccess = SearchSynchronizationSuccess.newInstance(ts);
        assertThat(searchSynchronizationSuccess).isNotNull();
        assertThat(searchSynchronizationSuccess.getUtcTimestamp()).isEqualTo(ts);
    }

    @Test
    public void equalsAndHashcode() {
        EqualsVerifier.forClass(SearchSynchronizationSuccess.class).verify();
    }

}