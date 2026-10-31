package poorcatgirl.event;

import poorcatgirl.event.types.Priority;

import java.lang.annotation.*;

/**
 * 打上它，EventManager 就会登记这个方法收事件；优先级由 value 指定。
 *
 * @see Priority
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface EventTarget {
    byte value() default Priority.MEDIUM;
}
