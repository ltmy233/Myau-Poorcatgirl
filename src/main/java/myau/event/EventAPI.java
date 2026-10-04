/**
 * 一个管事件的小 API，项目里到处都能用。
 * 想要的是好上手，同时别为了好用就把性能和扩展性搭进去。
 * <p>
 * 版本号还算 beta，但该稳的都稳了，放心用。
 * <p>
 * 想改点什么、修个 bug，欢迎直接提 PR：
 * https://bitbucket.org/DarkMagician6/eventapi/overview
 * <p>
 * 怎么用看这里的 wiki：
 * https://bitbucket.org/DarkMagician6/eventapi/wiki/Home
 *
 * @Todo wiki 还是老样子，哪天得重新写一遍。
 */
package myau.event;

/**
 * 这套 API 的门面，主要就是放几个版本、作者之类的元信息。
 *
 * @author DarkMagician6
 * @since July 31, 2013
 */
public final class EventAPI {
    /**
     * 方法全是静态的，构造器没用，直接封死。
     */
    private EventAPI() {
    }

    /**
     * 当前版本号。
     */
    public static final String VERSION = String.format("%s-%s", "0.7", "beta");
    /**
     * 作者名单。
     */
    public static final String[] AUTHORS = {
            "DarkMagician6"
    };
}
