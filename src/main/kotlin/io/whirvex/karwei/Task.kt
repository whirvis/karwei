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

/**
 * A task that can be accomplished.
 *
 * Tasks, by default, have no data other than their name. Users are
 * supposed to create their own type that implements this interface
 * and has the necessary data.
 */
public interface Task {

    /**
     * The name of the task.
     */
    public val name: String

}

/**
 * A task with no data other than its name.
 */
public class NamedTask
internal constructor(
    override val name: String,
) : Task

/**
 * Creates a task with the given name.
 */
public fun task(name: String): Task = NamedTask(name)

/**
 * A task that can be executed.
 *
 * @property task The task to execute.
 * @property block The code to execute.
 */
@ConsistentCopyVisibility
public data class TaskRunnable<out R>
internal constructor(
    public val task: Task,
    public val block: suspend LiveTaskContextScope.() -> R,
)

/**
 * Makes a task executable.
 *
 * @param block The code to execute.
 * @return A runnable task.
 */
public fun <R> Task.runnable(
    block: suspend LiveTaskContextScope.() -> R,
): TaskRunnable<R> = TaskRunnable(
    task = this,
    block = block,
)

/**
 * Makes a task executable.
 *
 * **Note:** This is an alias for [Task.runnable]
 *
 * @param block The code to execute.
 * @return A runnable task.
 */
public operator fun <R> Task.invoke(
    block: suspend LiveTaskContextScope.() -> R,
): TaskRunnable<R> = runnable(block)

/**
 * Shorthand to make an executable task with the given name.
 *
 * @param name The name of the task.
 * @param block The code to execute.
 * @return A runnable task with the given name.
 */
public fun <R> task(
    name: String,
    block: suspend LiveTaskContextScope.() -> R,
): TaskRunnable<R> {
    val task = NamedTask(name)
    val section = TaskRunnable(task, block)
    return section
}

/**
 * Signals an exception relating to a [Task].
 *
 * @property task The task that caused the exception.
 */
public sealed class TaskException(
    public val task: Task,
) : RuntimeException()
