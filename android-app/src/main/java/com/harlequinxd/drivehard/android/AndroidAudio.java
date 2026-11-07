package com.harlequinxd.drivehard.android;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

import com.harlequinxd.drivehard.core.audio.Mixer;

/** Plays the mixer through a streaming AudioTrack on its own thread; paused with the activity. */
final class AndroidAudio implements Runnable {
    private final Mixer mixer;
    private volatile boolean running, paused;
    private Thread thread;
    private AudioTrack track;

    AndroidAudio(Mixer mixer) { this.mixer = mixer; }

    synchronized void start() {
        if (thread != null) return;
        int min = AudioTrack.getMinBufferSize(Mixer.RATE, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT);
        try {
            track = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setSampleRate(Mixer.RATE)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
                    .setBufferSizeInBytes(Math.max(min, 1024 * 4 * 2))
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build();
            track.play();
        } catch (Exception e) {
            track = null;
            return;                                            // no audio: play on silently
        }
        running = true;
        thread = new Thread(this, "audio");
        thread.setPriority(Thread.MAX_PRIORITY);
        thread.start();
    }

    void pause() {
        paused = true;
        AudioTrack t = track;
        if (t != null) try { t.pause(); t.flush(); } catch (Exception ignored) { }
    }

    void resume() {
        AudioTrack t = track;
        if (t != null) try { t.play(); } catch (Exception ignored) { }
        paused = false;
    }

    synchronized void stop() {
        running = false;
        if (thread != null) try { thread.join(500); } catch (InterruptedException ignored) { }
        thread = null;
        if (track != null) { try { track.release(); } catch (Exception ignored) { } track = null; }
    }

    @Override public void run() {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);
        int frames = 512;
        short[] buf = new short[frames * 2];
        while (running) {
            if (paused) {
                try { Thread.sleep(50); } catch (InterruptedException e) { return; }
                continue;
            }
            mixer.render(buf, frames);
            AudioTrack t = track;
            if (t == null) return;
            t.write(buf, 0, buf.length);
        }
    }
}
