package com.prayerlink.notification;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.http.server.observation.DefaultServerRequestObservationConvention;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

public class ObservationConventionNpeReproductionTest {

    @Test
    void testNpeWhenMethodIsNullInDefaultObservationConvention() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod(null);
        MockHttpServletResponse response = new MockHttpServletResponse();

        ServerRequestObservationContext context = new ServerRequestObservationContext(request, response);
        DefaultServerRequestObservationConvention convention = new DefaultServerRequestObservationConvention();

        // Calling getLowCardinalityKeyValues directly triggers convention.method() -> KNOWN_METHODS.contains(null)
        assertThrows(NullPointerException.class, () -> {
            convention.getLowCardinalityKeyValues(context);
        });
    }
}
