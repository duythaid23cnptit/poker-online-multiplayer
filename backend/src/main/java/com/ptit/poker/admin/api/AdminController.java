package com.ptit.poker.admin.api;

import com.ptit.poker.admin.application.AdminQueryService;
import com.ptit.poker.admin.application.AdminModerationService;
import com.ptit.poker.auth.infrastructure.security.AuthenticatedUser;
import static com.ptit.poker.admin.application.AdminModels.*;
import java.time.Instant;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("!bootstrap")
@RequestMapping("/api/v1/admin")
public class AdminController {
    private final AdminQueryService admin;
    private final AdminModerationService moderation;

    public AdminController(AdminQueryService admin, AdminModerationService moderation) {
        this.admin = admin; this.moderation = moderation;
    }

    @GetMapping("/overview")
    public Overview overview() {
        return admin.overview();
    }

    @GetMapping("/users")
    public Page<UserItem> users(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search, @RequestParam(required = false) String status,
            @RequestParam(required = false) String role) {
        return admin.users(page, size, search, status, role);
    }

    @GetMapping("/users/{id}")
    public UserDetail user(@PathVariable long id) {
        return admin.user(id);
    }

    @GetMapping("/rooms")
    public Page<RoomItem> rooms(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search, @RequestParam(required = false) String status,
            @RequestParam(required = false) String roomType) {
        return admin.rooms(page, size, search, status, roomType);
    }

    @GetMapping("/rooms/{id}")
    public RoomDetail room(@PathVariable long id) {
        return admin.room(id);
    }

    @GetMapping("/games")
    public Page<GameItem> games(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status, @RequestParam(required = false) Long roomId,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return admin.games(page, size, status, roomId, userId, from, to);
    }

    @GetMapping("/games/{id}")
    public GameDetail game(@PathVariable long id) {
        return admin.game(id);
    }

    @GetMapping("/games/{id}/hands")
    public Page<HandItem> hands(@PathVariable long id, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return admin.hands(id, page, size);
    }

    @PostMapping("/users/{id}/suspend")
    public MutationResponse suspend(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable long id,
            @Valid @RequestBody(required=false) AdminMutationRequest request,
            @RequestHeader(name="X-Request-Id",required=false) String requestId) {
        return moderation.suspend(actor.userId(),id,request==null?null:request.reason(),requestId);
    }

    @PostMapping("/users/{id}/reactivate")
    public MutationResponse reactivate(@AuthenticationPrincipal AuthenticatedUser actor,@PathVariable long id,
            @Valid @RequestBody(required=false) AdminMutationRequest request,
            @RequestHeader(name="X-Request-Id",required=false) String requestId) {
        return moderation.reactivate(actor.userId(),id,request==null?null:request.reason(),requestId);
    }

    @PostMapping("/rooms/{roomId}/players/{userId}/remove")
    public MutationResponse remove(@AuthenticationPrincipal AuthenticatedUser actor,@PathVariable long roomId,@PathVariable long userId,
            @Valid @RequestBody(required=false) AdminMutationRequest request,@RequestHeader(name="X-Request-Id",required=false) String requestId){
        return moderation.removePlayer(actor.userId(),roomId,userId,request==null?null:request.reason(),requestId);
    }

    @PostMapping("/rooms/{id}/close")
    public MutationResponse close(@AuthenticationPrincipal AuthenticatedUser actor,@PathVariable long id,
            @Valid @RequestBody(required=false) AdminMutationRequest request,@RequestHeader(name="X-Request-Id",required=false) String requestId){
        return moderation.closeRoom(actor.userId(),id,request==null?null:request.reason(),requestId);
    }

    @PostMapping("/games/{id}/terminate")
    public MutationResponse terminate(@AuthenticationPrincipal AuthenticatedUser actor,@PathVariable long id,
            @Valid @RequestBody(required=false) AdminMutationRequest request,@RequestHeader(name="X-Request-Id",required=false) String requestId){
        return moderation.terminate(actor.userId(),id,request==null?null:request.reason(),requestId);
    }

    @GetMapping("/audit-log")
    public Page<AuditItem> audit(@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size,
            @RequestParam(required=false)Long adminUserId,@RequestParam(required=false)String actionType,
            @RequestParam(required=false)String targetType,@RequestParam(required=false)Long targetId,
            @RequestParam(required=false)@DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME)Instant from,
            @RequestParam(required=false)@DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME)Instant to){
        return moderation.audit(page,size,adminUserId,actionType,targetType,targetId,from,to);
    }
}
