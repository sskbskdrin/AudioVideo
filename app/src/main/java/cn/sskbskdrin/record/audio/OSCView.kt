package cn.sskbskdrin.record.audio

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.Handler
import android.os.HandlerThread
import android.util.AttributeSet
import android.view.SurfaceHolder
import android.view.SurfaceView
import java.util.*
import java.util.concurrent.ArrayBlockingQueue

/**
 * @author sskbskdrin
 * @date 2019/March/29
 */
class OSCView(context: Context, attr: AttributeSet) : SurfaceView(context, attr), SurfaceHolder.Callback, Runnable {

    init {
        holder.addCallback(this)
    }

    private val COLORS = intArrayOf(Color.RED, Color.BLUE, Color.GREEN, Color.YELLOW)
    private val MAX_SAMPLE = 300

    /**
     * 声道数
     */
    private var channelCount: Int = 0
    /**
     * 每个样本的数据的位数
     */
    private var bits: Int = 0
    private var channels: ArrayList<Channel>? = null

    private var thread: HandlerThread? = null
    private var mHandler: Handler? = null
    private var isDrawing: Boolean = false
    private var queue: ArrayBlockingQueue<ByteArray>? = null

    fun setParams(channelCount: Int, bits: Int) {
        channels = ArrayList(channelCount)
        for (i in 0 until channelCount) {
            channels?.add(Channel(COLORS[i]))
        }
        this.channelCount = channelCount
        this.bits = bits
    }

    fun updatePcmData(data: ByteArray, l: Int) {
        if (mHandler != null) {
            if (!isDrawing) {
                mHandler?.post(this)
            }
            if (l > 0) {
                queue?.offer(data)
            } else {
                isDrawing = false
                queue?.offer(ByteArray(0))
            }
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder?, format: Int, width: Int, height: Int) {
    }

    override fun surfaceCreated(holder: SurfaceHolder?) {
        thread = object : HandlerThread("oscView") {
            override fun onLooperPrepared() {
                mHandler = Handler(looper)
                queue = ArrayBlockingQueue(10)
            }
        }
        (thread as HandlerThread).start()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder?) {
        isDrawing = false
        thread?.quit()
    }

    override fun run() {
        isDrawing = true
        while (isDrawing) {
            val data = queue?.poll()
            if (data != null && data.isNotEmpty()) {
                deal(data)
                drawPath()
            }
            for (channel in channels!!) {
                channel.mPath.reset()
            }
        }
        drawPath()
    }

    private fun drawPath() {
        val canvas = holder.lockCanvas()
        canvas.drawColor(Color.BLACK)
        if (channels != null) {
            for (channel in channels!!) {
                channel.draw(canvas)
            }
        }
        holder.unlockCanvasAndPost(canvas)
    }

    private fun deal(data: ByteArray) {
        var base = height / 2
        //每个单位数据块字节大小
        val preBlockByte = channelCount * bits / 8
        //数据块数量
        val blockCount = data.size / preBlockByte

        //计算取样次数
        val time = if (blockCount > MAX_SAMPLE) MAX_SAMPLE else blockCount

        //X轴坐标移动步长
        val stepX = width * 1f / time
        //数据取样步长
        val stepData = blockCount / time * preBlockByte
        if (channelCount == 1) {
            if (bits == 8) {
                single8bit(data, stepData, stepX, base)
            } else {
                single16bit(data, stepData, stepX, base)
            }
        } else {
            base /= 2
            val base1 = base * 3
            if (bits == 8) {
                double8bit(data, stepData, stepX, base, base1)
            } else {
                double16bit(data, stepData, stepX, base, base1)
            }
        }
    }

    /**
     * 单声道16位数据
     */
    private fun single16bit(data: ByteArray, stepData: Int, stepX: Float, base: Int) {
        val channel = channels!![0]
        channel.mPath.moveTo(0f, base.toFloat())
        var x = 0f
        var i = 0
        while (i < data.size - 1) {
            val temp = bToI(data[i]) or (bToI(data[i + 1]) shl 8)
            x += stepX
            channel.mPath.lineTo(x, base + temp / 32768f * base)
            i += stepData
        }
        channel.mPath.lineTo((width + 1).toFloat(), base.toFloat())
    }

    /**
     * 双声道16位数据
     */
    private fun double16bit(data: ByteArray, stepData: Int, stepX: Float, base: Int, base1: Int) {
        val channel0 = channels!![0]
        val channel1 = channels!![1]
        channel0.mPath.moveTo(0f, base.toFloat())
        channel1.mPath.moveTo(0f, base1.toFloat())
        var x = 0f
        var i = 0
        while (i < data.size) {
            x += stepX
            var temp = bToI(data[i]) or (bToI(data[i + 1]) shl 8)
            channel0.mPath.lineTo(x, base + temp / 32768f * base)
            temp = bToI(data[i + 2]) or (bToI(data[i + 3]) shl 8)
            channel1.mPath.lineTo(x, base1 + temp / 32768f * base)
            i += stepData
        }
        channel0.mPath.lineTo((width + 1).toFloat(), base.toFloat())
        channel1.mPath.lineTo((width + 1).toFloat(), base1.toFloat())
    }

    /**
     * 单声道8位
     */
    private fun single8bit(data: ByteArray, stepData: Int, stepX: Float, base: Int) {
        val channel = channels!![0]
        channel.mPath.moveTo(0f, base.toFloat())
        var x = 0f
        var i = 0
        while (i < data.size) {
            x += stepX
            // 8bit的数据需要转换一下，不明白为啥
            channel.mPath.lineTo(x, base + (data[i] - 128.toByte()).toByte() / 128f * base)
            i += stepData
        }
        channel.mPath.lineTo((width + 1).toFloat(), base.toFloat())
    }

    /**
     * 双声道8位
     */
    private fun double8bit(data: ByteArray, stepData: Int, stepX: Float, base: Int, base1: Int) {
        val channel0 = channels!![0]
        val channel1 = channels!![1]
        channel0.mPath.moveTo(0f, base.toFloat())
        channel1.mPath.moveTo(0f, base1.toFloat())
        var x = 0f
        val con = 128.toByte()
        var i = 0
        while (i < data.size) {
            x += stepX
            var temp = (data[i] - con).toByte()
            channel0.mPath.lineTo(x, base + temp / 128f * base)
            temp = (data[i] - con).toByte()
            channel1.mPath.lineTo(x, base1 + temp / 128f * base)
            i += stepData
        }
        channel0.mPath.lineTo((width + 1).toFloat(), base.toFloat())
        channel1.mPath.lineTo((width + 1).toFloat(), base1.toFloat())
    }

    /**
     * 带符号byte转int
     */
    private fun bToI(value: Byte): Int {
        return value.toInt()
    }

    private class Channel(color: Int) {
        val mPath: Path = Path()
        private val mPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG)

        init {
            mPaint.color = color
            mPaint.strokeWidth = 3f
            mPaint.style = Paint.Style.STROKE
        }

        fun draw(canvas: Canvas) {
            canvas.drawPath(mPath, mPaint)
        }
    }
}