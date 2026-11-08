package com.harlequinxd.drivehard.desktop;

import com.harlequinxd.drivehard.core.gl.Gl;
import com.sun.jna.Function;
import com.sun.jna.Memory;
import com.sun.jna.Pointer;

import java.nio.Buffer;
import java.util.HashMap;
import java.util.Map;

/**
 * {@link Gl} on a desktop OpenGL driver through JNA: every entry point is looked up once (through
 * the context's loader) and called directly. Works with an OpenGL ES 3 context, or with a desktop
 * core-profile context, in which case the renderer's GLSL ES 3.00 shaders are rewritten to GLSL
 * 3.30 (the same language for everything they use). The context must be current on the calling
 * thread.
 */
public final class JnaGl implements Gl {
    /** Finds a GL entry point ("glClear"), or returns null. */
    public interface Loader { Pointer proc(String name); }

    private final Loader loader;
    private final boolean desktop;
    private final Map<String, Function> fns = new HashMap<>();
    private final int[] one = new int[1];

    public JnaGl(Loader loader, boolean desktop) {
        this.loader = loader;
        this.desktop = desktop;
    }

    private Function f(String name) {
        Function fn = fns.get(name);
        if (fn == null) {
            Pointer p = loader.proc("gl" + name);
            if (p == null) throw new IllegalStateException("OpenGL function missing: gl" + name);
            fn = Function.getFunction(p);
            fns.put(name, fn);
        }
        return fn;
    }

    private void v(String name, Object... args) { f(name).invokeVoid(args); }
    private int i(String name, Object... args) { return f(name).invokeInt(args); }
    private static Pointer off(long o) { return o == 0 ? null : Pointer.createConstant(o); }

    private int gen(String name) {
        int[] a = new int[1];
        v(name, 1, a);
        return a[0];
    }

    private void del(String name, int id) { v(name, 1, new int[]{id}); }

    // ------------------------------------------------------------------ shaders

    @Override public int createShader(int type) { return i("CreateShader", type); }

    @Override public void shaderSource(int shader, String source) {
        if (desktop) source = toDesktop(source);
        v("ShaderSource", shader, 1, new String[]{source}, null);
    }

    /** GLSL ES 3.00 to GLSL 3.30 core: the version line, and no default precisions. */
    static String toDesktop(String src) {
        StringBuilder out = new StringBuilder();
        for (String line : src.split("\n", -1)) {
            String t = line.trim();
            if (t.startsWith("#version")) { out.append("#version 330 core\n"); continue; }
            if (t.startsWith("precision ")) continue;
            out.append(line).append('\n');
        }
        return out.toString();
    }

    @Override public void compileShader(int shader) { v("CompileShader", shader); }

    @Override public int getShaderi(int shader, int pname) {
        v("GetShaderiv", shader, pname, one);
        return one[0];
    }

    @Override public String getShaderInfoLog(int shader) {
        Memory m = new Memory(8192);
        v("GetShaderInfoLog", shader, 8191, null, m);
        return m.getString(0);
    }

    @Override public void deleteShader(int shader) { v("DeleteShader", shader); }
    @Override public int createProgram() { return i("CreateProgram"); }
    @Override public void attachShader(int program, int shader) { v("AttachShader", program, shader); }
    @Override public void bindAttribLocation(int program, int index, String name) { v("BindAttribLocation", program, index, name); }
    @Override public void linkProgram(int program) { v("LinkProgram", program); }

    @Override public int getProgrami(int program, int pname) {
        v("GetProgramiv", program, pname, one);
        return one[0];
    }

    @Override public String getProgramInfoLog(int program) {
        Memory m = new Memory(8192);
        v("GetProgramInfoLog", program, 8191, null, m);
        return m.getString(0);
    }

    @Override public void deleteProgram(int program) { v("DeleteProgram", program); }
    @Override public void useProgram(int program) { v("UseProgram", program); }
    @Override public int getUniformLocation(int program, String name) { return i("GetUniformLocation", program, name); }
    @Override public void uniform1i(int loc, int x) { v("Uniform1i", loc, x); }
    @Override public void uniform1f(int loc, float x) { v("Uniform1f", loc, x); }
    @Override public void uniform2f(int loc, float a, float b) { v("Uniform2f", loc, a, b); }
    @Override public void uniform3f(int loc, float a, float b, float c) { v("Uniform3f", loc, a, b, c); }
    @Override public void uniform4f(int loc, float a, float b, float c, float d) { v("Uniform4f", loc, a, b, c, d); }
    @Override public void uniform4fv(int loc, int count, float[] values) { v("Uniform4fv", loc, count, values); }

    // ------------------------------------------------------------------ buffers

    @Override public int genBuffer() { return gen("GenBuffers"); }
    @Override public void deleteBuffer(int buffer) { del("DeleteBuffers", buffer); }
    @Override public void bindBuffer(int target, int buffer) { v("BindBuffer", target, buffer); }
    @Override public void bufferData(int target, int size, Buffer data, int usage) { v("BufferData", target, (long) size, data, usage); }
    @Override public void bufferSubData(int target, int offset, int size, Buffer data) { v("BufferSubData", target, (long) offset, (long) size, data); }
    @Override public int genVertexArray() { return gen("GenVertexArrays"); }
    @Override public void deleteVertexArray(int vao) { del("DeleteVertexArrays", vao); }
    @Override public void bindVertexArray(int vao) { v("BindVertexArray", vao); }
    @Override public void enableVertexAttribArray(int index) { v("EnableVertexAttribArray", index); }

    @Override public void vertexAttribPointer(int index, int size, int type, boolean normalized, int stride, int offset) {
        v("VertexAttribPointer", index, size, type, normalized ? 1 : 0, stride, off(offset));
    }

    @Override public void vertexAttribIPointer(int index, int size, int type, int stride, int offset) {
        v("VertexAttribIPointer", index, size, type, stride, off(offset));
    }

    // ------------------------------------------------------------------ textures

    @Override public int genTexture() { return gen("GenTextures"); }
    @Override public void deleteTexture(int texture) { del("DeleteTextures", texture); }
    @Override public void bindTexture(int target, int texture) { v("BindTexture", target, texture); }
    @Override public void activeTexture(int unit) { v("ActiveTexture", unit); }
    @Override public void texParameteri(int target, int pname, int value) { v("TexParameteri", target, pname, value); }
    @Override public void pixelStorei(int pname, int value) { v("PixelStorei", pname, value); }

    @Override public void texImage2D(int target, int level, int internalFormat, int w, int h, int format, int type, Buffer data) {
        v("TexImage2D", target, level, internalFormat, w, h, 0, format, type, data);
    }

    @Override public void texSubImage2D(int target, int level, int x, int y, int w, int h, int format, int type, Buffer data) {
        v("TexSubImage2D", target, level, x, y, w, h, format, type, data);
    }

    @Override public void texImage3D(int target, int level, int internalFormat, int w, int h, int depth, int format, int type, Buffer data) {
        v("TexImage3D", target, level, internalFormat, w, h, depth, 0, format, type, data);
    }

    @Override public void texSubImage3D(int target, int level, int x, int y, int z, int w, int h, int depth, int format, int type, Buffer data) {
        v("TexSubImage3D", target, level, x, y, z, w, h, depth, format, type, data);
    }

    // ------------------------------------------------------------------ frame buffers

    @Override public int genFramebuffer() { return gen("GenFramebuffers"); }
    @Override public void deleteFramebuffer(int fbo) { del("DeleteFramebuffers", fbo); }
    @Override public void bindFramebuffer(int target, int fbo) { v("BindFramebuffer", target, fbo); }

    @Override public void framebufferTexture2D(int target, int attachment, int texTarget, int texture, int level) {
        v("FramebufferTexture2D", target, attachment, texTarget, texture, level);
    }

    @Override public int genRenderbuffer() { return gen("GenRenderbuffers"); }
    @Override public void deleteRenderbuffer(int rb) { del("DeleteRenderbuffers", rb); }
    @Override public void bindRenderbuffer(int target, int rb) { v("BindRenderbuffer", target, rb); }
    @Override public void renderbufferStorage(int target, int internalFormat, int w, int h) { v("RenderbufferStorage", target, internalFormat, w, h); }

    @Override public void framebufferRenderbuffer(int target, int attachment, int rbTarget, int rb) {
        v("FramebufferRenderbuffer", target, attachment, rbTarget, rb);
    }

    @Override public int checkFramebufferStatus(int target) { return i("CheckFramebufferStatus", target); }

    // ------------------------------------------------------------------ state and drawing

    @Override public void viewport(int x, int y, int w, int h) { v("Viewport", x, y, w, h); }
    @Override public void clearColor(float r, float g, float b, float a) { v("ClearColor", r, g, b, a); }
    @Override public void clear(int mask) { v("Clear", mask); }
    @Override public void enable(int cap) { v("Enable", cap); }
    @Override public void disable(int cap) { v("Disable", cap); }
    @Override public void blendFunc(int src, int dst) { v("BlendFunc", src, dst); }
    @Override public void depthFunc(int func) { v("DepthFunc", func); }
    @Override public void depthMask(boolean write) { v("DepthMask", write ? 1 : 0); }
    @Override public void drawArrays(int mode, int first, int count) { v("DrawArrays", mode, first, count); }
    @Override public void drawElements(int mode, int count, int type, int offset) { v("DrawElements", mode, count, type, off(offset)); }
    @Override public int getError() { return i("GetError"); }

    /** glGetString (vendor 0x1F00, renderer 0x1F01, version 0x1F02). */
    public String getString(int name) {
        Pointer p = f("GetString").invokePointer(new Object[]{name});
        return p == null ? "" : p.getString(0);
    }

    @Override public int getInteger(int pname) {
        v("GetIntegerv", pname, one);
        return one[0];
    }

    @Override public void readPixels(int x, int y, int w, int h, int format, int type, Buffer data) {
        v("ReadPixels", x, y, w, h, format, type, data);
    }
}
