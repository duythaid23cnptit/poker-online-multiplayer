package com.ptit.poker.social.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record SendFriendRequest(@NotNull @Positive Long recipientUserId) {
}
