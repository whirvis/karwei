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

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.onEach
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.reflect.KProperty

/**
 * Used to store the results from [taskFlow].
 */
public class TaskFlowResult<R : Any> {

    private val lock = ReentrantReadWriteLock()

    internal var computedResult = false
        private set

    internal var value: R? = null
        get() = lock.read {
            if (!computedResult) {
                val message = "No result computed for task"
                throw NoSuchElementException(message)
            }
            return field
        }
        set(value) = lock.write {
            if (computedResult) {
                val message = "Result already computed for task"
                throw NoSuchElementException(message)
            }
            this.computedResult = true
            field = value
        }

    /**
     * Returns the computed value.
     *
     * @throws NoSuchElementException If the result has not been computed.
     */
    public fun get(): R = this.value!!

    /**
     * Returns the computed value.
     *
     * @throws NoSuchElementException If the result has not been computed.
     */
    public operator fun getValue(
        thisRef: Any?, property: KProperty<*>,
    ): R = this.value!!

}

private fun <R> TaskRunnable<R>.baseTaskFlow(
    concurrentTaskBehavior: ConcurrentTaskBehavior,
): Flow<TaskEvent> = channelFlow {
    LiveTaskContext().enter(
        events = this@channelFlow,
        concurrentTaskBehavior = concurrentTaskBehavior,
        runnable = this@baseTaskFlow,
    )
}

@Suppress("UNCHECKED_CAST")
private fun <S : Any, R : S> TaskRunnable<R>.processEvent(
    it: TaskEvent, result: TaskFlowResult<S>,
) {
    if (it.task == task && it is TaskFinishEvent) {
        result.value = it.result as R
    }
}

/**
 * Creates an instance of a _cold_ [Flow] that emits events related
 * to the task and its subtask's progress.
 *
 * Like other cold flows, the runnable is called every time a terminal
 * operator is applied to the resulting flow. This means the value stored
 * in [result] will be overwritten on subsequent invocations.
 *
 * @param result Where to write the result to.
 * @param concurrentTaskBehavior What should occur when a sibling task
 * starts before the other is finished.
 * @throws IllegalArgumentException If [result] already has a value.
 */
public fun <S : Any, R : S> TaskRunnable<R>.taskFlow(
    result: TaskFlowResult<S>?,
    concurrentTaskBehavior: ConcurrentTaskBehavior =
        ConcurrentTaskBehavior.IGNORE,
): Flow<TaskEvent> {
    if (result?.computedResult == true) {
        val message = "Result already has a value"
        throw IllegalArgumentException(message)
    }
    val flow = baseTaskFlow(concurrentTaskBehavior)
    return if (result == null) flow
    else flow.onEach { processEvent(it, result) }
}

/**
 * Creates an instance of a _cold_ [Flow] that emits events related
 * to the task and its subtask's progress.
 *
 * Like other cold flows, the runnable is called every time a terminal
 * operator is applied to the resulting flow.
 *
 * @param concurrentTaskBehavior What should occur when a sibling task
 * starts before the other is finished.
 */
public fun TaskRunnable<Unit>.taskFlow(
    concurrentTaskBehavior: ConcurrentTaskBehavior =
        ConcurrentTaskBehavior.IGNORE,
): Flow<TaskEvent> = baseTaskFlow(concurrentTaskBehavior)
