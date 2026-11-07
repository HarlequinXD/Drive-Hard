package com.harlequinxd.drivehard.desktop;

import com.harlequinxd.drivehard.core.audio.Mixer;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;

/** Plays the mixer through Java Sound: a daemon thread pulling ~23 ms blocks into a short line buffer. */
final class DesktopAudio implements Runnable {
    private final Mixer mixer;
    private volatile boolean running;
    private Thread thread;

    DesktopAudio(Mixer mixer) { this.mixer = mixer; }

    void start() {
        if (thread != null) return;
        running = true;
        thread = new Thread(this, "audio");
        thread.setDaemon(true);
        thread.setPriority(Thread.MAX_PRIORITY);
        thread.start();
    }

    void stop() { running = false; }

    @Override public void run() {
        AudioFormat fmt = new AudioFormat(Mixer.RATE, 16, 2, true, false);
        SourceDataLine line;
        try {
            line = AudioSystem.getSourceDataLine(fmt);
            line.open(fmt, 512 * 4 * 6);
            line.start();
        } catch (Exception e) {
            System.err.println("No sound: " + e);           // no audio device: play on silently
            return;
        }
        int frames = 512;
        short[] buf = new short[frames * 2];
        byte[] bytes = new byte[frames * 4];
        while (running) {
            mixer.render(buf, frames);
            for (int i = 0; i < frames * 2; i++) { bytes[i * 2] = (byte) buf[i]; bytes[i * 2 + 1] = (byte) (buf[i] >> 8); }
            line.write(bytes, 0, bytes.length);
        }
        line.drain();
        line.close();
    }
}
