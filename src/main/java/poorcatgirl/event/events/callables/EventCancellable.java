package poorcatgirl.event.events.callables;

import poorcatgirl.event.events.Cancellable;
import poorcatgirl.event.events.Event;

/**
 * {@link Cancellable} 的抽象实现，可取消的事件继承它即可。
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
