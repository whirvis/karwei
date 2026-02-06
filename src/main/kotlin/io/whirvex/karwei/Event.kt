/*
 * the MIT License (MIT)
 *
 * Copyright (c) 2025-2026 Whirvex Software, LLC
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
package io.whirvex.karwei

import java.time.LocalDateTime

/**
 * An event caused by a [Task].
 *
 * @property task The task that triggered the event.
 * @property context The context the event occurred in.
 * @see taskFlow
 */
public sealed class TaskEvent(
    public val task: Task,
    context: LiveTaskContext<*>,
) {

    /**
     * The context the event occurred in.
     */
    public val context: TaskContext<*> = context.toStatic()

    /**
     * The exact time at which this event occurred
     * (i.e., when this value was constructed).
     */
    public val occurredAt: LocalDateTime = LocalDateTime.now()

}

/**
 * A task has started execution.
 *
 * @param task The task that has started.
 * @param context The context the event occurred in.
 */
public class TaskBeginEvent
internal constructor(
    task: Task,
    context: LiveTaskContext<*>,
) : TaskEvent(task, context)

/**
 * A task has logged a message.
 *
 * @param task The task that logged the message.
 * @param context The context the event occurred in.
 * @param level The log message level.
 * @param message The log message.
 */
public class TaskLogEvent
internal constructor(
    task: Task,
    context: LiveTaskContext<*>,
    public val level: TaskLogLevel,
    public val message: () -> Any,
) : TaskEvent(task, context)

/**
 * A task has successfully finished execution.
 *
 * @param task The task that has finished.
 * @param context The context the event occurred in.
 * @property result The resulting value.
 */
public class TaskFinishEvent
internal constructor(
    task: Task,
    context: LiveTaskContext<*>,
    internal val result: Any?,
) : TaskEvent(task, context)

/**
 * A task has failed execution.
 *
 * @property task The task that failed.
 * @property context The context the event occurred in.
 * @property cause The uncaught exception.
 */
public class TaskFailEvent
internal constructor(
    task: Task,
    context: LiveTaskContext<*>,
    public val cause: Throwable,
) : TaskEvent(task, context)
