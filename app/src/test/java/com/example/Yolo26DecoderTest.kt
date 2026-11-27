package com.example

import com.example.ml.LetterboxInput
import com.example.ml.Yolo26Decoder
import org.junit.Assert.assertEquals
import org.junit.Test

class Yolo26DecoderTest {

    @Test
    fun decodesNmsFreePersonRowAndFiltersOtherClasses() {
        val letterbox = LetterboxInput(
            bitmap = null,
            srcWidth = 640,
            srcHeight = 480,
            inputWidth = 640,
            inputHeight = 640,
            scale = 1.0f,
            padX = 0f,
            padY = 80f
        )

        val rows = arrayOf(
            floatArrayOf(100f, 120f, 300f, 440f, 0.91f, 0f), // person
            floatArrayOf(50f, 100f, 200f, 300f, 0.88f, 56f), // chair (ignored)
            floatArrayOf(20f, 90f, 100f, 200f, 0.25f, 0f)    // low confidence person (ignored)
        )

        val boxes = Yolo26Decoder.decodeNmsFreeRows(rows, threshold = 0.45f, letterbox = letterbox)
        assertEquals(1, boxes.size)
        assertEquals(0.91f, boxes[0].confidence, 0.001f)
        assertEquals("person", boxes[0].label)
    }
}
