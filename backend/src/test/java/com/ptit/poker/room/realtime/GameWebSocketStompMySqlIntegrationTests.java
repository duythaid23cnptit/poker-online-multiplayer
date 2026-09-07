package com.ptit.poker.room.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import com.fasterxml.jackson.databind.*;
import com.ptit.poker.auth.domain.*;
import com.ptit.poker.auth.infrastructure.persistence.*;
import com.ptit.poker.auth.infrastructure.security.JwtService;
import com.ptit.poker.game.api.realtime.GameEventType;
import com.ptit.poker.game.application.realtime.GameRealtimePublisher;
import com.ptit.poker.game.application.realtime.GameRealtimeApplicationService;
import com.ptit.poker.game.application.runtime.*;
import com.ptit.poker.game.infrastructure.persistence.PlayerActionRepository;
import com.ptit.poker.game.infrastructure.persistence.PokerHandRepository;
import com.ptit.poker.room.api.dto.*;
import com.ptit.poker.room.application.RoomApplicationService;
import com.ptit.poker.room.domain.*;
import com.ptit.poker.room.infrastructure.persistence.RoomPlayerEntity;
import com.ptit.poker.room.infrastructure.persistence.RoomPlayerRepository;
import com.ptit.poker.support.TestDatabaseSafetyInitializer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Predicate;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@EnabledIfEnvironmentVariable(named="TEST_DB_URL", matches=".+")
@EnabledIfEnvironmentVariable(named="TEST_DB_USERNAME", matches=".+")
@EnabledIfEnvironmentVariable(named="TEST_DB_PASSWORD", matches=".+")
@ActiveProfiles("test")
@ContextConfiguration(initializers=TestDatabaseSafetyInitializer.class)
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
        "poker.game.turn-timeout=3s","poker.game.timer-update-cadence=500ms"})
@Import(RoomWebSocketStompMySqlIntegrationTests.SubscriptionProbeConfiguration.class)
class GameWebSocketStompMySqlIntegrationTests {
    @LocalServerPort int port;
    @Autowired UserRepository users; @Autowired PasswordEncoder passwords; @Autowired JwtService jwt;
    @Autowired RoomApplicationService rooms; @Autowired GameRuntimeService runtime;
    @Autowired GameRealtimeApplicationService realtime; @Autowired PlayerActionRepository actions;
    @Autowired RoomPlayerRepository roomPlayers;
    @Autowired PokerHandRepository hands;
    @MockitoSpyBean GameRealtimePublisher gamePublisher;
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
        readyWithoutAutoStart(roomId,first,second);
        GameRuntimeView started=runtime.startGame(roomId,first.getId());

        Handler firstHandler=new Handler(), secondHandler=new Handler(), spectatorHandler=new Handler(), outsiderHandler=new Handler();
        StompSession firstSession=connect(first,firstHandler), secondSession=connect(second,secondHandler), spectatorSession=connect(spectator,spectatorHandler);
        StompSession outsiderSession=connect(outsider,outsiderHandler);
        QueueFrames firstPublic=new QueueFrames(started), secondPublic=new QueueFrames(started), spectatorPublic=new QueueFrames(started);
        QueueFrames firstPrivate=new QueueFrames(started), secondPrivate=new QueueFrames(started), spectatorPrivate=new QueueFrames(started);
        subscribe(firstSession,"/topic/game/"+started.gameId(),firstPublic);
        subscribe(secondSession,"/topic/game/"+started.gameId(),secondPublic);
        subscribe(spectatorSession,"/topic/game/"+started.gameId(),spectatorPublic);
        subscribe(firstSession,"/user/queue/private",firstPrivate);
        subscribe(secondSession,"/user/queue/private",secondPrivate);
        subscribe(spectatorSession,"/user/queue/private",spectatorPrivate);
        outsiderSession.subscribe("/topic/game/"+started.gameId(),new QueueFrames(started));
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
        JsonNode spectatorTimer=await(spectatorPublic,GameEventType.TIMER_UPDATE);
        assertThat(spectatorTimer.at("/payload/turnId").asText()).isEqualTo(started.turnId().toString());
        assertThat(spectatorTimer.toString()).doesNotContain("holeCards","legalActions");
        assertThat(spectatorPrivate.poll(300,TimeUnit.MILLISECONDS)).isNull();

        UUID clientId=UUID.randomUUID();
        spectatorSession.send("/app/game/"+started.gameId()+"/action",Map.of("actionType","FOLD","turnId",started.turnId(),"clientActionId",UUID.randomUUID()));
        assertThat(await(spectatorPrivate,GameEventType.COMMAND_ERROR).at("/payload/code").asText()).isEqualTo("ACTION_REJECTED");
        assertThat(actions.findAllByPokerHandIdOrderByActionSequence(started.handId())).isEmpty();

        StompSession actorSession=started.currentTurnUserId().equals(first.getId())?firstSession:secondSession;
        actorSession.send("/app/game/"+started.gameId()+"/action",Map.of("actionType","FOLD","turnId",started.turnId(),"clientActionId",clientId));
        JsonNode action=await(secondPublic,GameEventType.PLAYER_ACTION);
        assertThat(action.at("/payload/clientActionId").asText()).isEqualTo(clientId.toString());
        await(secondPublic,GameEventType.GAME_RESULT); await(secondPublic,GameEventType.HAND_FINISHED,
                node->node.at("/payload/handId").asLong()==started.handId());
        secondPublic.assertOrder(GameEventType.PLAYER_ACTION,GameEventType.GAME_RESULT,GameEventType.HAND_FINISHED);
        assertThat(actions.findAllByPokerHandIdOrderByActionSequence(started.handId())).hasSize(1);

        actorSession.send("/app/game/"+started.gameId()+"/action",Map.of("actionType","FOLD","turnId",started.turnId(),"clientActionId",UUID.randomUUID()));
        QueueFrames actorPrivate=started.currentTurnUserId().equals(first.getId())?firstPrivate:secondPrivate;
        await(actorPrivate,GameEventType.COMMAND_ERROR);
        assertThat(actions.findAllByPokerHandIdOrderByActionSequence(started.handId())).hasSize(1);

        GameRuntimeView secondHand=realtime.startNextHand(started.gameId());
        assertThat(secondHand.handNumber()).isEqualTo(2);
        assertThat(secondHand.handId()).isNotEqualTo(started.handId());
        assertThat(awaitSecondHandStarted(secondPublic,started,secondHand).at("/payload/handNumber").asLong()).isEqualTo(2);
        secondPublic.assertOrder(GameEventType.HAND_FINISHED,GameEventType.HAND_STARTED);
        assertThat(await(firstPrivate,GameEventType.HOLE_CARDS).at("/payload/handId").asLong()).isNotEqualTo(started.handId());
        assertThat(await(secondPrivate,GameEventType.HOLE_CARDS).at("/payload/handId").asLong()).isNotEqualTo(started.handId());
    }

    @Test void finalReadyCommandLeavesRoomWaitingUntilExplicitHostStart() throws Exception {
        UserEntity first=user(),second=user();long roomId=rooms.create(first.getId(),new CreateRoomRequest(
                "auto-stomp-"+UUID.randomUUID(),RoomType.PUBLIC,6,50,100,1_000,null)).room().id();
        rooms.join(first.getId(),roomId,new JoinRoomRequest(false,1,1_000L,null));
        rooms.join(second.getId(),roomId,new JoinRoomRequest(false,2,1_000L,null));
        Handler firstHandler=new Handler(),secondHandler=new Handler();
        StompSession firstSession=connect(first,firstHandler),secondSession=connect(second,secondHandler);
        QueueFrames firstPrivate=new QueueFrames(),secondPrivate=new QueueFrames();
        subscribe(firstSession,"/user/queue/private",firstPrivate);subscribe(secondSession,"/user/queue/private",secondPrivate);

        firstSession.send("/app/room/"+roomId+"/ready",Map.of("clientCommandId",UUID.randomUUID(),"ready",true));
        secondSession.send("/app/room/"+roomId+"/ready",Map.of("clientCommandId",UUID.randomUUID(),"ready",true));

        awaitCondition(() -> rooms.detail(first.getId(),roomId).members().stream()
                .filter(member -> member.seatNumber() != null)
                .allMatch(member -> member.state() == RoomPlayerState.READY));
        assertThat(runtime.currentViewByRoom(roomId)).isEmpty();
        assertThat(rooms.detail(first.getId(),roomId).room().status()).isEqualTo(RoomStatus.WAITING);
        assertThat(firstPrivate.poll(100,TimeUnit.MILLISECONDS)).isNull();
        assertThat(secondPrivate.poll(100,TimeUnit.MILLISECONDS)).isNull();

        GameRuntimeView explicitlyStarted=realtime.startGame(roomId,first.getId());
        JsonNode firstHole=await(firstPrivate,GameEventType.HOLE_CARDS),secondHole=await(secondPrivate,GameEventType.HOLE_CARDS);
        UUID gameId=UUID.fromString(firstHole.path("gameId").asText());
        assertThat(secondHole.path("gameId").asText()).isEqualTo(gameId.toString());
        assertThat(firstHole.path("roomId").asLong()).isEqualTo(roomId);
        assertThat(runtime.currentView(gameId).players()).extracting(GameRuntimeView.PlayerView::userId)
                .containsExactlyInAnyOrder(first.getId(),second.getId());
        assertThat(explicitlyStarted.gameId()).isEqualTo(gameId);
        verify(gamePublisher,timeout(5_000).times(1)).publishPublic(argThat(
                event->event.type()==GameEventType.GAME_STARTED&&event.roomId()==roomId&&event.gameId().equals(gameId)));
    }

    @Test void deadlineAutoFoldPersistsAndPublishesTerminalEvents() throws Exception {
        Fixture fixture=fixture(); GameRuntimeView started=runtime.startGame(fixture.roomId(),fixture.first().getId());
        Handler handler=new Handler();StompSession session=connect(fixture.first(),handler);QueueFrames publicFrames=new QueueFrames(started);
        subscribe(session,"/topic/game/"+started.gameId(),publicFrames);realtime.announceStartedGame(started);
        await(publicFrames,GameEventType.TIMER_UPDATE);
        JsonNode action=await(publicFrames,GameEventType.PLAYER_ACTION);
        assertThat(action.at("/payload/actionType").asText()).isEqualTo("FOLD");
        assertThat(action.at("/payload/automatic").asBoolean()).isTrue();
        await(publicFrames,GameEventType.GAME_RESULT);await(publicFrames,GameEventType.HAND_FINISHED,
                node->node.at("/payload/handId").asLong()==started.handId());
        publicFrames.assertOrder(GameEventType.PLAYER_ACTION,GameEventType.GAME_RESULT,GameEventType.HAND_FINISHED);
        assertThat(actions.findAllByPokerHandIdOrderByActionSequence(started.handId())).hasSize(1)
                .extracting(value->value.getActionType()).containsExactly(com.ptit.poker.game.domain.betting.PokerActionType.FOLD);
    }

    @Test void deadlineAutoCheckPersistsAndPublishesThenOldTurnCannotDuplicate() throws Exception {
        Fixture fixture=fixture();GameRuntimeView started=runtime.startGame(fixture.roomId(),fixture.first().getId());
        GameRuntimeView matched=runtime.applyAction(started.gameId(),started.currentTurnUserId(),new GameActionIntent(
                started.turnId(),UUID.randomUUID(),com.ptit.poker.game.domain.betting.PokerActionType.CALL,0));
        Handler handler=new Handler();StompSession session=connect(fixture.second(),handler);QueueFrames publicFrames=new QueueFrames(matched);
        subscribe(session,"/topic/game/"+matched.gameId(),publicFrames);realtime.announceStartedGame(matched);
        await(publicFrames,GameEventType.TIMER_UPDATE);JsonNode action=await(publicFrames,GameEventType.PLAYER_ACTION);
        assertThat(action.at("/payload/actionType").asText()).isEqualTo("CHECK");assertThat(action.at("/payload/automatic").asBoolean()).isTrue();
        assertThat(actions.findAllByPokerHandIdOrderByActionSequence(matched.handId())).hasSize(2);
        GameRuntimeView current=runtime.currentView(matched.gameId());
        realtime.handleAction(current.gameId(),current.currentTurnUserId(),new com.ptit.poker.game.api.realtime.GameActionMessage(
                com.ptit.poker.game.domain.betting.PokerActionType.FOLD,null,current.turnId(),UUID.randomUUID()));
        assertThat(actions.findAllByPokerHandIdOrderByActionSequence(matched.handId())).hasSize(3);
    }

    private Fixture fixture(){UserEntity first=user(),second=user();long roomId=rooms.create(first.getId(),new CreateRoomRequest(
            "timer-"+UUID.randomUUID(),RoomType.PUBLIC,6,50,100,1_000,null)).room().id();
        rooms.join(first.getId(),roomId,new JoinRoomRequest(false,1,1_000L,null));rooms.join(second.getId(),roomId,new JoinRoomRequest(false,2,1_000L,null));
        readyWithoutAutoStart(roomId,first,second);return new Fixture(roomId,first,second);}
    private void readyWithoutAutoStart(long roomId,UserEntity...players){for(UserEntity player:players){
        RoomPlayerEntity member=roomPlayers.findByRoomIdAndUserId(roomId,player.getId()).orElseThrow();member.setReady(true);roomPlayers.saveAndFlush(member);}}
    private record Fixture(long roomId,UserEntity first,UserEntity second){}

    private JsonNode await(QueueFrames frames,GameEventType type)throws Exception{
        return await(frames,type,node->true);
    }
    private static void awaitCondition(java.util.function.BooleanSupplier condition) throws InterruptedException {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while(!condition.getAsBoolean()) {
            if(System.nanoTime()>=deadline)throw new AssertionError("Timed out waiting for authoritative readiness");
            Thread.sleep(20);
        }
    }
    private JsonNode await(QueueFrames frames,GameEventType type,Predicate<JsonNode> identity)throws Exception{
        return frames.await(type,identity,15,TimeUnit.SECONDS);
    }
    private JsonNode awaitSecondHandStarted(QueueFrames frames,GameRuntimeView first,GameRuntimeView expected)throws Exception{
        try{return await(frames,GameEventType.HAND_STARTED,node->node.at("/payload/handNumber").asLong()==2);}
        catch(AssertionError failure){throw secondHandTimeout(frames,first,expected);}
    }
    private AssertionError secondHandTimeout(QueueFrames frames,GameRuntimeView first,GameRuntimeView expected){
        GameRuntimeView current=runtime.currentView(first.gameId());
        long persisted=hands.findAllByGameSessionIdOrderByHandNumber(first.gameSessionId()).size();
        return new AssertionError("Timed out waiting for hand 2 HAND_STARTED: gameId="+first.gameId()
                +", sessionId="+first.gameSessionId()+", hand1Id="+first.handId()+", expectedHand2Id="+expected.handId()
                +", runtimeHandId="+current.handId()+", runtimeHandNumber="+current.handNumber()
                +", handCompleted="+current.handCompleted()+", sessionFinished="+current.sessionFinished()
                +", persistedHands="+persisted+", capturedTypes="+frames.types());
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
    private final class QueueFrames implements StompFrameHandler{
        private final Object monitor=new Object();private final List<JsonNode> history=new ArrayList<>();
        private final Set<Integer> consumed=new HashSet<>();private final GameRuntimeView expected;
        private QueueFrames(){this.expected=null;}
        private QueueFrames(GameRuntimeView expected){this.expected=expected;}
        public Type getPayloadType(StompHeaders headers){return byte[].class;}
        public void handleFrame(StompHeaders headers,Object payload){synchronized(monitor){try{history.add(json.readTree((byte[])payload));}
            catch(Exception failure){throw new IllegalStateException("Invalid STOMP JSON",failure);}monitor.notifyAll();}}
        JsonNode await(GameEventType type,Predicate<JsonNode> identity,long time,TimeUnit unit)throws InterruptedException{
            long deadline=System.nanoTime()+unit.toNanos(time);synchronized(monitor){while(true){
                for(int index=0;index<history.size();index++)if(!consumed.contains(index)){
                    JsonNode node=history.get(index);if(type.name().equals(node.path("type").asText())&&identity.test(node)){
                        consumed.add(index);return node;}}
                long remaining=deadline-System.nanoTime();if(remaining<=0)throw timeout(type);
                TimeUnit.NANOSECONDS.timedWait(monitor,remaining);}}}
        JsonNode poll(long time,TimeUnit unit)throws InterruptedException{try{return awaitAny(time,unit);}catch(AssertionError ignored){return null;}}
        private JsonNode awaitAny(long time,TimeUnit unit)throws InterruptedException{long deadline=System.nanoTime()+unit.toNanos(time);
            synchronized(monitor){while(true){for(int index=0;index<history.size();index++)if(consumed.add(index))return history.get(index);
                long remaining=deadline-System.nanoTime();if(remaining<=0)throw new AssertionError("No event received");TimeUnit.NANOSECONDS.timedWait(monitor,remaining);}}}
        void assertOrder(GameEventType...types){synchronized(monitor){int previous=-1;for(GameEventType type:types){int found=-1;
            for(int index=previous+1;index<history.size();index++)if(type.name().equals(history.get(index).path("type").asText())){found=index;break;}
            assertThat(found).as("event order "+Arrays.toString(types)+" in "+types()).isGreaterThan(previous);previous=found;}}}
        List<String> types(){synchronized(monitor){return history.stream().map(node->node.path("type").asText()).toList();}}
        private AssertionError timeout(GameEventType type){if(expected==null)return new AssertionError("Timed out waiting for "+type+": capturedTypes="+types());
            GameRuntimeView current=null;boolean active=true;try{current=runtime.currentView(expected.gameId());}
            catch(RuntimeException failure){active=false;}long handCount=hands.findAllByGameSessionIdOrderByHandNumber(expected.gameSessionId()).size();
            long actionCount=actions.countByPokerHandId(expected.handId());boolean result=types().contains(GameEventType.GAME_RESULT.name());
            return new AssertionError("Timed out waiting for "+type+": gameId="+expected.gameId()+", expectedHandId="+expected.handId()
                    +", expectedHandNumber="+expected.handNumber()+", activeGamePresent="+active+", runtimeHandId="+(current==null?null:current.handId())
                    +", runtimeHandNumber="+(current==null?null:current.handNumber())+", handCompleted="+(current==null?null:current.handCompleted())
                    +", sessionFinished="+(current==null?null:current.sessionFinished())+", currentTurnId="+(current==null?null:current.turnId())
                    +", persistedHands="+handCount+", playerActions="+actionCount+", gameResultReceived="+result+", capturedTypes="+types());}}
    private static final class Handler extends StompSessionHandlerAdapter{
        private final CompletableFuture<Throwable> failure=new CompletableFuture<>();
        public void handleTransportError(StompSession session,Throwable exception){failure.complete(exception);}
        public void handleException(StompSession session,StompCommand command,StompHeaders headers,byte[] payload,Throwable exception){failure.complete(exception);}
        public void handleFrame(StompHeaders headers,Object payload){failure.complete(new IllegalStateException("STOMP ERROR: "+new String((byte[])payload,StandardCharsets.UTF_8)));}}
}
