package za.org.rtc.community.data.local

import org.junit.Assert.assertEquals
import org.junit.Test

class PreparedImageEncodingTest {
    @Test
    fun `alpha capable image uses png output`() {
        assertEquals(PreparedImageEncoding.PNG, preparedImageEncoding(hasAlphaChannel = true))
    }

    @Test
    fun `opaque image uses jpeg output`() {
        assertEquals(PreparedImageEncoding.JPEG, preparedImageEncoding(hasAlphaChannel = false))
    }
}
