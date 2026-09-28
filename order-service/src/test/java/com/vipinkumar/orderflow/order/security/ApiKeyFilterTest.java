package com.vipinkumar.orderflow.order.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiKeyFilterTest {

    private static final String KEY = "a-sufficiently-long-key";
    private final ApiKeyFilter filter = new ApiKeyFilter(KEY);

    private MockHttpServletResponse run(String path, String key) throws Exception {
        var request = new MockHttpServletRequest("GET", path);
        if (key != null) request.addHeader(ApiKeyFilter.HEADER, key);
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    @Test
    void rejectsMissingOrWrongKey() throws Exception {
        assertThat(run("/api/v1/orders", null).getStatus()).isEqualTo(401);
        assertThat(run("/api/v1/orders", "wrong").getStatus()).isEqualTo(401);
        assertThat(run("/graphql", "wrong").getStatus()).isEqualTo(401);
    }

    @Test
    void acceptsCorrectKey() throws Exception {
        assertThat(run("/api/v1/orders", KEY).getStatus()).isEqualTo(200);
    }

    @Test
    void leavesProbesOpen() throws Exception {
        assertThat(run("/actuator/health/readiness", null).getStatus()).isEqualTo(200);
    }

    @Test
    void refusesWeakKeysAtStartup() {
        assertThatThrownBy(() -> new ApiKeyFilter("short")).isInstanceOf(IllegalArgumentException.class);
    }
}
