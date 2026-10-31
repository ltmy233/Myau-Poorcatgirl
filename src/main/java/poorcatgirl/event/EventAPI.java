package poorcatgirl.event;

/**
 * 门面类：版本、作者等元信息。
 */
public final class EventAPI {
    /**
     * 工具类，不实例化。
     */
    private EventAPI() {
    }

    /**
     * 版本号。
     */
    public static final String VERSION = String.format("%s-%s", "0.7", "beta");
    /**
     * 作者。
     */
    public static final String[] AUTHORS = {
            "DarkMagician6"
    };
}
