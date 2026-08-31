package com.ptit.poker.social.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ptit.poker.player.domain.PresenceStatus;
import com.ptit.poker.social.domain.FriendshipStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FriendshipViewSerializationTests {
    private final ObjectMapper json = new ObjectMapper();

    @Test void lifecycleViewOmitsUndefinedPresenceStatus() throws Exception {
        JsonNode serialized = serialize(null);

        assertThat(serialized.has("presenceStatus")).isFalse();
    }

    @Test void acceptedFriendViewIncludesDefinedPresenceStatus() throws Exception {
        JsonNode serialized = serialize(PresenceStatus.OFFLINE);

        assertThat(serialized.path("presenceStatus").asText()).isEqualTo("OFFLINE");
    }

    private JsonNode serialize(PresenceStatus presence) throws Exception {
        FriendshipStatus status = presence == null ? FriendshipStatus.PENDING : FriendshipStatus.ACCEPTED;
        FriendshipView view = new FriendshipView(7, status, null,
                null, new SocialPlayerQueryPort.SafePlayerSummary(8, "Friend", null), presence);
        return json.readTree(json.writeValueAsString(view));
    }
}
