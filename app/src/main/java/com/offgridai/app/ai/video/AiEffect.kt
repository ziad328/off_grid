package com.offgridai.app.ai.video

import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLUtils
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * A Media3 GlEffect that intercepts each video frame, converts it to a Bitmap,
 * runs the provided AI processor function, and uploads the modified Bitmap back to the GPU.
 */
class AiEffect(private val aiProcessor: suspend (Bitmap) -> Bitmap) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram {
        return AiShaderProgram(context, useHdr, aiProcessor)
    }
}

class AiShaderProgram(
    context: Context,
    useHdr: Boolean,
    private val aiProcessor: suspend (Bitmap) -> Bitmap
) : BaseGlShaderProgram(useHdr, 1) {

    private var outputSize = Size.ZERO
    private var pixelBuffer: ByteBuffer? = null
    private var inputBitmap: Bitmap? = null

    private var programId = 0
    private var aPositionLoc = 0
    private var aTexCoordLoc = 0
    private var uTexSamplerLoc = 0
    private var positionBuffer: FloatBuffer? = null
    private var texCoordBuffer: FloatBuffer? = null
    
    private var aiTextureId = 0

    private fun compileShader(type: Int, shaderCode: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, shaderCode)
        GLES20.glCompileShader(shader)
        return shader
    }

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        outputSize = Size(inputWidth, inputHeight)
        val capacity = inputWidth * inputHeight * 4
        if (pixelBuffer == null || pixelBuffer!!.capacity() != capacity) {
            pixelBuffer = ByteBuffer.allocateDirect(capacity)
            inputBitmap = Bitmap.createBitmap(inputWidth, inputHeight, Bitmap.Config.ARGB_8888)
        }

        if (aiTextureId == 0) {
            val textures = IntArray(1)
            GLES20.glGenTextures(1, textures, 0)
            aiTextureId = textures[0]
        }

        if (programId == 0) {
            val vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, """
                attribute vec4 aFramePosition;
                attribute vec4 aTexSamplingCoord;
                varying vec2 vTexSamplingCoord;
                void main() {
                  gl_Position = aFramePosition;
                  vTexSamplingCoord = aTexSamplingCoord.xy;
                }
            """.trimIndent())
            val fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, """
                precision mediump float;
                uniform sampler2D uTexSampler;
                varying vec2 vTexSamplingCoord;
                void main() {
                  gl_FragColor = texture2D(uTexSampler, vTexSamplingCoord);
                }
            """.trimIndent())
            programId = GLES20.glCreateProgram()
            GLES20.glAttachShader(programId, vertexShader)
            GLES20.glAttachShader(programId, fragmentShader)
            GLES20.glLinkProgram(programId)

            aPositionLoc = GLES20.glGetAttribLocation(programId, "aFramePosition")
            aTexCoordLoc = GLES20.glGetAttribLocation(programId, "aTexSamplingCoord")
            uTexSamplerLoc = GLES20.glGetUniformLocation(programId, "uTexSampler")

            positionBuffer = ByteBuffer.allocateDirect(8 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
                put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
                position(0)
            }
            texCoordBuffer = ByteBuffer.allocateDirect(8 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
                put(floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f))
                position(0)
            }
        }
        return outputSize
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        val width = outputSize.width
        val height = outputSize.height

        try {
            // Save the currently bound framebuffer (the output FBO Media3 expects us to draw into)
            val currentFbo = IntArray(1)
            GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, currentFbo, 0)

            // 1. Bind an FBO to the input texture so we can read its pixels
            val fbo = GlUtil.createFboForTexture(inputTexId)
            GlUtil.focusFramebufferUsingCurrentContext(fbo, width, height)

            // 2. Read pixels from GPU to CPU
            pixelBuffer!!.clear()
            GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, pixelBuffer)
            pixelBuffer!!.rewind()

            // 3. Convert RGBA buffer to Android ARGB Bitmap
            inputBitmap!!.copyPixelsFromBuffer(pixelBuffer!!)

            // 4. Run AI Processing (Blocking synchronously since drawFrame is called on a background GL thread)
            val resultBitmap = kotlinx.coroutines.runBlocking {
                aiProcessor(inputBitmap!!)
            }

            // 5. Upload the modified Bitmap to OUR dedicated texture
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, aiTextureId)
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR.toFloat())
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR.toFloat())
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE.toFloat())
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE.toFloat())
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, resultBitmap, 0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)

            if (resultBitmap != inputBitmap) {
                resultBitmap.recycle()
            }

            // Cleanup FBO
            GLES20.glDeleteFramebuffers(1, intArrayOf(fbo), 0)

            // 6. Restore the original output framebuffer
            GlUtil.focusFramebufferUsingCurrentContext(currentFbo[0], width, height)

            // 7. Draw the mutated input texture to the output framebuffer
            GLES20.glUseProgram(programId)

            GLES20.glEnableVertexAttribArray(aPositionLoc)
            GLES20.glVertexAttribPointer(aPositionLoc, 2, GLES20.GL_FLOAT, false, 0, positionBuffer)

            GLES20.glEnableVertexAttribArray(aTexCoordLoc)
            GLES20.glVertexAttribPointer(aTexCoordLoc, 2, GLES20.GL_FLOAT, false, 0, texCoordBuffer)

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, aiTextureId)
            GLES20.glUniform1i(uTexSamplerLoc, 0)

            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

            GlUtil.checkGlError()

        } catch (e: Exception) {
            throw VideoFrameProcessingException("AI processing failed", e)
        }
    }

    override fun release() {
        super.release()
        if (programId != 0) {
            GLES20.glDeleteProgram(programId)
            programId = 0
        }
        if (aiTextureId != 0) {
            val textures = intArrayOf(aiTextureId)
            GLES20.glDeleteTextures(1, textures, 0)
            aiTextureId = 0
        }
    }
}
