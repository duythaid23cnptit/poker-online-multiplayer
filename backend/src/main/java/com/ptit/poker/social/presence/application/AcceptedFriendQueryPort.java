package com.ptit.poker.social.presence.application;

import java.util.List;

public interface AcceptedFriendQueryPort {
    List<Long> findAcceptedFriendIds(long userId);
}
