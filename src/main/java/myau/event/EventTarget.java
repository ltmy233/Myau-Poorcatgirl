package myau.event;

import myau.event.types.Priority;

import java.lang.annotation.*;

/**
 * 打上它，EventManager 就会登记这个方法收事件；优先级由 value 指定。
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
