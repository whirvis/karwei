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
public sealed class JobTask : NamedTask {

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
public fun <T : Task, R> runBlockingTask(
    task: T,
    context: CoroutineContext = EmptyCoroutineContext,
    block: TaskRunnableBlock<T, R>,
): R = runBlocking(context) {
    LiveTaskContext<T>().enter(
        events = null,
        runnable = task.runnable(block),
    )
}

/**
 * Runs a coroutine task via [runBlocking].
 *
 * @param task The task to run the coroutine as.
 * @param block The coroutine code.
 */
public fun <R> runBlockingTask(
    context: CoroutineContext = EmptyCoroutineContext,
    block: TaskRunnableBlock<Task, R>,
): R = runBlockingTask(
    task = JobTask.RunBlocking,
    context = context,
    block = block,
)

/**
 * Runs a coroutine task via [runBlocking].
 *
 * @param name The name of the task to run the coroutine as.
 * @param block The coroutine code.
 */
public fun <R> runBlockingTask(
    name: String,
    context: CoroutineContext = EmptyCoroutineContext,
    block: TaskRunnableBlock<NamedTask, R>,
): R = runBlockingTask(
    task = BareNamedTask(name),
    context = context,
    block = block,
)

/**
 * Runs a coroutine task via [runBlocking].
 *
 * @param block The coroutine code.
 */
public fun <T : Task, R> T.runBlocking(
    context: CoroutineContext = EmptyCoroutineContext,
    block: TaskRunnableBlock<T, R>,
): R = runBlockingTask(
    task = this,
    context = context,
    block = block,
)

/**
 * Runs a coroutine task via [runBlocking].
 */
public fun <T : Task, R> TaskRunnable<T, R>.runBlocking(
    context: CoroutineContext = EmptyCoroutineContext,
): R = runBlockingTask(
    task = task,
    context = context,
    block = block,
)

context(coroutineScope: CoroutineScope)
private suspend fun <T : Task, R> T.enterContext(
    block: TaskRunnableBlock<T, R>,
): R {
    val coroutineContext = coroutineScope.coroutineContext

    /*
     * Attempt to obtain the LiveTaskContextElement associated
     * with the current CoroutineScope. If there isn't any, then
     * it means there's no parent task.
     */
    val taskContextElement =
        coroutineContext[LiveTaskContextElement]
            ?: return LiveTaskContext<T>().enter(
                events = null, /* no events to broadcast */
                runnable = this.runnable(block),
            )

    return LiveTaskContext<T>().enter(
        parent = taskContextElement.taskContext,
        runnable = this.runnable(block),
    )
}

/**
 * Runs a coroutine task via [runBlocking].
 *
 * **Note:** To be consistent with coroutines API, this method does
 * not inherit the entire context from the outer [CoroutineScope].
 * However, the new scope created by this method *will* inherit task
 * context elements to preserve the task hierarchy (if any).
 *
 * @param task The task to run the coroutine as.
 * @param block The coroutine code.
 */
context(coroutineScope: CoroutineScope)
public fun <T : Task, R> runBlockingTask(
    task: T,
    context: CoroutineContext = EmptyCoroutineContext,
    block: TaskRunnableBlock<T, R>,
): R = runBlocking(context) {
    val taskContextElement = coroutineScope
        .coroutineContext[LiveTaskContextElement]
        ?: EmptyCoroutineContext

    withContext(context = taskContextElement) {
        task.enterContext(block)
    }
}

/**
 * Runs a coroutine task via [runBlocking].
 *
 * **Note:** To be consistent with coroutines API, this method does
 * not inherit the entire context from the outer [CoroutineScope].
 * However, the new scope created by this method *will* inherit task
 * context elements to preserve the task hierarchy (if any).
 *
 * @param block The coroutine code.
 */
context(_: CoroutineScope)
public fun <R> runBlockingTask(
    context: CoroutineContext = EmptyCoroutineContext,
    block: TaskRunnableBlock<Task, R>,
): R = runBlockingTask(
    task = JobTask.RunBlocking,
    context = context,
    block = block,
)

/**
 * Runs a coroutine task via [runBlocking].
 *
 * **Note:** To be consistent with coroutines API, this method does
 * not inherit the entire context from the outer [CoroutineScope].
 * However, the new scope created by this method *will* inherit task
 * context elements to preserve the task hierarchy (if any).
 *
 * @param name The name of the task to run the coroutine as.
 * @param block The coroutine code.
 */
context(_: CoroutineScope)
public fun <R> runBlockingTask(
    name: String,
    context: CoroutineContext = EmptyCoroutineContext,
    block: TaskRunnableBlock<NamedTask, R>,
): R = runBlockingTask(
    task = BareNamedTask(name),
    context = context,
    block = block,
)

/**
 * Runs a coroutine task via [runBlocking].
 *
 * **Note:** To be consistent with coroutines API, this method does
 * not inherit the entire context from the outer [CoroutineScope].
 * However, the new scope created by this method *will* inherit task
 * context elements to preserve the task hierarchy (if any).
 *
 * @param block The coroutine code.
 */
context(_: CoroutineScope)
public fun <T : Task, R> T.runBlocking(
    context: CoroutineContext = EmptyCoroutineContext,
    block: TaskRunnableBlock<T, R>,
): R = runBlockingTask(
    task = this,
    context = context,
    block = block,
)

/**
 * Runs a coroutine task via [runBlocking].
 *
 * **Note:** To be consistent with coroutines API, this method does
 * not inherit the entire context from the outer [CoroutineScope].
 * However, the new scope created by this method *will* inherit task
 * context elements to preserve the task hierarchy (if any).
 */
context(_: CoroutineScope)
public fun <T : Task, R> TaskRunnable<T, R>.runBlocking(
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
context(coroutineScope: CoroutineScope)
public suspend fun <T : Task> launchTask(
    task: T,
    context: CoroutineContext = EmptyCoroutineContext,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    block: TaskRunnableBlock<T, Unit>,
): Job {
    val job = coroutineScope.launch(context, start) {
        task.enterContext(block)
    }
    yield() /* give parent time to process */
    return job
}

/**
 * Creates a coroutine task via [CoroutineScope.launch].
 *
 * @param block The coroutine code.
 */
context(_: CoroutineScope)
public suspend fun launchTask(
    context: CoroutineContext = EmptyCoroutineContext,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    block: TaskRunnableBlock<Task, Unit>,
): Job = launchTask(
    task = JobTask.Launch,
    context = context,
    start = start,
    block = block,
)

/**
 * Creates a coroutine task via [CoroutineScope.launch].
 *
 * @param name The name of the task to run the coroutine as.
 * @param block The coroutine code.
 */
context(_: CoroutineScope)
public suspend fun launchTask(
    name: String,
    context: CoroutineContext = EmptyCoroutineContext,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    block: TaskRunnableBlock<NamedTask, Unit>,
): Job = launchTask(
    task = BareNamedTask(name),
    context = context,
    start = start,
    block = block,
)

/**
 * Creates a coroutine task via [CoroutineScope.launch].
 *
 * @param block The coroutine code.
 */
context(_: CoroutineScope)
public suspend fun <T: Task> T.launch(
    context: CoroutineContext = EmptyCoroutineContext,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    block: TaskRunnableBlock<T, Unit>,
): Job = launchTask(
    task = this,
    context = context,
    start = start,
    block = block,
)

/**
 * Creates a coroutine task via [CoroutineScope.launch].
 */
context(_: CoroutineScope)
public suspend fun <T: Task> TaskRunnable<T, Unit>.launch(
    context: CoroutineContext = EmptyCoroutineContext,
    start: CoroutineStart = CoroutineStart.DEFAULT,
): Job = launchTask(
    task = task,
    context = context,
    start = start,
    block = block,
)

/**
 * Creates a coroutine task via [CoroutineScope.async].
 *
 * @param task The task to run the coroutine as.
 * @param block The coroutine code.
 */
context(coroutineScope: CoroutineScope)
public suspend fun <T : Task, R> asyncTask(
    task: T,
    context: CoroutineContext = EmptyCoroutineContext,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    block: TaskRunnableBlock<T, R>,
): Deferred<R> {
    val deferred = coroutineScope.async(context, start) {
        task.enterContext(block)
    }
    yield() /* give parent time to process */
    return deferred
}

/**
 * Creates a coroutine task via [CoroutineScope.async].
 *
 * @param block The coroutine code.
 */
context(_: CoroutineScope)
public suspend fun <R> asyncTask(
    context: CoroutineContext = EmptyCoroutineContext,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    block: TaskRunnableBlock<Task, R>,
): Deferred<R> = asyncTask(
    task = JobTask.Async,
    context = context,
    start = start,
    block = block,
)

/**
 * Creates a coroutine task via [CoroutineScope.async].
 *
 * @param name The name of the task to run the coroutine as.
 * @param block The coroutine code.
 */
context(_: CoroutineScope)
public suspend fun <R> asyncTask(
    name: String,
    context: CoroutineContext = EmptyCoroutineContext,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    block: TaskRunnableBlock<NamedTask, R>,
): Deferred<R> = asyncTask(
    task = BareNamedTask(name),
    context = context,
    start = start,
    block = block,
)

/**
 * Creates a coroutine task via [CoroutineScope.async].
 *
 * @param block The coroutine code.
 */
context(_: CoroutineScope)
public suspend fun <T: Task, R> T.async(
    context: CoroutineContext = EmptyCoroutineContext,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    block: TaskRunnableBlock<T, R>,
): Deferred<R> = asyncTask(
    task = this,
    context = context,
    start = start,
    block = block,
)

/**
 * Creates a coroutine task via [CoroutineScope.async].
 */
context(_: CoroutineScope)
public suspend fun <T : Task, R> TaskRunnable<T, R>.async(
    context: CoroutineContext = EmptyCoroutineContext,
    start: CoroutineStart = CoroutineStart.DEFAULT,
): Deferred<R> = asyncTask(
    task = task,
    context = context,
    start = start,
    block = block,
)
