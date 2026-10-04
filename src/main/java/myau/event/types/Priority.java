package myau.event.types;

/**
 * 调度的时候靠它决定先叫谁后叫谁。
 * 当时 Ram 提了一嘴我这么存数据有点费内存，所以优先级干脆用 byte：
 * 一个值只要 8 位，换成 enum 得 32 位，跟 int 一样贵。
 *
 * @author DarkMagician6
 * @since August 3, 2013
 */
public final class Priority {
    public static final byte
            /**
             * 最高，排第一个叫。
             */
            HIGHEST = 0,
    /**
     * 次高，紧跟着 HIGHEST。
     */
    HIGH = 1,
    /**
     * 居中，排在 HIGH 后面。
     */
    MEDIUM = 2,
    /**
     * 靠后，排在 MEDIUM 后面。
     */
    LOW = 3,
    /**
     * 最低，所有都叫完了才轮到它。
     */
    LOWEST = 4;
    /**
     * 把所有优先级按顺序收在一起，排序时照着它走。
     */
    public static final byte[] VALUE_ARRAY;

    /**
     * 类第一次被用到时才填 {@link #VALUE_ARRAY}。
     */
    static {
        VALUE_ARRAY = new byte[]{
                HIGHEST,
                HIGH,
                MEDIUM,
                LOW,
                LOWEST
        };
    }
}
