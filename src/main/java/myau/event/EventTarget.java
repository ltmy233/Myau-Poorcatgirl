package myau.event;

import myau.event.types.Priority;

import java.lang.annotation.*;

/**
 * 打上它，EventManager 就知道这个方法该登记进来收事件。
 * 优先级也是靠它给的。
 *
 * @author DarkMagician6
 * @see Priority
 * @since July 30, 2013
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface EventTarget {
    byte value() default Priority.MEDIUM;
}
