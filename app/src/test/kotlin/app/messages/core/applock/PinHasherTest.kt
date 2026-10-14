package app.messages.core.applock

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** PIN hashing must verify the right PIN, reject wrong ones, and never store it raw. */
class PinHasherTest {

    @Test fun `correct pin verifies`() {
        val cred = PinHasher.create("2468")
        assertTrue(PinHasher.verify("2468", cred))
    }

    @Test fun `wrong pin is rejected`() {
        val cred = PinHasher.create("2468")
        assertFalse(PinHasher.verify("1357", cred))
        assertFalse(PinHasher.verify("24680", cred))
        assertFalse(PinHasher.verify("", cred))
    }

    @Test fun `salt is random so two credentials for the same pin differ`() {
        val a = PinHasher.create("0000")
        val b = PinHasher.create("0000")
        assertNotEquals(a.saltHex, b.saltHex)
        assertNotEquals(a.hashHex, b.hashHex)
        // ...but both still verify the original pin.
        assertTrue(PinHasher.verify("0000", a))
        assertTrue(PinHasher.verify("0000", b))
    }

    @Test fun `the raw pin never appears in the stored credential`() {
        val cred = PinHasher.create("9173")
        assertFalse(cred.encode().contains("9173"))
    }

    @Test fun `encode then decode round-trips`() {
        val cred = PinHasher.create("4242")
        val restored = PinCredential.decode(cred.encode())
        assertTrue(restored != null && PinHasher.verify("4242", restored))
    }

    @Test fun `decode rejects malformed input`() {
        assertNull(PinCredential.decode("nocolon"))
        assertNull(PinCredential.decode(":onlyhash"))
        assertNull(PinCredential.decode("onlysalt:"))
        assertNull(PinCredential.decode(""))
    }
}
