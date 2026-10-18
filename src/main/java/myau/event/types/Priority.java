package myau.event.types;

/**
 * 事件调度优先级。
 * 用 byte 存（enum 要 32 位，不值）。
 *
 * @author DarkMagician6
 * @since August 3, 2013
 */
public final class Priority {
    public static final byte
            /**
             * 最高。
             */
            HIGHEST = 0,
    /**
     * 次高。
     */
    HIGH = 1,
    /**
     * 中。
     */
    MEDIUM = 2,
    /**
     * 低。
     */
    LOW = 3,
    /**
     * 最低。
     */
    LOWEST = 4;
    /**
     * 按顺序排好的全部优先级。
     */
    public static final byte[] VALUE_ARRAY;

    /**
     * 首次使用时填充 {@link #VALUE_ARRAY}。
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
