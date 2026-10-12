package com.swhurl.app

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

// Which orders the team hears about: ten or more of something. demo-shopper buys one to three, so
// the steady demo traffic never notifies anyone.
class BigOrderTest {
    @Test
    fun `only an order of ten or more is worth telling the team`() {
        assertNull(bigOrder("tea", 3, "demo-shop"))
        assertNull(bigOrder("tea", 9, "demo-shop"))
        assertNull(bigOrder("tea", null, "demo-shop"))
        assertEquals("10 tea, placed through demo-shop.", bigOrder("tea", 10, "demo-shop"))
        assertEquals("250 of something, placed through an unknown app.", bigOrder(null, 250, null))
    }
}
