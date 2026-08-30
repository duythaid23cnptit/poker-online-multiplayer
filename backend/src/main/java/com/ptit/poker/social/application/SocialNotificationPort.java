package com.ptit.poker.social.application;

public interface SocialNotificationPort {
    void send(FriendshipNotificationRequested notification);
}
