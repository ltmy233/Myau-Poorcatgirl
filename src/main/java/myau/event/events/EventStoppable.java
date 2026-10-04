package myau.event.events;

/**
 * 能喊停的事件的基类。
 * 这类事件单独走一条路，一旦有人调了 stop()，后面的方法就不再往下叫了。
 *
 * @author DarkMagician6
 * @since 26-9-13
 */
public abstract class EventStoppable implements Event {
    private boolean stopped;

    /**
     * 只给自己人用，没必要开放。
     */
    protected EventStoppable() {
    }

    /**
     * 喊停。之后的监听者就不会再被叫到了。
     */
    public void stop() {
        stopped = true;
    }

    /**
     * @return 已经停了就返回 true。
     */
    public boolean isStopped() {
        return stopped;
    }
}
