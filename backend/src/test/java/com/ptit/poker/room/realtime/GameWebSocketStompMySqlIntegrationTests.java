package com.ptit.poker.room.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import com.fasterxml.jackson.databind.*;
import com.ptit.poker.auth.domain.*;
import com.ptit.poker.auth.infrastructure.persistence.*;
import com.ptit.poker.auth.infrastructure.security.JwtService;
import com.ptit.poker.game.api.realtime.GameEventType;
import com.ptit.poker.game.application.realtime.GameRealtimeApplicationService;
import com.ptit.poker.game.application.runtime.*;
import com.ptit.poker.game.infrastructure.persistence.PlayerActionRepository;
import com.ptit.poker.room.api.dto.*;
import com.ptit.poker.room.application.RoomApplicationService;
import com.ptit.poker.room.domain.*;
import com.ptit.poker.support.TestDatabaseSafetyInitializer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.converter.*;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.*;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

@EnabledIfEnvironmentVariable(named="TEST_DB_URL", matches=".+")
@EnabledIfEnvironmentVariable(named="TEST_DB_USERNAME", matches=".+")
@EnabledIfEnvironmentVariable(named="TEST_DB_PASSWORD", matches=".+")
@ActiveProfiles("test")
@ContextConfiguration(initializers=TestDatabaseSafetyInitializer.class)
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(RoomWebSocketStompMySqlIntegrationTests.SubscriptionProbeConfiguration.class)
class GameWebSocketStompMySqlIntegrationTests {
    @LocalServerPort int port;
    @Autowired UserRepository users; @Autowired PasswordEncoder passwords; @Autowired JwtService jwt;
    @Autowired RoomApplicationService rooms; @Autowired GameRuntimeService runtime;
    @Autowired GameRealtimeApplicationService realtime; @Autowired PlayerActionRepository actions;
    @Autowired ObjectMapper json; @Autowired RoomWebSocketStompMySqlIntegrationTests.SubscriptionProbe subscriptions;
    private final List<StompSession> sessions = new ArrayList<>();
    private final List<WebSocketStompClient> clients = new ArrayList<>();

    @AfterEach void disconnect() {
        RuntimeException unexpected = null;
        try {
            for (StompSession session : sessions) try { disconnectQuietly(session); }
            catch (RuntimeException failure) { if (unexpected == null) unexpected = failure; else unexpected.addSuppressed(failure); }
        } finally { clients.forEach(WebSocketStompClient::stop); subscriptions.reset(); }
        if (unexpected != null) throw unexpected;
    }

    @Test void authoritativePrivatePublicSecurityStaleAndNextHandLifecycle() throws Exception {
        UserEntity first=user(), second=user(), spectator=user(), outsider=user();
        long roomId=rooms.create(first.getId(), new CreateRoomRequest("game-"+UUID.randomUUID(), RoomType.PUBLIC,6,50,100,1_000,null)).room().id();
        rooms.join(first.getId(),roomId,new JoinRoomRequest(false,1,1_000L,null));
        rooms.join(second.getId(),roomId,new JoinRoomRequest(false,2,1_000L,null));
        rooms.join(spectator.getId(),roomId,new JoinRoomRequest(true,null,null,null));
        rooms.setReady(first.getId(),roomId,true); rooms.setReady(second.getId(),roomId,true);
        GameRuntimeView started=runtime.startGame(roomId);

        Handler firstHandler=new Handler(), secondHandler=new Handler(), spectatorHandler=new Handler(), outsiderHandler=new Handler();
        StompSession firstSession=connect(first,firstHandler), secondSession=connect(second,secondHandler), spectatorSession=connect(spectator,spectatorHandler);
        StompSession outsiderSession=connect(outsider,outsiderHandler);
        QueueFrames firstPublic=new QueueFrames(), secondPublic=new QueueFrames(), spectatorPublic=new QueueFrames();
        QueueFrames firstPrivate=new QueueFrames(), secondPrivate=new QueueFrames(), spectatorPrivate=new QueueFrames();
        subscribe(firstSession,"/topic/game/"+started.gameId(),firstPublic);
        subscribe(secondSession,"/topic/game/"+started.gameId(),secondPublic);
        subscribe(spectatorSession,"/topic/game/"+started.gameId(),spectatorPublic);
        subscribe(firstSession,"/user/queue/private",firstPrivate);
        subscribe(secondSession,"/user/queue/private",secondPrivate);
        subscribe(spectatorSession,"/user/queue/private",spectatorPrivate);
        outsiderSession.subscribe("/topic/game/"+started.gameId(),new QueueFrames());
        assertThat(outsiderHandler.failure.get(5,TimeUnit.SECONDS)).isNotNull();

        realtime.announceStartedGame(started);
        JsonNode firstHole=await(firstPrivate,GameEventType.HOLE_CARDS), secondHole=await(secondPrivate,GameEventType.HOLE_CARDS);
        assertThat(firstHole.at("/payload/cards").size()).isEqualTo(2); assertThat(secondHole.at("/payload/cards").size()).isEqualTo(2);
        assertThat(firstHole.at("/payload/cards")).isNotEqualTo(secondHole.at("/payload/cards"));
        await(firstPrivate,GameEventType.YOUR_TURN);
        List<String> privateCodes=new ArrayList<>();
        firstHole.at("/payload/cards").forEach(card->privateCodes.add(card.toString()));
        secondHole.at("/payload/cards").forEach(card->privateCodes.add(card.toString()));
        for(QueueFrames publicFrames:List.of(firstPublic,secondPublic,spectatorPublic)) {
            JsonNode gameStarted=await(publicFrames,GameEventType.GAME_STARTED); JsonNode handStarted=await(publicFrames,GameEventType.HAND_STARTED);
            String publicJson=gameStarted+handStarted.toString();
            assertThat(publicJson).doesNotContain("holeCards","HOLE_CARDS");
            privateCodes.forEach(code->assertThat(publicJson).doesNotContain(code));
        }
        assertThat(spectatorPrivate.poll(300,TimeUnit.MILLISECONDS)).isNull();

        UUID clientId=UUID.randomUUID();
        spectatorSession.send("/app/game/"+started.gameId()+"/action",Map.of("actionType","FOLD","turnId",started.turnId(),"clientActionId",UUID.randomUUID()));
        assertThat(await(spectatorPrivate,GameEventType.COMMAND_ERROR).at("/payload/code").asText()).isEqualTo("ACTION_REJECTED");
        assertThat(actions.findAllByPokerHandIdOrderByActionSequence(started.handId())).isEmpty();

        StompSession actorSession=started.currentTurnUserId().equals(first.getId())?firstSession:secondSession;
        actorSession.send("/app/game/"+started.gameId()+"/action",Map.of("actionType","FOLD","turnId",started.turnId(),"clientActionId",clientId));
        JsonNode action=await(secondPublic,GameEventType.PLAYER_ACTION);
        assertThat(action.at("/payload/clientActionId").asText()).isEqualTo(clientId.toString());
        await(secondPublic,GameEventType.GAME_RESULT); await(secondPublic,GameEventType.HAND_FINISHED);
        assertThat(actions.findAllByPokerHandIdOrderByActionSequence(started.handId())).hasSize(1);

        actorSession.send("/app/game/"+started.gameId()+"/action",Map.of("actionType","FOLD","turnId",started.turnId(),"clientActionId",UUID.randomUUID()));
        QueueFrames actorPrivate=started.currentTurnUserId().equals(first.getId())?firstPrivate:secondPrivate;
        await(actorPrivate,GameEventType.COMMAND_ERROR);
        assertThat(actions.findAllByPokerHandIdOrderByActionSequence(started.handId())).hasSize(1);

        realtime.startNextHand(started.gameId());
        assertThat(await(secondPublic,GameEventType.HAND_STARTED).at("/payload/handNumber").asLong()).isEqualTo(2);
        assertThat(await(firstPrivate,GameEventType.HOLE_CARDS).at("/payload/handId").asLong()).isNotEqualTo(started.handId());
        assertThat(await(secondPrivate,GameEventType.HOLE_CARDS).at("/payload/handId").asLong()).isNotEqualTo(started.handId());
    }

    private JsonNode await(QueueFrames frames,GameEventType type)throws Exception{
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while(true){long remaining=deadline-System.nanoTime();if(remaining<=0)throw new AssertionError("Timed out waiting for "+type);
            byte[] payload=frames.poll(remaining,TimeUnit.NANOSECONDS);if(payload==null)throw new AssertionError("Timed out waiting for "+type);
            JsonNode node=json.readTree(payload);if(type.name().equals(node.path("type").asText()))return node;}
    }
    private StompSession connect(UserEntity user,Handler handler)throws Exception{
        StompHeaders headers=new StompHeaders();headers.add(HttpHeaders.AUTHORIZATION,"Bearer "+jwt.createAccessToken(user));
        StompSession session=client().connectAsync(url(),new WebSocketHttpHeaders(),headers,handler).get(5,TimeUnit.SECONDS);
        sessions.add(session);return session;
    }
    private void subscribe(StompSession session,String destination,StompFrameHandler handler)throws Exception{
        String id=UUID.randomUUID().toString();CompletableFuture<Void> ready=subscriptions.await(id,destination);
        StompHeaders headers=new StompHeaders();headers.setId(id);headers.setDestination(destination);session.subscribe(headers,handler);
        try{ready.get(5,TimeUnit.SECONDS);}catch(TimeoutException failure){subscriptions.cancel(id,destination);throw failure;}
    }
    private WebSocketStompClient client(){WebSocketStompClient client=new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new CompositeMessageConverter(List.of(new ByteArrayMessageConverter(),new MappingJackson2MessageConverter())));clients.add(client);return client;}
    private String url(){return "ws://localhost:"+port+"/ws";}
    private UserEntity user(){String suffix=UUID.randomUUID().toString().replace("-","").substring(0,12);
        return users.saveAndFlush(new UserEntity("g7_"+suffix,passwords.encode("password"),"g7_"+suffix+"@example.test",Role.PLAYER,AccountStatus.ACTIVE,5_000));}
    private void disconnectQuietly(StompSession session){if(session==null)return;try{if(session.isConnected())session.disconnect();}
        catch(MessageDeliveryException failure){if(!closed(failure))throw failure;}}
    private static boolean closed(Throwable failure){for(Throwable current=failure;current!=null&&current.getCause()!=current;current=current.getCause())
        if(current instanceof IllegalStateException state&&state.getMessage()!=null&&state.getMessage().toLowerCase(Locale.ROOT).contains("closed"))return true;return false;}
    private static final class QueueFrames implements StompFrameHandler{
        private final BlockingQueue<byte[]> frames=new LinkedBlockingQueue<>();public Type getPayloadType(StompHeaders headers){return byte[].class;}
        public void handleFrame(StompHeaders headers,Object payload){frames.add((byte[])payload);}byte[] poll(long time,TimeUnit unit)throws InterruptedException{return frames.poll(time,unit);}}
    private static final class Handler extends StompSessionHandlerAdapter{
        private final CompletableFuture<Throwable> failure=new CompletableFuture<>();
        public void handleTransportError(StompSession session,Throwable exception){failure.complete(exception);}
        public void handleException(StompSession session,StompCommand command,StompHeaders headers,byte[] payload,Throwable exception){failure.complete(exception);}
        public void handleFrame(StompHeaders headers,Object payload){failure.complete(new IllegalStateException("STOMP ERROR: "+new String((byte[])payload,StandardCharsets.UTF_8)));}}
}
