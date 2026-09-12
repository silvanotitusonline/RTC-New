package za.org.rtc.community.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class RtcMathTest {
    @Test
    fun goldenSubdivisionsFormAWhole() {
        assertEquals(1f, RtcMath.GoldenMajor + RtcMath.GoldenMinor, 0.00001f)
    }

    @Test
    fun goldenLandscapeHeightUsesPhi() {
        assertEquals(200f, RtcMath.goldenLandscapeHeight(323.6068f), 0.001f)
    }

    @Test
    fun circularGeometryUsesPi() {
        assertEquals(62.83185f, RtcMath.circleCircumference(10f), 0.001f)
        assertEquals(314.15927f, RtcMath.circleArea(10f), 0.001f)
    }

    @Test
    fun widthClassesRespectAndroidBreakpoints() {
        assertEquals(RtcWindowWidth.COMPACT, classifyRtcWindowWidth(599))
        assertEquals(RtcWindowWidth.MEDIUM, classifyRtcWindowWidth(600))
        assertEquals(RtcWindowWidth.MEDIUM, classifyRtcWindowWidth(839))
        assertEquals(RtcWindowWidth.EXPANDED, classifyRtcWindowWidth(840))
    }
}
