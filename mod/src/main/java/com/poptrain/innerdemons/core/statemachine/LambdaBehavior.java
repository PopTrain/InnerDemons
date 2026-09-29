package com.poptrain.innerdemons.core.statemachine;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Consumer;

final class LambdaBehavior<C, S> implements StateBehavior<C, S> {

    record TypedHandler<C, S, E>(Class<E> type, EventHandler<C, S, E> handler) {

        boolean tryHandle(StateContext<C, S> context, Object event) {
            return type.isInstance(event) && handler.handle(context, type.cast(event));
        }
    }

    private final BiConsumer<StateContext<C, S>, TransitionInfo<S>> enter;
    private final BiConsumer<StateContext<C, S>, TransitionInfo<S>> exit;
    private final Consumer<StateContext<C, S>> tick;
    private final List<TypedHandler<C, S, ?>> handlers;
    private final BiPredicate<C, TransitionInfo<S>> canEnter;
    private final BiPredicate<StateContext<C, S>, TransitionInfo<S>> canExit;

    LambdaBehavior(BiConsumer<StateContext<C, S>, TransitionInfo<S>> enter,
                   BiConsumer<StateContext<C, S>, TransitionInfo<S>> exit,
                   Consumer<StateContext<C, S>> tick,
                   List<TypedHandler<C, S, ?>> handlers,
                   BiPredicate<C, TransitionInfo<S>> canEnter,
                   BiPredicate<StateContext<C, S>, TransitionInfo<S>> canExit) {
        this.enter = enter;
        this.exit = exit;
        this.tick = tick;
        this.handlers = List.copyOf(handlers);
        this.canEnter = canEnter;
        this.canExit = canExit;
    }

    @Override
    public void onEnter(StateContext<C, S> context, TransitionInfo<S> info) {
        if (enter != null) {
            enter.accept(context, info);
        }
    }

    @Override
    public void onExit(StateContext<C, S> context, TransitionInfo<S> info) {
        if (exit != null) {
            exit.accept(context, info);
        }
    }

    @Override
    public void onTick(StateContext<C, S> context) {
        if (tick != null) {
            tick.accept(context);
        }
    }

    @Override
    public boolean onEvent(StateContext<C, S> context, Object event) {
        for (TypedHandler<C, S, ?> handler : handlers) {
            if (handler.tryHandle(context, event)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean canEnter(C owner, TransitionInfo<S> info) {
        return canEnter == null || canEnter.test(owner, info);
    }

    @Override
    public boolean canExit(StateContext<C, S> context, TransitionInfo<S> info) {
        return canExit == null || canExit.test(context, info);
    }
}
