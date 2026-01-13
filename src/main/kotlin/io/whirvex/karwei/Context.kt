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

import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.yield
import java.util.concurrent.locks.ReentrantLock

/**
 * Describes what should occur when a task on the same level
 * as another (i.e., a sibling rather than a child) starts before
 * the other is finished.
 */
public enum class ConcurrentTaskBehavior {

    /**
     * Do nothing and run the tasks as normal.
     *
     * This is the default behavior.
     */
    IGNORE,

    /**
     * Wait for the previous task to finish.
     *
     * Be aware this may slow down execution.
     */
    AWAIT,

    /**
     * Halt execution and throw an exception.
     *
     * @see ConcurrentTaskException
     */
    ERROR,

}

/**
 * Signals that a task has started before a sibling task could finish
 * in an environment that does not allow it.
 *
 * @param task The task that started prematurely.
 */
public class ConcurrentTaskException(
    task: Task,
    override val message: String?
) : TaskException(task)

private fun TaskContext.getLevel(): Int {
    var level = 0
    var current: TaskContext? = parent
    while (current != null) {
        level += 1
        current = current.parent
    }
    return level
}

private fun List<TaskContext>.names() = joinToString {
    "\"${it.task.name}\""
}

/**
 * Persistent context for a [Task].
 */
public interface TaskContext {

    /**
     * Returns the task being run in this context.
     *
     * @throws IllegalStateException If no task is currently running.
     */
    public val task: Task

    /**
     * Returns the parent of the current task.
     *
     * This will be `null` if the task has no parent or has completed.
     *
     * Accesses to this property are not idempotent, it will become
     * `null` once the task transitions to its final state and all its
     * [children] have completed.
     */
    public val parent: TaskContext?

    /**
     * Returns the level this task is at in the hierarchy.
     *
     * This will be zero if the task has no parent or has completed.
     *
     * Accesses to this property are not idempotent, it will become
     * zero once the task transitions to its final state and all its
     * [children] have completed.
     */
    public val level: Int

    /**
     * Returns `true` when this task is active.
     *
     * A task is considered active once it has started and has not yet
     * completed. Note that a task that is waiting for its [children] to
     * complete is still considered active if it has not failed.
     */
    public val isActive: Boolean

    /**
     * Returns `true` when this task has completed.
     *
     * A task is considered complete once it has finished execution,
     * **even if it failed.** A task is considered complete only after
     * all its [children] complete.
     */
    public val isCompleted: Boolean

    /**
     * Returns `true` if this task has failed.
     *
     * A task is considered failed if it throws an uncaught
     * exception during execution. A task is considered failed
     * even if the exception came from one of its [children].
     */
    public val isFailed: Boolean

    /**
     * Returns the failure cause, if any.
     *
     * The failure cause may come from a child task whose exception
     * propagated upward.
     */
    public val failureCause: Throwable?

    /**
     * Returns a list of this task's children.
     *
     * A task becomes a child of this task when it is constructed with this
     * task in its [TaskContext] or an explicit parent parameter.
     */
    public val children: List<TaskContext>

    /**
     * The logger for this task.
     */
    public val logger: TaskLogger

}

private fun List<LiveTaskContext>.toStatic() =
    map { StaticTaskContext(context = it) }

/**
 * Snapshot context of a [Task] at a certain point in time.
 *
 * This type is necessary as events sometimes take long enough to emit
 * such that the state of a [LiveTaskContext] has already changed.
 */
@ConsistentCopyVisibility
public data class StaticTaskContext
private constructor(
    override val task: Task,
    override val level: Int,
    override val isActive: Boolean,
    override val isCompleted: Boolean,
    override val isFailed: Boolean,
    override val failureCause: Throwable?,
    override val children: List<StaticTaskContext>,
) : TaskContext {

    /*
     * This is set by parent contexts *after* initial construction.
     *
     * Doing it at construction would result in a stack overflow
     * (the parent calls on the child to convert to a static context,
     * which then calls on the parent to convert to a static context,
     * which then calls on the child to convert, and so on.)
     */
    private var _parent: StaticTaskContext? = null

    private var _logger: StaticTaskLogger? = null

    override val parent: StaticTaskContext?
        get() = _parent

    override val logger: StaticTaskLogger
        get() = _logger!!

    internal constructor(
        context: LiveTaskContext,
    ) : this(
        task = context.task,
        level = context.level,
        isActive = context.isActive,
        isCompleted = context.isCompleted,
        isFailed = context.isFailed,
        failureCause = context.failureCause,
        children = context.children.toStatic(),
    ) {
        children.forEach { it._parent = this }
        this._logger = StaticTaskLogger(context = this)
    }

}

/**
 * Current context for a [Task].
 */
public class LiveTaskContext
internal constructor() : TaskContext {

    private val contextLock = ReentrantLock()
    private var contextEntered = false

    private var _events: ProducerScope<TaskEvent>? = null
    private var _concurrentTaskBehavior: ConcurrentTaskBehavior? = null

    private var _task: Task? = null
    private var _parent: LiveTaskContext? = null
    private var _isActive: Boolean = false
    private var _isCompleted: Boolean = false
    private var _failureCause: Throwable? = null
    private val _children = mutableListOf<LiveTaskContext>()
    private val _logger = LiveTaskLogger(this)

    internal val events: ProducerScope<TaskEvent>?
        get() = this._events

    internal val concurrentTaskBehavior: ConcurrentTaskBehavior
        get() = this._concurrentTaskBehavior!!

    override val task: Task
        get() {
            val field = _task
            if (field == null) {
                val message = "No task currently running"
                throw IllegalStateException(message)
            }
            return field
        }

    override val parent: LiveTaskContext?
        get() = this._parent

    override val level: Int
        get() = this.getLevel()

    override val isActive: Boolean
        get() = this._isActive

    override val isCompleted: Boolean
        get() = this._isCompleted

    override val isFailed: Boolean
        get() = this._failureCause != null

    override val failureCause: Throwable?
        get() = this._failureCause

    override val children: List<LiveTaskContext>
        get() = this._children.toList()

    override val logger: LiveTaskLogger
        get() = this._logger

    private suspend fun TaskEvent.emit() {
        yield() /* give collectors time to process */
        events?.send(element = this@emit)
        yield() /* give collectors time to process */
    }

    internal suspend fun emitLog(
        level: TaskLogLevel,
        message: () -> Any,
    ) {
        contextLock.lock()
        try {
            if (_task == null) {
                val message = "Scope has already been exited"
                throw IllegalStateException(message)
            }
            TaskLogEvent(task, this, level, message).emit()
        } finally {
            contextLock.unlock()
        }
    }

    private fun setEnteredFlag() {
        if (contextEntered) {
            val message = "Context already entered"
            throw IllegalStateException(message)
        }
        this.contextEntered = true
    }

    private suspend fun enforceConcurrentBehavior() {
        val firstborn = parent?._children?.firstOrNull()
            ?: return /* nothing to check */

        fun ignore() {
            /* nothing to do */
        }

        suspend fun await() {
            while (firstborn.isActive) {
                yield()
            }
        }

        fun error() {
            val name = firstborn.task.name
            val message = "Sibling task \"$name\" still running"
            throw ConcurrentTaskException(task, message)
        }

        when (concurrentTaskBehavior) {
            ConcurrentTaskBehavior.IGNORE -> ignore()
            ConcurrentTaskBehavior.AWAIT -> await()
            ConcurrentTaskBehavior.ERROR -> error()
        }
    }

    private suspend fun <R> start(
        runnable: TaskRunnable<R>,
    ): R {
        val task = runnable.task

        fun setup() {
            this._task = task
            this._isActive = true
            this._isCompleted = true
            parent?._children?.add(this)
        }

        fun cleanup() {
            parent?._children -= this
            this._isCompleted = true
            this._isActive = false
            this._task = null
            this._parent = null
        }

        fun ensureNoRunningChildren() {
            if (children.isNotEmpty()) {
                val names = children.names()
                val message = "Child tasks ($names) are still running"
                throw ConcurrentTaskException(task, message)
            }
        }

        suspend fun runTask(): R {
            val block = runnable.block

            val scope = LiveTaskContextScope(taskContext = this)
            TaskBeginEvent(task, this).emit()
            val result = coroutineScope { scope.block() }

            /*
             * Ensure *before* sending the finish event. Even if the block
             * completed successfully, it is considered an error for a parent
             * block to complete while child tasks are still running.
             */
            ensureNoRunningChildren()

            TaskFinishEvent(task, this, result).emit()
            return result
        }

        suspend fun failTask(cause: Throwable): Nothing {
            this._failureCause = cause
            TaskFailEvent(task, this, cause).emit()
            throw cause
        }

        setup()
        return try {
            runTask()
        } catch (cause: Throwable) {
            failTask(cause)
        } finally {
            cleanup()
        }
    }

    private suspend fun <R> enter(
        parent: LiveTaskContext?,
        events: ProducerScope<TaskEvent>?,
        concurrentTaskBehavior: ConcurrentTaskBehavior,
        runnable: TaskRunnable<R>,
    ): R {
        contextLock.lock()
        try {
            this._parent = parent
            this._events = events
            this._concurrentTaskBehavior = concurrentTaskBehavior

            this.setEnteredFlag()
            this.enforceConcurrentBehavior()
            return this.start(runnable)
        } finally {
            contextLock.unlock()
        }
    }

    internal suspend fun <R> enter(
        events: ProducerScope<TaskEvent>?,
        concurrentTaskBehavior: ConcurrentTaskBehavior,
        runnable: TaskRunnable<R>,
    ): R = enter(
        parent = null,
        events = events,
        concurrentTaskBehavior = concurrentTaskBehavior,
        runnable = runnable,
    )

    internal suspend fun <R> enter(
        parent: LiveTaskContext,
        runnable: TaskRunnable<R>,
    ): R = enter(
        parent = parent,
        events = parent.events,
        concurrentTaskBehavior = parent.concurrentTaskBehavior,
        runnable = runnable,
    )

}

/**
 * Defines a scope for new tasks.
 *
 * @see LiveTaskContextScope
 */
public interface TaskContextScope {

    /**
     * The context of this scope.
     */
    public val taskContext: TaskContext

    /**
     * Shorthand for `taskContext.logger`.
     */
    public val taskLogger: TaskLogger
        get() = taskContext.logger

}

/**
 * The scope for a [LiveTaskContext].
 *
 * Every **task builder** (like [launchTask], [asyncTask], etc.) is
 * an extension on [LiveTaskContextScope] and inherits its context to
 * automatically propagate its elements and cancellation.
 */
public class LiveTaskContextScope
internal constructor(
    override val taskContext: LiveTaskContext
) : TaskContextScope
