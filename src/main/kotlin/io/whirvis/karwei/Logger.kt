/*
 * the MIT License (MIT)
 *
 * Copyright (c) 2025 "Whirvis" Trent Summerlin
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * the above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package io.whirvis.karwei

/**
 * Represents a log level.
 *
 * It is possible to add more levels by extending this class:
 * ```
 * public object Danger: TaskLogLevel(
 *   level = 35, /* between Warn and Error */
 *   name = "DANGER",
 * )
 * ```
 *
 * It is up to the receivers of log messages to determine if they
 * should do anything with a message based on it's level. This is to
 * avoid confusion from having multiple log configurations.
 */
public open class TaskLogLevel(
    public val level: Int,
    public val name: String,
) {

    public object Trace : TaskLogLevel(
        level = 0,
        name = "TRACE",
    )

    public object Debug : TaskLogLevel(
        level = 10,
        name = "DEBUG",
    )

    public object Info : TaskLogLevel(
        level = 20,
        name = "INFO",
    )

    public object Warn : TaskLogLevel(
        level = 30,
        name = "WARN",
    )

    public object Error : TaskLogLevel(
        level = 40,
        name = "ERROR",
    )

    public object Fatal : TaskLogLevel(
        level = 50,
        name = "FATAL",
    )

    public object Off : TaskLogLevel(
        level = Integer.MAX_VALUE,
        name = "OFF",
    )

    public operator fun compareTo(other: TaskLogLevel): Int {
        return level.compareTo(other.level)
    }

    /**
     * Returns if this level is equal to another.
     *
     * Two `TaskLogLevel` objects are considered equal to each other if
     * their `level` property is equal. Their name is not considered.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        } else if (other !is TaskLogLevel) {
            return false
        }
        return level == other.level
    }

    override fun hashCode(): Int {
        return level.hashCode()
    }

    override fun toString(): String {
        return this.name
    }

}

/**
 * A logger for [TaskContext].
 *
 * This exists so that tasks may log messages without needing to print
 * directly to the console. This should be used when a task wants to convey
 * information (e.g., a possible error) that may be useful to the consumer.
 *
 * By design, [log] accepts `Any`. This is so tasks can log objects with
 * properties rather than raw strings. An example use-case of this would be
 * adding support for multilingual log messages.
 */
public interface TaskLogger {

    /**
     * The context this logger is for.
     *
     * @see TaskContext.logger
     */
    public val context: TaskContext

    /**
     * Emits a log message from the [context].
     *
     * @param level The log level.
     * @param message The message.
     * @see TaskLogger.trace
     * @see TaskLogger.debug
     * @see TaskLogger.info
     * @see TaskLogger.warn
     * @see TaskLogger.error
     * @see TaskLogger.fatal
     */
    public suspend fun log(level: TaskLogLevel, message: () -> Any)

}

/**
 * The logger used for a [StaticTaskContext].
 *
 * It is impossible to log anything with this object. Calling [log] will
 * cause an `IllegalStateException` to be thrown.
 */
public class StaticTaskLogger
internal constructor(
    override val context: StaticTaskContext,
) : TaskLogger {

    override suspend fun log(level: TaskLogLevel, message: () -> Any) {
        val message = "Cannot log from a static context"
        throw IllegalStateException(message)
    }

}

/**
 * The logger used for a [LiveTaskContext].
 */
public class LiveTaskLogger
internal constructor(
    override val context: LiveTaskContext,
) : TaskLogger {

    override suspend fun log(level: TaskLogLevel, message: () -> Any) {
        context.emitLog(level, message)
    }

}

/**
 * Emits a log message from the [context] at a level of
 * [TaskLogLevel.Trace].
 *
 * @param message The message.
 * @see TaskLogger.log
 */
public suspend fun TaskLogger.trace(message: () -> Any) {
    this.log(TaskLogLevel.Trace, message)
}

/**
 * Emits a log message from the [context] at a level of
 * [TaskLogLevel.Debug].
 *
 * @param message The message.
 * @see TaskLogger.log
 */
public suspend fun TaskLogger.debug(message: () -> Any) {
    this.log(TaskLogLevel.Debug, message)
}

/**
 * Emits a log message from the [context] at a level of
 * [TaskLogLevel.Info].
 *
 * @param message The message.
 * @see TaskLogger.log
 */
public suspend fun TaskLogger.info(message: () -> Any) {
    this.log(TaskLogLevel.Info, message)
}

/**
 * Emits a log message from the [context] at a level of
 * [TaskLogLevel.Warn].
 *
 * @param message The message.
 * @see TaskLogger.log
 */
public suspend fun TaskLogger.warn(message: () -> Any) {
    this.log(TaskLogLevel.Warn, message)
}

/**
 * Emits a log message from the [context] at a level of
 * [TaskLogLevel.Error].
 *
 * @param message The message.
 * @see TaskLogger.log
 */
public suspend fun TaskLogger.error(message: () -> Any) {
    this.log(TaskLogLevel.Error, message)
}

/**
 * Emits a log message from the [context] at a level of
 * [TaskLogLevel.Fatal].
 *
 * @param message The message.
 * @see TaskLogger.log
 */
public suspend fun TaskLogger.fatal(message: () -> Any) {
    this.log(TaskLogLevel.Fatal, message)
}
