package myau.event.events;

/**
 * 可取消的事件实现这个接口。
 *
 * @author DarkMagician6
 * @since August 27, 2013
 */
public interface Cancellable {
    /**
     * @return 是否已取消。
     */
    boolean isCancelled();

    /**
     * 标记取消，后续监听者能看到。
     *
     * @param state true 取消，false 放行。
     */
    void setCancelled(boolean state);
}
