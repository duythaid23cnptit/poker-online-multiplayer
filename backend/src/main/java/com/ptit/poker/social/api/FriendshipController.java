package com.ptit.poker.social.api;

import com.ptit.poker.auth.infrastructure.security.AuthenticatedUser;
import com.ptit.poker.social.application.FriendRequestDirection;
import com.ptit.poker.social.application.FriendshipSendResult;
import com.ptit.poker.social.application.FriendshipService;
import com.ptit.poker.social.application.FriendshipView;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Profile("!bootstrap")
@RequestMapping("/api/v1")
public class FriendshipController {
    private final FriendshipService friendships;

    public FriendshipController(FriendshipService friendships) {
        this.friendships = friendships;
    }

    @PostMapping("/friend-requests")
    ResponseEntity<FriendshipView> send(@AuthenticationPrincipal AuthenticatedUser user,
                                        @Valid @RequestBody SendFriendRequest request) {
        FriendshipSendResult result = friendships.sendFriendRequest(user.userId(), request.recipientUserId());
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(result.friendship());
    }

    @GetMapping("/friend-requests")
    List<FriendshipView> requests(@AuthenticationPrincipal AuthenticatedUser user,
                                  @RequestParam String direction) {
        return friendships.listFriendRequests(user.userId(), FriendRequestDirection.parse(direction));
    }

    @PostMapping("/friend-requests/{requestId}/accept")
    FriendshipView accept(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long requestId) {
        return friendships.acceptFriendRequest(user.userId(), requestId);
    }

    @PostMapping("/friend-requests/{requestId}/reject")
    FriendshipView reject(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long requestId) {
        return friendships.rejectFriendRequest(user.userId(), requestId);
    }

    @GetMapping("/friends")
    List<FriendshipView> friends(@AuthenticationPrincipal AuthenticatedUser user) {
        return friendships.listFriends(user.userId());
    }

    @DeleteMapping("/friends/{friendId}")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.NO_CONTENT)
    void remove(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long friendId) {
        friendships.removeFriend(user.userId(), friendId);
    }
}
