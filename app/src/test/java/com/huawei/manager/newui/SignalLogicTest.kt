package com.huawei.manager.newui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.huawei.manager.newui.model.BandOption
import com.huawei.manager.newui.model.SignalData
import com.huawei.manager.newui.ui.theme.SignalGray
import com.huawei.manager.newui.ui.theme.SignalGreen
import com.huawei.manager.newui.ui.theme.SignalOrange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SignalLogicTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Huawei Management", appName)
    }

    @Test
    fun `verify SINR color logic matches user specifications`() {
        // 0-3 dB should be Gray
        val lowSinr = SignalData(sinr = 2.5f)
        assertEquals(SignalGray, lowSinr.sinrColor)

        // 4-6 dB should be Orange
        val midSinr = SignalData(sinr = 5.2f)
        assertEquals(SignalOrange, midSinr.sinrColor)

        // 7+ dB should be Green
        val highSinr = SignalData(sinr = 9.0f)
        assertEquals(SignalGreen, highSinr.sinrColor)
    }

    @Test
    fun `verify Indonesian bands are present`() {
        val bands = BandOption.INDONESIA_BANDS
        assertTrue(bands.any { it.id == "B3" }) // 1800 MHz
        assertTrue(bands.any { it.id == "B1" }) // 2100 MHz
        assertTrue(bands.any { it.id == "B8" }) // 900 MHz
        assertTrue(bands.any { it.id == "B40" }) // 2300 MHz
    }
}
