package myau.event.events;

/**
 * 可叫停的事件基类。
 * 有人调 stop() 后，后面的监听者不再被调用。
 *
 * @author DarkMagician6
 * @since 26-9-13
 */
public abstract class EventStoppable implements Event {
    private boolean stopped;

    /**
     * 只给子类用。
     */
    protected EventStoppable() {
    }

    /**
     * 叫停，后续监听者不再收到。
     */
    public void stop() {
        stopped = true;
    }

    /**
     * @return 是否已停止。
     */
    public boolean isStopped() {
        return stopped;
    }
}
