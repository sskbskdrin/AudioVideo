package cn.sskbskdrin.record.opengl

import android.annotation.TargetApi
import android.app.Activity
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraManager
import android.hardware.camera2.params.StreamConfigurationMap
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.util.Size
import android.view.Surface
import android.view.SurfaceHolder
import cn.sskbskdrin.log.L
import cn.sskbskdrin.log.logcat.LogcatPrinter
import cn.sskbskdrin.log.logcat.PrettyFormat
import cn.sskbskdrin.record.camera.Camera2Manager
import cn.sskbskdrin.record.R
import cn.sskbskdrin.record.mesh.Cube
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class OpenGlActivity : Activity(), GLSurfaceView.Renderer {

    private val TAG = "OpenGlActivity"

    private val manager = Camera2Manager(this)
    private val glView by lazy { findViewById<GLView>(R.id.main_gl_surface) }

    private val mesh = Cube(0f, 0f, 0f)

    init {
        val X = 0.5f
        val Y = 0.5f
        val Z = 0.5f
        val vertices = floatArrayOf(
                -X, 0.0f, Z,
                X, 0.0f, Z,
                -X, 0.0f, -Z,
                X, 0.0f, -Z,
                0.0f, Y, X,
                0.0f, Y, -X,
                0.0f, -Y, X,
                0.0f, -Y, -X,
                X, Y, 0.0f,
                -X, Y, 0.0f,
                X, -Y, 0.0f,
                -X, -Y, 0.0f
        )
        val indices = shortArrayOf(
                0, 4, 1,
                0, 9, 4,
                9, 5, 4,
                4, 5, 8,
                4, 8, 1,
                8, 10, 1,
                8, 3, 10,
                5, 3, 8,
                5, 2, 3,
                2, 7, 3,
                7, 10, 3,
                7, 6, 10,
                7, 11, 6,
                11, 0, 6,
                0, 1, 6,
                6, 1, 10,
                9, 0, 11,
                9, 11, 2,
                9, 2, 5,
                7, 2, 11
        )
        val colors = floatArrayOf(
                0f, 0f, 1f, 1f,
                0f, 1f, 0f, 1f,
                1f, 0f, 0f, 1f,
                0f, 1f, 1f, 1f,
                1f, 0f, 1f, 1f,
                1f, 1f, 0f, 1f,
                1f, 1f, 0f, 1f,
                0.5f, 0f, 0f, 1f,
                0f, 0.5f, 0f, 1f,
                0f, 0f, 0.5f, 1f,
                0f, 0.5f, 1f, 1f,
                0f, 0.5f, 1f, 1f
        )
//        mesh.setVertices(vertices)
//        mesh.setIndices(indices)
//        mesh.setColors(colors)
    }

    @TargetApi(21)
    private val listener = object : Camera2Manager.CameraListener {

        override fun getCameraId(manager: CameraManager): String? {
            return null
        }

        override fun getPreviewSize(map: StreamConfigurationMap): Size {
            val sizes = map.getOutputSizes(SurfaceHolder::class.java)
            L.append("preview size=")
            for (size in sizes) {
                L.append("$size ")
            }
            L.i(TAG, "")
            return Size(1280, 720)
        }

        override fun getVideoSize(map: StreamConfigurationMap): Size {
            return Size(1280, 720)
        }

        override fun getSurfaceList(list: ArrayList<Surface>) {
            val texture = SurfaceTexture(2)
            texture.setOnFrameAvailableListener {
                L.d(TAG, "texture on Frame")
            }
//            texture.setDefaultBufferSize(1280,720)
            list.add(Surface(texture))
            list.add(glView.holder.surface)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_opengl)
        L.addPinter(LogcatPrinter(PrettyFormat()))
        glView.render = this
    }

    override fun onResume() {
        super.onResume()
        glView.onResume()
    }

    override fun onPause() {
        super.onPause()
        glView.onPause()
    }

    override fun onDrawFrame(gl: GL10?) {
        L.i(TAG, "onDrawFrame...")
        gl!!
        mesh.rx += 0.3f
        mesh.draw(gl)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {

    }

}
