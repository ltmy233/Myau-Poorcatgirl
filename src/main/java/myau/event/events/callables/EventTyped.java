package myau.event.events.callables;

import myau.event.events.Event;
import myau.event.events.Typed;

/**
 * {@link Typed} 的一个抽象实现，带类型的事件直接继承它。
 *
 * @author DarkMagician6
 * @since August 27, 2013
 */
public abstract class EventTyped implements Event, Typed {
    private final byte type;

    /**
     * 造的时候就把类型定死，之后只读。
     *
     * @param eventType 这个事件的类型 ID。
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
