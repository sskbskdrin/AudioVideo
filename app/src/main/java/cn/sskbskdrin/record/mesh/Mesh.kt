package cn.sskbskdrin.record.mesh

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import javax.microedition.khronos.opengles.GL10


/**
 * Created by ex-keayuan001 on 2018/11/30.
 *
 * @author ex-keayuan001
 */

public open class Mesh {
    // Our vertex buffer.
    private var verticesBuffer: FloatBuffer? = null
    // Our index buffer.
    private var indicesBuffer: ShortBuffer? = null
    // The number of indices.
    private var numOfIndices = -1
    // Flat Color
    private val rgba = floatArrayOf(1.0f, 1.0f, 1.0f, 1.0f)
    // Smooth Colors
    private var colorBuffer: FloatBuffer? = null
    // Translate params.
    var x = 0f
    var y = 0f
    var z = -0f
    // Rotate params.
    var rx = 0f
    var ry = 0f
    var rz = 0f
    open fun draw(gl: GL10) {
//        gl.glPushMatrix()
        // Counter-clockwise winding.
        gl.glFrontFace(GL10.GL_CCW)
        // Enable face culling.
        gl.glEnable(GL10.GL_CULL_FACE)
        // What faces to remove with the face culling.
        gl.glCullFace(GL10.GL_BACK)
        // Enabled the vertices buffer for writing and
        //to be used during
        // rendering.
        gl.glEnableClientState(GL10.GL_VERTEX_ARRAY)
        // Specifies the location and data format
        //of an array of vertex
        // coordinates to use when rendering.
        gl.glVertexPointer(3, GL10.GL_FLOAT, 0, verticesBuffer)
        // Set flat color
        gl.glColor4f(rgba[0], rgba[1], rgba[2], rgba[3])
        // Smooth color
        if (colorBuffer != null) {
            // Enable the color array buffer to be
            //used during rendering.
            gl.glEnableClientState(GL10.GL_COLOR_ARRAY)
            gl.glColorPointer(4, GL10.GL_FLOAT, 0, colorBuffer)
        }
        gl.glTranslatef(x, y, z)
        gl.glRotatef(rx, 1f, 0f, 0f)
        gl.glRotatef(ry, 0f, 1f, 0f)
        gl.glRotatef(rz, 0f, 0f, 1f)
        // Point out the where the color buffer is.
        gl.glDrawElements(GL10.GL_TRIANGLES, numOfIndices, GL10.GL_UNSIGNED_SHORT, indicesBuffer)
        // Disable the vertices buffer.
        if (colorBuffer != null) {
            gl.glDisableClientState(GL10.GL_COLOR_ARRAY)
        }
        gl.glDisableClientState(GL10.GL_VERTEX_ARRAY)
        // Disable face culling.
        gl.glDisable(GL10.GL_CULL_FACE)
//        gl.glPopMatrix()
    }

    public fun setVertices(vertices: FloatArray) {
        // a float is 4 bytes, therefore
        //we multiply the number if
        // vertices with 4.
        val vbb = ByteBuffer.allocateDirect(vertices.size * 4)
        vbb.order(ByteOrder.nativeOrder())
        verticesBuffer = vbb.asFloatBuffer()
        verticesBuffer!!.put(vertices)
        verticesBuffer!!.position(0)
    }

    public fun setIndices(indices: ShortArray) {
        // short is 2 bytes, therefore we multiply
        //the number if
        // vertices with 2.
        val ibb = ByteBuffer.allocateDirect(indices.size * 2)
        ibb.order(ByteOrder.nativeOrder())
        indicesBuffer = ibb.asShortBuffer()
        indicesBuffer!!.put(indices)
        indicesBuffer!!.position(0)
        numOfIndices = indices.size
    }

    public fun setColor(red: Float, green: Float,
                        blue: Float, alpha: Float) {
        // Setting the flat color.
        rgba[0] = red
        rgba[1] = green
        rgba[2] = blue
        rgba[3] = alpha
    }

    public fun setColors(colors: FloatArray) {
        // float has 4 bytes.
        val cbb = ByteBuffer.allocateDirect(colors.size * 4)
        cbb.order(ByteOrder.nativeOrder())
        colorBuffer = cbb.asFloatBuffer()
        colorBuffer!!.put(colors)
        colorBuffer!!.position(0)
    }
}

class Plane(width: Float = 1f, height: Float = 1f, widthSegments: Int = 1,
            heightSegments: Int = 1) : Mesh() {
    init {
        val vertices = FloatArray((widthSegments + 1)
                * (heightSegments + 1) * 3)
        val indices = ShortArray((widthSegments + 1)
                * (heightSegments + 1) * 6)
        val xOffset = width / -2
        val yOffset = height / -2
        val xWidth = width / widthSegments
        val yHeight = height / heightSegments
        var currentVertex = 0
        var currentIndex = 0
        val w = (widthSegments + 1).toShort()
        for (y in 0 until heightSegments + 1) {
            for (x in 0 until widthSegments + 1) {
                vertices[currentVertex] = xOffset + x * xWidth
                vertices[currentVertex + 1] = yOffset + y * yHeight
                vertices[currentVertex + 2] = 0f
                currentVertex += 3
                val n = y * (widthSegments + 1) + x
                if (y < heightSegments && x < widthSegments) {
                    // Face one
                    indices[currentIndex] = n.toShort()
                    indices[currentIndex + 1] = (n + 1).toShort()
                    indices[currentIndex + 2] = (n + w).toShort()
                    // Face two
                    indices[currentIndex + 3] = (n + 1).toShort()
                    indices[currentIndex + 4] = (n + 1 + w.toInt()).toShort()
                    indices[currentIndex + 5] = (n + 1 + w.toInt() - 1).toShort()
                    currentIndex += 6
                }
            }
        }
        setIndices(indices)
        setVertices(vertices)
    }
}

class Cube(var width: Float, var height: Float, var depth: Float) : Mesh() {
    val group = MeshGroup()

    init {
        width /= 2f
        height /= 2f
        depth /= 2f
        val vertices = floatArrayOf(-width, -height, -depth, // 0
                width, -height, -depth, // 1
                width, height, -depth, // 2
                -width, height, -depth, // 3
                -width, -height, depth, // 4
                width, -height, depth, // 5
                width, height, depth, // 6
                -width, height, depth)// 7
        val indices = shortArrayOf(0, 4, 5, 0, 5, 1, 1, 5, 6, 1, 6, 2, 2, 6, 7, 2, 7, 3, 3, 7, 4, 3, 4, 0, 4, 7, 6, 4, 6, 5, 3, 0, 1, 3, 1, 2)
        setIndices(indices)
        setVertices(vertices)
        group.add(Square(0.5f))
        group.get(0).z = 0.25f
        group.get(0).x = -0.175f
        group.get(0).ry = -45f
        group.get(0).rx = 30f
        group.get(0).setColor(1f, 0f, 0f, 1f)
        group.add(Square(0.5f))
        group.get(1).z = 0.25f
        group.get(1).x = 0.175f
        group.get(1).ry = 45f
        group.get(1).rx = 30f
        group.get(1).setColor(0f, 1f, 0f, 1f)
//        group.add(Square(0.5f))
    }

    override fun draw(gl: GL10) {
        group.draw(gl)
    }
}

class Square(var width: Float) : Mesh() {

    init {
//        val vertices = floatArrayOf(
//                0.5f, 0.28125f, -0.5f,  // 0, Top Left
//                -0.5f, 0.28125f, -0.5f,  // 1, Bottom Left
//                -0.5f, -0.28125f, -0.5f,  // 2, Bottom Right
//                0.5f, -0.28125f, -0.5f,  // 3, Top Right
//                0.5f, 0.28125f, 0.5f,
//                -0.5f, 0.28125f, 0.5f,
//                -0.5f, -0.28125f, 0.5f,
//                0.5f, -0.28125f, 0.5f
//        )
//        val indices = shortArrayOf(
//                0, 1, 2,
//                0, 2, 3,
//
//                0, 5, 4,
//                0, 1, 5,
//
//                0, 4, 7,
//                0, 7, 3,
//
//                6, 5, 1,
//                6, 1, 2,
//
//                6, 7, 2,
//                6, 3, 2,
//
//                6, 7, 4,
//                6, 4, 5
//        )
        width /= 2
        val height = width * 0.5625f
        val vertices = floatArrayOf(
                -width, height, 0f,
                -width, -height, 0f,
                width, -height, 0f,
                width, height, 0f
        )
        val indices = shortArrayOf(0, 1, 2, 0, 2, 3)
        setIndices(indices)
        setVertices(vertices)
    }
}

class MeshGroup : Mesh() {

    private val child = ArrayList<Mesh>()

    init {
        setVertices(floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f))
        setIndices(shortArrayOf(0, 1, 2))
    }

    fun contains(element: Mesh): Boolean {
        return child.contains(element)
    }

    fun get(index: Int): Mesh {
        return child[index]
    }

    fun indexOf(element: Mesh): Int {
        return child.indexOf(element)
    }

    fun isEmpty(): Boolean {
        return child.isEmpty()
    }

    fun lastIndexOf(element: Mesh): Int {
        return child.lastIndexOf(element)
    }

    fun add(element: Mesh): Boolean {
        return child.add(element)
    }

    fun add(index: Int, element: Mesh) {
        child.add(index, element)
    }

    fun addAll(index: Int, elements: Collection<Mesh>): Boolean {
        return child.addAll(index, elements)
    }

    fun addAll(elements: Collection<Mesh>): Boolean {
        return child.addAll(elements)
    }

    fun clear() {
        child.clear()
    }

    fun remove(element: Mesh): Boolean {
        return child.remove(element)
    }

    override fun draw(gl: GL10) {
        for (mesh in child) {
            mesh.draw(gl)
        }
        super.draw(gl)
    }
}