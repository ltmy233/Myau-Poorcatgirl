package myau.event.events.callables;

import myau.event.events.Cancellable;
import myau.event.events.Event;

/**
 * {@link Cancellable} 的一个抽象实现，能取消的事件直接继承它就省事了。
 *
 * @author DarkMagician6
 * @since August 27, 2013
 */
public abstract class EventCancellable implements Event, Cancellable {
    private boolean cancelled;

    protected EventCancellable() {
    }

    /**
     * @see Cancellable.isCancelled
     */
    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    /**
     * @see Cancellable.setCancelled
     */
    @Override
    public void setCancelled(boolean state) {
        cancelled = state;
    }
}
