package com.hdil.datacollection_researcher.status

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeleteParticipantDataUseCaseTest {

    @Test
    fun `deletes participant directory recursively`() {
        val base = createTempDirectory(prefix = "out-").toFile()
        val outputDir = File(base, "output").apply { mkdirs() }

        val id = "p1"
        val participantDir = File(outputDir, id).apply { mkdirs() }
        File(participantDir, "a.csv").writeText("x")
        File(participantDir, "nested").apply { mkdirs() }
        File(File(participantDir, "nested"), "b.xlsx").writeText("y")

        val repo = DesktopParticipantDataRepository()
        val useCase = DeleteParticipantDataUseCase(repo)

        val deletedCount = kotlinx.coroutines.runBlocking {
            useCase(outputDir, id)
        }

        assertTrue(deletedCount >= 1)
        assertFalse(participantDir.exists())
    }

    @Test
    fun `returns 0 when directory does not exist`() {
        val base = createTempDirectory(prefix = "out-").toFile()
        val outputDir = File(base, "output").apply { mkdirs() }

        val repo = DesktopParticipantDataRepository()
        val useCase = DeleteParticipantDataUseCase(repo)

        val deletedCount = kotlinx.coroutines.runBlocking {
            useCase(outputDir, "p2")
        }

        assertEquals(0, deletedCount)
    }

    @Test
    fun `rejects unknown participant id`() {
        val base = createTempDirectory(prefix = "out-").toFile()
        val outputDir = File(base, "output").apply { mkdirs() }

        val repo = DesktopParticipantDataRepository()
        val useCase = DeleteParticipantDataUseCase(repo)

        assertFailsWith<IllegalArgumentException> {
            kotlinx.coroutines.runBlocking { useCase(outputDir, "unknown") }
        }
        assertFailsWith<IllegalArgumentException> {
            kotlinx.coroutines.runBlocking { useCase(outputDir, "(unknown)") }
        }
    }
}
