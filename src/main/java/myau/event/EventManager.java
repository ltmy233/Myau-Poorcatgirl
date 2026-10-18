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
 * 事件调度：按类型分发，调用登记的监听方法。
 *
 * @author DarkMagician6
 * @since February 2, 2014
 */
public final class EventManager {
    /**
     * 各事件类型 → 监听方法列表。
     */
    private static final HashMap<Class<? extends Event>, List<MethodData>> REGISTRY_MAP = new HashMap<>();

    /**
     * 工具类，不实例化。
     */
    private EventManager() {
    }

    /**
     * 登记对象里所有带 {@link EventTarget} 的方法。
     *
     * @param object 要登记的对象。
     */
    public static void register(Object object) {
        for (final Method method : object.getClass().getDeclaredMethods()) {
            if (!isMethodBad(method)) {
                register(method, object);
            }
        }
    }

    /**
     * 只登记参数为指定事件类型的方法。
     *
     * @param object     持有方法的对象。
     * @param eventClass 只登记这个事件类型。
     */
    public static void register(Object object, Class<? extends Event> eventClass) {
        for (final Method method : object.getClass().getDeclaredMethods()) {
            if (!isMethodBad(method, eventClass)) {
                register(method, object);
            }
        }
    }

    /**
     * 摘掉这个对象登记的全部方法。
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
     * 只摘掉这个对象对指定事件的监听。
     *
     * @param object     要退订的对象。
     * @param eventClass 只取消这个事件的监听。
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
     * 单方法登记：同类型已有列表就插入并重排，否则新建列表。
     *
     * @param method 要登记的方法。
     * @param object 方法挂的对象，调用时当实例。
     */
    private static void register(Method method, Object object) {
        Class<? extends Event> indexClass = (Class<? extends Event>) method.getParameterTypes()[0];
        // 打包方法 + 优先级
        final MethodData data = new MethodData(object, method, method.getAnnotation(EventTarget.class).value());
        // 私有/受保护方法也要能调
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
                // 消 serialVersionUID 警告
                private static final long serialVersionUID = 666L;

                {
                    add(data);
                }
            });
        }
    }

    /**
     * 删掉某个事件类型的整条记录。
     *
     * @param indexClass 要删的事件类型。
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
     * 清理空列表条目（走迭代器删）。
     *
     * @param onlyEmptyEntries true 只清空条目；false 清整张表。
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
     * 按优先级重排监听列表，高优先级在前。
     *
     * @param indexClass 要重排的事件类型。
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
        // 覆盖排序结果
        REGISTRY_MAP.put(indexClass, sortedList);
    }

    /**
     * 方法参数不是一个、或没打 {@link EventTarget} 的不收事件。
     *
     * @param method 待检查的方法。
     * @return true = 不合格，不能收事件。
     * @see EventTarget
     */
    private static boolean isMethodBad(Method method) {
        return method.getParameterTypes().length != 1 || !method.isAnnotationPresent(EventTarget.class);
    }

    /**
     * 同上，再要求参数类型匹配。
     *
     * @param method     待检查的方法。
     * @param eventClass 要发的事件类型。
     * @return true = 这方法不收这个事件。
     * @see EventTarget
     */
    private static boolean isMethodBad(Method method, Class<? extends Event> eventClass) {
        return isMethodBad(method) || !method.getParameterTypes()[0].equals(eventClass);
    }

    /**
     * 派发事件；{@link EventStoppable} 每调完一个检查是否被叫停。
     *
     * @param event 要发的事件。
     * @return 走完一圈后的事件。
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
     * 反射调用监听方法，异常只打堆栈。
     *
     * @param data     方法 + 对象。
     * @param argument 传进去的事件。
     */
    private static void invoke(MethodData data, Event argument) {
        try {
            data.getTarget().invoke(data.getSource(), argument);
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    /**
     * 一条登记记录：对象 + 方法 + 优先级。
     *
     * @author DarkMagician6
     * @since January 2, 2014
     */
    private static final class MethodData {
        private final Object source;
        private final Method target;
        private final byte priority;

        /**
         * 构造。
         *
         * @param source   方法挂的对象。
         * @param target   要调的方法。
         * @param priority 优先级。
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
