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

import kotlinx.coroutines.CoroutineScope

/**
 * A task that can be accomplished.
 *
 * Tasks, by default, have no data. Users are expected to create their own
 * type that implements this interface and has the necessary data.
 */
public interface Task

/**
 * A task which also has a name.
 */
public interface NamedTask : Task {
    public val name: String
}

/**
 * A task with no data other than its name.
 */
internal class BareNamedTask
internal constructor(
    override val name: String,
) : NamedTask

/**
 * An anonymous task with no data.
 */
public class AnonymousTask
internal constructor() : Task

/**
 * Creates a task with the given name.
 */
public fun task(name: String): NamedTask = BareNamedTask(name)

/**
 * Creates an anonymous tasks with no name.
 */
public fun task(): Task = AnonymousTask()

/**
 * Returns if this task is anonymous.
 *
 * This is a shorthand for `is AnonymousTask`.
 */
public fun Task.isAnonymous(): Boolean = this is AnonymousTask

/**
 * Returns if this task is **not** anonymous.
 *
 * This is a shorthand for `!isAnonymous()`.
 */
public fun Task.isNotAnonymous(): Boolean = !this.isAnonymous()

/**
 * Returns if this task has a name.
 *
 * This is a shorthand for `is NamedTask`.
 */
public fun Task.isNamed(): Boolean = this is NamedTask

/**
 * Returns if this task does **not** have a name.
 *
 * This is a shorthand for `!isNamed()`.
 */
public fun Task.isNotNamed(): Boolean = !this.isNamed()

/**
 * Returns the name of this task or the given fallback value if this
 * task is not named.
 */
public fun Task.getNameOrElse(
    fallback: String = "<unnamed task>",
): String = if (this is NamedTask) name else fallback

/**
 * Returns the name of this task or `null` if this task is not named.
 */
public val Task.nameOrNull: String?
    get() = if (this is NamedTask) name else null

/**
 * The function signature for a runnable task.
 */
public typealias TaskRunnableBlock<T, R> =
        suspend context(CoroutineScope)
        LiveTaskContextScope<T>.() -> R

/**
 * A task that can be executed.
 *
 * @property task The task to execute.
 * @property block The code to execute.
 */
@ConsistentCopyVisibility
public data class TaskRunnable<T : Task, out R>
internal constructor(
    public val task: T,
    public val block: TaskRunnableBlock<T, R>,
)

/**
 * Makes a task executable.
 *
 * @param block The code to execute.
 * @return A runnable task.
 */
public fun <T : Task, R> T.runnable(
    block: TaskRunnableBlock<T, R>,
): TaskRunnable<T, R> = TaskRunnable(
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
public operator fun <T : Task, R> T.invoke(
    block: TaskRunnableBlock<T, R>,
): TaskRunnable<T, R> = runnable(block)

/**
 * Shorthand to make an executable task with the given name.
 *
 * @param name The name of the task.
 * @param block The code to execute.
 * @return A runnable task with the given name.
 */
public fun <R> task(
    name: String,
    block: TaskRunnableBlock<NamedTask, R>,
): TaskRunnable<NamedTask, R> {
    val task = BareNamedTask(name)
    val section = TaskRunnable(task, block)
    return section
}

/**
 * Shorthand to make an anonymous executable task.
 *
 * @param block The code to execute.
 * @return A runnable task with the given name.
 */
public fun <R> task(
    block: TaskRunnableBlock<Task, R>,
): TaskRunnable<Task, R> {
    val task = AnonymousTask()
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
