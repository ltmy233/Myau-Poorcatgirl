package myau.event.events;

/**
 * 带类型的事件实现这个接口，构造时固定类型。
 *
 * @author DarkMagician6
 * @since August 27, 2013
 */
public interface Typed {
    /**
     * @return 事件类型 ID。
     */
    byte getType();
}
