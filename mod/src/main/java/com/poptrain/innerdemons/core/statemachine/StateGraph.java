package com.poptrain.innerdemons.core.statemachine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public final class StateGraph<C, S> {

    private static final StateBehavior<?, ?> EMPTY_BEHAVIOR = new StateBehavior<>() {
    };

    private final String name;
    private final Map<S, StateNode<C, S>> nodes;
    private final Map<String, StateNode<C, S>> nodesByName;
    private final List<StateNode<C, S>> topLevel;
    private final StateNode<C, S> initial;
    private final List<Transition<C, S>> globalRequest;
    private final List<Transition<C, S>> globalEvent;
    private final List<Transition<C, S>> globalAuto;
    private final boolean threadConfined;

    private StateGraph(Builder<C, S> builder, Map<S, StateNode<C, S>> nodes, Map<String, StateNode<C, S>> nodesByName,
                       List<Transition<C, S>> globalRequest, List<Transition<C, S>> globalEvent,
                       List<Transition<C, S>> globalAuto) {
        this.name = builder.name;
        this.nodes = Collections.unmodifiableMap(nodes);
        this.nodesByName = Collections.unmodifiableMap(nodesByName);
        this.topLevel = nodes.values().stream().filter(n -> n.parent == null).toList();
        this.initial = nodes.get(builder.initial);
        this.globalRequest = Transition.sorted(globalRequest);
        this.globalEvent = Transition.sorted(globalEvent);
        this.globalAuto = Transition.sorted(globalAuto);
        this.threadConfined = builder.threadConfined;
    }

    public static <C, S> Builder<C, S> builder(String name) {
        return new Builder<>(name, String::valueOf);
    }

    public static <C, S extends Enum<S>> Builder<C, S> builder(String name, Class<C> ownerType, Class<S> stateType) {
        Objects.requireNonNull(ownerType, "ownerType");
        Objects.requireNonNull(stateType, "stateType");
        return new Builder<>(name, Enum::name);
    }

    public String name() {
        return name;
    }

    public boolean contains(S state) {
        return nodes.containsKey(state);
    }

    public Set<S> states() {
        return nodes.keySet();
    }

    public S initialState() {
        return initial.key;
    }

    public Optional<S> parentOf(S state) {
        StateNode<C, S> node = require(state);
        return node.parent == null ? Optional.empty() : Optional.of(node.parent.key);
    }

    public List<S> childrenOf(S state) {
        return require(state).children.stream().map(n -> n.key).toList();
    }

    public boolean isComposite(S state) {
        return require(state).isComposite();
    }

    public boolean isAncestorOf(S ancestor, S descendant) {
        return require(descendant).isDescendantOf(require(ancestor));
    }

    public String encode(S state) {
        return require(state).name;
    }

    public Optional<S> decode(String encoded) {
        StateNode<C, S> node = nodesByName.get(encoded);
        return node == null ? Optional.empty() : Optional.of(node.key);
    }

    public boolean isThreadConfined() {
        return threadConfined;
    }

    StateNode<C, S> node(S state) {
        return nodes.get(state);
    }

    StateNode<C, S> nodeByName(String encoded) {
        return nodesByName.get(encoded);
    }

    StateNode<C, S> initialNode() {
        return initial;
    }

    List<StateNode<C, S>> topLevel() {
        return topLevel;
    }

    List<Transition<C, S>> globalRequest() {
        return globalRequest;
    }

    List<Transition<C, S>> globalEvent() {
        return globalEvent;
    }

    List<Transition<C, S>> globalAuto() {
        return globalAuto;
    }

    StateNode<C, S> require(S state) {
        StateNode<C, S> node = nodes.get(state);
        if (node == null) {
            throw new IllegalArgumentException("State " + state + " is not part of state graph " + name);
        }
        return node;
    }

    @SuppressWarnings("unchecked")
    private static <C, S> StateBehavior<C, S> emptyBehavior() {
        return (StateBehavior<C, S>) EMPTY_BEHAVIOR;
    }

    public static final class Builder<C, S> {

        private final String name;
        private final Function<? super S, String> encoder;
        private final Map<S, StateBuilder<C, S>> states = new LinkedHashMap<>();
        private final List<TransitionBuilder<C, S>> transitions = new ArrayList<>();
        private S initial;
        private boolean threadConfined = true;

        private Builder(String name, Function<? super S, String> encoder) {
            this.name = Objects.requireNonNull(name, "name");
            this.encoder = encoder;
        }

        public StateBuilder<C, S> state(S key) {
            Objects.requireNonNull(key, "key");
            if (states.containsKey(key)) {
                throw new StateGraphException(name + ": state " + key + " is declared twice");
            }
            StateBuilder<C, S> builder = new StateBuilder<>(this, key);
            states.put(key, builder);
            return builder;
        }

        public StateBuilder<C, S> substate(S parent, S key) {
            return state(key).parent(parent);
        }

        public Builder<C, S> initial(S key) {
            this.initial = Objects.requireNonNull(key, "key");
            return this;
        }

        public TransitionBuilder<C, S> transition(S from, S to) {
            Objects.requireNonNull(from, "from");
            return addTransition(from, to);
        }

        public TransitionBuilder<C, S> anyTransition(S to) {
            return addTransition(null, to);
        }

        public Builder<C, S> threadConfined(boolean value) {
            this.threadConfined = value;
            return this;
        }

        private TransitionBuilder<C, S> addTransition(S from, S to) {
            Objects.requireNonNull(to, "to");
            TransitionBuilder<C, S> builder = new TransitionBuilder<>(this, from, to, transitions.size());
            transitions.add(builder);
            return builder;
        }

        public StateGraph<C, S> build() {
            if (states.isEmpty()) {
                throw new StateGraphException(name + ": no states declared");
            }
            if (initial == null) {
                throw new StateGraphException(name + ": no initial state declared, call initial(...)");
            }
            if (!states.containsKey(initial)) {
                throw new StateGraphException(name + ": initial state " + initial + " was never declared");
            }

            Map<S, StateNode<C, S>> nodes = new LinkedHashMap<>();
            Map<String, StateNode<C, S>> byName = new HashMap<>();
            for (StateBuilder<C, S> sb : states.values()) {
                String encoded = Objects.requireNonNull(encoder.apply(sb.key), "encoded name");
                StateNode<C, S> node = new StateNode<>(sb.key, encoded, sb.history, sb.resolveBehavior());
                if (byName.put(encoded, node) != null) {
                    throw new StateGraphException(name + ": two states encode to the same name '" + encoded + "'");
                }
                nodes.put(sb.key, node);
            }

            for (StateBuilder<C, S> sb : states.values()) {
                if (sb.parent == null) {
                    continue;
                }
                StateNode<C, S> parent = nodes.get(sb.parent);
                if (parent == null) {
                    throw new StateGraphException(name + ": state " + sb.key + " has unknown parent " + sb.parent);
                }
                StateNode<C, S> node = nodes.get(sb.key);
                node.parent = parent;
                parent.children.add(node);
            }

            for (StateNode<C, S> node : nodes.values()) {
                int depth = 0;
                for (StateNode<C, S> p = node.parent; p != null; p = p.parent) {
                    if (++depth > nodes.size()) {
                        throw new StateGraphException(name + ": parent cycle detected at state " + node.key);
                    }
                }
                node.depth = depth;
            }

            for (StateBuilder<C, S> sb : states.values()) {
                StateNode<C, S> node = nodes.get(sb.key);
                if (sb.initialChild != null) {
                    StateNode<C, S> child = nodes.get(sb.initialChild);
                    if (child == null || child.parent != node) {
                        throw new StateGraphException(name + ": initial child " + sb.initialChild + " of " + sb.key
                                + " must be a direct substate of it");
                    }
                    node.initialChild = child;
                } else if (node.isComposite()) {
                    throw new StateGraphException(name + ": composite state " + sb.key
                            + " must declare its initial substate with initial(...)");
                }
                if (sb.history && !node.isComposite()) {
                    throw new StateGraphException(name + ": state " + sb.key + " uses history() but has no substates");
                }
            }

            List<Transition<C, S>> globalRequest = new ArrayList<>();
            List<Transition<C, S>> globalEvent = new ArrayList<>();
            List<Transition<C, S>> globalAuto = new ArrayList<>();
            for (TransitionBuilder<C, S> tb : transitions) {
                if (tb.from != null && !nodes.containsKey(tb.from)) {
                    throw new StateGraphException(name + ": transition " + tb.describe() + " starts at unknown state " + tb.from);
                }
                if (!nodes.containsKey(tb.to)) {
                    throw new StateGraphException(name + ": transition " + tb.describe() + " targets unknown state " + tb.to);
                }
                Transition<C, S> transition = tb.build();
                StateNode<C, S> from = tb.from == null ? null : nodes.get(tb.from);
                switch (transition.kind()) {
                    case REQUEST -> (from == null ? globalRequest : from.requestTransitions).add(transition);
                    case EVENT -> (from == null ? globalEvent : from.eventTransitions).add(transition);
                    case AUTO -> (from == null ? globalAuto : from.autoTransitions).add(transition);
                }
            }

            nodes.values().forEach(StateNode::freeze);
            return new StateGraph<>(this, nodes, byName, globalRequest, globalEvent, globalAuto);
        }
    }

    public static final class StateBuilder<C, S> {

        private final Builder<C, S> owner;
        private final S key;
        private S parent;
        private S initialChild;
        private boolean history;
        private StateBehavior<C, S> behavior;
        private BiConsumer<StateContext<C, S>, TransitionInfo<S>> enter;
        private BiConsumer<StateContext<C, S>, TransitionInfo<S>> exit;
        private Consumer<StateContext<C, S>> tick;
        private final List<LambdaBehavior.TypedHandler<C, S, ?>> handlers = new ArrayList<>();
        private BiPredicate<C, TransitionInfo<S>> canEnter;
        private BiPredicate<StateContext<C, S>, TransitionInfo<S>> canExit;

        private StateBuilder(Builder<C, S> owner, S key) {
            this.owner = owner;
            this.key = key;
        }

        public StateBuilder<C, S> parent(S parent) {
            if (Objects.equals(parent, key)) {
                throw new StateGraphException(owner.name + ": state " + key + " cannot be its own parent");
            }
            this.parent = parent;
            return this;
        }

        public StateBuilder<C, S> initial(S child) {
            this.initialChild = Objects.requireNonNull(child, "child");
            return this;
        }

        public StateBuilder<C, S> history() {
            this.history = true;
            return this;
        }

        public StateBuilder<C, S> behavior(StateBehavior<C, S> behavior) {
            this.behavior = Objects.requireNonNull(behavior, "behavior");
            return this;
        }

        public StateBuilder<C, S> onEnter(Consumer<StateContext<C, S>> action) {
            Objects.requireNonNull(action, "action");
            return onEnter((ctx, info) -> action.accept(ctx));
        }

        public StateBuilder<C, S> onEnter(BiConsumer<StateContext<C, S>, TransitionInfo<S>> action) {
            this.enter = chain(enter, action);
            return this;
        }

        public StateBuilder<C, S> onExit(Consumer<StateContext<C, S>> action) {
            Objects.requireNonNull(action, "action");
            return onExit((ctx, info) -> action.accept(ctx));
        }

        public StateBuilder<C, S> onExit(BiConsumer<StateContext<C, S>, TransitionInfo<S>> action) {
            this.exit = chain(exit, action);
            return this;
        }

        public StateBuilder<C, S> onTick(Consumer<StateContext<C, S>> action) {
            Objects.requireNonNull(action, "action");
            this.tick = tick == null ? action : tick.andThen(action);
            return this;
        }

        public <E> StateBuilder<C, S> onEvent(Class<E> type, EventHandler<C, S, E> handler) {
            handlers.add(new LambdaBehavior.TypedHandler<>(Objects.requireNonNull(type, "type"),
                    Objects.requireNonNull(handler, "handler")));
            return this;
        }

        public StateBuilder<C, S> canEnter(Predicate<C> condition) {
            Objects.requireNonNull(condition, "condition");
            BiPredicate<C, TransitionInfo<S>> next = (o, info) -> condition.test(o);
            this.canEnter = canEnter == null ? next : canEnter.and(next);
            return this;
        }

        public StateBuilder<C, S> canExit(Predicate<StateContext<C, S>> condition) {
            Objects.requireNonNull(condition, "condition");
            return canExit((ctx, info) -> condition.test(ctx));
        }

        public StateBuilder<C, S> canExit(BiPredicate<StateContext<C, S>, TransitionInfo<S>> condition) {
            Objects.requireNonNull(condition, "condition");
            this.canExit = canExit == null ? condition : canExit.and(condition);
            return this;
        }

        private static <A, B> BiConsumer<A, B> chain(BiConsumer<A, B> current, BiConsumer<A, B> next) {
            Objects.requireNonNull(next, "action");
            return current == null ? next : current.andThen(next);
        }

        private boolean hasLambdas() {
            return enter != null || exit != null || tick != null || !handlers.isEmpty() || canEnter != null || canExit != null;
        }

        private StateBehavior<C, S> resolveBehavior() {
            if (behavior != null && hasLambdas()) {
                throw new StateGraphException(owner.name + ": state " + key
                        + " mixes behavior(...) with lambda hooks, pick one");
            }
            if (behavior != null) {
                return behavior;
            }
            if (hasLambdas()) {
                return new LambdaBehavior<>(enter, exit, tick, handlers, canEnter, canExit);
            }
            return emptyBehavior();
        }
    }

    public static final class TransitionBuilder<C, S> {

        private final Builder<C, S> owner;
        private final S from;
        private final S to;
        private final int order;
        private Class<?> eventType;
        private TransitionGuard<C, S> autoCondition;
        private final List<Transition.NamedGuard<C, S>> guards = new ArrayList<>();
        private TransitionAction<C, S> action;
        private int priority;
        private String name;

        private TransitionBuilder(Builder<C, S> owner, S from, S to, int order) {
            this.owner = owner;
            this.from = from;
            this.to = to;
            this.order = order;
        }

        public <E> TransitionBuilder<C, S> on(Class<E> type) {
            Objects.requireNonNull(type, "type");
            if (eventType != null) {
                throw new StateGraphException(owner.name + ": transition " + describe() + " already listens for " + eventType.getSimpleName());
            }
            if (autoCondition != null) {
                throw new StateGraphException(owner.name + ": transition " + describe() + " cannot be both automatic and event driven");
            }
            this.eventType = type;
            return this;
        }

        public <E> TransitionBuilder<C, S> on(Class<E> type, EventGuard<C, S, E> guard) {
            on(type);
            Objects.requireNonNull(guard, "guard");
            guards.add(new Transition.NamedGuard<>("event:" + type.getSimpleName(),
                    (source, info) -> type.isInstance(info.event()) && guard.test(source, type.cast(info.event()))));
            return this;
        }

        public TransitionBuilder<C, S> when(Predicate<StateContext<C, S>> condition) {
            Objects.requireNonNull(condition, "condition");
            return when((source, info) -> condition.test(source));
        }

        public TransitionBuilder<C, S> when(TransitionGuard<C, S> condition) {
            Objects.requireNonNull(condition, "condition");
            if (eventType != null) {
                throw new StateGraphException(owner.name + ": transition " + describe() + " cannot be both automatic and event driven");
            }
            if (autoCondition != null) {
                TransitionGuard<C, S> previous = autoCondition;
                this.autoCondition = (s, i) -> previous.test(s, i) && condition.test(s, i);
            } else {
                this.autoCondition = condition;
            }
            return this;
        }

        public TransitionBuilder<C, S> guard(Predicate<StateContext<C, S>> guard) {
            Objects.requireNonNull(guard, "guard");
            return guard("guard#" + guards.size(), (source, info) -> guard.test(source));
        }

        public TransitionBuilder<C, S> guard(TransitionGuard<C, S> guard) {
            return guard("guard#" + guards.size(), guard);
        }

        public TransitionBuilder<C, S> guard(String guardName, Predicate<StateContext<C, S>> guard) {
            Objects.requireNonNull(guard, "guard");
            return guard(guardName, (source, info) -> guard.test(source));
        }

        public TransitionBuilder<C, S> guard(String guardName, TransitionGuard<C, S> guard) {
            guards.add(new Transition.NamedGuard<>(Objects.requireNonNull(guardName, "guardName"),
                    Objects.requireNonNull(guard, "guard")));
            return this;
        }

        public TransitionBuilder<C, S> action(TransitionAction<C, S> action) {
            Objects.requireNonNull(action, "action");
            this.action = this.action == null ? action : this.action.andThen(action);
            return this;
        }

        public TransitionBuilder<C, S> priority(int priority) {
            this.priority = priority;
            return this;
        }

        public TransitionBuilder<C, S> name(String name) {
            this.name = Objects.requireNonNull(name, "name");
            return this;
        }

        private String describe() {
            return name != null ? name : (from == null ? "*" : String.valueOf(from)) + "->" + to;
        }

        private Transition<C, S> build() {
            List<Transition.NamedGuard<C, S>> all = new ArrayList<>();
            Transition.Kind kind;
            if (eventType != null) {
                kind = Transition.Kind.EVENT;
            } else if (autoCondition != null) {
                kind = Transition.Kind.AUTO;
                all.add(new Transition.NamedGuard<>("when", autoCondition));
            } else {
                kind = Transition.Kind.REQUEST;
            }
            all.addAll(guards);
            return new Transition<>(from, to, kind, eventType, all, action, priority, order, describe());
        }
    }
}
