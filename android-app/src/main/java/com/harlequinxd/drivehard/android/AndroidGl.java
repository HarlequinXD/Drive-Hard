package com.harlequinxd.drivehard.android;

import android.opengl.GLES30;

import com.harlequinxd.drivehard.core.gl.Gl;

import java.nio.Buffer;

/** {@link Gl} on Android's OpenGL ES 3.0 bindings. */
final class AndroidGl implements Gl {
    private final int[] one = new int[1];

    private int gen1(int kind) {
        switch (kind) {
            case 0: GLES30.glGenBuffers(1, one, 0); break;
            case 1: GLES30.glGenTextures(1, one, 0); break;
            case 2: GLES30.glGenVertexArrays(1, one, 0); break;
            case 3: GLES30.glGenFramebuffers(1, one, 0); break;
            default: GLES30.glGenRenderbuffers(1, one, 0); break;
        }
        return one[0];
    }

    @Override public int createShader(int type) { return GLES30.glCreateShader(type); }
    @Override public void shaderSource(int shader, String source) { GLES30.glShaderSource(shader, source); }
    @Override public void compileShader(int shader) { GLES30.glCompileShader(shader); }
    @Override public int getShaderi(int shader, int pname) { GLES30.glGetShaderiv(shader, pname, one, 0); return one[0]; }
    @Override public String getShaderInfoLog(int shader) { return GLES30.glGetShaderInfoLog(shader); }
    @Override public void deleteShader(int shader) { GLES30.glDeleteShader(shader); }
    @Override public int createProgram() { return GLES30.glCreateProgram(); }
    @Override public void attachShader(int program, int shader) { GLES30.glAttachShader(program, shader); }
    @Override public void bindAttribLocation(int program, int index, String name) { GLES30.glBindAttribLocation(program, index, name); }
    @Override public void linkProgram(int program) { GLES30.glLinkProgram(program); }
    @Override public int getProgrami(int program, int pname) { GLES30.glGetProgramiv(program, pname, one, 0); return one[0]; }
    @Override public String getProgramInfoLog(int program) { return GLES30.glGetProgramInfoLog(program); }
    @Override public void deleteProgram(int program) { GLES30.glDeleteProgram(program); }
    @Override public void useProgram(int program) { GLES30.glUseProgram(program); }
    @Override public int getUniformLocation(int program, String name) { return GLES30.glGetUniformLocation(program, name); }
    @Override public void uniform1i(int loc, int v) { GLES30.glUniform1i(loc, v); }
    @Override public void uniform1f(int loc, float v) { GLES30.glUniform1f(loc, v); }
    @Override public void uniform2f(int loc, float a, float b) { GLES30.glUniform2f(loc, a, b); }
    @Override public void uniform3f(int loc, float a, float b, float c) { GLES30.glUniform3f(loc, a, b, c); }
    @Override public void uniform4f(int loc, float a, float b, float c, float d) { GLES30.glUniform4f(loc, a, b, c, d); }

    @Override public int genBuffer() { return gen1(0); }
    @Override public void deleteBuffer(int buffer) { one[0] = buffer; GLES30.glDeleteBuffers(1, one, 0); }
    @Override public void bindBuffer(int target, int buffer) { GLES30.glBindBuffer(target, buffer); }
    @Override public void bufferData(int target, int size, Buffer data, int usage) { GLES30.glBufferData(target, size, data, usage); }
    @Override public void bufferSubData(int target, int offset, int size, Buffer data) { GLES30.glBufferSubData(target, offset, size, data); }
    @Override public int genVertexArray() { return gen1(2); }
    @Override public void deleteVertexArray(int vao) { one[0] = vao; GLES30.glDeleteVertexArrays(1, one, 0); }
    @Override public void bindVertexArray(int vao) { GLES30.glBindVertexArray(vao); }
    @Override public void enableVertexAttribArray(int index) { GLES30.glEnableVertexAttribArray(index); }
    @Override public void vertexAttribPointer(int index, int size, int type, boolean normalized, int stride, int offset) {
        GLES30.glVertexAttribPointer(index, size, type, normalized, stride, offset);
    }
    @Override public void vertexAttribIPointer(int index, int size, int type, int stride, int offset) {
        GLES30.glVertexAttribIPointer(index, size, type, stride, offset);
    }

    @Override public int genTexture() { return gen1(1); }
    @Override public void deleteTexture(int texture) { one[0] = texture; GLES30.glDeleteTextures(1, one, 0); }
    @Override public void bindTexture(int target, int texture) { GLES30.glBindTexture(target, texture); }
    @Override public void activeTexture(int unit) { GLES30.glActiveTexture(unit); }
    @Override public void texParameteri(int target, int pname, int value) { GLES30.glTexParameteri(target, pname, value); }
    @Override public void pixelStorei(int pname, int value) { GLES30.glPixelStorei(pname, value); }
    @Override public void texImage2D(int target, int level, int internalFormat, int w, int h, int format, int type, Buffer data) {
        GLES30.glTexImage2D(target, level, internalFormat, w, h, 0, format, type, data);
    }
    @Override public void texSubImage2D(int target, int level, int x, int y, int w, int h, int format, int type, Buffer data) {
        GLES30.glTexSubImage2D(target, level, x, y, w, h, format, type, data);
    }
    @Override public void texImage3D(int target, int level, int internalFormat, int w, int h, int depth, int format, int type, Buffer data) {
        GLES30.glTexImage3D(target, level, internalFormat, w, h, depth, 0, format, type, data);
    }
    @Override public void texSubImage3D(int target, int level, int x, int y, int z, int w, int h, int depth, int format, int type, Buffer data) {
        GLES30.glTexSubImage3D(target, level, x, y, z, w, h, depth, format, type, data);
    }

    @Override public int genFramebuffer() { return gen1(3); }
    @Override public void deleteFramebuffer(int fbo) { one[0] = fbo; GLES30.glDeleteFramebuffers(1, one, 0); }
    @Override public void bindFramebuffer(int target, int fbo) { GLES30.glBindFramebuffer(target, fbo); }
    @Override public void framebufferTexture2D(int target, int attachment, int texTarget, int texture, int level) {
        GLES30.glFramebufferTexture2D(target, attachment, texTarget, texture, level);
    }
    @Override public int genRenderbuffer() { return gen1(4); }
    @Override public void deleteRenderbuffer(int rb) { one[0] = rb; GLES30.glDeleteRenderbuffers(1, one, 0); }
    @Override public void bindRenderbuffer(int target, int rb) { GLES30.glBindRenderbuffer(target, rb); }
    @Override public void renderbufferStorage(int target, int internalFormat, int w, int h) { GLES30.glRenderbufferStorage(target, internalFormat, w, h); }
    @Override public void framebufferRenderbuffer(int target, int attachment, int rbTarget, int rb) {
        GLES30.glFramebufferRenderbuffer(target, attachment, rbTarget, rb);
    }
    @Override public int checkFramebufferStatus(int target) { return GLES30.glCheckFramebufferStatus(target); }

    @Override public void viewport(int x, int y, int w, int h) { GLES30.glViewport(x, y, w, h); }
    @Override public void clearColor(float r, float g, float b, float a) { GLES30.glClearColor(r, g, b, a); }
    @Override public void clear(int mask) { GLES30.glClear(mask); }
    @Override public void enable(int cap) { GLES30.glEnable(cap); }
    @Override public void disable(int cap) { GLES30.glDisable(cap); }
    @Override public void blendFunc(int src, int dst) { GLES30.glBlendFunc(src, dst); }
    @Override public void depthFunc(int func) { GLES30.glDepthFunc(func); }
    @Override public void depthMask(boolean write) { GLES30.glDepthMask(write); }
    @Override public void drawArrays(int mode, int first, int count) { GLES30.glDrawArrays(mode, first, count); }
    @Override public void drawElements(int mode, int count, int type, int offset) { GLES30.glDrawElements(mode, count, type, offset); }
    @Override public int getError() { return GLES30.glGetError(); }
    @Override public int getInteger(int pname) { GLES30.glGetIntegerv(pname, one, 0); return one[0]; }
    @Override public void readPixels(int x, int y, int w, int h, int format, int type, Buffer data) {
        GLES30.glReadPixels(x, y, w, h, format, type, data);
    }
}
