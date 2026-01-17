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

import kotlinx.coroutines.*
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * A task that runs a [Job].
 *
 * Use this when you don't want to specify anything about the task
 * other than the fact it's a job from [kotlinx.coroutines].
 */
public sealed class JobTask : Task {

    override val name: String = javaClass.simpleName

    /**
     * A task job that's been started via [runBlocking].
     */
    public object RunBlocking : JobTask()

    /**
     * A task job that's been started via [launch].
     */
    public object Launch : JobTask()

    /**
     * A task job that's been started via [async].
     */
    public object Async : JobTask()

}

/**
 * Runs a coroutine task via [runBlocking].
 *
 * @param task The task to run the coroutine as.
 * @param block The coroutine code.
 */
public fun <R> runBlockingTask(
    task: Task = JobTask.RunBlocking,
    allowConcurrentTasks: Boolean = true,
    context: CoroutineContext = EmptyCoroutineContext,
    block: TaskRunnableBlock<R>,
): R = runBlocking(context) {
    LiveTaskContext().enter(
        events = null,
        allowConcurrentTasks = allowConcurrentTasks,
        runnable = task.runnable(block),
    )
}

/**
 * Runs a coroutine task via [runBlocking].
 *
 * @param name The name of the task to run the coroutine as.
 * @param block The coroutine code.
 */
public fun <R> runBlockingTask(
    name: String,
    allowConcurrentTasks: Boolean = true,
    context: CoroutineContext = EmptyCoroutineContext,
    block: TaskRunnableBlock<R>,
): R = runBlockingTask(
    task = NamedTask(name),
    allowConcurrentTasks = allowConcurrentTasks,
    context = context,
    block = block,
)

/**
 * Runs a coroutine task via [runBlocking].
 *
 * @param block The coroutine code.
 */
public fun <R> Task.runBlocking(
    allowConcurrentTasks: Boolean = true,
    context: CoroutineContext = EmptyCoroutineContext,
    block: TaskRunnableBlock<R>,
): R = runBlockingTask(
    task = this,
    allowConcurrentTasks = allowConcurrentTasks,
    context = context,
    block = block,
)

/**
 * Runs a coroutine task via [runBlocking].
 */
public fun <R> TaskRunnable<R>.runBlocking(
    allowConcurrentTasks: Boolean = true,
    context: CoroutineContext = EmptyCoroutineContext,
): R = runBlockingTask(
    task = task,
    allowConcurrentTasks = allowConcurrentTasks,
    context = context,
    block = block,
)

/**
 * Runs a coroutine task via [runBlocking].
 *
 * @param task The task to run the coroutine as.
 * @param block The coroutine code.
 */
context(taskScope: LiveTaskContextScope)
public fun <R> runBlockingTask(
    task: Task = JobTask.RunBlocking,
    context: CoroutineContext = EmptyCoroutineContext,
    block: TaskRunnableBlock<R>,
): R = runBlocking(context) {
    LiveTaskContext().enter(
        parent = taskScope.taskContext,
        runnable = task.runnable(block),
    )
}

/**
 * Runs a coroutine task via [runBlocking].
 *
 * @param name The name of the task to run the coroutine as.
 * @param block The coroutine code.
 */
context(_: LiveTaskContextScope)
public fun <R> runBlockingTask(
    name: String,
    context: CoroutineContext = EmptyCoroutineContext,
    block: TaskRunnableBlock<R>,
): R = runBlockingTask(
    task = NamedTask(name),
    context = context,
    block = block,
)

/**
 * Runs a coroutine task via [runBlocking].
 *
 * @param block The coroutine code.
 */
context(_: LiveTaskContextScope)
public fun <R> Task.runBlocking(
    context: CoroutineContext = EmptyCoroutineContext,
    block: TaskRunnableBlock<R>,
): R = runBlockingTask(
    task = this,
    context = context,
    block = block,
)

/**
 * Runs a coroutine task via [runBlocking].
 */
context(_: LiveTaskContextScope)
public fun <R> TaskRunnable<R>.runBlocking(
    context: CoroutineContext = EmptyCoroutineContext,
): R = runBlockingTask(
    task = task,
    context = context,
    block = block,
)

/**
 * Creates a coroutine task via [CoroutineScope.launch].
 *
 * @param task The task to run the coroutine as.
 * @param block The coroutine code.
 */
context(coroutineScope: CoroutineScope, taskScope: LiveTaskContextScope)
public suspend fun launchTask(
    task: Task = JobTask.Launch,
    context: CoroutineContext = EmptyCoroutineContext,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    block: TaskRunnableBlock<Unit>,
): Job {
    val job = coroutineScope.launch(context, start) {
        LiveTaskContext().enter(
            parent = taskScope.taskContext,
            runnable = task.runnable(block),
        )
    }
    yield() /* give parent time to process */
    return job
}

/**
 * Creates a coroutine task via [CoroutineScope.launch].
 *
 * @param name The name of the task to run the coroutine as.
 * @param block The coroutine code.
 */
context(_: CoroutineScope, _: LiveTaskContextScope)
public suspend fun launchTask(
    name: String,
    context: CoroutineContext = EmptyCoroutineContext,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    block: TaskRunnableBlock<Unit>,
): Job = launchTask(
    task = NamedTask(name),
    context = context,
    start = start,
    block = block,
)

/**
 * Creates a coroutine task via [CoroutineScope.launch].
 *
 * @param block The coroutine code.
 */
context(_: CoroutineScope, _: LiveTaskContextScope)
public suspend fun Task.launch(
    context: CoroutineContext = EmptyCoroutineContext,
    block: TaskRunnableBlock<Unit>,
): Job = launchTask(
    task = this,
    context = context,
    block = block,
)

/**
 * Creates a coroutine task via [CoroutineScope.launch].
 */
context(_: CoroutineScope, _: LiveTaskContextScope)
public suspend fun TaskRunnable<Unit>.launch(
    context: CoroutineContext = EmptyCoroutineContext,
): Job = launchTask(
    task = task,
    context = context,
    block = block,
)

/**
 * Creates a coroutine task via [CoroutineScope.async].
 *
 * @param task The task to run the coroutine as.
 * @param block The coroutine code.
 */
context(coroutineScope: CoroutineScope, taskScope: LiveTaskContextScope)
public suspend fun <R> asyncTask(
    task: Task = JobTask.Async,
    context: CoroutineContext = EmptyCoroutineContext,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    block: TaskRunnableBlock<R>,
): Deferred<R> {
    val deferred = coroutineScope.async(context, start) {
        LiveTaskContext().enter(
            parent = taskScope.taskContext,
            runnable = task.runnable(block),
        )
    }
    yield() /* give parent time to process */
    return deferred
}

/**
 * Creates a coroutine task via [CoroutineScope.async].
 *
 * @param name The name of the task to run the coroutine as.
 * @param block The coroutine code.
 */
context(_: CoroutineScope, _: LiveTaskContextScope)
public suspend fun <R> asyncTask(
    name: String,
    context: CoroutineContext = EmptyCoroutineContext,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    block: TaskRunnableBlock<R>,
): Deferred<R> = asyncTask(
    task = NamedTask(name),
    context = context,
    start = start,
    block = block,
)

/**
 * Creates a coroutine task via [CoroutineScope.async].
 *
 * @param block The coroutine code.
 */
context(_: CoroutineScope, _: LiveTaskContextScope)
public suspend fun <R> Task.async(
    context: CoroutineContext = EmptyCoroutineContext,
    block: TaskRunnableBlock<R>,
): Deferred<R> = asyncTask(
    task = this,
    context = context,
    block = block,
)

/**
 * Creates a coroutine task via [CoroutineScope.async].
 */
context(_: CoroutineScope, _: LiveTaskContextScope)
public suspend fun <R> TaskRunnable<R>.async(
    context: CoroutineContext = EmptyCoroutineContext,
): Deferred<R> = asyncTask(
    task = task,
    context = context,
    block = block,
)
