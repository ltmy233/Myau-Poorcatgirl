package myau.event.events.callables;

import myau.event.events.Event;
import myau.event.events.Typed;

/**
 * {@link Typed} 的抽象实现，带类型的事件继承它即可。
 *
 * @author DarkMagician6
 * @since August 27, 2013
 */
public abstract class EventTyped implements Event, Typed {
    private final byte type;

    /**
     * 构造时固定类型。
     *
     * @param eventType 事件类型 ID。
     */
    protected EventTyped(byte eventType) {
        type = eventType;
    }

    /**
     * @see Typed.getType
     */
    @Override
    public byte getType() {
        return type;
    }
}
