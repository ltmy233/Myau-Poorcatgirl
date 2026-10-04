package myau.event;

import myau.event.events.Event;
import myau.event.events.EventStoppable;
import myau.event.types.Priority;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 调度中心：事件发出去以后，由它去把该收的人挨个叫一遍。
 *
 * @author DarkMagician6
 * @since February 2, 2014
 */
public final class EventManager {
    /**
     * 所有登记过的监听方法都在这里，按事件类型分好组，取的时候直接对号入座。
     */
    private static final HashMap<Class<? extends Event>, List<MethodData>> REGISTRY_MAP = new HashMap<>();

    /**
     * 全是静态方法，没必要再 new 一个出来，构造器直接掐掉。
     */
    private EventManager() {
    }

    /**
     * 把对象里所有带 {@link EventTarget} 的方法都登记进来。
     *
     * @param object 要登记的对象，一般就是模块自己。
     */
    public static void register(Object object) {
        for (final Method method : object.getClass().getDeclaredMethods()) {
            if (!isMethodBad(method)) {
                register(method, object);
            }
        }
    }

    /**
     * 同上，但只挑参数是指定事件类型的那些方法登记。
     *
     * @param object     持有这些方法的对象。
     * @param eventClass 只关心这一个事件类型。
     */
    public static void register(Object object, Class<? extends Event> eventClass) {
        for (final Method method : object.getClass().getDeclaredMethods()) {
            if (!isMethodBad(method, eventClass)) {
                register(method, object);
            }
        }
    }

    /**
     * 把这个对象登记过的监听方法全部摘掉，模块关掉或者对象销毁时走这里。
     *
     * @param object 要退订的对象。
     */
    public static void unregister(Object object) {
        for (final List<MethodData> dataList : REGISTRY_MAP.values()) {
            for (final MethodData data : dataList) {
                if (data.getSource().equals(object)) {
                    dataList.remove(data);
                }
            }
        }
        cleanMap(true);
    }

    /**
     * 只退订这个对象里监听某一种事件的方法，其它的留着不动。
     *
     * @param object     要退订的对象。
     * @param eventClass 只取消对这个事件的监听。
     */
    public static void unregister(Object object, Class<? extends Event> eventClass) {
        if (REGISTRY_MAP.containsKey(eventClass)) {
            for (final MethodData data : REGISTRY_MAP.get(eventClass)) {
                if (data.getSource().equals(object)) {
                    REGISTRY_MAP.get(eventClass).remove(data);
                }
            }
            cleanMap(true);
        }
    }

    /**
     * 单个方法的登记入口。
     * 这个事件类型已经有人占了，就插进它那条列表再按优先级排一次；
     * 还没人管，就新开一条列表把这个方法放进去。
     * 排序的规则看 {@link Priority}。
     *
     * @param method 要登记的方法。
     * @param object 方法挂在谁身上，回头调用时要拿它当实例。
     */
    private static void register(Method method, Object object) {
        Class<? extends Event> indexClass = (Class<? extends Event>) method.getParameterTypes()[0];
        // 先把方法连同它的优先级打包好
        final MethodData data = new MethodData(object, method, method.getAnnotation(EventTarget.class).value());
        // 私有/受保护的方法也得能调，不然白登记了
        if (!data.getTarget().isAccessible()) {
            data.getTarget().setAccessible(true);
        }
        if (REGISTRY_MAP.containsKey(indexClass)) {
            if (!REGISTRY_MAP.get(indexClass).contains(data)) {
                REGISTRY_MAP.get(indexClass).add(data);
                sortListValue(indexClass);
            }
        } else {
            REGISTRY_MAP.put(indexClass, new CopyOnWriteArrayList<MethodData>() {
                // 匿名子类不写这个的话编译器会一直提示
                private static final long serialVersionUID = 666L;

                {
                    add(data);
                }
            });
        }
    }

    /**
     * 把某个事件类型整条记录从表里删掉。
     *
     * @param indexClass 要删的那个事件类型。
     */
    public static void removeEntry(Class<? extends Event> indexClass) {
        Iterator<Map.Entry<Class<? extends Event>, List<MethodData>>> mapIterator = REGISTRY_MAP.entrySet().iterator();
        while (mapIterator.hasNext()) {
            if (mapIterator.next().getKey().equals(indexClass)) {
                mapIterator.remove();
                break;
            }
        }
    }

    /**
     * 清理表里的空壳记录。走迭代器删，直接对着 map 删会出问题。
     *
     * @param onlyEmptyEntries 只清空列表的那些条目；给 false 就整张表一起清。
     */
    public static void cleanMap(boolean onlyEmptyEntries) {
        Iterator<Map.Entry<Class<? extends Event>, List<MethodData>>> mapIterator = REGISTRY_MAP.entrySet().iterator();
        while (mapIterator.hasNext()) {
            if (!onlyEmptyEntries || mapIterator.next().getValue().isEmpty()) {
                mapIterator.remove();
            }
        }
    }

    /**
     * 按优先级把某个事件的监听列表重新排一遍，优先级高的排前面。
     *
     * @param indexClass 要重排的那条列表对应的事件类型。
     */
    private static void sortListValue(Class<? extends Event> indexClass) {
        List<MethodData> sortedList = new CopyOnWriteArrayList<>();
        for (final byte priority : Priority.VALUE_ARRAY) {
            for (final MethodData data : REGISTRY_MAP.get(indexClass)) {
                if (data.getPriority() == priority) {
                    sortedList.add(data);
                }
            }
        }
        // 排完直接覆盖原来的那条
        REGISTRY_MAP.put(indexClass, sortedList);
    }

    /**
     * 这个方法能不能用来收事件？参数不是一个、或者压根没打 {@link EventTarget}，就不收。
     *
     * @param method 待检查的方法。
     * @return true 表示这方法不合格，别拿它收事件。
     * @see EventTarget
     */
    private static boolean isMethodBad(Method method) {
        return method.getParameterTypes().length != 1 || !method.isAnnotationPresent(EventTarget.class);
    }

    /**
     * 在上一条的基础上再挑一次：参数类型还得跟我们要发的事件对得上。
     *
     * @param method     待检查的方法。
     * @param eventClass 正准备发出去的那个事件类型。
     * @return true 表示这方法不收这个事件。
     * @see EventTarget
     */
    private static boolean isMethodBad(Method method, Class<? extends Event> eventClass) {
        return isMethodBad(method) || !method.getParameterTypes()[0].equals(eventClass);
    }

    /**
     * 发事件的地方，谁登记了就叫谁。
     * 先按事件类型把名单取出来，没有就算了。
     * 要是这事件是能叫停的 {@link EventStoppable}，那每叫完一个就回头看一眼停没停，停了立刻收工；
     * 普通事件就一口气全叫完。最后把事件原样还回去。
     *
     * @param event 要发出去的事件。
     * @return 走完一圈之后的事件，可能是被别人改过的状态。
     */
    public static Event call(final Event event) {
        List<MethodData> dataList = REGISTRY_MAP.get(event.getClass());
        if (dataList != null) {
            if (event instanceof EventStoppable) {
                EventStoppable stoppable = (EventStoppable) event;
                for (final MethodData data : dataList) {
                    invoke(data, event);
                    if (stoppable.isStopped()) {
                        break;
                    }
                }
            } else {
                for (final MethodData data : dataList) {
                    invoke(data, event);
                }
            }
        }
        return event;
    }

    /**
     * 真正动手去调那个监听方法，异常打个堆栈就完了，别把游戏一起带崩。
     *
     * @param data     要调的方法和它挂的对象。
     * @param argument 当作参数传进去的事件。
     */
    private static void invoke(MethodData data, Event argument) {
        try {
            data.getTarget().invoke(data.getSource(), argument);
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    /**
     * 一条登记记录，绑着"谁的""哪个方法""什么优先级"。
     *
     * @author DarkMagician6
     * @since January 2, 2014
     */
    private static final class MethodData {
        private final Object source;
        private final Method target;
        private final byte priority;

        /**
         * 三个字段一次填满，之后只读。
         *
         * @param source   方法挂的对象，调用时当实例用。
         * @param target   真正要调的那个方法。
         * @param priority 这个方法的优先级，列表排序靠它。
         */
        public MethodData(Object source, Method target, byte priority) {
            this.source = source;
            this.target = target;
            this.priority = priority;
        }

        /**
         * @return 方法挂的对象。
         */
        public Object getSource() {
            return source;
        }

        /**
         * @return 登记进来的那个方法。
         */
        public Method getTarget() {
            return target;
        }

        /**
         * @return 这个方法的优先级。
         */
        public byte getPriority() {
            return priority;
        }
    }
}
