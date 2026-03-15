package guru.urchin.sdr

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ServerSocket

class NetworkProbeTest {
  @Test
  fun `reachable when server is listening`() = runBlocking {
    val server = ServerSocket(0) // bind to random available port
    try {
      val target = ProbeTarget("test", "127.0.0.1", server.localPort)
      val results = NetworkProbe.probeAll(listOf(target))
      assertEquals(1, results.size)
      assertTrue(results[0].reachable)
      assertNull(results[0].errorMessage)
    } finally {
      server.close()
    }
  }

  @Test
  fun `unreachable when no server on port`() = runBlocking {
    // Find a port that nothing is listening on
    val port = ServerSocket(0).use { it.localPort }
    val target = ProbeTarget("test", "127.0.0.1", port)
    val results = NetworkProbe.probeAll(listOf(target))
    assertEquals(1, results.size)
    assertFalse(results[0].reachable)
    assertTrue(results[0].errorMessage != null)
  }

  @Test
  fun `empty targets returns empty results`() = runBlocking {
    val results = NetworkProbe.probeAll(emptyList())
    assertTrue(results.isEmpty())
  }

  @Test
  fun `multiple targets probed in parallel`() = runBlocking {
    val server = ServerSocket(0)
    val closedPort = ServerSocket(0).use { it.localPort }
    try {
      val targets = listOf(
        ProbeTarget("open", "127.0.0.1", server.localPort),
        ProbeTarget("closed", "127.0.0.1", closedPort)
      )
      val start = System.currentTimeMillis()
      val results = NetworkProbe.probeAll(targets)
      val elapsed = System.currentTimeMillis() - start

      assertEquals(2, results.size)
      val open = results.first { it.target.label == "open" }
      val closed = results.first { it.target.label == "closed" }
      assertTrue(open.reachable)
      assertFalse(closed.reachable)
      // Parallel execution: should complete well under 2x timeout (6s)
      assertTrue("Expected parallel execution but took ${elapsed}ms", elapsed < 5000)
    } finally {
      server.close()
    }
  }
}
