package com.ptit.poker.admin.application;

public interface AdminUserModerationPort {
    Change suspend(long userId);
    Change reactivate(long userId);
    record Change(boolean changed, String previousStatus, String currentStatus) {}
}
