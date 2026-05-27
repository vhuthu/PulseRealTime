import com.vhuthu.pulse_core.model.ExponentialBackoff
import com.vhuthu.pulse_core.model.NoReconnect
import org.junit.Assert.*
import org.junit.Test

class ExponentialBackoffTest {

    private val policy = ExponentialBackoff(
        maxAttempts = 5,
        initialDelayMillis = 1_000L,
        multiplier = 2.0,
        maxDelayMillis = 30_000L,
    )


    @Test
    fun `shouldRetry returns true for attempts within maxAttempts`() {
        assertTrue(policy.shouldRetry(1))
        assertTrue(policy.shouldRetry(3))
        assertTrue(policy.shouldRetry(5))
    }

    @Test
    fun `shouldRetry returns false once maxAttempts is exceeded`() {
        assertFalse(policy.shouldRetry(6))
        assertFalse(policy.shouldRetry(100))
    }


    @Test
    fun `delayMillis doubles each attempt`() {
        assertEquals(1_000L,  policy.delayMillis(1))
        assertEquals(2_000L,  policy.delayMillis(2))
        assertEquals(4_000L,  policy.delayMillis(3))
        assertEquals(8_000L,  policy.delayMillis(4))
        assertEquals(16_000L, policy.delayMillis(5))
    }

    @Test
    fun `delayMillis is capped at maxDelayMillis`() {
        val capped = ExponentialBackoff(
            maxAttempts = 20,
            initialDelayMillis = 1_000L,
            multiplier = 2.0,
            maxDelayMillis = 10_000L,
        )
        assertEquals(10_000L, capped.delayMillis(10))
        assertEquals(10_000L, capped.delayMillis(20))
    }

    @Test
    fun `delayMillis with multiplier 1_0 returns flat delay`() {
        val flat = ExponentialBackoff(
            maxAttempts = 5,
            initialDelayMillis = 2_000L,
            multiplier = 1.0,
            maxDelayMillis = 30_000L,
        )
        assertEquals(2_000L, flat.delayMillis(1))
        assertEquals(2_000L, flat.delayMillis(3))
        assertEquals(2_000L, flat.delayMillis(5))
    }


    @Test(expected = IllegalArgumentException::class)
    fun `constructor throws when maxAttempts is zero`() {
        ExponentialBackoff(maxAttempts = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `constructor throws when initialDelayMillis is zero`() {
        ExponentialBackoff(initialDelayMillis = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `constructor throws when multiplier is below 1_0`() {
        ExponentialBackoff(multiplier = 0.5)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `constructor throws when maxDelayMillis is less than initialDelayMillis`() {
        ExponentialBackoff(initialDelayMillis = 5_000, maxDelayMillis = 1_000)
    }
}

class NoReconnectTest {

    @Test
    fun `shouldRetry always returns false`() {
        assertFalse(NoReconnect.shouldRetry(1))
        assertFalse(NoReconnect.shouldRetry(100))
    }

    @Test
    fun `delayMillis always returns zero`() {
        assertEquals(0L, NoReconnect.delayMillis(1))
        assertEquals(0L, NoReconnect.delayMillis(5))
    }
}
 