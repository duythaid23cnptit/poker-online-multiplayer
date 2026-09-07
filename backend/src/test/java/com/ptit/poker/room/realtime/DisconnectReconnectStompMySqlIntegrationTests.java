package com.ptit.poker.room.realtime;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.*;
import com.ptit.poker.auth.domain.*;
import com.ptit.poker.auth.infrastructure.persistence.*;
import com.ptit.poker.auth.infrastructure.security.JwtService;
import com.ptit.poker.game.api.realtime.GameEventType;
import com.ptit.poker.game.application.realtime.*;
import com.ptit.poker.game.application.runtime.*;
import com.ptit.poker.game.domain.betting.PokerActionType;
import com.ptit.poker.game.infrastructure.persistence.*;
import com.ptit.poker.room.api.dto.*;
import com.ptit.poker.room.application.RoomApplicationService;
import com.ptit.poker.room.domain.*;
import com.ptit.poker.room.infrastructure.persistence.*;
import com.ptit.poker.support.TestDatabaseSafetyInitializer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;
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

@EnabledIfEnvironmentVariable(named="TEST_DB_URL",matches=".+")
@EnabledIfEnvironmentVariable(named="TEST_DB_USERNAME",matches=".+")
@EnabledIfEnvironmentVariable(named="TEST_DB_PASSWORD",matches=".+")
@ActiveProfiles("test")
@ContextConfiguration(initializers=TestDatabaseSafetyInitializer.class)
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
        "poker.game.reconnect-grace=3s","poker.game.turn-timeout=6s","poker.game.timer-update-cadence=500ms"})
@Import(RoomWebSocketStompMySqlIntegrationTests.SubscriptionProbeConfiguration.class)
class DisconnectReconnectStompMySqlIntegrationTests {
    @LocalServerPort int port;
    @Autowired UserRepository users; @Autowired PasswordEncoder passwords; @Autowired JwtService jwt;
    @Autowired RoomApplicationService rooms; @Autowired GameRuntimeService runtime;
    @Autowired GameRealtimeApplicationService realtime; @Autowired RealtimeConnectionRegistry connections;
    @Autowired RoomPlayerRepository roomPlayers; @Autowired GameSessionRepository gameSessions;
    @Autowired PokerHandRepository hands; @Autowired PlayerActionRepository actions; @Autowired HandPlayerRepository handPlayers;
    @Autowired PotRepository pots; @Autowired ObjectMapper json;
    @Autowired RoomWebSocketStompMySqlIntegrationTests.SubscriptionProbe subscriptions;
    private final List<StompSession> sessions=new ArrayList<>();
    private final List<WebSocketStompClient> clients=new ArrayList<>();

    @AfterEach void cleanup(){RuntimeException unexpected=null;try{for(StompSession session:sessions)try{disconnectQuietly(session);}
        catch(RuntimeException failure){if(unexpected==null)unexpected=failure;else unexpected.addSuppressed(failure);}}
        finally{clients.forEach(WebSocketStompClient::stop);subscriptions.reset();}if(unexpected!=null)throw unexpected;}

    @Test void lastOfTwoSessionsDisconnectsOnceAndReconnectRestoresPrivately() throws Exception {
        Fixture f=fixture();GameRuntimeView started=runtime.startGame(f.roomId(),f.first().getId());
        StompSession observer=connect(f.first());QueueFrames roomEvents=new QueueFrames();
        subscribe(observer,"/topic/room/"+f.roomId(),roomEvents);
        StompSession b1=connect(f.second()),b2=connect(f.second());
        awaitTrue(()->connections.activeSessionCount(f.second().getId())==2,"two B sessions");

        disconnectQuietly(b1);awaitTrue(()->connections.activeSessionCount(f.second().getId())==1,"one B session");
        assertThat(member(f).getPlayerState()).isEqualTo(RoomPlayerState.PLAYING);
        assertThat(runtime.currentView(started.gameId()).players().stream().filter(p->p.userId()==f.second().getId())
                .findFirst().orElseThrow().connected()).isTrue();
        assertThat(roomEvents.poll(300,TimeUnit.MILLISECONDS)).isNull();

        disconnectQuietly(b2);JsonNode disconnected=await(roomEvents,"PLAYER_DISCONNECTED");
        awaitTrue(()->member(f).getPlayerState()==RoomPlayerState.DISCONNECTED,"B disconnected in MySQL");
        assertThat(disconnected.path("type").asText()).isEqualTo("PLAYER_DISCONNECTED");
        assertThat(runtime.currentView(started.gameId()).players().stream().filter(p->p.userId()==f.second().getId())
                .findFirst().orElseThrow().connected()).isFalse();

        StompSession b3=connect(f.second());QueueFrames privateFrames=new QueueFrames();
        subscribe(b3,"/user/queue/private",privateFrames);
        assertThat(await(roomEvents,"PLAYER_RECONNECTED").path("type").asText()).isEqualTo("PLAYER_RECONNECTED");
        JsonNode state=await(privateFrames,GameEventType.GAME_STATE_UPDATE.name());
        JsonNode cards=await(privateFrames,GameEventType.HOLE_CARDS.name());
        assertThat(state.path("version").asLong()).isEqualTo(runtime.currentView(started.gameId()).stateVersion());
        assertThat(cards.at("/payload/cards").size()).isEqualTo(2);
        assertThat(member(f).getPlayerState()).isEqualTo(RoomPlayerState.PLAYING);
        assertThat(gameSessions.findAllByRoomIdOrderByStartedAtDesc(f.roomId())).hasSize(1);
    }

    @Test void completedHeadsUpHandWaitsAndReconnectResumesSameSession() throws Exception {
        Fixture f=fixture();GameRuntimeView started=runtime.startGame(f.roomId(),f.first().getId());long accountBefore=accountChips(f.second());
        StompSession observer=connect(f.first());QueueFrames roomEvents=new QueueFrames();subscribe(observer,"/topic/room/"+f.roomId(),roomEvents);
        StompSession b=connect(f.second());disconnectQuietly(b);await(roomEvents,"PLAYER_DISCONNECTED");
        awaitTrue(()->member(f).getPlayerState()==RoomPlayerState.DISCONNECTED,"B disconnected");

        realtime.handleAction(started.gameId(),started.currentTurnUserId(),new com.ptit.poker.game.api.realtime.GameActionMessage(
                PokerActionType.FOLD,null,started.turnId(),UUID.randomUUID()));
        awaitTrue(()->runtime.currentView(started.gameId()).handCompleted(),"hand completed");
        GameRuntimeView waiting=realtime.startNextHand(started.gameId());
        assertThat(waiting.handNumber()).isOne();assertThat(waiting.sessionFinished()).isFalse();
        assertThat(gameSessions.findById(started.gameSessionId()).orElseThrow().getStatus()).isEqualTo(GameSessionStatus.ACTIVE);
        assertThat(hands.findAllByGameSessionIdOrderByHandNumber(started.gameSessionId())).hasSize(1);
        assertThat(users.findById(f.second().getId()).orElseThrow().getAccountChips()).isEqualTo(accountBefore);

        StompSession reconnected=connect(f.second());QueueFrames privateFrames=new QueueFrames();subscribe(reconnected,"/user/queue/private",privateFrames);
        await(roomEvents,"PLAYER_RECONNECTED");await(privateFrames,GameEventType.GAME_STATE_UPDATE.name());
        GameRuntimeView next=realtime.startNextHand(started.gameId());
        assertThat(next.gameSessionId()).isEqualTo(started.gameSessionId());assertThat(next.handNumber()).isEqualTo(2);
        assertThat(hands.findAllByGameSessionIdOrderByHandNumber(started.gameSessionId())).hasSize(2);
        assertThat(gameSessions.findAllByRoomIdOrderByStartedAtDesc(f.roomId())).hasSize(1);
        assertThat(users.findById(f.second().getId()).orElseThrow().getAccountChips()).isEqualTo(accountBefore);
    }

    @Test void graceExpiryAfterCompletedHandCashOutsOnceAndFinishesSession() throws Exception {
        Fixture f=fixture();GameRuntimeView started=runtime.startGame(f.roomId(),f.first().getId());long accountBefore=accountChips(f.second());
        StompSession observer=connect(f.first());QueueFrames roomEvents=new QueueFrames();subscribe(observer,"/topic/room/"+f.roomId(),roomEvents);
        StompSession b=connect(f.second());disconnectQuietly(b);await(roomEvents,"PLAYER_DISCONNECTED");
        realtime.handleAction(started.gameId(),started.currentTurnUserId(),new com.ptit.poker.game.api.realtime.GameActionMessage(
                PokerActionType.FOLD,null,started.turnId(),UUID.randomUUID()));
        awaitTrue(()->runtime.currentView(started.gameId()).handCompleted(),"hand completed");
        realtime.startNextHand(started.gameId());long returned=member(f).getTableChips();
        assertThat(users.findById(f.second().getId()).orElseThrow().getAccountChips()).isEqualTo(accountBefore);

        awaitTrue(()->roomPlayers.findByRoomIdAndUserId(f.roomId(),f.second().getId()).orElseThrow().getLeftAt()!=null,"departure finalized");
        awaitTrue(()->gameSessions.findById(started.gameSessionId()).orElseThrow().getStatus()==GameSessionStatus.FINISHED,
                "game session finished after departure");
        assertThat(gameSessions.findById(started.gameSessionId()).orElseThrow().getStatus()).isEqualTo(GameSessionStatus.FINISHED);
        var departed=member(f);assertThat(departed.getSeatNumber()).isNull();assertThat(departed.getTableChips()).isZero();
        assertThat(users.findById(f.second().getId()).orElseThrow().getAccountChips()).isEqualTo(accountBefore+returned);
        assertThat(hands.findAllByGameSessionIdOrderByHandNumber(started.gameSessionId())).hasSize(1);
        awaitTrue(()->{try{runtime.currentView(started.gameId());return false;}catch(GameRuntimeException expected){return true;}},
                "runtime removed after finished session");
        assertThatThrownBy(()->runtime.currentView(started.gameId())).isInstanceOf(GameRuntimeException.class);
        StompSession late=connect(f.second());QueueFrames latePrivate=new QueueFrames();subscribe(late,"/user/queue/private",latePrivate);
        assertThat(latePrivate.poll(500,TimeUnit.MILLISECONDS)).isNull();
        assertThat(users.findById(f.second().getId()).orElseThrow().getAccountChips()).isEqualTo(accountBefore+returned);
    }

    @Test void reconnectingCurrentActorReceivesOnlyOwnCardsAndOriginalTurnDeadline() throws Exception {
        Fixture f=fixture();GameRuntimeView started=runtime.startGame(f.roomId(),f.first().getId());
        UserEntity actor=started.currentTurnUserId().equals(f.first().getId())?f.first():f.second();
        UserEntity other=actor.getId().equals(f.first().getId())?f.second():f.first();
        StompSession observer=connect(other);QueueFrames publicFrames=new QueueFrames(),roomEvents=new QueueFrames();
        subscribe(observer,"/topic/game/"+started.gameId(),publicFrames);subscribe(observer,"/topic/room/"+f.roomId(),roomEvents);
        StompSession actorSession=connect(actor);realtime.announceStartedGame(started);
        JsonNode originalTimer=await(publicFrames,GameEventType.TIMER_UPDATE.name());
        String deadline=originalTimer.at("/payload/deadline").asText();
        var own=runtime.privateView(started.gameId(),actor.getId()).holeCards();
        var others=runtime.privateView(started.gameId(),other.getId()).holeCards();

        disconnectQuietly(actorSession);await(roomEvents,"PLAYER_DISCONNECTED");
        StompSession restored=connect(actor);QueueFrames privateFrames=new QueueFrames();subscribe(restored,"/user/queue/private",privateFrames);
        await(roomEvents,"PLAYER_RECONNECTED");JsonNode state=await(privateFrames,GameEventType.GAME_STATE_UPDATE.name());
        JsonNode holeCards=await(privateFrames,GameEventType.HOLE_CARDS.name());
        JsonNode turn=await(privateFrames,GameEventType.YOUR_TURN.name());JsonNode timer=await(privateFrames,GameEventType.TIMER_UPDATE.name());
        assertThat(holeCards.at("/payload/cards")).isEqualTo(json.valueToTree(own));
        for(var card:others)assertThat(holeCards.at("/payload/cards").toString()).doesNotContain(json.writeValueAsString(card));
        assertThat(turn.at("/payload/turnId").asText()).isEqualTo(started.turnId().toString());
        assertThat(state.path("version").asLong()).isEqualTo(runtime.currentView(started.gameId()).stateVersion());
        assertThat(timer.at("/payload/deadline").asText()).isEqualTo(deadline);
        String publicJson=originalTimer.toString();for(var card:own)assertThat(publicJson).doesNotContain(json.writeValueAsString(card));
        for(var card:others)assertThat(publicJson).doesNotContain(json.writeValueAsString(card));
    }

    @Test void disconnectDuringTurnCreatesNoActionAndExistingDeadlineCreatesExactlyOne() throws Exception {
        Fixture f=fixture();GameRuntimeView started=runtime.startGame(f.roomId(),f.first().getId());
        UserEntity actor=started.currentTurnUserId().equals(f.first().getId())?f.first():f.second();
        UserEntity observerUser=actor.getId().equals(f.first().getId())?f.second():f.first();
        StompSession observer=connect(observerUser);QueueFrames publicFrames=new QueueFrames(),roomEvents=new QueueFrames();
        subscribe(observer,"/topic/game/"+started.gameId(),publicFrames);subscribe(observer,"/topic/room/"+f.roomId(),roomEvents);
        StompSession actorSession=connect(actor);realtime.announceStartedGame(started);
        JsonNode timer=await(publicFrames,GameEventType.TIMER_UPDATE.name());Instant deadline=Instant.parse(timer.at("/payload/deadline").asText());
        long before=actions.countByPokerHandId(started.handId());assertThat(before).isZero();
        disconnectQuietly(actorSession);await(roomEvents,"PLAYER_DISCONNECTED");
        long afterDisconnect=actions.countByPokerHandId(started.handId());assertThat(afterDisconnect).isEqualTo(before);
        awaitTrueUntil(()->actions.countByPokerHandId(started.handId())==before+1,deadline.plusSeconds(3),
                ()->timeoutDiagnostics(started,actor.getId(),before,afterDisconnect,deadline));
        JsonNode automatic=awaitUntil(publicFrames,GameEventType.PLAYER_ACTION.name(),deadline.plusSeconds(5));
        assertThat(automatic.at("/payload/automatic").asBoolean()).isTrue();
        awaitTrue(()->actions.countByPokerHandId(started.handId())==1,"one automatic player action");
        assertThat(actions.findAllByPokerHandIdOrderByActionSequence(started.handId())).hasSize(1);
    }

    @Test void foldedPlayerDisconnectAndReconnectPreservesFoldAndCommitment() throws Exception {
        ThreeFixture f=threeFixture();GameRuntimeView started=runtime.startGame(f.roomId(),f.first().getId());
        StompSession observer=connect(f.first());QueueFrames roomEvents=new QueueFrames();subscribe(observer,"/topic/room/"+f.roomId(),roomEvents);
        StompSession actorSession=connect(f.second());
        realtime.handleAction(started.gameId(),started.currentTurnUserId(),new com.ptit.poker.game.api.realtime.GameActionMessage(
                PokerActionType.CALL,null,started.turnId(),UUID.randomUUID()));
        GameRuntimeView beforeFold=runtime.currentView(started.gameId());assertThat(beforeFold.currentTurnUserId()).isEqualTo(f.second().getId());
        long committed=player(beforeFold,f.second().getId()).totalCommitted();
        realtime.handleAction(started.gameId(),f.second().getId(),new com.ptit.poker.game.api.realtime.GameActionMessage(
                PokerActionType.FOLD,null,beforeFold.turnId(),UUID.randomUUID()));
        disconnectQuietly(actorSession);await(roomEvents,"PLAYER_DISCONNECTED");
        var disconnected=player(runtime.currentView(started.gameId()),f.second().getId());
        assertThat(disconnected.participation()).isEqualTo(com.ptit.poker.game.domain.state.PokerPlayerState.FOLDED);
        assertThat(disconnected.connected()).isFalse();assertThat(disconnected.totalCommitted()).isEqualTo(committed);
        StompSession restored=connect(f.second());QueueFrames privateFrames=new QueueFrames();subscribe(restored,"/user/queue/private",privateFrames);
        await(roomEvents,"PLAYER_RECONNECTED");await(privateFrames,GameEventType.GAME_STATE_UPDATE.name());
        var reconnected=player(runtime.currentView(started.gameId()),f.second().getId());
        assertThat(reconnected.connected()).isTrue();
        assertThat(reconnected.participation()).isEqualTo(com.ptit.poker.game.domain.state.PokerPlayerState.FOLDED);
        assertThat(reconnected.totalCommitted()).isEqualTo(committed);
    }

    @Test void allInDisconnectExpiryPreservesSettlementAndPotEligibility() throws Exception {
        Fixture f=fixture();GameRuntimeView started=runtime.startGame(f.roomId(),f.first().getId());
        UserEntity actor=started.currentTurnUserId().equals(f.first().getId())?f.first():f.second();
        UserEntity other=actor.getId().equals(f.first().getId())?f.second():f.first();
        StompSession observer=connect(other);QueueFrames roomEvents=new QueueFrames();subscribe(observer,"/topic/room/"+f.roomId(),roomEvents);
        StompSession actorSession=connect(actor);
        realtime.handleAction(started.gameId(),actor.getId(),new com.ptit.poker.game.api.realtime.GameActionMessage(
                PokerActionType.ALL_IN,null,started.turnId(),UUID.randomUUID()));
        GameRuntimeView allIn=runtime.currentView(started.gameId());var beforeDisconnect=player(allIn,actor.getId());
        assertThat(beforeDisconnect.participation()).isEqualTo(com.ptit.poker.game.domain.state.PokerPlayerState.ALL_IN);
        long committed=beforeDisconnect.totalCommitted();disconnectQuietly(actorSession);await(roomEvents,"PLAYER_DISCONNECTED");
        var disconnected=player(runtime.currentView(started.gameId()),actor.getId());
        assertThat(disconnected.participation()).isEqualTo(com.ptit.poker.game.domain.state.PokerPlayerState.ALL_IN);
        assertThat(disconnected.connected()).isFalse();assertThat(disconnected.totalCommitted()).isEqualTo(committed);
        LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(3_500));
        GameRuntimeView current=runtime.currentView(started.gameId());
        realtime.handleAction(current.gameId(),other.getId(),new com.ptit.poker.game.api.realtime.GameActionMessage(
                PokerActionType.CALL,null,current.turnId(),UUID.randomUUID()));
        awaitTrue(()->runtime.currentView(started.gameId()).handCompleted(),"all-in showdown completed");
        assertThat(handPlayers.findAllByPokerHandIdOrderBySeatNumber(started.handId()))
                .extracting(HandPlayerEntity::getUserId).contains(actor.getId());
        assertThat(pots.findAllByPokerHandIdOrderByPotIndex(started.handId())).isNotEmpty()
                .allSatisfy(pot->assertThat(pot.getAmount()).isPositive());
        realtime.startNextHand(started.gameId());
        awaitTrue(()->member(f,actor).getLeftAt()!=null,"expired all-in player departure");
    }

    @Test void disconnectedThirdPlayerIsNotInjectedIntoCurrentTwoPlayerHand() throws Exception {
        ThreeFixture f=threeFixture();GameRuntimeView started=runtime.startGame(f.roomId(),f.first().getId());
        StompSession observer=connect(f.first());QueueFrames roomEvents=new QueueFrames();subscribe(observer,"/topic/room/"+f.roomId(),roomEvents);
        StompSession cSession=connect(f.third());disconnectQuietly(cSession);await(roomEvents,"PLAYER_DISCONNECTED");
        GameRuntimeView current=runtime.currentView(started.gameId());
        while(!current.handCompleted()){
            realtime.handleAction(current.gameId(),current.currentTurnUserId(),new com.ptit.poker.game.api.realtime.GameActionMessage(
                    PokerActionType.FOLD,null,current.turnId(),UUID.randomUUID()));current=runtime.currentView(started.gameId());
        }
        GameRuntimeView next=realtime.startNextHand(started.gameId());
        assertThat(next.players()).extracting(GameRuntimeView.PlayerView::userId).doesNotContain(f.third().getId());
        assertThat(handPlayers.findAllByPokerHandIdOrderBySeatNumber(next.handId())).extracting(HandPlayerEntity::getUserId)
                .doesNotContain(f.third().getId());
        StompSession restored=connect(f.third());QueueFrames privateFrames=new QueueFrames();subscribe(restored,"/user/queue/private",privateFrames);
        await(roomEvents,"PLAYER_RECONNECTED");await(privateFrames,GameEventType.GAME_STATE_UPDATE.name());
        assertThat(runtime.currentView(next.gameId()).players()).extracting(GameRuntimeView.PlayerView::userId)
                .doesNotContain(f.third().getId());
        assertThat(privateFrames.poll(300,TimeUnit.MILLISECONDS)).isNull();
    }

    private Fixture fixture(){UserEntity first=user(),second=user();long roomId=rooms.create(first.getId(),new CreateRoomRequest(
            "g7c2-"+shortId(),RoomType.PUBLIC,6,50,100,1_000,null)).room().id();
        rooms.join(first.getId(),roomId,new JoinRoomRequest(false,1,1_000L,null));rooms.join(second.getId(),roomId,new JoinRoomRequest(false,2,1_000L,null));
        readyWithoutAutoStart(roomId,first,second);return new Fixture(roomId,first,second);}
    private ThreeFixture threeFixture(){UserEntity first=user(),second=user(),third=user();long roomId=rooms.create(first.getId(),new CreateRoomRequest(
            "g7c3-"+shortId(),RoomType.PUBLIC,6,50,100,1_000,null)).room().id();
        rooms.join(first.getId(),roomId,new JoinRoomRequest(false,1,1_000L,null));rooms.join(second.getId(),roomId,new JoinRoomRequest(false,2,1_000L,null));
        rooms.join(third.getId(),roomId,new JoinRoomRequest(false,3,1_000L,null));readyWithoutAutoStart(roomId,first,second,third);
        return new ThreeFixture(roomId,first,second,third);}
    private void readyWithoutAutoStart(long roomId,UserEntity...users){for(UserEntity user:users){RoomPlayerEntity member=
        roomPlayers.findByRoomIdAndUserId(roomId,user.getId()).orElseThrow();member.setReady(true);roomPlayers.saveAndFlush(member);}}
    private RoomPlayerEntity member(Fixture f){return roomPlayers.findByRoomIdAndUserId(f.roomId(),f.second().getId()).orElseThrow();}
    private RoomPlayerEntity member(Fixture f,UserEntity user){return roomPlayers.findByRoomIdAndUserId(f.roomId(),user.getId()).orElseThrow();}
    private static GameRuntimeView.PlayerView player(GameRuntimeView view,long userId){return view.players().stream().filter(player->player.userId()==userId).findFirst().orElseThrow();}
    private long accountChips(UserEntity user){return users.findById(user.getId()).orElseThrow().getAccountChips();}
    private record Fixture(long roomId,UserEntity first,UserEntity second){}
    private record ThreeFixture(long roomId,UserEntity first,UserEntity second,UserEntity third){}
    private UserEntity user(){String suffix=shortId();return users.saveAndFlush(new UserEntity("c2_"+suffix,passwords.encode("password"),
            "c2_"+suffix+"@example.test",Role.PLAYER,AccountStatus.ACTIVE,5_000));}
    private static String shortId(){return UUID.randomUUID().toString().replace("-","").substring(0,12);}
    private void awaitTrue(BooleanSupplier condition,String description){long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);
        while(!condition.getAsBoolean()){if(System.nanoTime()>=deadline)throw new AssertionError("Timed out waiting for "+description);LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(25));}}
    private void awaitTrueUntil(BooleanSupplier condition,Instant deadline,java.util.function.Supplier<String> diagnostic){
        while(!condition.getAsBoolean()){if(!Instant.now().isBefore(deadline))throw new AssertionError(diagnostic.get());
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(25));}}
    private String timeoutDiagnostics(GameRuntimeView started,long actor,long before,long afterDisconnect,Instant deadline){
        long afterDeadline=actions.countByPokerHandId(started.handId());
        try{GameRuntimeView current=runtime.currentView(started.gameId());var player=player(current,actor);
            return "Turn timeout produced no action: game="+started.gameId()+", hand="+started.handId()+", turn="+current.turnId()
                    +", expectedTurn="+started.turnId()+", actor="+current.currentTurnUserId()+", expectedActor="+actor
                    +", participation="+player.participation()+", connected="+player.connected()+", handCompleted="+current.handCompleted()
                    +", sessionFinished="+current.sessionFinished()+", deadline="+deadline+", counts="+before+"/"+afterDisconnect+"/"+afterDeadline;
        }catch(RuntimeException failure){return "Turn timeout produced no action and runtime is unavailable: game="+started.gameId()
                +", hand="+started.handId()+", expectedTurn="+started.turnId()+", deadline="+deadline
                +", counts="+before+"/"+afterDisconnect+"/"+afterDeadline+", runtimeFailure="+failure.getMessage();}}
    private JsonNode await(QueueFrames frames,String type)throws Exception{long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);
        while(true){long remaining=deadline-System.nanoTime();if(remaining<=0)throw new AssertionError("Timed out waiting for "+type);
            byte[] payload=frames.poll(remaining,TimeUnit.NANOSECONDS);if(payload==null)throw new AssertionError("Timed out waiting for "+type);
            JsonNode node=json.readTree(payload);if(type.equals(node.path("type").asText()))return node;}}
    private JsonNode awaitUntil(QueueFrames frames,String type,Instant deadline)throws Exception{
        while(true){long remaining=Duration.between(Instant.now(),deadline).toNanos();if(remaining<=0)throw new AssertionError("Timed out waiting for "+type);
            byte[] payload=frames.poll(remaining,TimeUnit.NANOSECONDS);if(payload==null)throw new AssertionError("Timed out waiting for "+type);
            JsonNode node=json.readTree(payload);if(type.equals(node.path("type").asText()))return node;}}
    private StompSession connect(UserEntity user)throws Exception{StompHeaders headers=new StompHeaders();headers.add(HttpHeaders.AUTHORIZATION,"Bearer "+jwt.createAccessToken(user));
        StompSession session=client().connectAsync(url(),new WebSocketHttpHeaders(),headers,new Handler()).get(5,TimeUnit.SECONDS);sessions.add(session);return session;}
    private void subscribe(StompSession session,String destination,StompFrameHandler handler)throws Exception{String id=UUID.randomUUID().toString();
        CompletableFuture<Void> ready=subscriptions.await(id,destination);StompHeaders headers=new StompHeaders();headers.setId(id);headers.setDestination(destination);session.subscribe(headers,handler);
        try{ready.get(5,TimeUnit.SECONDS);}catch(TimeoutException failure){subscriptions.cancel(id,destination);throw failure;}}
    private WebSocketStompClient client(){WebSocketStompClient client=new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new CompositeMessageConverter(List.of(new ByteArrayMessageConverter(),new MappingJackson2MessageConverter())));clients.add(client);return client;}
    private String url(){return "ws://localhost:"+port+"/ws";}
    private void disconnectQuietly(StompSession session){if(session==null)return;try{if(session.isConnected())session.disconnect();}
        catch(MessageDeliveryException failure){if(!closed(failure))throw failure;}}
    private static boolean closed(Throwable failure){for(Throwable current=failure;current!=null&&current.getCause()!=current;current=current.getCause())
        if(current instanceof IllegalStateException state&&state.getMessage()!=null&&state.getMessage().toLowerCase(Locale.ROOT).contains("closed"))return true;return false;}
    private static final class QueueFrames implements StompFrameHandler{private final BlockingQueue<byte[]> frames=new LinkedBlockingQueue<>();
        public Type getPayloadType(StompHeaders headers){return byte[].class;}public void handleFrame(StompHeaders headers,Object payload){frames.add((byte[])payload);}
        byte[] poll(long time,TimeUnit unit)throws InterruptedException{return frames.poll(time,unit);}}
    private static final class Handler extends StompSessionHandlerAdapter{public void handleFrame(StompHeaders headers,Object payload){
        throw new AssertionError("STOMP ERROR: "+new String((byte[])payload,StandardCharsets.UTF_8));}}
}
