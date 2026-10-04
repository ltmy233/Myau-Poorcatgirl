package myau.event.events;

/**
 * 带"类型"的事件实现这个接口。
 * 同一个事件可能在好几个地方都会发，用类型标出这次是哪儿发的。
 * <p/>
 * 类型在构造器里给定，实例一造出来就固定了。
 *
 * @author DarkMagician6
 * @since August 27, 2013
 */
public interface Typed {
    /**
     * @return 这个事件当前的类型 ID。
     */
    byte getType();
}
