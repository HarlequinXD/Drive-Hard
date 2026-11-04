package com.harlequinxd.drivehard.desktop;

import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.Native;
import com.sun.jna.Structure;

/** Windows XInput 1.4 (Xbox 360/One/Series controllers). */
public final class XboxInput {
    private interface XInput extends StdCallLibrary {
        int XInputGetState(int index, State state);
    }
    @Structure.FieldOrder({"wButtons", "bLeftTrigger", "bRightTrigger", "sThumbLX", "sThumbLY", "sThumbRX", "sThumbRY"})
    public static class Gamepad extends Structure {
        public short wButtons;
        public byte bLeftTrigger, bRightTrigger;
        public short sThumbLX, sThumbLY, sThumbRX, sThumbRY;
    }
    @Structure.FieldOrder({"dwPacketNumber", "Gamepad"})
    public static class State extends Structure {
        public int dwPacketNumber;
        public Gamepad Gamepad = new Gamepad();
    }
    public static final int A=0x1000, B=0x2000, X=0x4000, Y=0x8000, START=0x0010, BACK=0x0020;
    public static final int DPAD_UP=1, DPAD_DOWN=2, DPAD_LEFT=4, DPAD_RIGHT=8;
    private final XInput api;
    private final State state = new State();
    public XboxInput() {
        XInput found = null;
        try { found = Native.load("xinput1_4", XInput.class); }
        catch (Throwable ignored) {
            try { found = Native.load("xinput9_1_0", XInput.class); }
            catch (Throwable ignored2) { }
        }
        api = found;
    }
    public boolean poll() {
        if (api == null) return false;
        for (int i=0; i<4; i++) {
            try { if (api.XInputGetState(i, state)==0) { state.read(); return true; } }
            catch (Throwable ignored) { return false; }
        }
        return false;
    }
    public boolean button(int mask) { return (state.Gamepad.wButtons & mask)!=0; }
    public float lx() { return axis(state.Gamepad.sThumbLX); }
    public float ly() { return axis(state.Gamepad.sThumbLY); }
    public float rx() { return axis(state.Gamepad.sThumbRX); }
    public float ry() { return axis(state.Gamepad.sThumbRY); }
    public float lt() { return trigger(state.Gamepad.bLeftTrigger); }
    public float rt() { return trigger(state.Gamepad.bRightTrigger); }
    private static float trigger(byte b) { int n=b & 255; return n<25?0f:(n-25f)/230f; }
    private static float axis(short s) {
        float v = s < 0 ? s/32768f : s/32767f;
        float dead=0.17f;
        return Math.abs(v)<dead?0f:Math.copySign((Math.abs(v)-dead)/(1f-dead),v);
    }
}
