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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.jetbrains.annotations.VisibleForTesting
import java.util.concurrent.locks.ReentrantLock
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * Signals that a task concurrency error has occurred.
 *
 * The most common concurrency error is a parent task finishing before
 * all of its children are finished.
 *
 * @param task The task that caused the exception.
 */
public class ConcurrentTaskException(
    task: Task,
    override val message: String?
) : TaskException(task)

private fun TaskContext<*>.getLevel(): Int {
    var level = 0
    var current: TaskContext<*>? = parent
    while (current != null) {
        level += 1
        current = current.parent
    }
    return level
}

private fun List<TaskContext<*>>.names() = joinToString {
    "\"${it.task.getNameOrElse()}\""
}

/**
 * Persistent context for a [Task].
 */
public interface TaskContext<T : Task> {

    /**
     * Returns the task being run in this context.
     *
     * @throws IllegalStateException If no task is currently running.
     */
    public val task: T

    /**
     * Returns the parent of the current task.
     *
     * This will be `null` if the task has no parent or has completed.
     *
     * Accesses to this property are not idempotent, it will become
     * `null` once the task transitions to its final state and all its
     * [children] have completed.
     */
    public val parent: TaskContext<*>?

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
    public val children: List<TaskContext<*>>

    /**
     * The logger for this task.
     */
    public val logger: TaskLogger

}

private fun LiveTaskContext<*>.getRoot(): LiveTaskContext<*> {
    return parent?.getRoot() ?: this
}

@VisibleForTesting
internal fun LiveTaskContext<*>.toStatic(): StaticTaskContext {
    var captured: StaticTaskContext? = null

    /*
     * We start from the root context and work our way down. If we
     * don't do this, the parent context (if any) won't be converted
     * to a static context which is not what we want.
     *
     * The callback lets us know when the target context is actually
     * converted, and gives us the instance directly so we can return
     * it to the caller. This way, we don't need to traverse our way
     * down the root static context to locate the target ourselves.
     */
    StaticTaskContext(
        context = this.getRoot(),
    ) { context, converted ->
        if (context !== this) {
            return@StaticTaskContext /* nothing to do */
        } else if (captured != null) {
            val msg = "Context already converted, this is a bug!"
            throw IllegalStateException(msg)
        }
        captured = converted
    }

    if (captured == null) {
        val msg = "Context never converted, this is a bug!"
        throw IllegalStateException(msg)
    }

    return captured
}

private fun List<LiveTaskContext<*>>.toStatic(
    onConvert: (LiveTaskContext<*>, StaticTaskContext) -> Unit,
) = map { context ->
    StaticTaskContext(context, onConvert)
}

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
) : TaskContext<Task> {

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
        context: LiveTaskContext<*>,
        onConvert: (LiveTaskContext<*>, StaticTaskContext) -> Unit,
    ) : this(
        task = context.task,
        level = context.level,
        isActive = context.isActive,
        isCompleted = context.isCompleted,
        isFailed = context.isFailed,
        failureCause = context.failureCause,
        children = context.children.toStatic(onConvert),
    ) {
        children.forEach { it._parent = this }
        this._logger = StaticTaskLogger(context = this)
        onConvert(context, this)
    }

}

/**
 * Current context for a [Task].
 */
public class LiveTaskContext<T : Task>
internal constructor() : TaskContext<T> {

    private val contextLock = ReentrantLock()
    private var contextEntered = false

    private var _events: ProducerScope<TaskEvent>? = null

    private var _task: T? = null
    private var _parent: LiveTaskContext<*>? = null
    private var _isActive: Boolean = false
    private var _isCompleted: Boolean = false
    private var _failureCause: Throwable? = null
    private val _children = mutableListOf<LiveTaskContext<*>>()
    private val _logger = LiveTaskLogger(this)

    internal val events: ProducerScope<TaskEvent>?
        get() = this._events

    override val task: T
        get() {
            val field = this._task
            if (field == null) {
                val message = "No task currently running"
                throw IllegalStateException(message)
            }
            return field
        }

    override val parent: LiveTaskContext<*>?
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

    override val children: List<LiveTaskContext<*>>
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

    private fun ensureLivingParent() {
        if (parent?.isActive == false) {
            val msg = "Cannot enter context with dead parent"
            throw ConcurrentTaskException(task, msg)
        }
    }

    private suspend fun <R> start(
        runnable: TaskRunnable<T, R>,
    ): R {
        fun setup() {
            this._isActive = true
            this._isCompleted = false
            parent?._children?.add(this)
        }

        fun cleanup() {
            parent?._children?.remove(this)
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
            val result = withContext(LiveTaskContextElement(this)) {
                scope.block()
            }

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
        parent: LiveTaskContext<*>?,
        events: ProducerScope<TaskEvent>?,
        runnable: TaskRunnable<T, R>,
    ): R {
        contextLock.lock()
        try {
            this.setEnteredFlag()

            this._task = runnable.task
            this._parent = parent
            this._events = events

            this.ensureLivingParent()
            return this.start(runnable)
        } finally {
            contextLock.unlock()
        }
    }

    internal suspend fun <R> enter(
        events: ProducerScope<TaskEvent>?,
        runnable: TaskRunnable<T, R>,
    ): R = enter(
        parent = null,
        events = events,
        runnable = runnable,
    )

    internal suspend fun <R> enter(
        parent: LiveTaskContext<*>,
        runnable: TaskRunnable<T, R>,
    ): R = enter(
        parent = parent,
        events = parent.events,
        runnable = runnable,
    )

}

private val TaskContext<*>.taskNameOrDead
    get() = if (isActive) task.getNameOrElse() else "<dead task>"

internal class LiveTaskContextElement(
    val taskContext: LiveTaskContext<*>,
) : AbstractCoroutineContextElement(key = LiveTaskContextElement) {

    companion object Key :
        CoroutineContext.Key<LiveTaskContextElement>

    override fun toString(): String =
        "LiveTaskContextElement(${taskContext.taskNameOrDead})"

}

/**
 * Defines a scope for new tasks.
 *
 * @see LiveTaskContextScope
 */
public interface TaskContextScope<T : Task> {

    /**
     * The context of this scope.
     */
    public val taskContext: TaskContext<T>

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
public class LiveTaskContextScope<T : Task>
internal constructor(
    override val taskContext: LiveTaskContext<T>
) : TaskContextScope<T>
