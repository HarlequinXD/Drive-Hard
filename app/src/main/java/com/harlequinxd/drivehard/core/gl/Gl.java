package com.harlequinxd.drivehard.core.gl;

import java.nio.Buffer;

/**
 * The OpenGL ES 3.0 calls the GPU renderer uses, so the renderer is platform independent: Android
 * forwards them to {@code android.opengl.GLES30}; the desktop test tool calls the system's GL
 * library directly. All buffers passed in are direct, native-order NIO buffers.
 */
public interface Gl {
    int NO_ERROR = 0;
    int DEPTH_BUFFER_BIT = 0x0100, COLOR_BUFFER_BIT = 0x4000;
    int TRIANGLES = 0x0004, TRIANGLE_STRIP = 0x0005;
    int ZERO = 0, ONE = 1, SRC_ALPHA = 0x0302, ONE_MINUS_SRC_ALPHA = 0x0303, DST_COLOR = 0x0306;
    int CULL_FACE = 0x0B44, DEPTH_TEST = 0x0B71, BLEND = 0x0BE2, SCISSOR_TEST = 0x0C11;
    int LEQUAL = 0x0203, LESS = 0x0201, ALWAYS = 0x0207;
    int UNPACK_ALIGNMENT = 0x0CF5, PACK_ALIGNMENT = 0x0D05;
    int TEXTURE_2D = 0x0DE1, TEXTURE_2D_ARRAY = 0x8C1A;
    int BYTE = 0x1400, UNSIGNED_BYTE = 0x1401, SHORT = 0x1402, UNSIGNED_SHORT = 0x1403, INT = 0x1404,
            UNSIGNED_INT = 0x1405, FLOAT = 0x1406, UNSIGNED_SHORT_5_6_5 = 0x8363;
    int RED = 0x1903, RGB = 0x1907, RGBA = 0x1908, R8 = 0x8229, RG = 0x8227, RG8 = 0x822B, RGBA8 = 0x8058, RGB565 = 0x8D62;
    int NEAREST = 0x2600, LINEAR = 0x2601, NEAREST_MIPMAP_NEAREST = 0x2700;
    int TEXTURE_MAG_FILTER = 0x2800, TEXTURE_MIN_FILTER = 0x2801, TEXTURE_WRAP_S = 0x2802, TEXTURE_WRAP_T = 0x2803,
            TEXTURE_MAX_LEVEL = 0x813D, TEXTURE_BASE_LEVEL = 0x813C;
    int REPEAT = 0x2901, CLAMP_TO_EDGE = 0x812F;
    int TEXTURE0 = 0x84C0;
    int ARRAY_BUFFER = 0x8892, ELEMENT_ARRAY_BUFFER = 0x8893, STATIC_DRAW = 0x88E4, STREAM_DRAW = 0x88E0, DYNAMIC_DRAW = 0x88E8;
    int FRAGMENT_SHADER = 0x8B30, VERTEX_SHADER = 0x8B31, COMPILE_STATUS = 0x8B81, LINK_STATUS = 0x8B82;
    int FRAMEBUFFER = 0x8D40, RENDERBUFFER = 0x8D41, COLOR_ATTACHMENT0 = 0x8CE0, DEPTH_ATTACHMENT = 0x8D00,
            DEPTH_COMPONENT24 = 0x81A6, DEPTH_COMPONENT16 = 0x81A5, FRAMEBUFFER_COMPLETE = 0x8CD5;
    int MAX_TEXTURE_SIZE = 0x0D33;

    int createShader(int type);
    void shaderSource(int shader, String source);
    void compileShader(int shader);
    int getShaderi(int shader, int pname);
    String getShaderInfoLog(int shader);
    void deleteShader(int shader);
    int createProgram();
    void attachShader(int program, int shader);
    void bindAttribLocation(int program, int index, String name);
    void linkProgram(int program);
    int getProgrami(int program, int pname);
    String getProgramInfoLog(int program);
    void deleteProgram(int program);
    void useProgram(int program);
    int getUniformLocation(int program, String name);
    void uniform1i(int loc, int v);
    void uniform1f(int loc, float v);
    void uniform2f(int loc, float a, float b);
    void uniform3f(int loc, float a, float b, float c);
    void uniform4f(int loc, float a, float b, float c, float d);

    int genBuffer();
    void deleteBuffer(int buffer);
    void bindBuffer(int target, int buffer);
    void bufferData(int target, int size, Buffer data, int usage);
    void bufferSubData(int target, int offset, int size, Buffer data);
    int genVertexArray();
    void deleteVertexArray(int vao);
    void bindVertexArray(int vao);
    void enableVertexAttribArray(int index);
    void vertexAttribPointer(int index, int size, int type, boolean normalized, int stride, int offset);
    void vertexAttribIPointer(int index, int size, int type, int stride, int offset);

    int genTexture();
    void deleteTexture(int texture);
    void bindTexture(int target, int texture);
    void activeTexture(int unit);
    void texParameteri(int target, int pname, int value);
    void pixelStorei(int pname, int value);
    void texImage2D(int target, int level, int internalFormat, int w, int h, int format, int type, Buffer data);
    void texSubImage2D(int target, int level, int x, int y, int w, int h, int format, int type, Buffer data);
    void texImage3D(int target, int level, int internalFormat, int w, int h, int depth, int format, int type, Buffer data);
    void texSubImage3D(int target, int level, int x, int y, int z, int w, int h, int depth, int format, int type, Buffer data);

    int genFramebuffer();
    void deleteFramebuffer(int fbo);
    void bindFramebuffer(int target, int fbo);
    void framebufferTexture2D(int target, int attachment, int texTarget, int texture, int level);
    int genRenderbuffer();
    void deleteRenderbuffer(int rb);
    void bindRenderbuffer(int target, int rb);
    void renderbufferStorage(int target, int internalFormat, int w, int h);
    void framebufferRenderbuffer(int target, int attachment, int rbTarget, int rb);
    int checkFramebufferStatus(int target);

    void viewport(int x, int y, int w, int h);
    void clearColor(float r, float g, float b, float a);
    void clear(int mask);
    void enable(int cap);
    void disable(int cap);
    void blendFunc(int src, int dst);
    void depthFunc(int func);
    void depthMask(boolean write);
    void drawArrays(int mode, int first, int count);
    void drawElements(int mode, int count, int type, int offset);
    int getError();
    int getInteger(int pname);
    void readPixels(int x, int y, int w, int h, int format, int type, Buffer data);
}
