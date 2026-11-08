package com.harlequinxd.drivehard.desktop;

import com.sun.jna.Function;
import com.sun.jna.Memory;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Platform;
import com.sun.jna.Pointer;
import com.sun.jna.WString;

/**
 * An off-screen OpenGL context for the GPU renderer on a PC. The renderer draws into its own frame
 * buffer and the frame is read back into the window's picture, so the context needs no visible
 * surface: on Windows it lives on a hidden 16 x 16 window (WGL), elsewhere it is surfaceless (EGL).
 * A desktop core profile 3.3 context is preferred (the renderer's shaders are translated to GLSL
 * 3.30); an OpenGL ES 3.0 context is the fallback. The context is current on the creating thread.
 */
public final class DesktopGl {
    private DesktopGl() { }

    /** What was made: the GL calls, and a line describing the context. */
    public static final class Context {
        public final JnaGl gl;
        public final String description;
        Context(JnaGl gl, String description) { this.gl = gl; this.description = description; }
    }

    public static Context create() {
        return Platform.isWindows() ? wgl() : egl();
    }

    // ------------------------------------------------------------------ Windows

    private static final int WGL_CONTEXT_MAJOR_VERSION_ARB = 0x2091, WGL_CONTEXT_MINOR_VERSION_ARB = 0x2092,
            WGL_CONTEXT_PROFILE_MASK_ARB = 0x9126, WGL_CONTEXT_CORE_PROFILE_BIT_ARB = 1, WGL_CONTEXT_ES2_PROFILE_BIT_EXT = 4;

    private static Context wgl() {
        NativeLibrary user32 = NativeLibrary.getInstance("user32"), gdi32 = NativeLibrary.getInstance("gdi32");
        NativeLibrary kernel32 = NativeLibrary.getInstance("kernel32"), ogl = NativeLibrary.getInstance("opengl32");
        Pointer hinst = kernel32.getFunction("GetModuleHandleW").invokePointer(new Object[]{null});
        Pointer hwnd = user32.getFunction("CreateWindowExW").invokePointer(new Object[]{
                0, new WString("STATIC"), new WString("Drive Hard GL"), 0x80000000, 0, 0, 16, 16, null, null, hinst, null});
        if (hwnd == null) throw new IllegalStateException("no window for OpenGL");
        Pointer hdc = user32.getFunction("GetDC").invokePointer(new Object[]{hwnd});
        Memory pfd = new Memory(40);
        pfd.clear();
        pfd.setShort(0, (short) 40);
        pfd.setShort(2, (short) 1);
        pfd.setInt(4, 0x4 | 0x20 | 0x1);             // draw to window, support OpenGL, double buffer
        pfd.setByte(9, (byte) 32);                    // colour bits
        pfd.setByte(23, (byte) 24);                   // depth bits
        pfd.setByte(24, (byte) 8);                    // stencil bits
        int fmt = gdi32.getFunction("ChoosePixelFormat").invokeInt(new Object[]{hdc, pfd});
        if (fmt == 0 || gdi32.getFunction("SetPixelFormat").invokeInt(new Object[]{hdc, fmt, pfd}) == 0)
            throw new IllegalStateException("no OpenGL pixel format");
        Function makeCurrent = ogl.getFunction("wglMakeCurrent"), getProc = ogl.getFunction("wglGetProcAddress");
        Pointer legacy = ogl.getFunction("wglCreateContext").invokePointer(new Object[]{hdc});
        if (legacy == null) throw new IllegalStateException("wglCreateContext failed");
        makeCurrent.invokeInt(new Object[]{hdc, legacy});
        Pointer ccaP = getProc.invokePointer(new Object[]{"wglCreateContextAttribsARB"});
        if (!valid(ccaP)) throw new IllegalStateException("OpenGL 3.3 is not supported (no wglCreateContextAttribsARB)");
        Function cca = Function.getFunction(ccaP);
        String ext = "";
        Pointer extP = getProc.invokePointer(new Object[]{"wglGetExtensionsStringARB"});
        if (valid(extP)) {
            Pointer s = Function.getFunction(extP).invokePointer(new Object[]{hdc});
            if (s != null) ext = s.getString(0);
        }
        Pointer ctx = cca.invokePointer(new Object[]{hdc, null, new int[]{WGL_CONTEXT_MAJOR_VERSION_ARB, 3, WGL_CONTEXT_MINOR_VERSION_ARB, 3,
                WGL_CONTEXT_PROFILE_MASK_ARB, WGL_CONTEXT_CORE_PROFILE_BIT_ARB, 0}});
        boolean desktop = true;
        String what = "OpenGL 3.3 core (WGL)";
        if (ctx == null && ext.contains("WGL_EXT_create_context_es2_profile")) {
            ctx = cca.invokePointer(new Object[]{hdc, null, new int[]{WGL_CONTEXT_MAJOR_VERSION_ARB, 3, WGL_CONTEXT_MINOR_VERSION_ARB, 0,
                    WGL_CONTEXT_PROFILE_MASK_ARB, WGL_CONTEXT_ES2_PROFILE_BIT_EXT, 0}});
            desktop = false;
            what = "OpenGL ES 3.0 (WGL)";
        }
        if (ctx == null) throw new IllegalStateException("could not create an OpenGL 3.3 context");
        makeCurrent.invokeInt(new Object[]{hdc, ctx});
        ogl.getFunction("wglDeleteContext").invokeInt(new Object[]{legacy});
        JnaGl gl = new JnaGl(name -> {
            Pointer p = getProc.invokePointer(new Object[]{name});
            if (valid(p)) return p;
            try { return ogl.getFunction(name); } catch (UnsatisfiedLinkError e) { return null; }   // OpenGL 1.1 entry points
        }, desktop);
        return new Context(gl, what + ": " + glString(gl, 0x1F01) + ", " + glString(gl, 0x1F02));
    }

    /** wglGetProcAddress answers 0, 1, 2, 3 or -1 for what it does not have. */
    private static boolean valid(Pointer p) {
        if (p == null) return false;
        long v = Pointer.nativeValue(p);
        return v != 1 && v != 2 && v != 3 && v != -1;
    }

    // ------------------------------------------------------------------ Linux and others

    private static Context egl() {
        NativeLibrary egl = NativeLibrary.getInstance("EGL");
        Pointer dpy = null;
        try { dpy = egl.getFunction("eglGetPlatformDisplay").invokePointer(new Object[]{0x31DD, null, null}); }   // surfaceless (Mesa)
        catch (UnsatisfiedLinkError ignored) { }
        if (dpy == null) dpy = egl.getFunction("eglGetDisplay").invokePointer(new Object[]{null});
        if (dpy == null || egl.getFunction("eglInitialize").invokeInt(new Object[]{dpy, new int[1], new int[1]}) == 0)
            throw new IllegalStateException("eglInitialize failed");
        boolean es = Boolean.getBoolean("gles");
        egl.getFunction("eglBindAPI").invokeInt(new Object[]{es ? 0x30A0 : 0x30A2});
        int[] attrs = es ? new int[]{0x3098, 3, 0x3038}
                : new int[]{0x3098, 3, 0x30FB, 3, 0x30FD, 1, 0x3038};       // 3.3 core profile
        Pointer ctx = egl.getFunction("eglCreateContext").invokePointer(new Object[]{dpy, null, null, attrs});
        if (ctx == null) throw new IllegalStateException("eglCreateContext failed");
        if (egl.getFunction("eglMakeCurrent").invokeInt(new Object[]{dpy, null, null, ctx}) == 0)
            throw new IllegalStateException("eglMakeCurrent failed");
        Function getProc = egl.getFunction("eglGetProcAddress");
        JnaGl gl = new JnaGl(name -> getProc.invokePointer(new Object[]{name}), !es);
        return new Context(gl, (es ? "OpenGL ES 3.0" : "OpenGL 3.3 core") + " (EGL): " + glString(gl, 0x1F01) + ", " + glString(gl, 0x1F02));
    }

    private static String glString(JnaGl gl, int name) {
        try { return gl.getString(name); } catch (Throwable t) { return "?"; }
    }
}
