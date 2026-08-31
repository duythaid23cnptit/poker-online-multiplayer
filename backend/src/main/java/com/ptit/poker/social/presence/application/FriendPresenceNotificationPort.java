package com.ptit.poker.social.presence.application;

public interface FriendPresenceNotificationPort {
    void notifyFriend(long recipientUserId, PresenceChanged changed);
}
