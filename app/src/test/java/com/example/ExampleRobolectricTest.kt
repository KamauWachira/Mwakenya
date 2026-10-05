package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.ExamStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("ExamSnap", appName)
    }

    @Test
    fun `exam step serialization test`() {
        val steps = listOf(
            ExamStep(1, "Given Identification", "Identify variables m=5kg, v=10m/s", formula = "KE = 0.5 * m * v^2", tip = "Ensure velocity is in m/s")
        )
        val jsonStr = ExamStep.listToJsonString(steps)
        val parsed = ExamStep.listFromJsonString(jsonStr)

        assertEquals(1, parsed.size)
        assertEquals("Given Identification", parsed[0].title)
        assertEquals("KE = 0.5 * m * v^2", parsed[0].formula)
    }
}
