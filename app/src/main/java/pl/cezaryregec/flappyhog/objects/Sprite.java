package pl.cezaryregec.flappyhog.objects;

import android.opengl.GLES20;
import android.opengl.Matrix;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.nio.channels.Pipe;

import pl.cezaryregec.flappyhog.pl.cezaryregec.flappyhog.view.FHRenderer;

public class Sprite {

    // number of coordinates
    public static final int COORDS_PER_VERTEX = 3;
    public static final int COORDS_PER_TEXTURE = 2;

    private final int vertexStride = COORDS_PER_VERTEX * 4; // 4 bytes per vertex

    public float[] collision_margin = {
            0.05f, // horizontal
            0.05f  // vertical
    };

    // default drawing mode
    protected int DRAW_MODE = GLES20.GL_TRIANGLES;

    // Sprite and texture adjustments
    public float[] rotation = { 0.0f, 0.0f, 0.0f };
    public float[] position = { 0.0f, 0.0f, 0.0f };
    public float[] scale    = { 1.0f, 1.0f, 1.0f };
    public float[] color    = { 1.0f, 1.0f, 1.0f, 1.0f };

    // Animated rotation
    public float[] target_rotation = { 0.0f, 0.0f, 0.0f };
    public float[] rotation_speed = { 0.0f, 0.0f, 0.0f };
    public float[] rotation_acceleration = { 0.0f, 0.0f, 0.0f };

    // Animated movement
    public float[] target_position = { 0.0f, 0.0f, 0.0f };
    public float[] movement_speed = { 0.0f, 0.0f, 0.0f };
    public float[] movement_acceleration = { 0.0f, 0.0f, 0.0f };

    // Animated scaling
    public float[] target_scale = { 0.0f, 0.0f, 0.0f };
    public float[] scaling_speed = { 0.0f, 0.0f, 0.0f };
    public float[] scaling_acceleration = { 0.0f, 0.0f, 0.0f };

    // scrolling
    public boolean scroll = false;
    public float[] SCROLL_SPEED = { 0.005f, 0.0f };
    public float[] scroll_value = { 0.0f, 0.0f };

    // animation
    public boolean animation = false;
    public int[] animation_blocks = { 1, 1 };
    public int frame_time = 10;

    private int[] current_frame = { 0, 0 };
    private int animation_timer = 0;

    // Buffers
    private FloatBuffer vertexBuffer;
    private FloatBuffer textureBuffer;
    private ShortBuffer drawListBuffer;

    // Use to access and set the view transformation
    private int mMVPMatrixHandle;
    private int mPositionHandle;
    private int mColorHandle;

    private int mTextureUniformHandle;
    private int mTextureCoordinateHandle;

    // Texture handle hook
    public int mTextureHandle = -1;

    // Transformation matrix
    protected float[] result_matrix = new float[16];


    // Coordinates
    protected float squareCoords[] = {
            -1.0f,  1.0f, 0.0f,   // top left
            -1.0f, -1.0f, 0.0f,   // bottom left
             1.0f, -1.0f, 0.0f,   // bottom right
             1.0f,  1.0f, 0.0f }; // top right

    // Texture coordinates
    protected float textureCoords[] = {
            1.0f, 0.0f,
            1.0f, 1.0f,
            0.0f, 1.0f,
            0.0f, 0.0f };

    private short drawOrder[] = { 0, 1, 2, 0, 2, 3 }; // order to draw vertices


    private final String vertexShaderCode =
            // This matrix member variable provides a hook to manipulate
            // the coordinates of the objects that use this vertex shader
            "uniform mat4 uMVPMatrix;" +
            "attribute vec4 vPosition;" +
            // Texture
            "attribute vec2 a_TexCoordinate;" +
            "varying vec2 v_TexCoordinate;" +
            "void main() {" +
            // the matrix must be included as a modifier of gl_Position
            // Note that the uMVPMatrix factor *must be first* in order
            // for the matrix multiplication product to be correct.
            "  gl_Position = uMVPMatrix * vPosition;" +
            "  v_TexCoordinate = a_TexCoordinate;" +
            "}";


    private final String fragmentShaderCode =
            "precision mediump float;" +
            "uniform vec4 vColor;" +
            "uniform sampler2D u_Texture;" +
            "varying vec2 v_TexCoordinate;" +
            "void main() {" +
            //"  gl_FragColor = vColor;" +
            "  gl_FragColor = vColor * texture2D(u_Texture, v_TexCoordinate);" +
            "}";

    // Shaders program
    private final int mProgram;


    public Sprite(int textureId) {
        mTextureHandle = textureId;

        // initialize vertex byte buffer for shape coordinates
        ByteBuffer bb = ByteBuffer.allocateDirect(
                // (# of coordinate values * 4 bytes per float)
                squareCoords.length * 4);

        bb.order(ByteOrder.nativeOrder());
        vertexBuffer = bb.asFloatBuffer();
        vertexBuffer.put(squareCoords);
        vertexBuffer.position(0);

        // initialize byte buffer for the draw list
        ByteBuffer dlb = ByteBuffer.allocateDirect(
                // (# of coordinate values * 2 bytes per short)
                drawOrder.length * 2);
        dlb.order(ByteOrder.nativeOrder());
        drawListBuffer = dlb.asShortBuffer();
        drawListBuffer.put(drawOrder);
        drawListBuffer.position(0);

        initTexture();

        // Shaders
        int vertexShader = FHRenderer.loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode);
        int fragmentShader = FHRenderer.loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode);

        // Prepare
        mProgram = GLES20.glCreateProgram();

        // Attach GLES20 shaders
        GLES20.glAttachShader(mProgram, vertexShader);
        GLES20.glAttachShader(mProgram, fragmentShader);

        // Bind texture
        GLES20.glBindAttribLocation(mProgram, 0, "a_TexCoordinate");

        // Link GLES20 shaders
        GLES20.glLinkProgram(mProgram);

        Log.i("GLES", GLES20.glGetProgramInfoLog(mProgram));
    }

    private void initTexture() {
        // initialize byte buffer for the texture
        ByteBuffer tb = ByteBuffer.allocateDirect(
                // (# of coordinate values * 2 bytes per float)
                textureCoords.length * 4);
        tb.order(ByteOrder.nativeOrder());
        textureBuffer = tb.asFloatBuffer();
        textureBuffer.put(textureCoords);
        textureBuffer.position(0);
    }

    public void draw(float[] mvpMatrix) {
        // if has to scroll
        if(scroll) {
            scroll_value[0] += SCROLL_SPEED[0]; // horizontal scroll
            scroll_value[1] += SCROLL_SPEED[1]; // vertical scroll

            // maximum horizontal scroll
            if(scroll_value[0] >= 1.0f) {
                scroll_value[0] = 0.0f; // reset
            }

            // maximum vertical scroll
            if(scroll_value[1] >= 1.0f) {
                scroll_value[1] = 0.0f; // reset
            }

            // update texture coords
            textureCoords = new float[]{
                    1.0f + scroll_value[0], scroll_value[1],
                    1.0f + scroll_value[0], 1.0f + scroll_value[1],
                    scroll_value[0], 1.0f + scroll_value[1],
                    scroll_value[0], scroll_value[1]
            };

            initTexture();
        }

        // if animated
        if(animation) {
            // if maximum horizontal frames reached
            if(current_frame[0] >= animation_blocks[0]) {
                current_frame[0] = 0; // return
                current_frame[1]++;   // new line
            }

            // if maximum vertical frames reached
            if(current_frame[1] >= animation_blocks[1]) {
                current_frame[1] = 0; // reset
            }

            // get frame
            textureBlock(current_frame[0], current_frame[1], animation_blocks[0], animation_blocks[1]);

            // count frame
            animation_timer++;

            if(animation_timer >= frame_time) {
                current_frame[0]++;  // next frame
                animation_timer = 0; // reset timer
            }
        }

        // Add program to OpenGL ES environment
        GLES20.glUseProgram(mProgram);

        // Translate sprite
        translateRotateScale(result_matrix, mvpMatrix);

        // get handle to vertex shader's vPosition member
        mPositionHandle = GLES20.glGetAttribLocation(mProgram, "vPosition");

        // Enable a handle to the triangle vertices
        GLES20.glEnableVertexAttribArray(mPositionHandle);

        // Prepare the coordinate data
        GLES20.glVertexAttribPointer(mPositionHandle, COORDS_PER_VERTEX,
                GLES20.GL_FLOAT, false,
                vertexStride, vertexBuffer);

        // get handle to fragment shader's vColor member
        mColorHandle = GLES20.glGetUniformLocation(mProgram, "vColor");

        // Set color for drawing the triangle
        GLES20.glUniform4fv(mColorHandle, 1, color, 0);

        // Set Texture Handles
        mTextureUniformHandle = GLES20.glGetAttribLocation(mProgram, "u_Texture");
        mTextureCoordinateHandle = GLES20.glGetAttribLocation(mProgram, "a_TexCoordinate");

        //Set the active texture unit to texture unit 0.
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);

        //Bind the texture to this unit.
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextureHandle);

        //Tell the texture uniform sampler to use this texture in the shader by binding to texture unit 0.
        GLES20.glUniform1i(mTextureUniformHandle, 0);

        // Map texture coordinates
        GLES20.glVertexAttribPointer(mTextureCoordinateHandle, COORDS_PER_TEXTURE, GLES20.GL_FLOAT, false, 0, textureBuffer);
        GLES20.glEnableVertexAttribArray(mTextureCoordinateHandle);

        // Get handle to shape's transformation matrix
        mMVPMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uMVPMatrix");

        // Pass the projection and view transformation to the shader
        GLES20.glUniformMatrix4fv(mMVPMatrixHandle, 1, false, result_matrix, 0);

        // Eventually draw the element
        GLES20.glDrawElements(DRAW_MODE, drawOrder.length, GLES20.GL_UNSIGNED_SHORT, drawListBuffer);

        // Reset arrays
        GLES20.glDisableVertexAttribArray(mTextureCoordinateHandle);
        GLES20.glDisableVertexAttribArray(mPositionHandle);
    }

    private void translateRotateScale(float[] matrix, float[] perspectiveMatrix)
    {
        for (int i= 0; i < perspectiveMatrix.length;i++)
            matrix[i] = perspectiveMatrix[i];

        nextRotationStep();
        nextMovementStep();
        nextScalingStep();

        Matrix.translateM(matrix, 0, position[0], position[1], position[2]);
        Matrix.rotateM(matrix, 0, rotation[0], 1.0f, 0.0f, 0.0f);
        Matrix.rotateM(matrix, 0, rotation[1], 0.0f, 1.0f, 0.0f);
        Matrix.rotateM(matrix, 0, rotation[2], 0.0f, 0.0f, 1.0f);
        Matrix.scaleM(matrix, 0, scale[0], scale[1], scale[2]);
    }

    private void nextRotationStep() {
        // for every axis
        for(int i = 0; i < rotation.length; i++) {

            // if can rotate correctly
            if(canMove(rotation[i], target_rotation[i], rotation_speed[i], rotation_acceleration[i])) {

                rotation_speed[i] += rotation_acceleration[i]; // add acceleration
                rotation[i] += rotation_speed[i]; // add speed
            } else {
                rotation_speed[i] = 0.0f;
            }
        }
    }

    private void nextMovementStep() {
        // for every axis
        for(int i = 0; i < position.length; i++) {

            // if can move correctly
            if(canMove(position[i], target_position[i], movement_speed[i], movement_acceleration[i])) {

                movement_speed[i] += movement_acceleration[i]; // add acceleration
                position[i] += movement_speed[i]; // add speed
            } else {
                movement_speed[i] = 0.0f;
            }
        }
    }

    private void nextScalingStep() {
        // for every axis
        for(int i = 0; i < scale.length; i++) {

            // if can move correctly
            if(canMove(scale[i], target_scale[i], scaling_speed[i], scaling_acceleration[i])) {

                scaling_speed[i] += scaling_acceleration[i]; // add acceleration
                scale[i] += scaling_speed[i]; // add speed
            } else {
                scaling_speed[i] = 0.0f;
            }
        }
    }

    private boolean canMove(float current, float target, float speed, float acceleration) {

        // Checks if an object can move in a direction with current speed or acceleration
        return ((current < target && (speed > 0 || acceleration > 0)
                || (current > target && (speed < 0 || acceleration < 0)))
                && (target * speed >= 0)
                && (speed * acceleration >= 0));
    }


    public void textureBlock(int x, int y, int maxX, int maxY) {
        // Cut into blocks (get block size)
        float stepX = 1.0f / maxX; // horizontal
        float stepY = 1.0f / maxY; // vertical

        // Get size location
        float currentX = stepX * x;
        float currentY = stepY * y;

        // Set new coords (for a block)
        textureCoords = new float[]{
                currentX + stepX, currentY,
                currentX + stepX, currentY + stepY,
                currentX, currentY + stepY,
                currentX, currentY
        };

        initTexture();
    }

    public boolean isTouching(Sprite obj) {

        // this sprite
        float this_right = position[0] + scale[0] - collision_margin[0];
        float this_left = position[0] - scale[0] + collision_margin[0];
        float this_top = position[1] + scale[1] - collision_margin[1];
        float this_bottom = position[1] - scale[1] + collision_margin[1];

        // obj coordinates
        float obj_right = obj.position[0] + obj.scale[0] - obj.collision_margin[0];
        float obj_left = obj.position[0] - obj.scale[0] + obj.collision_margin[0];
        float obj_top = obj.position[1] + obj.scale[1] - obj.collision_margin[1];
        float obj_bottom = obj.position[1] - obj.scale[1] + obj.collision_margin[1];


        return (this_left < obj_right && this_right > obj_left
                && this_top > obj_bottom && this_bottom < obj_top);
    }
}
