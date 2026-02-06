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

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.jupiter.api.assertDoesNotThrow
import kotlin.concurrent.thread
import kotlin.test.*

private val TEST_TASK = task()

private suspend fun yieldWhile(block: suspend () -> Boolean) {
    while (block()) yield()
}

private suspend fun yieldUntil(block: suspend () -> Boolean) {
    while (!block()) yield()
}

private fun sleepUntil(block: () -> Boolean) {
    while (!block()) Thread.sleep(0)
}

internal class ContextTest {

    @Test
    fun exceptionsExtendTaskException() {
        assertIs<TaskException>(ConcurrentTaskException(TEST_TASK, ""))
    }

    @Test
    fun staticContextCapturesLiveContextCorrectly() {
        runBlockingTask {
            taskContext.let {
                val copy = it.toStatic()
                assertSame(copy.task, it.task)
                /* assertEquals(copy.parent, it.parent) */
                assertEquals(copy.level, it.level)
                assertEquals(copy.isActive, it.isActive)
                assertEquals(copy.isCompleted, it.isCompleted)
                assertEquals(copy.isFailed, it.isFailed)
                assertEquals(copy.failureCause, it.failureCause)
                /* assertContentEquals(copy.children, it.children) */
                /* assertEquals(copy.logger, it.logger) */
            }
        }
    }

    @Test
    fun staticContextHasStaticParent() {
        val context = TEST_TASK.runBlocking {
            runBlockingTask { taskContext.toStatic() }
        }
        assertIs<StaticTaskContext>(context.parent)
    }

    @Test
    fun staticContextCapturesChildren() {
        val context = runBlockingTask {
            var capturing = true
            var counter = 0

            launchTask {
                launchTask {
                    counter += 1
                    yieldWhile { capturing }
                }
                launchTask {
                    counter += 1
                    yieldWhile { capturing }
                }
                counter += 1
                yieldWhile { capturing }
            }

            launchTask {
                launchTask {
                    counter += 1
                    yieldWhile { capturing }
                }
                counter += 1
                yieldWhile { capturing }
            }
            launchTask {
                counter += 1
                yieldWhile { capturing }
            }

            yieldUntil { counter >= 6 }

            val context = taskContext.toStatic()
            capturing = false
            return@runBlockingTask context
        }

        assertEquals(3, context.children.size)
        assertEquals(2, context.children[0].children.size)
        assertEquals(1, context.children[1].children.size)
        assertEquals(0, context.children[2].children.size)
    }

    @Test
    fun staticContextUsesStaticLogger() {
        val context = runBlockingTask { taskContext.toStatic() }
        assertIs<StaticTaskLogger>(context.logger)
    }

    @Test
    fun staticContextThrowsOnLog() {
        val logger = runBlockingTask {
            taskContext.toStatic().logger
        }
        assertFailsWith<IllegalStateException> {
            runBlocking { logger.info {} }
        }
    }

    @Test
    fun liveContextRepresentsCorrectTask() {
        val task = TEST_TASK.runBlocking { taskContext.task }
        assertSame(TEST_TASK, task)
    }

    @Test
    fun liveContextThrowsOnTaskGetAfterFinish() {
        val context = TEST_TASK.runBlocking { taskContext }
        assertFailsWith<IllegalStateException> { context.task }
    }

    @Test
    fun liveContextParentIsNullForParentTask() {
        val parent = runBlockingTask { taskContext.parent }
        assertNull(parent)
    }

    @Test
    fun liveContextParentIsCorrect() {
        val expectedParent = task()
        val actualParent = expectedParent.runBlocking {
            runBlockingTask { taskContext.parent?.task }
        }
        assertSame(expectedParent, actualParent)
    }

    @Test
    fun liveContextParentIsNullAfterCompletion() {
        val context = runBlockingTask {
            runBlockingTask { taskContext }
        }
        assertNull(context.parent)
    }

    @Test
    fun liveContextLevelIsAccurate() {
        val levels = mutableListOf<Int>()
        runBlockingTask {
            levels += taskContext.level
            runBlockingTask {
                levels += taskContext.level
                runBlockingTask { levels += taskContext.level }
            }
        }
        assertContentEquals(listOf(0, 1, 2), levels)
    }

    @Test
    fun liveContextLevelIsZeroAfterCompletion() {
        val contexts = mutableListOf<TaskContext<*>>()
        runBlockingTask {
            contexts += taskContext
            runBlockingTask {
                contexts += taskContext
                runBlockingTask { contexts += taskContext }
            }
        }
        val levels = contexts.map { it.level }
        assertContentEquals(listOf(0, 0, 0), levels)
    }

    @Test
    fun liveContextIsActiveDuringExecution() {
        val active = runBlockingTask { taskContext.isActive }
        assertTrue { active }
    }

    @Test
    fun liveContextIsNotActiveAfterExecution() {
        val context = runBlockingTask { taskContext }
        assertFalse { context.isActive }
    }

    @Test
    fun liveContextIsNotCompleteDuringExecution() {
        val completed = runBlockingTask { taskContext.isCompleted }
        assertFalse { completed }
    }

    @Test
    fun liveContextIsCompleteAfterExecution() {
        val context = runBlockingTask { taskContext }
        assertTrue { context.isCompleted }
    }

    @Test
    fun liveContextIsNotFailedBeforeException() {
        val context = runBlockingTask { taskContext }
        assertFalse { context.isFailed }
    }

    @Test
    fun liveContextHasNoFailureCauseBeforeException() {
        val context = runBlockingTask { taskContext }
        assertNull(context.failureCause)
    }

    @Test
    fun liveContextIsFailedAfterException() {
        lateinit var context: TaskContext<*>

        @Suppress("AssignedValueIsNeverRead")
        try {
            runBlockingTask {
                context = taskContext
                throw RuntimeException()
            }
        } catch (_: Exception) {
            /* ignore */
        }

        assertTrue { context.isFailed }
    }

    @Test
    fun liveContextHasFailureCauseAfterException() {
        val exception = RuntimeException()
        lateinit var context: TaskContext<*>

        @Suppress("AssignedValueIsNeverRead")
        try {
            runBlockingTask {
                context = taskContext
                throw exception
            }
        } catch (_: Exception) {
            /* ignore */
        }

        /*
         * For some reason, when the exception is thrown, it seems to wrap
         * itself and become a different instance of the same exception.
         * But, the cause of the exception instance is the same instance of
         * the original exception.
         */
        assertSame(exception, context.failureCause!!.cause)
    }

    @Test
    fun liveContextChildrenReturnsCopy() {
        /*
         * We have to launch two tasks here because if the child list
         * is empty, Kotlin's toList() will return the same instance of
         * an empty list (presumably for optimization reasons).
         */
        runBlockingTask {
            var checking = true
            launchTask { yieldWhile { checking } }
            assertNotSame(taskContext.children, taskContext.children)
            checking = false
        }
    }

    @Test
    fun liveContextAddsItselfAsChildToParentOnStart() {
        val expectedTasks = listOf(task(), task(), task())

        val actualTasks = runBlockingTask {
            var mapping = true
            expectedTasks.forEach { it.launch { yieldWhile { mapping } } }
            val tasks = taskContext.children.map { it.task }
            mapping = false
            return@runBlockingTask tasks
        }

        assertContentEquals(expectedTasks, actualTasks)
    }

    @Test
    fun liveContextRemovesItselfAsChildFromParentOnCompletion() {
        runBlockingTask {
            var checking = true
            val job = launchTask { yieldWhile { checking } }
            assertEquals(1, taskContext.children.size)
            checking = false
            job.join()
            assertEquals(0, taskContext.children.size)
        }
    }

    @Test
    fun liveContextLoggerEmitsEvents() {
        val message = "Hello, world!"

        val logRunnable = TEST_TASK {
            taskLogger.info { message }
        }

        val event = runBlocking {
            logRunnable
                .taskFlow()
                .filterIsInstance<TaskLogEvent>()
                .first()
        }

        assertSame(TEST_TASK, event.task)
        assertSame(TaskLogLevel.Info, event.level)
        assertEquals(message, event.message())
    }

    @Test
    fun liveContextLoggerThrowsAfterExit() {
        val logger = runBlockingTask {
            taskContext.logger
        }
        assertFailsWith<IllegalStateException> {
            runBlocking { logger.info {} }
        }
    }

    @Test
    fun liveContextThrowsOnSecondEntry() {
        val context = LiveTaskContext<Task>()

        assertDoesNotThrow {
            runBlocking {
                context.enter(
                    events = null,
                    /* allowConcurrentTasks = true, */
                    runnable = task {},
                )
            }
        }

        assertFailsWith<IllegalStateException> {
            runBlocking {
                context.enter(
                    events = null,
                    /* allowConcurrentTasks = true, */
                    runnable = task {},
                )
            }
        }
    }

    @Test
    fun liveContextHasLiveTaskContextElement() {
        val element = runBlockingTask {
            val coroutineContext = currentCoroutineContext()
            coroutineContext[LiveTaskContextElement]
        }
        assertNotNull(element)
    }

    @Test
    fun liveContextEmitsBeginEventOnStart() {
        val firstEvent = runBlocking { TEST_TASK {}.taskFlow().first() }
        assertIs<TaskBeginEvent>(firstEvent)
        assertSame(TEST_TASK, firstEvent.task)
    }

    @Test
    fun liveContextEmitsFinishEventOnCompletion() {
        val lastEvent = runBlocking { TEST_TASK {}.taskFlow().last() }
        assertIs<TaskFinishEvent>(lastEvent)
        assertSame(TEST_TASK, lastEvent.task)
    }

    @Test
    fun liveContextEmitsFailEventOnException() {
        val exception = RuntimeException()

        val lastEvent = runBlocking {
            TEST_TASK { throw exception }
                .taskFlow().catch { /* ignore */ }.last()
        }

        assertIs<TaskFailEvent>(lastEvent)
        assertSame(TEST_TASK, lastEvent.task)

        /*
         * For some reason, when the exception is thrown, it seems to wrap
         * itself and become a different instance of the same exception.
         * But, the cause of the exception instance is the same instance of
         * the original exception.
         */
        assertSame(exception, lastEvent.cause.cause)
    }

    @Test
    fun liveContextDoesNotEmitFinishEventOnException() {
        val events = runBlocking {
            TEST_TASK { throw RuntimeException() }
                .taskFlow().catch { /* ignore */ }.toList()
        }
        assertTrue {
            events.none { it is TaskFinishEvent }
        }
    }

    @Test
    fun liveContextThrowsOnExitIfParentNotActive() {
        var parentDead = false
        runBlockingTask {
            thread {
                sleepUntil { parentDead }
                assertFailsWith<ConcurrentTaskException> {
                    runBlockingTask {}
                }
            }
        }
        parentDead = true
    }

    @Test
    fun liveContextThrowsOnExitIfChildrenStillActive() {
        assertFailsWith<ConcurrentTaskException> {
            runBlockingTask {
                var childStarted = false
                thread {
                    runBlockingTask {
                        childStarted = true
                        yieldWhile { true }
                    }
                }
                yieldUntil { childStarted }
            }
        }
    }

    @Test
    fun liveTaskContextElementToStringUsesTaskName() {
        runBlockingTask(name = "test") {
            val coroutineContext = currentCoroutineContext()
            val element = coroutineContext[LiveTaskContextElement]
            assertEquals(
                "LiveTaskContextElement(${taskContext.task.name})",
                element.toString(),
            )
        }
    }

    @Test
    fun liveTaskContextElementToStringAccountsForDeadTasks() {
        val element = runBlockingTask(name = "test") {
            val coroutineContext = currentCoroutineContext()
            coroutineContext[LiveTaskContextElement]
        }
        assertEquals(
            "LiveTaskContextElement(<dead task>)",
            element.toString(),
        )
    }

    @Test
    fun taskContextScopeTaskLoggerReturnsCorrectLogger() {
        runBlockingTask {
            assertSame(taskContext.logger, taskLogger)
        }
    }

    @Test
    fun liveTaskContextScopeUsesGivenContext() {
        val taskContext = LiveTaskContext<Task>()
        val scope = LiveTaskContextScope(taskContext)
        assertSame(taskContext, scope.taskContext)
    }

}
