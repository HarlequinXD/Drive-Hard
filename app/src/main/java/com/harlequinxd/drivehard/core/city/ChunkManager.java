package com.harlequinxd.drivehard.core.city;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Streams chunks in and out of memory around a focus point. A background thread generates
 * missing chunks nearest-first; chunks far outside the load radius are dropped.
 */
public final class ChunkManager {
    public static final float SIZE = 64f;

    private final City city;
    private final ConcurrentHashMap<Long, Chunk> loaded = new ConcurrentHashMap<>();
    private final Object lock = new Object();
    private final List<long[]> wanted = new ArrayList<>();   // {key, distance^2}
    private final List<Thread> workers = new ArrayList<>();
    private volatile boolean running;
    public float loadRadius = 200f, unloadRadius = 280f;
    public volatile int generatedTotal, unloadedTotal;

    public ChunkManager(City city) { this.city = city; }

    public static long key(int cx, int cz) { return ((long) cx << 32) ^ (cz & 0xFFFFFFFFL); }

    public static int chunkOf(float v) { return (int) Math.floor(v / SIZE); }

    public Collection<Chunk> chunks() { return loaded.values(); }

    public int loadedCount() { return loaded.size(); }

    public Chunk get(int cx, int cz) { return loaded.get(key(cx, cz)); }

    /** Chunks are generated on several cores at once (painting the ground map is the costly part). */
    public void start() {
        if (!workers.isEmpty()) return;
        running = true;
        int n = Math.max(1, Math.min(3, Runtime.getRuntime().availableProcessors() / 2));
        for (int i = 0; i < n; i++) {
            Thread t = new Thread(this::work, "chunk-streamer-" + i);
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY - 1);
            t.start();
            workers.add(t);
        }
    }

    public void stop() {
        running = false;
        synchronized (lock) { lock.notifyAll(); }
        workers.clear();
    }

    /** Generates a chunk immediately on the calling thread if it is missing. */
    public Chunk ensure(int cx, int cz) {
        long k = key(cx, cz);
        Chunk c = loaded.get(k);
        if (c != null) return c;
        c = city.generate(cx, cz);
        Chunk prev = loaded.putIfAbsent(k, c);
        if (prev == null) generatedTotal++;
        return prev != null ? prev : c;
    }

    /** Makes sure the 3x3 chunks around a point exist (collision needs them now). */
    public void ensureAround(float x, float z) {
        int cx = chunkOf(x), cz = chunkOf(z);
        for (int dz = -1; dz <= 1; dz++)
            for (int dx = -1; dx <= 1; dx++) ensure(cx + dx, cz + dz);
    }

    /** Updates the wanted set around the focus and unloads distant chunks. Cheap; call every frame. */
    public void update(float fx, float fz) {
        int r = (int) Math.ceil(loadRadius / SIZE);
        int cx0 = chunkOf(fx), cz0 = chunkOf(fz);
        List<long[]> list = new ArrayList<>();
        for (int dz = -r; dz <= r; dz++) {
            for (int dx = -r; dx <= r; dx++) {
                int cx = cx0 + dx, cz = cz0 + dz;
                float px = Math.max(cx * SIZE, Math.min(fx, (cx + 1) * SIZE));
                float pz = Math.max(cz * SIZE, Math.min(fz, (cz + 1) * SIZE));
                float d2 = (px - fx) * (px - fx) + (pz - fz) * (pz - fz);
                if (d2 > loadRadius * loadRadius) continue;
                long k = key(cx, cz);
                if (!loaded.containsKey(k)) list.add(new long[]{k, (long) d2});
            }
        }
        list.sort((a, b) -> Long.compare(a[1], b[1]));
        synchronized (lock) {
            wanted.clear();
            wanted.addAll(list);
            if (!wanted.isEmpty()) lock.notifyAll();
        }
        float ur2 = unloadRadius * unloadRadius;
        for (Chunk c : loaded.values()) {
            float px = Math.max(c.cx * SIZE, Math.min(fx, (c.cx + 1) * SIZE));
            float pz = Math.max(c.cz * SIZE, Math.min(fz, (c.cz + 1) * SIZE));
            if ((px - fx) * (px - fx) + (pz - fz) * (pz - fz) > ur2) {
                loaded.remove(key(c.cx, c.cz));
                unloadedTotal++;
            }
        }
    }

    /** Loads everything wanted right now on the calling thread (desktop tools / first frame). */
    public void loadAllNow(float fx, float fz) {
        update(fx, fz);
        List<long[]> copy;
        synchronized (lock) { copy = new ArrayList<>(wanted); wanted.clear(); }
        copy.parallelStream().forEach(w -> ensure((int) (w[0] >> 32), (int) w[0]));
    }

    private void work() {
        while (running) {
            long k;
            synchronized (lock) {
                while (running && wanted.isEmpty()) {
                    try { lock.wait(200); } catch (InterruptedException e) { return; }
                }
                if (!running) return;
                k = wanted.remove(0)[0];
            }
            if (loaded.containsKey(k)) continue;
            try {
                ensure((int) (k >> 32), (int) k);
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }
}
