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

import org.junit.jupiter.api.Assertions.assertTrue
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame

private val TEST_TASK = task(name = "test")

internal class TaskTest {

    @Test
    fun namedTaskUsesGivenName() {
        assertEquals("test", TEST_TASK.name)
    }

    @Test
    fun isAnonymousReturnsTrueForAnonymousTask() {
        assertTrue { task().isAnonymous() }
        assertFalse { task().isNotAnonymous() }
    }

    @Test
    fun isAnonymousReturnsFalseForNamedTask() {
        assertFalse { TEST_TASK.isAnonymous() }
        assertTrue { TEST_TASK.isNotAnonymous() }
    }

    @Test
    fun isNamedReturnsTrueForNamedTask() {
        assertTrue { TEST_TASK.isNamed() }
        assertFalse { TEST_TASK.isNotNamed() }
    }

    @Test
    fun isNamedReturnsFalseForUnnamedTask() {
        assertFalse { task().isNamed() }
        assertTrue { task().isNotNamed() }
    }

    @Test
    fun getNameOrElseReturnsNameForNamedTask() {
        assertEquals("test", TEST_TASK.getNameOrElse())
    }

    @Test
    fun getNameOrElseReturnsFallbackForUnnamedTask() {
        assertEquals("fallback", task().getNameOrElse("fallback"))
    }

    @Test
    fun nameReturnsNameForNamedTask() {
        assertEquals("test", TEST_TASK.nameOrNull)
    }

    @Test
    fun nameOrNullReturnsNullForUnnamedTask() {
        assertNull(task().nameOrNull)
    }

    @Test
    fun runnableUsesGivenTask() {
        val runnable = TEST_TASK.runnable {}
        assertSame(TEST_TASK, runnable.task)
    }

    @Test
    fun runnableUsesGivenBlock() {
        val n = Random.nextInt()
        val result = TEST_TASK.runnable { n }.runBlocking()
        assertEquals(n, result)
    }

    @Test
    fun invokeUsesGivenTask() {
        val runnable = TEST_TASK {}
        assertSame(TEST_TASK, runnable.task)
    }

    @Test
    fun invokeUsesGivenBlock() {
        val n = Random.nextInt()
        val result = TEST_TASK { n }.runBlocking()
        assertEquals(n, result)
    }

    @Test
    fun runnableNamedTaskUsesGivenName() {
        val runnable = task(TEST_TASK.name) {}
        assertEquals(TEST_TASK.name, runnable.task.name)
    }

    @Test
    fun runnableNamedTaskUsesGivenBlock() {
        val n = Random.nextInt()
        val result = task(TEST_TASK.name) { n }.runBlocking()
        assertEquals(n, result)
    }

    @Test
    fun runnableAnonymousTaskUsesAnonymousTask() {
        val runnable = task {}
        assertTrue { runnable.task.isAnonymous() }
    }

    @Test
    fun runnableAnonymousTaskUsesGivenBlock() {
        val n = Random.nextInt()
        val result = task { n }.runBlocking()
        assertEquals(n, result)
    }

}
