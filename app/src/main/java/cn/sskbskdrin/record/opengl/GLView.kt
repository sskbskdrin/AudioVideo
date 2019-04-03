package cn.sskbskdrin.record.opengl

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import android.util.Log
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10


class GLView : GLSurfaceView, GLSurfaceView.Renderer {

    private val TAG = "GLView"

    var render: Renderer? = null

    constructor(context: Context?) : this(context, null)

    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {
        setRenderer(this)
        renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        Log.d(TAG, "onSurfaceCreated:")
        gl!!
        gl.glClear(GL10.GL_COLOR_BUFFER_BIT)
        // Set the background color to black ( rgba ).
        gl.glClearColor(0f, 0f, 0f, 1.0f)
        // Enable Smooth Shading, default not really needed.
        gl.glShadeModel(GL10.GL_SMOOTH)
        // Depth buffer setup.
        gl.glClearDepthf(1.0f)
        // Enables depth testing.
        gl.glEnable(GL10.GL_DEPTH_TEST)
        // The type of depth testing to do.
        gl.glDepthFunc(GL10.GL_LEQUAL)
        // Really nice perspective calculations.
        gl.glHint(GL10.GL_PERSPECTIVE_CORRECTION_HINT, GL10.GL_NICEST)
        render?.onSurfaceCreated(gl, config)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        gl?.glViewport(0, 0, width, height)
        render?.onSurfaceChanged(gl, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        gl!!
        gl.glClear(GL10.GL_COLOR_BUFFER_BIT or GL10.GL_DEPTH_BUFFER_BIT)
        gl.glLoadIdentity()
        render?.onDrawFrame(gl)
    }


}