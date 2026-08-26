package net.morsecode.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import net.morsecode.shared.media.DateGrouping
import net.morsecode.shared.media.FileCategorizer
import net.morsecode.shared.media.GenericFile
import net.morsecode.shared.net.TokenBucket

class LogicTest {

    private fun file(name: String, size: Long = 100) = GenericFile(
        uri = "/tmp/$name", filename = name, relativePath = "Download",
        sizeBytes = size, extension = name.substringAfterLast('.', ""), modifiedEpochMs = 0,
    )

    @Test
    fun categorizerIsNonExclusive() {
        val bigDoc = file("report.docx", size = 60 * 1024 * 1024)
        val cats = FileCategorizer.categorize(bigDoc)
        assertTrue("documents" in cats)
        assertTrue("big" in cats)

        assertEquals(listOf("apks"), FileCategorizer.categorize(file("app.apk")))
        assertTrue("archives" in FileCategorizer.categorize(file("backup.zip")))
        assertTrue("ebooks" in FileCategorizer.categorize(file("novel.epub")))
        assertTrue(FileCategorizer.filter(listOf(bigDoc), "big").size == 1)
        assertTrue(FileCategorizer.filter(listOf(bigDoc), "apks").isEmpty())
    }

    @Test
    fun dateGroupingFormatsHeaders() {
        val header = DateGrouping.headerFor(LocalDate(2026, 8, 26))
        assertEquals("August 26, 2026", header)
    }

    @Test
    fun dateGroupingGroupsAndSortsDescending() {
        data class Item(val name: String, val at: Long)
        // 2026-08-25 local epoch-ms values; grouping only needs relative ordering
        val day1 = 1756080000000L // 2025-08-25-ish
        val items = listOf(
            Item("a", 1000), Item("b", 2000), Item("c", 86_400_000 + 1500),
        )
        val groups = DateGrouping.groupByDay(items) { it.at }
        assertEquals(2, groups.size)
        assertEquals(listOf("a", "b"), groups.last().second.map { it.name })
    }

    @Test
    fun throttleUnlimitedIsNoOp() {
        runBlocking {
            val bucket = TokenBucket(0)
            val t0 = System.currentTimeMillis()
            bucket.acquire(10 * 1024 * 1024)
            assertTrue(System.currentTimeMillis() - t0 < 500)
        }
    }
}
