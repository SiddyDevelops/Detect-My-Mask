package com.siddydevelops.detectmymask

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Rect
import android.media.Image
import android.os.Build
import android.renderscript.*
import android.util.Config
import androidx.core.os.BuildCompat
import java.nio.ByteBuffer


class YuvToRGBConverter(context : Context) {

    private val rs = RenderScript.create(context)
    private val scriptYuvToRGb = ScriptIntrinsicYuvToRGB.create(rs, Element.U8_4(rs))
    private lateinit var yuvBuffer: ByteBuffer
    private lateinit var inputAllocation : Allocation
    private lateinit var outputAllocation: Allocation
    private var pixelCount: Int = -1

    @Synchronized
    fun yuvToRgb(image: Image, output: Bitmap)
    {
        if(!::yuvBuffer.isInitialized)
        {
            pixelCount = image.cropRect.width()*image.cropRect.height()
            val pixelSizeBits = ImageFormat.getBitsPerPixel(ImageFormat.YUV_420_888)
            yuvBuffer = ByteBuffer.allocateDirect(pixelCount*pixelSizeBits/8)
        }

        yuvBuffer.rewind()
        imageToByteBuffer(image,yuvBuffer.array())

        if(!::inputAllocation.isInitialized)
        {
            val elemType = Type.Builder(rs, Element.YUV(rs)).setYuvFormat(ImageFormat.NV21).create()
            inputAllocation = Allocation.createSized(rs, elemType.element, yuvBuffer.array().size)
        }

        if(!::outputAllocation.isInitialized)
        {
            outputAllocation = Allocation.createFromBitmap(rs, output)
        }

        inputAllocation.copyFrom(yuvBuffer.array())
        scriptYuvToRGb.setInput(inputAllocation)
        scriptYuvToRGb.forEach(outputAllocation)
        outputAllocation.copyTo(output)

    }

    private fun imageToByteBuffer(image: Image, outputBuffer: ByteArray) {
        if(BuildConfig.DEBUG && image.format != ImageFormat.YUV_420_888)
        {
            error("Assertion Failure")
        }
        val imageCrop = image.cropRect
        val imagePlanes = image.planes
        imagePlanes.forEachIndexed { planeIndex, plane ->
            val outputStride: Int
            var outputOffset: Int
            when(planeIndex)
            {
                0 -> {
                    outputStride = 1
                    outputOffset = 0
                }
                1 -> {
                    outputStride = 2
                    outputOffset = pixelCount + 1
                }
                2 -> {
                    outputStride = 2
                    outputOffset = pixelCount
                }
                else -> {
                    return@forEachIndexed
                }
            }

            val planeBuffer = plane.buffer
            val rowStride = plane.rowStride
            val pixelStride = plane.pixelStride
            val planeCrop = if(planeIndex == 0){
                imageCrop
            } else {
                Rect(imageCrop.left/2, imageCrop.top/2, imageCrop.right/2, imageCrop.bottom/2)
            }

            val planeWidth = planeCrop.width()
            val planeHeight = planeCrop.height()
            val rowBuffer = ByteArray(plane.rowStride)
            val rowLength = if(pixelStride == 1 && outputStride == 1)
            {
                planeWidth
            } else {
                (planeWidth-1)*pixelStride+1
            }

            for(row in 0 until planeHeight){
                planeBuffer.position(
                    (row + planeCrop.top)*rowStride + planeCrop.left*pixelStride
                )
                if(pixelStride == 1 && outputStride == 1){
                    planeBuffer.get(outputBuffer,outputOffset,rowLength)
                    outputOffset+=rowLength
                }
                else {
                    planeBuffer.get(rowBuffer,0,rowLength)
                    for (col in 0 until planeWidth)
                    {
                        outputBuffer[outputOffset] = rowBuffer[col*pixelStride]
                        outputOffset += outputStride
                    }
                }
            }

        }
    }

}