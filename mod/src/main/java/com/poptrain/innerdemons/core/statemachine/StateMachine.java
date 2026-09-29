package com.poptrain.innerdemons.core.statemachine;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StateMachine<C, S> {

    public static final int MAX_CHAINED_TRANSITIONS = 64;

    private static final Logger LOGGER = LoggerFactory.getLogger(StateMachine.class);

    private enum PendingKind {
        REQUEST,
        EVENT,
        FORCE,
        STOP
    }

    private record Pending<C, S>(PendingKind kind, StateContext<C, S> requester, S target, Object event) {
    }

    private record Outcome(TransitionResult result, String detail) {

        static final Outcome OK = new Outcome(TransitionResult.TRANSITIONED, null);
    }

    private record Plan<C, S>(List<StateContext<C, S>> exiting, List<StateNode<C, S>> entering) {
    }

    private final StateGraph<C, S> graph;
    private final C owner;
    private final List<StateContext<C, S>> activePath = new ArrayList<>();
    private final Map<S, S> history = new HashMap<>();
    private final ArrayDeque<Pending<C, S>> pending = new ArrayDeque<>();
    private final List<StateMachineListener<C, S>> listeners = new CopyOnWriteArrayList<>();
    private boolean running;
    private boolean busy;
    private boolean draining;
    private boolean ticking;
    private Thread ownerThread;

    public StateMachine(StateGraph<C, S> graph, C owner) {
        this.graph = Objects.requireNonNull(graph, "graph");
        this.owner = Objects.requireNonNull(owner, "owner");
    }

    public final StateGraph<C, S> graph() {
        return graph;
    }

    public final C owner() {
        return owner;
    }

    public final boolean isRunning() {
        return running;
    }

    public final boolean isBusy() {
        return busy;
    }

    public final void addListener(StateMachineListener<C, S> listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    public final void removeListener(StateMachineListener<C, S> listener) {
        listeners.remove(listener);
    }

    public final S currentState() {
        return activePath.isEmpty() ? null : leaf().state();
    }

    public final List<S> activeStates() {
        return activePath.stream().map(StateContext::state).toList();
    }

    public final boolean isIn(S state) {
        for (StateContext<C, S> ctx : activePath) {
            if (ctx.state().equals(state)) {
                return true;
            }
        }
        return false;
    }

    public final Optional<StateContext<C, S>> context(S state) {
        for (StateContext<C, S> ctx : activePath) {
            if (ctx.state().equals(state)) {
                return Optional.of(ctx);
            }
        }
        return Optional.empty();
    }

    public final int ticksInCurrentState() {
        return activePath.isEmpty() ? 0 : leaf().ticksInState();
    }

    public final int ticksIn(S state) {
        return context(state).map(StateContext::ticksInState).orElse(0);
    }

    public final void start() {
        checkThread();
        if (busy) {
            throw new IllegalStateException(graph.name() + ": start() called from inside a state callback");
        }
        if (running) {
            throw new IllegalStateException(graph.name() + ": state machine is already running");
        }
        bindThread();
        running = true;
        StateNode<C, S> initial = graph.initialNode();
        TransitionInfo<S> info = new TransitionInfo<>(null, initial.key, null, TransitionCause.START, "start");
        runBusy(() -> {
            switchStates(List.of(), entryPath(null, initial), info, null);
            return null;
        });
        safeHook("onStarted", null, this::onStarted);
        drain();
    }

    public final void stop() {
        checkThread();
        if (!running) {
            return;
        }
        if (busy) {
            pending.add(new Pending<>(PendingKind.STOP, null, null, null));
            return;
        }
        runBusy(() -> {
            performStop();
            return null;
        });
    }

    public final void tick() {
        checkThread();
        if (busy || ticking) {
            throw new IllegalStateException(graph.name() + ": tick() called from inside a state callback");
        }
        if (!running) {
            return;
        }
        ticking = true;
        try {
            List<StateContext<C, S>> snapshot = List.copyOf(activePath);
            for (StateContext<C, S> ctx : snapshot) {
                ctx.tick();
            }
            for (StateContext<C, S> ctx : snapshot) {
                if (!ctx.isActive() || !running) {
                    break;
                }
                runBusy(() -> {
                    invoke("tick", ctx.node(), () -> ctx.node().behavior.onTick(ctx));
                    return null;
                });
                drain();
            }
            if (running) {
                runBusy(this::attemptAuto);
                drain();
            }
        } finally {
            ticking = false;
        }
    }

    public final TransitionResult requestTransition(S target) {
        return request(null, target);
    }

    public final TransitionResult fire(Object event) {
        return dispatch(null, event);
    }

    public final TransitionResult forceState(S target) {
        checkThread();
        StateNode<C, S> node = graph.require(target);
        if (!running) {
            return TransitionResult.NOT_RUNNING;
        }
        if (busy) {
            pending.add(new Pending<>(PendingKind.FORCE, null, target, null));
            return TransitionResult.QUEUED;
        }
        TransitionResult result = runBusy(() -> performForce(node));
        drain();
        return result;
    }

    public final boolean canRequest(S target) {
        checkThread();
        StateNode<C, S> node = graph.require(target);
        if (!running || busy) {
            return false;
        }
        return runBusy(() -> attemptRequest(node, true).result() == TransitionResult.TRANSITIONED);
    }

    public final StateSnapshot snapshot() {
        List<String> path = new ArrayList<>(activePath.size());
        List<Integer> ticks = new ArrayList<>(activePath.size());
        for (StateContext<C, S> ctx : activePath) {
            path.add(ctx.node().name);
            ticks.add(ctx.ticksInState());
        }
        Map<String, String> savedHistory = new HashMap<>();
        history.forEach((composite, child) -> savedHistory.put(graph.encode(composite), graph.encode(child)));
        return new StateSnapshot(path, ticks, savedHistory);
    }

    public final boolean restore(StateSnapshot snapshot) {
        checkThread();
        if (busy) {
            throw new IllegalStateException(graph.name() + ": restore() called from inside a state callback");
        }
        List<StateNode<C, S>> path = validatePath(snapshot);
        if (path == null) {
            LOGGER.warn("[{}] Discarding invalid saved state {} for {}", graph.name(), snapshot.path(), owner);
            if (!running) {
                start();
            }
            return false;
        }
        if (running) {
            runBusy(() -> {
                exitAll(TransitionCause.RESTORE);
                return null;
            });
        }
        pending.clear();
        history.clear();
        snapshot.history().forEach((compositeName, childName) -> {
            StateNode<C, S> composite = graph.nodeByName(compositeName);
            StateNode<C, S> child = graph.nodeByName(childName);
            if (composite != null && child != null && composite.history && child.parent == composite) {
                history.put(composite.key, child.key);
            }
        });
        bindThread();
        running = true;
        StateNode<C, S> leafNode = path.get(path.size() - 1);
        TransitionInfo<S> info = new TransitionInfo<>(null, leafNode.key, null, TransitionCause.RESTORE, "restore");
        runBusy(() -> {
            switchStates(List.of(), path, info, null);
            return null;
        });
        for (int i = 0; i < activePath.size() && i < snapshot.ticks().size(); i++) {
            activePath.get(i).setTicks(snapshot.ticks().get(i));
        }
        safeHook("onStarted", null, this::onStarted);
        drain();
        return true;
    }

    protected void onStarted() {
    }

    protected void onStopped() {
    }

    protected void onStateEntered(S state, TransitionInfo<S> info) {
    }

    protected void onStateExited(S state, TransitionInfo<S> info) {
    }

    protected void onTransitioned(TransitionInfo<S> info) {
    }

    protected void onTransitionRejected(S from, S target, TransitionResult result, String detail) {
    }

    protected void onCallbackError(String phase, S state, RuntimeException error) {
        LOGGER.error("[{}] {} callback failed in state {} for {}", graph.name(), phase, state, owner, error);
    }

    final TransitionResult request(StateContext<C, S> requester, S target) {
        checkThread();
        StateNode<C, S> node = graph.require(target);
        if (!running) {
            return TransitionResult.NOT_RUNNING;
        }
        if (requester != null && !requester.isActive()) {
            return TransitionResult.STALE_REQUESTER;
        }
        if (busy) {
            pending.add(new Pending<>(PendingKind.REQUEST, requester, target, null));
            return TransitionResult.QUEUED;
        }
        TransitionResult result = runBusy(() -> reportRequest(node));
        drain();
        return result;
    }

    final TransitionResult dispatch(StateContext<C, S> requester, Object event) {
        checkThread();
        Objects.requireNonNull(event, "event");
        if (!running) {
            return TransitionResult.NOT_RUNNING;
        }
        if (requester != null && !requester.isActive()) {
            return TransitionResult.STALE_REQUESTER;
        }
        if (busy) {
            pending.add(new Pending<>(PendingKind.EVENT, requester, null, event));
            return TransitionResult.QUEUED;
        }
        TransitionResult result = runBusy(() -> dispatchEvent(event));
        drain();
        return result;
    }

    private TransitionResult reportRequest(StateNode<C, S> target) {
        Outcome outcome = attemptRequest(target, false);
        if (outcome.result() != TransitionResult.TRANSITIONED) {
            notifyRejected(currentState(), target.key, outcome);
        }
        return outcome.result();
    }

    private Outcome attemptRequest(StateNode<C, S> target, boolean dryRun) {
        Outcome rejection = null;
        for (int i = activePath.size() - 1; i >= 0; i--) {
            StateContext<C, S> source = activePath.get(i);
            for (Transition<C, S> t : source.node().requestTransitions) {
                if (!t.to().equals(target.key)) {
                    continue;
                }
                Outcome outcome = tryExecute(t, source, null, TransitionCause.REQUEST, dryRun);
                if (outcome.result() == TransitionResult.TRANSITIONED) {
                    return outcome;
                }
                rejection = rejection == null ? outcome : rejection;
            }
        }
        for (Transition<C, S> t : graph.globalRequest()) {
            if (!t.to().equals(target.key)) {
                continue;
            }
            Outcome outcome = tryExecute(t, leaf(), null, TransitionCause.REQUEST, dryRun);
            if (outcome.result() == TransitionResult.TRANSITIONED) {
                return outcome;
            }
            rejection = rejection == null ? outcome : rejection;
        }
        return rejection != null ? rejection
                : new Outcome(TransitionResult.NO_SUCH_TRANSITION, "no declared transition from " + activeStates() + " to " + target.key);
    }

    private TransitionResult dispatchEvent(Object event) {
        Outcome rejection = null;
        S from = currentState();
        for (int i = activePath.size() - 1; i >= 0; i--) {
            StateContext<C, S> ctx = activePath.get(i);
            boolean consumed = check("event", ctx.node(), () -> ctx.node().behavior.onEvent(ctx, event));
            if (consumed) {
                return TransitionResult.HANDLED;
            }
            for (Transition<C, S> t : ctx.node().eventTransitions) {
                if (!t.matchesEvent(event)) {
                    continue;
                }
                Outcome outcome = tryExecute(t, ctx, event, TransitionCause.EVENT, false);
                if (outcome.result() == TransitionResult.TRANSITIONED) {
                    return TransitionResult.TRANSITIONED;
                }
                rejection = rejection == null ? outcome : rejection;
            }
        }
        for (Transition<C, S> t : graph.globalEvent()) {
            if (!t.matchesEvent(event)) {
                continue;
            }
            Outcome outcome = tryExecute(t, leaf(), event, TransitionCause.EVENT, false);
            if (outcome.result() == TransitionResult.TRANSITIONED) {
                return TransitionResult.TRANSITIONED;
            }
            rejection = rejection == null ? outcome : rejection;
        }
        if (rejection == null) {
            return TransitionResult.UNHANDLED;
        }
        notifyRejected(from, null, rejection);
        return rejection.result();
    }

    private Void attemptAuto() {
        for (int i = activePath.size() - 1; i >= 0; i--) {
            StateContext<C, S> source = activePath.get(i);
            for (Transition<C, S> t : source.node().autoTransitions) {
                if (tryExecute(t, source, null, TransitionCause.AUTO, false).result() == TransitionResult.TRANSITIONED) {
                    return null;
                }
            }
        }
        for (Transition<C, S> t : graph.globalAuto()) {
            if (tryExecute(t, leaf(), null, TransitionCause.AUTO, false).result() == TransitionResult.TRANSITIONED) {
                return null;
            }
        }
        return null;
    }

    private Outcome tryExecute(Transition<C, S> transition, StateContext<C, S> source, Object event,
                               TransitionCause cause, boolean dryRun) {
        StateNode<C, S> target = graph.node(transition.to());
        TransitionInfo<S> info = new TransitionInfo<>(currentState(), target.key, event, cause, transition.name());
        for (Transition.NamedGuard<C, S> guard : transition.guards()) {
            if (!check("guard", source.node(), () -> guard.guard().test(source, info))) {
                return new Outcome(TransitionResult.GUARD_REJECTED, transition.name() + " blocked by " + guard.name());
            }
        }
        Plan<C, S> plan = plan(source.node(), target);
        for (StateContext<C, S> exiting : plan.exiting()) {
            if (!check("canExit", exiting.node(), () -> exiting.node().behavior.canExit(exiting, info))) {
                return new Outcome(TransitionResult.EXIT_BLOCKED, exiting.node().name + " refused to exit");
            }
        }
        for (StateNode<C, S> entering : plan.entering()) {
            if (!check("canEnter", entering, () -> entering.behavior.canEnter(owner, info))) {
                return new Outcome(TransitionResult.ENTRY_BLOCKED, entering.name + " refused entry");
            }
        }
        if (!dryRun) {
            switchStates(plan.exiting(), plan.entering(), info, transition.action());
        }
        return Outcome.OK;
    }

    private TransitionResult performForce(StateNode<C, S> target) {
        TransitionInfo<S> info = new TransitionInfo<>(currentState(), target.key, null, TransitionCause.FORCED, "force");
        Plan<C, S> plan = plan(leaf().node(), target);
        switchStates(plan.exiting(), plan.entering(), info, null);
        return TransitionResult.TRANSITIONED;
    }

    private void performStop() {
        exitAll(TransitionCause.STOP);
        running = false;
        pending.clear();
        safeHook("onStopped", null, this::onStopped);
    }

    private void exitAll(TransitionCause cause) {
        List<StateContext<C, S>> exiting = new ArrayList<>(activePath);
        java.util.Collections.reverse(exiting);
        TransitionInfo<S> info = new TransitionInfo<>(currentState(), null, null, cause, cause.name().toLowerCase());
        switchStates(exiting, List.of(), info, null);
    }

    private Plan<C, S> plan(StateNode<C, S> source, StateNode<C, S> target) {
        StateNode<C, S> lca = commonAncestor(source, target);
        if (lca == target) {
            lca = target.parent;
        }
        int lcaDepth = lca == null ? -1 : lca.depth;
        List<StateContext<C, S>> exiting = new ArrayList<>();
        for (int i = activePath.size() - 1; i > lcaDepth; i--) {
            exiting.add(activePath.get(i));
        }
        return new Plan<>(exiting, entryPath(lca, target));
    }

    private List<StateNode<C, S>> entryPath(StateNode<C, S> lca, StateNode<C, S> target) {
        ArrayDeque<StateNode<C, S>> upward = new ArrayDeque<>();
        for (StateNode<C, S> n = target; n != null && n != lca; n = n.parent) {
            upward.addFirst(n);
        }
        List<StateNode<C, S>> path = new ArrayList<>(upward);
        StateNode<C, S> current = target;
        while (current.isComposite()) {
            current = resolveChild(current);
            path.add(current);
        }
        return path;
    }

    private StateNode<C, S> resolveChild(StateNode<C, S> composite) {
        if (composite.history) {
            S remembered = history.get(composite.key);
            if (remembered != null) {
                StateNode<C, S> child = graph.node(remembered);
                if (child != null && child.parent == composite) {
                    return child;
                }
            }
        }
        return composite.initialChild;
    }

    private static <C, S> StateNode<C, S> commonAncestor(StateNode<C, S> a, StateNode<C, S> b) {
        while (a != null && b != null && a.depth > b.depth) {
            a = a.parent;
        }
        while (a != null && b != null && b.depth > a.depth) {
            b = b.parent;
        }
        while (a != null && b != null && a != b) {
            a = a.parent;
            b = b.parent;
        }
        return a;
    }

    private void switchStates(List<StateContext<C, S>> exiting, List<StateNode<C, S>> entering,
                              TransitionInfo<S> info, TransitionAction<C, S> action) {
        for (StateContext<C, S> ctx : exiting) {
            StateNode<C, S> node = ctx.node();
            if (node.parent != null && node.parent.history) {
                history.put(node.parent.key, node.key);
            }
            invoke("exit", node, () -> node.behavior.onExit(ctx, info));
            ctx.deactivate();
            activePath.remove(activePath.size() - 1);
            safeHook("onStateExited", node, () -> onStateExited(node.key, info));
            for (StateMachineListener<C, S> listener : listeners) {
                safeHook("listener.onStateExited", node, () -> listener.onStateExited(this, node.key, info));
            }
        }
        if (action != null) {
            StateNode<C, S> actionNode = entering.isEmpty() ? null : entering.get(0);
            invoke("action", actionNode, () -> action.run(owner, info));
        }
        for (StateNode<C, S> node : entering) {
            StateContext<C, S> parent = activePath.isEmpty() ? null : leaf();
            StateContext<C, S> ctx = new StateContext<>(this, node, parent);
            activePath.add(ctx);
            invoke("enter", node, () -> node.behavior.onEnter(ctx, info));
            safeHook("onStateEntered", node, () -> onStateEntered(node.key, info));
            for (StateMachineListener<C, S> listener : listeners) {
                safeHook("listener.onStateEntered", node, () -> listener.onStateEntered(this, node.key, info));
            }
        }
        safeHook("onTransitioned", null, () -> onTransitioned(info));
        for (StateMachineListener<C, S> listener : listeners) {
            safeHook("listener.onTransition", null, () -> listener.onTransition(this, info));
        }
    }

    private void drain() {
        if (draining || busy) {
            return;
        }
        draining = true;
        try {
            int processed = 0;
            while (!pending.isEmpty()) {
                if (!running) {
                    pending.clear();
                    return;
                }
                if (++processed > MAX_CHAINED_TRANSITIONS) {
                    LOGGER.error("[{}] More than {} chained transitions for {} (now in {}), dropping {} queued requests. This is almost always a transition loop.",
                            graph.name(), MAX_CHAINED_TRANSITIONS, owner, activeStates(), pending.size());
                    pending.clear();
                    return;
                }
                Pending<C, S> next = pending.poll();
                if (next.requester() != null && !next.requester().isActive()) {
                    notifyRejected(currentState(), next.target(),
                            new Outcome(TransitionResult.STALE_REQUESTER, "requested by exited state " + next.requester().node().name));
                    continue;
                }
                runBusy(() -> switch (next.kind()) {
                    case REQUEST -> reportRequest(graph.node(next.target()));
                    case EVENT -> dispatchEvent(next.event());
                    case FORCE -> performForce(graph.node(next.target()));
                    case STOP -> {
                        performStop();
                        yield TransitionResult.TRANSITIONED;
                    }
                });
            }
        } finally {
            draining = false;
        }
    }

    private <T> T runBusy(Supplier<T> body) {
        boolean previous = busy;
        busy = true;
        try {
            return body.get();
        } finally {
            busy = previous;
        }
    }

    private void notifyRejected(S from, S target, Outcome outcome) {
        safeHook("onTransitionRejected", null, () -> onTransitionRejected(from, target, outcome.result(), outcome.detail()));
        for (StateMachineListener<C, S> listener : listeners) {
            safeHook("listener.onTransitionRejected", null,
                    () -> listener.onTransitionRejected(this, from, target, outcome.result(), outcome.detail()));
        }
    }

    private void invoke(String phase, StateNode<C, S> node, Runnable body) {
        try {
            body.run();
        } catch (RuntimeException e) {
            reportError(phase, node, e);
        }
    }

    private boolean check(String phase, StateNode<C, S> node, BooleanSupplier body) {
        try {
            return body.getAsBoolean();
        } catch (RuntimeException e) {
            reportError(phase, node, e);
            return false;
        }
    }

    private void safeHook(String phase, StateNode<C, S> node, Runnable body) {
        invoke(phase, node, body);
    }

    private void reportError(String phase, StateNode<C, S> node, RuntimeException e) {
        try {
            onCallbackError(phase, node == null ? null : node.key, e);
        } catch (RuntimeException nested) {
            LOGGER.error("[{}] onCallbackError itself failed", graph.name(), nested);
        }
    }

    private List<StateNode<C, S>> validatePath(StateSnapshot snapshot) {
        if (snapshot == null || snapshot.isEmpty()) {
            return null;
        }
        List<StateNode<C, S>> path = new ArrayList<>();
        StateNode<C, S> previous = null;
        for (String name : snapshot.path()) {
            StateNode<C, S> node = graph.nodeByName(name);
            if (node == null || node.parent != previous) {
                return null;
            }
            path.add(node);
            previous = node;
        }
        return previous.isComposite() ? null : path;
    }

    private StateContext<C, S> leaf() {
        return activePath.get(activePath.size() - 1);
    }

    private void bindThread() {
        if (graph.isThreadConfined()) {
            ownerThread = Thread.currentThread();
        }
    }

    private void checkThread() {
        if (ownerThread != null && Thread.currentThread() != ownerThread) {
            throw new IllegalStateException(graph.name() + ": state machine owned by thread " + ownerThread.getName()
                    + " was accessed from " + Thread.currentThread().getName());
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + graph.name() + ", " + (running ? activeStates() : "stopped") + "]";
    }
}
