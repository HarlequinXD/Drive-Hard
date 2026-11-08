import com.harlequinxd.drivehard.core.gl.Gl;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.nio.Buffer;
import java.util.HashMap;
import java.util.Map;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

/**
 * Desktop test binding of {@link Gl}: a headless OpenGL ES 3 context (EGL, surfaceless) on the
 * system's driver (Mesa's llvmpipe works), called through Java's foreign function API. Lets the
 * GPU renderer be run and screenshotted without a phone. Needs JDK 21 with --enable-preview
 * (or JDK 22+).
 */
public final class FfmGl implements Gl {
    private final Linker linker = Linker.nativeLinker();
    private final Arena arena = Arena.global();
    private final SymbolLookup egl, gles;
    private final Map<String, MethodHandle> handles = new HashMap<>();

    public FfmGl() {
        egl = SymbolLookup.libraryLookup("libEGL.so.1", arena);
        gles = SymbolLookup.libraryLookup("libGLESv2.so.2", arena);
        try {
            MemorySegment d = (MemorySegment) h(egl, "eglGetPlatformDisplay", ADDRESS, JAVA_INT, ADDRESS, ADDRESS)
                    .invoke(0x31DD, MemorySegment.NULL, MemorySegment.NULL);   // EGL_PLATFORM_SURFACELESS_MESA
            if ((int) h(egl, "eglInitialize", JAVA_INT, ADDRESS, ADDRESS, ADDRESS).invoke(d, arena.allocate(4), arena.allocate(4)) == 0)
                throw new IllegalStateException("eglInitialize failed");
            h(egl, "eglBindAPI", JAVA_INT, JAVA_INT).invoke(0x30A0);            // EGL_OPENGL_ES_API
            MemorySegment attrs = arena.allocateArray(JAVA_INT, 0x3098, 3, 0x3038);  // CONTEXT_CLIENT_VERSION 3
            MemorySegment ctx = (MemorySegment) h(egl, "eglCreateContext", ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS)
                    .invoke(d, MemorySegment.NULL, MemorySegment.NULL, attrs);
            if (ctx.address() == 0) throw new IllegalStateException("eglCreateContext failed");
            if ((int) h(egl, "eglMakeCurrent", JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS).invoke(d, MemorySegment.NULL, MemorySegment.NULL, ctx) == 0)
                throw new IllegalStateException("eglMakeCurrent failed");
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
    }

    private MethodHandle h(SymbolLookup lib, String name, MemoryLayout ret, MemoryLayout... args) {
        return handles.computeIfAbsent(name, n -> linker.downcallHandle(lib.find(n).orElseThrow(),
                ret == null ? FunctionDescriptor.ofVoid(args) : FunctionDescriptor.of(ret, args)));
    }

    private MethodHandle g(String name, MemoryLayout ret, MemoryLayout... args) { return h(gles, "gl" + name, ret, args); }

    private static MemorySegment seg(Buffer b) { return b == null ? MemorySegment.NULL : MemorySegment.ofBuffer(b); }

    private static RuntimeException wrap(Throwable t) { return t instanceof RuntimeException ? (RuntimeException) t : new IllegalStateException(t); }

    private int i(MethodHandle m, Object... a) { try { return (int) m.invokeWithArguments(a); } catch (Throwable t) { throw wrap(t); } }
    private void v(MethodHandle m, Object... a) { try { m.invokeWithArguments(a); } catch (Throwable t) { throw wrap(t); } }

    private int gen(String name) {
        MemorySegment s = arena.allocate(4);
        v(g(name, null, JAVA_INT, ADDRESS), 1, s);
        return s.get(JAVA_INT, 0);
    }

    private void del(String name, int id) {
        MemorySegment s = arena.allocate(4);
        s.set(JAVA_INT, 0, id);
        v(g(name, null, JAVA_INT, ADDRESS), 1, s);
    }

    @Override public int createShader(int type) { return i(g("CreateShader", JAVA_INT, JAVA_INT), type); }
    @Override public void shaderSource(int shader, String source) {
        try (Arena a = Arena.ofConfined()) {
            MemorySegment str = a.allocateUtf8String(source);
            MemorySegment ptr = a.allocate(ADDRESS);
            ptr.set(ADDRESS, 0, str);
            v(g("ShaderSource", null, JAVA_INT, JAVA_INT, ADDRESS, ADDRESS), shader, 1, ptr, MemorySegment.NULL);
        }
    }
    @Override public void compileShader(int shader) { v(g("CompileShader", null, JAVA_INT), shader); }
    @Override public int getShaderi(int shader, int pname) {
        MemorySegment s = arena.allocate(4);
        v(g("GetShaderiv", null, JAVA_INT, JAVA_INT, ADDRESS), shader, pname, s);
        return s.get(JAVA_INT, 0);
    }
    @Override public String getShaderInfoLog(int shader) {
        MemorySegment s = arena.allocate(8192);
        v(g("GetShaderInfoLog", null, JAVA_INT, JAVA_INT, ADDRESS, ADDRESS), shader, 8192, MemorySegment.NULL, s);
        return s.getUtf8String(0);
    }
    @Override public void deleteShader(int shader) { v(g("DeleteShader", null, JAVA_INT), shader); }
    @Override public int createProgram() { return i(g("CreateProgram", JAVA_INT)); }
    @Override public void attachShader(int program, int shader) { v(g("AttachShader", null, JAVA_INT, JAVA_INT), program, shader); }
    @Override public void bindAttribLocation(int program, int index, String name) {
        try (Arena a = Arena.ofConfined()) { v(g("BindAttribLocation", null, JAVA_INT, JAVA_INT, ADDRESS), program, index, a.allocateUtf8String(name)); }
    }
    @Override public void linkProgram(int program) { v(g("LinkProgram", null, JAVA_INT), program); }
    @Override public int getProgrami(int program, int pname) {
        MemorySegment s = arena.allocate(4);
        v(g("GetProgramiv", null, JAVA_INT, JAVA_INT, ADDRESS), program, pname, s);
        return s.get(JAVA_INT, 0);
    }
    @Override public String getProgramInfoLog(int program) {
        MemorySegment s = arena.allocate(8192);
        v(g("GetProgramInfoLog", null, JAVA_INT, JAVA_INT, ADDRESS, ADDRESS), program, 8192, MemorySegment.NULL, s);
        return s.getUtf8String(0);
    }
    @Override public void deleteProgram(int program) { v(g("DeleteProgram", null, JAVA_INT), program); }
    @Override public void useProgram(int program) { v(g("UseProgram", null, JAVA_INT), program); }
    @Override public int getUniformLocation(int program, String name) {
        try (Arena a = Arena.ofConfined()) { return i(g("GetUniformLocation", JAVA_INT, JAVA_INT, ADDRESS), program, a.allocateUtf8String(name)); }
    }
    @Override public void uniform1i(int loc, int x) { v(g("Uniform1i", null, JAVA_INT, JAVA_INT), loc, x); }
    @Override public void uniform1f(int loc, float x) { v(g("Uniform1f", null, JAVA_INT, JAVA_FLOAT), loc, x); }
    @Override public void uniform2f(int loc, float a, float b) { v(g("Uniform2f", null, JAVA_INT, JAVA_FLOAT, JAVA_FLOAT), loc, a, b); }
    @Override public void uniform3f(int loc, float a, float b, float c) { v(g("Uniform3f", null, JAVA_INT, JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT), loc, a, b, c); }
    @Override public void uniform4fv(int loc, int count, float[] values) {
        try (Arena a = Arena.ofConfined()) {
            MemorySegment m = a.allocateArray(JAVA_FLOAT, values);
            v(g("Uniform4fv", null, JAVA_INT, JAVA_INT, ADDRESS), loc, count, m);
        }
    }
    @Override public void uniform4f(int loc, float a, float b, float c, float d) { v(g("Uniform4f", null, JAVA_INT, JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT), loc, a, b, c, d); }

    @Override public int genBuffer() { return gen("GenBuffers"); }
    @Override public void deleteBuffer(int buffer) { del("DeleteBuffers", buffer); }
    @Override public void bindBuffer(int target, int buffer) { v(g("BindBuffer", null, JAVA_INT, JAVA_INT), target, buffer); }
    @Override public void bufferData(int target, int size, Buffer data, int usage) { v(g("BufferData", null, JAVA_INT, JAVA_LONG, ADDRESS, JAVA_INT), target, (long) size, seg(data), usage); }
    @Override public void bufferSubData(int target, int offset, int size, Buffer data) { v(g("BufferSubData", null, JAVA_INT, JAVA_LONG, JAVA_LONG, ADDRESS), target, (long) offset, (long) size, seg(data)); }
    @Override public int genVertexArray() { return gen("GenVertexArrays"); }
    @Override public void deleteVertexArray(int vao) { del("DeleteVertexArrays", vao); }
    @Override public void bindVertexArray(int vao) { v(g("BindVertexArray", null, JAVA_INT), vao); }
    @Override public void enableVertexAttribArray(int index) { v(g("EnableVertexAttribArray", null, JAVA_INT), index); }
    @Override public void vertexAttribPointer(int index, int size, int type, boolean normalized, int stride, int offset) {
        v(g("VertexAttribPointer", null, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, ADDRESS), index, size, type, normalized ? 1 : 0, stride, MemorySegment.ofAddress(offset));
    }
    @Override public void vertexAttribIPointer(int index, int size, int type, int stride, int offset) {
        v(g("VertexAttribIPointer", null, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, ADDRESS), index, size, type, stride, MemorySegment.ofAddress(offset));
    }

    @Override public int genTexture() { return gen("GenTextures"); }
    @Override public void deleteTexture(int texture) { del("DeleteTextures", texture); }
    @Override public void bindTexture(int target, int texture) { v(g("BindTexture", null, JAVA_INT, JAVA_INT), target, texture); }
    @Override public void activeTexture(int unit) { v(g("ActiveTexture", null, JAVA_INT), unit); }
    @Override public void texParameteri(int target, int pname, int value) { v(g("TexParameteri", null, JAVA_INT, JAVA_INT, JAVA_INT), target, pname, value); }
    @Override public void pixelStorei(int pname, int value) { v(g("PixelStorei", null, JAVA_INT, JAVA_INT), pname, value); }
    @Override public void texImage2D(int target, int level, int internalFormat, int w, int h, int format, int type, Buffer data) {
        v(g("TexImage2D", null, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, ADDRESS), target, level, internalFormat, w, h, 0, format, type, seg(data));
    }
    @Override public void texSubImage2D(int target, int level, int x, int y, int w, int h, int format, int type, Buffer data) {
        v(g("TexSubImage2D", null, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, ADDRESS), target, level, x, y, w, h, format, type, seg(data));
    }
    @Override public void texImage3D(int target, int level, int internalFormat, int w, int h, int depth, int format, int type, Buffer data) {
        v(g("TexImage3D", null, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, ADDRESS), target, level, internalFormat, w, h, depth, 0, format, type, seg(data));
    }
    @Override public void texSubImage3D(int target, int level, int x, int y, int z, int w, int h, int depth, int format, int type, Buffer data) {
        v(g("TexSubImage3D", null, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, ADDRESS), target, level, x, y, z, w, h, depth, format, type, seg(data));
    }

    @Override public int genFramebuffer() { return gen("GenFramebuffers"); }
    @Override public void deleteFramebuffer(int fbo) { del("DeleteFramebuffers", fbo); }
    @Override public void bindFramebuffer(int target, int fbo) { v(g("BindFramebuffer", null, JAVA_INT, JAVA_INT), target, fbo); }
    @Override public void framebufferTexture2D(int target, int attachment, int texTarget, int texture, int level) {
        v(g("FramebufferTexture2D", null, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT), target, attachment, texTarget, texture, level);
    }
    @Override public int genRenderbuffer() { return gen("GenRenderbuffers"); }
    @Override public void deleteRenderbuffer(int rb) { del("DeleteRenderbuffers", rb); }
    @Override public void bindRenderbuffer(int target, int rb) { v(g("BindRenderbuffer", null, JAVA_INT, JAVA_INT), target, rb); }
    @Override public void renderbufferStorage(int target, int internalFormat, int w, int h) { v(g("RenderbufferStorage", null, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT), target, internalFormat, w, h); }
    @Override public void framebufferRenderbuffer(int target, int attachment, int rbTarget, int rb) {
        v(g("FramebufferRenderbuffer", null, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT), target, attachment, rbTarget, rb);
    }
    @Override public int checkFramebufferStatus(int target) { return i(g("CheckFramebufferStatus", JAVA_INT, JAVA_INT), target); }

    @Override public void viewport(int x, int y, int w, int h) { v(g("Viewport", null, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT), x, y, w, h); }
    @Override public void clearColor(float r, float gg, float b, float a) { v(g("ClearColor", null, JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT), r, gg, b, a); }
    @Override public void clear(int mask) { v(g("Clear", null, JAVA_INT), mask); }
    @Override public void enable(int cap) { v(g("Enable", null, JAVA_INT), cap); }
    @Override public void disable(int cap) { v(g("Disable", null, JAVA_INT), cap); }
    @Override public void blendFunc(int src, int dst) { v(g("BlendFunc", null, JAVA_INT, JAVA_INT), src, dst); }
    @Override public void depthFunc(int func) { v(g("DepthFunc", null, JAVA_INT), func); }
    @Override public void depthMask(boolean write) { v(g("DepthMask", null, JAVA_INT), write ? 1 : 0); }
    @Override public void drawArrays(int mode, int first, int count) { v(g("DrawArrays", null, JAVA_INT, JAVA_INT, JAVA_INT), mode, first, count); }
    @Override public void drawElements(int mode, int count, int type, int offset) {
        v(g("DrawElements", null, JAVA_INT, JAVA_INT, JAVA_INT, ADDRESS), mode, count, type, MemorySegment.ofAddress(offset));
    }
    @Override public int getError() { return i(g("GetError", JAVA_INT)); }
    @Override public int getInteger(int pname) {
        MemorySegment s = arena.allocate(4);
        v(g("GetIntegerv", null, JAVA_INT, ADDRESS), pname, s);
        return s.get(JAVA_INT, 0);
    }
    @Override public void readPixels(int x, int y, int w, int h, int format, int type, Buffer data) {
        v(g("ReadPixels", null, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, ADDRESS), x, y, w, h, format, type, seg(data));
    }
}
