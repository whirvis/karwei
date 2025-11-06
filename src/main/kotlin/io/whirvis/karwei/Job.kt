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

import kotlinx.coroutines.*
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Runs a coroutine task via [runBlocking].
 *
 * @param task The task to run the coroutine as.
 * @param block The coroutine code.
 */
public fun <R> runBlockingTask(
    task: Task,
    concurrentTaskBehavior: ConcurrentTaskBehavior =
        ConcurrentTaskBehavior.IGNORE,
    context: CoroutineContext = EmptyCoroutineContext,
    block: suspend LiveTaskContextScope.() -> R,
): R = runBlocking(context) {
    LiveTaskContext().enter(
        events = null,
        concurrentTaskBehavior = concurrentTaskBehavior,
        runnable = task.runnable(block),
    )
}

/**
 * Runs a coroutine task via [runBlocking].
 *
 * @param task The name of the task to run the coroutine as.
 * @param block The coroutine code.
 */
public fun <R> runBlockingTask(
    name: String,
    concurrentTaskBehavior: ConcurrentTaskBehavior =
        ConcurrentTaskBehavior.IGNORE,
    context: CoroutineContext = EmptyCoroutineContext,
    block: suspend LiveTaskContextScope.() -> R,
): R = runBlockingTask(
    task = NamedTask(name),
    concurrentTaskBehavior = concurrentTaskBehavior,
    context = context,
    block = block,
)

/**
 * Runs a coroutine task via [runBlocking].
 *
 * @param block The coroutine code.
 */
public fun <R> Task.runBlocking(
    concurrentTaskBehavior: ConcurrentTaskBehavior =
        ConcurrentTaskBehavior.IGNORE,
    context: CoroutineContext = EmptyCoroutineContext,
    block: suspend LiveTaskContextScope.() -> R,
): R = runBlockingTask(
    task = this,
    concurrentTaskBehavior = concurrentTaskBehavior,
    context = context,
    block = block,
)

/**
 * Runs a coroutine task via [runBlocking].
 */
public fun <R> TaskRunnable<R>.runBlocking(
    concurrentTaskBehavior: ConcurrentTaskBehavior =
        ConcurrentTaskBehavior.IGNORE,
    context: CoroutineContext = EmptyCoroutineContext,
): R = runBlockingTask(
    task = task,
    concurrentTaskBehavior = concurrentTaskBehavior,
    context = context,
    block = block,
)

/**
 * Runs a coroutine task via [runBlocking].
 *
 * @param task The task to run the coroutine as.
 * @param block The coroutine code.
 */
public fun <R> LiveTaskContextScope.runBlockingTask(
    task: Task,
    context: CoroutineContext = EmptyCoroutineContext,
    block: suspend LiveTaskContextScope.() -> R,
): R = runBlocking(context) {
    LiveTaskContext().enter(
        parent = this@runBlockingTask.taskContext,
        runnable = task.runnable(block),
    )
}

/**
 * Runs a coroutine task via [runBlocking].
 *
 * @param task The name of the task to run the coroutine as.
 * @param block The coroutine code.
 */
public fun <R> LiveTaskContextScope.runBlockingTask(
    name: String,
    context: CoroutineContext = EmptyCoroutineContext,
    block: suspend LiveTaskContextScope.() -> R,
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
context(taskScope: LiveTaskContextScope)
public fun <R> Task.runBlocking(
    context: CoroutineContext = EmptyCoroutineContext,
    block: suspend LiveTaskContextScope.() -> R,
): R = taskScope.runBlockingTask(
    task = this,
    context = context,
    block = block,
)

/**
 * Runs a coroutine task via [runBlocking].
 */
context(taskScope: LiveTaskContextScope)
public fun <R> TaskRunnable<R>.runBlocking(
    context: CoroutineContext = EmptyCoroutineContext,
): R = taskScope.runBlockingTask(
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
context(taskScope: LiveTaskContextScope)
public fun CoroutineScope.launchTask(
    task: Task,
    context: CoroutineContext = EmptyCoroutineContext,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    block: suspend LiveTaskContextScope.() -> Unit,
): Job = launch(context, start) {
    LiveTaskContext().enter(
        parent = taskScope.taskContext,
        runnable = task.runnable(block),
    )
}

/**
 * Creates a coroutine task via [CoroutineScope.launch].
 *
 * @param task The name of the task to run the coroutine as.
 * @param block The coroutine code.
 */
context(taskScope: LiveTaskContextScope)
public fun CoroutineScope.launchTask(
    name: String,
    context: CoroutineContext = EmptyCoroutineContext,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    block: suspend LiveTaskContextScope.() -> Unit,
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
context(_: LiveTaskContextScope, coroutineScope: CoroutineScope)
public fun Task.launch(
    context: CoroutineContext = EmptyCoroutineContext,
    block: suspend LiveTaskContextScope.() -> Unit,
): Job = coroutineScope.launchTask(
    task = this,
    context = context,
    block = block,
)

/**
 * Creates a coroutine task via [CoroutineScope.launch].
 */
context(_: LiveTaskContextScope, coroutineScope: CoroutineScope)
public fun TaskRunnable<Unit>.launch(
    context: CoroutineContext = EmptyCoroutineContext,
): Job = coroutineScope.launchTask(
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
context(taskScope: LiveTaskContextScope)
public fun <R> CoroutineScope.asyncTask(
    task: Task,
    context: CoroutineContext = EmptyCoroutineContext,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    block: suspend LiveTaskContextScope.() -> R,
): Deferred<R> = async(context, start) {
    LiveTaskContext().enter(
        parent = taskScope.taskContext,
        runnable = task.runnable(block),
    )
}

/**
 * Creates a coroutine task via [CoroutineScope.async].
 *
 * @param task The name of the task to run the coroutine as.
 * @param block The coroutine code.
 */
context(taskScope: LiveTaskContextScope)
public fun <R> CoroutineScope.asyncTask(
    name: String,
    context: CoroutineContext = EmptyCoroutineContext,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    block: suspend LiveTaskContextScope.() -> R,
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
context(_: LiveTaskContextScope, coroutineScope: CoroutineScope)
public fun <R> Task.async(
    context: CoroutineContext = EmptyCoroutineContext,
    block: suspend LiveTaskContextScope.() -> R,
): Job = coroutineScope.asyncTask(
    task = this,
    context = context,
    block = block,
)

/**
 * Creates a coroutine task via [CoroutineScope.async].
 */
context(_: LiveTaskContextScope, coroutineScope: CoroutineScope)
public fun <R> TaskRunnable<R>.async(
    context: CoroutineContext = EmptyCoroutineContext,
): Job = coroutineScope.asyncTask(
    task = task,
    context = context,
    block = block,
)
