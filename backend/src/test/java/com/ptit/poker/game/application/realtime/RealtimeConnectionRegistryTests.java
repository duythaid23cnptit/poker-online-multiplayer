package com.ptit.poker.game.application.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class RealtimeConnectionRegistryTests {
    private final RealtimeConnectionRegistry registry = new RealtimeConnectionRegistry();

    @Test void firstAndAdditionalSessionsAreDistinguished() {
        assertThat(registry.register("a", 7)).isTrue();
        assertThat(registry.register("b", 7)).isFalse();
        assertThat(registry.activeSessionCount(7)).isEqualTo(2);
    }

    @Test void onlyLastSessionDisconnectTransitionsUser() {
        registry.register("a", 7); registry.register("b", 7);
        assertThat(registry.unregister("a").lastSession()).isFalse();
        assertThat(registry.unregister("b").lastSession()).isTrue();
    }

    @Test void duplicateAndObsoleteDisconnectAreIdempotent() {
        registry.register("new", 7);
        assertThat(registry.unregister("old").knownSession()).isFalse();
        assertThat(registry.activeSessionCount(7)).isOne();
        registry.unregister("new");
        assertThat(registry.unregister("new").knownSession()).isFalse();
    }

    @Test void sessionIdCannotBeReassignedWhileActive() {
        assertThat(registry.register("same", 7)).isTrue();
        assertThat(registry.register("same", 8)).isFalse();
        assertThat(registry.activeSessionCount(7)).isOne();
        assertThat(registry.activeSessionCount(8)).isZero();
    }

    @Test void firstSessionIsATransition() {
        assertThat(registry.register("first", 7)).isTrue();
    }

    @Test void secondSessionForSameUserIsNotATransition() {
        registry.register("first", 7);
        assertThat(registry.register("second", 7)).isFalse();
    }

    @Test void removingOneOfTwoSessionsKeepsUserConnected() {
        registry.register("first", 7); registry.register("second", 7);
        assertThat(registry.unregister("first").lastSession()).isFalse();
        assertThat(registry.activeSessionCount(7)).isOne();
    }

    @Test void duplicateConnectDoesNotDoubleCount() {
        registry.register("same", 7);
        assertThat(registry.register("same", 7)).isFalse();
        assertThat(registry.activeSessionCount(7)).isOne();
    }

    @Test void usersAreTrackedIndependently() {
        assertThat(registry.register("a", 7)).isTrue();
        assertThat(registry.register("b", 8)).isTrue();
        assertThat(registry.unregister("a").lastSession()).isTrue();
        assertThat(registry.activeSessionCount(8)).isOne();
    }

    @Test void concurrentRegistrationsProduceExactlyOneFirstTransition() throws Exception {
        List<Boolean> results = runTogether(() -> registry.register("a", 7), () -> registry.register("b", 7));
        assertThat(results).containsExactlyInAnyOrder(true, false);
        assertThat(registry.activeSessionCount(7)).isEqualTo(2);
    }

    @Test void concurrentRemovalsProduceExactlyOneLastTransition() throws Exception {
        registry.register("a", 7); registry.register("b", 7);
        List<Boolean> results = runTogether(() -> registry.unregister("a").lastSession(),
                () -> registry.unregister("b").lastSession());
        assertThat(results).containsExactlyInAnyOrder(true, false);
        assertThat(registry.activeSessionCount(7)).isZero();
    }

    @Test void serializesFirstAndLastTransitionCallbacksForTheSameUser() throws Exception {
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch removalStarted = new CountDownLatch(1);
        List<String> transitions = new CopyOnWriteArrayList<>();
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<Boolean> first = executor.submit(() -> registry.register("a", 7, () -> {
                transitions.add("FIRST");
                firstEntered.countDown();
                await(releaseFirst);
            }));
            await(firstEntered);
            Future<RealtimeConnectionRegistry.DisconnectResult> last = executor.submit(() -> {
                removalStarted.countDown();
                return registry.unregister("a", userId -> transitions.add("LAST"));
            });
            await(removalStarted);
            assertThat(last.isDone()).isFalse();

            releaseFirst.countDown();

            assertThat(first.get()).isTrue();
            assertThat(last.get().lastSession()).isTrue();
        }
        assertThat(transitions).containsExactly("FIRST", "LAST");
        assertThat(registry.activeSessionCount(7)).isZero();
    }

    @Test void sameSessionReconnectWaitsForPriorLastTransitionWithoutPhantomState() throws Exception {
        registry.register("same", 7);
        CountDownLatch lastEntered = new CountDownLatch(1);
        CountDownLatch releaseLast = new CountDownLatch(1);
        List<String> transitions = new CopyOnWriteArrayList<>();
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<RealtimeConnectionRegistry.DisconnectResult> disconnect = executor.submit(() ->
                    registry.unregister("same", userId -> {
                        transitions.add("LAST");
                        lastEntered.countDown();
                        await(releaseLast);
                    }));
            await(lastEntered);
            Future<Boolean> reconnect = executor.submit(() -> registry.register("same", 7,
                    () -> transitions.add("FIRST")));
            assertThat(reconnect.isDone()).isFalse();

            releaseLast.countDown();

            assertThat(disconnect.get().lastSession()).isTrue();
            assertThat(reconnect.get()).isTrue();
        }
        assertThat(transitions).containsExactly("LAST", "FIRST");
        assertThat(registry.activeSessionCount(7)).isOne();
    }

    private static List<Boolean> runTogether(java.util.concurrent.Callable<Boolean> first,
                                             java.util.concurrent.Callable<Boolean> second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2); CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<Boolean> one = executor.submit(() -> { ready.countDown(); start.await(); return first.call(); });
            Future<Boolean> two = executor.submit(() -> { ready.countDown(); start.await(); return second.call(); });
            ready.await(); start.countDown(); return List.of(one.get(), two.get());
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) throw new AssertionError("Timed out waiting for test transition");
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for test transition", failure);
        }
    }
}
