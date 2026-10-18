/**
 * 轻量事件 API。
 * <p>
 * 版本号还是 beta，功能已经稳定。
 * <p>
 * 想改点啥、修 bug，欢迎提 PR：
 * https://bitbucket.org/DarkMagician6/eventapi/overview
 * <p>
 * 用法看 wiki：
 * https://bitbucket.org/DarkMagician6/eventapi/wiki/Home
 */
package myau.event;

/**
 * 门面类：版本、作者等元信息。
 *
 * @author DarkMagician6
 * @since July 31, 2013
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
