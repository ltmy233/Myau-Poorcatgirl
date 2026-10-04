package myau.event.events;

/**
 * 能被叫停的事件都实现这个接口。
 *
 * @author DarkMagician6
 * @since August 27, 2013
 */
public interface Cancellable {
    /**
     * @return 这个事件现在是不是已经被取消了。
     */
    boolean isCancelled();

    /**
     * 把事件标记成取消，后面的监听者就能看到。
     *
     * @param state true 取消，false 放行。
     */
    void setCancelled(boolean state);
}
