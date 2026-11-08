package com.example.util

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.paddle.ocr.util.OpenCVUtils
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Detects native "library not found" while the Android device is running.
 * Complements the release APK's all-ABI ELF packaging gate.
 */
@RunWith(AndroidJUnit4::class)
class SoruLabNativeLibrariesDeviceTest {
    @Test
    fun opencvAndOnnxRuntimeLoadInsideTheInstalledPackage() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val nativeDir = File(app.applicationInfo.nativeLibraryDir.orEmpty())
        val abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
        assertTrue("Missing device ABI", abi.isNotBlank())
        assertTrue("Native library directory absent for $abi", nativeDir.exists())
        assertTrue("OpenCV failed to load on $abi", OpenCVUtils.init(app))
        // Test the real ONNX Java JNI binding, not only a packaged .so.
        // The OCR module owns the dependency, so invoke ONNX through its
        // runtime class without leaking implementation deps to app tests.
        val ortClass = Class.forName("ai.onnxruntime.OrtEnvironment")
        val environment = ortClass.getMethod("getEnvironment").invoke(null)
        assertTrue("ONNX environment could not initialize on $abi", environment != null)
    }
}
