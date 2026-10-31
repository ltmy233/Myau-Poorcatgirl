package poorcatgirl.event.events;

/**
 * 带类型的事件实现这个接口，构造时固定类型。
 */
public interface Typed {
    /**
     * @return 事件类型 ID。
     */
    byte getType();
}
