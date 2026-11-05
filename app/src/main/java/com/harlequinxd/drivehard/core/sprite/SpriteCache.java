package com.harlequinxd.drivehard.core.sprite;

import com.harlequinxd.drivehard.core.render.Texture;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Bakes sprite sheets on a background thread and hands them to the renderer's texture table.
 * Each group (flora, cars, pedestrians) has a capacity; least recently used sheets are evicted
 * so memory stays bounded while all 100 car and 100 pedestrian styles can still appear over time.
 * Only {@link #update} and {@link #get} touch the texture table, both on the game thread.
 */
public final class SpriteCache {
    public interface Recipe { SpriteSheet bake(); }

    public static final class Entry {
        public final String key;
        public final int group;
        final Recipe recipe;
        final int priority;
        volatile SpriteSheet baked;
        public SpriteSheet sheet;   // published on the game thread
        public int tex = -1;
        long lastUse;
        boolean queued;

        Entry(String key, int group, Recipe recipe, int priority) {
            this.key = key; this.group = group; this.recipe = recipe; this.priority = priority;
        }

        public boolean ready() { return tex >= 0; }
    }

    private final Texture[] textures;
    private final int firstFreeSlot;
    private final List<Integer> freeSlots = new ArrayList<>();
    private int nextSlot;
    private final Map<String, Entry> entries = new HashMap<>();
    private final int[] capacity;
    private final List<Entry> queue = new ArrayList<>();
    private final ConcurrentLinkedQueue<Entry> done = new ConcurrentLinkedQueue<>();
    private Thread worker;
    private volatile boolean running;
    private long frame;
    public volatile int bakedTotal;

    /**
     * @param textures shared renderer texture table; slots from {@code firstFreeSlot} up are managed here
     */
    public SpriteCache(Texture[] textures, int firstFreeSlot, int[] groupCapacity) {
        this.textures = textures;
        this.firstFreeSlot = firstFreeSlot;
        this.nextSlot = firstFreeSlot;
        this.capacity = groupCapacity;
    }

    public void start() {
        running = true;
        worker = new Thread(this::work, "sprite-baker");
        worker.setDaemon(true);
        worker.setPriority(Thread.MIN_PRIORITY + 1);
        worker.start();
    }

    public void stop() {
        running = false;
        synchronized (queue) { queue.notifyAll(); }
    }

    /** Returns the entry, queueing a bake if needed. Ready when {@link Entry#ready()}. */
    public Entry get(String key, int group, int priority, Recipe recipe) {
        Entry e = entries.get(key);
        if (e == null) {
            e = new Entry(key, group, recipe, priority);
            entries.put(key, e);
        }
        e.lastUse = frame;
        if (e.tex < 0 && !e.queued && e.baked == null) {
            e.queued = true;
            synchronized (queue) {
                queue.add(e);
                queue.sort((a, b) -> Integer.compare(a.priority, b.priority));
                queue.notifyAll();
            }
        }
        return e;
    }

    /** False once the entry has been evicted. */
    public boolean alive(Entry e) { return entries.get(e.key) == e; }

    /** Marks an entry as used this frame (keeps it from eviction). */
    public void touch(Entry e) { e.lastUse = frame; }

    public int readyCount(int group) {
        int n = 0;
        for (Entry e : entries.values()) if (e.group == group && e.ready()) n++;
        return n;
    }

    public int queuedCount() {
        synchronized (queue) { return queue.size(); }
    }

    /** Publishes finished bakes and evicts over-capacity groups. Call once per frame on the game thread. */
    public void update() {
        frame++;
        Entry e;
        while ((e = done.poll()) != null) {
            SpriteSheet s = e.baked;
            int slot = freeSlots.isEmpty() ? nextSlot++ : freeSlots.remove(freeSlots.size() - 1);
            if (slot >= textures.length) { e.baked = null; e.queued = false; continue; }
            textures[slot] = Texture.padded(s.pixels, s.width, s.height);
            e.sheet = s;
            e.tex = slot;
            e.baked = null;
        }
        for (int g = 0; g < capacity.length; g++) {
            if (capacity[g] <= 0) continue;
            List<Entry> ready = new ArrayList<>();
            for (Entry x : entries.values()) if (x.group == g && x.ready()) ready.add(x);
            // stream out what has not been drawn for a while, even under capacity
            for (Entry x : ready) {
                if (frame - x.lastUse <= IDLE_FRAMES) continue;
                evict(x);
            }
            ready.removeIf(x -> !x.ready());
            if (ready.size() <= capacity[g]) continue;
            ready.sort((a, b) -> Long.compare(a.lastUse, b.lastUse));
            for (int i = 0; i < ready.size() - capacity[g]; i++) {
                Entry x = ready.get(i);
                if (frame - x.lastUse < 2) break;            // still on screen
                evict(x);
            }
        }
    }

    /** Frames a sheet may go unused (about 40 s at 30 fps) before its memory is given back. */
    public static final int IDLE_FRAMES = 1200;

    private void evict(Entry x) {
        if (x.tex < 0) return;
        textures[x.tex] = null;
        freeSlots.add(x.tex);
        x.tex = -1;
        x.sheet = null;
        x.queued = false;
        entries.remove(x.key);
    }

    /** Synchronously bakes everything queued (desktop tools / tests). */
    public void bakeAllNow() {
        List<Entry> copy;
        synchronized (queue) { copy = new ArrayList<>(queue); queue.clear(); }
        for (Entry e : copy) { e.baked = e.recipe.bake(); bakedTotal++; done.add(e); }
        update();
    }

    private void work() {
        while (running) {
            Entry e;
            synchronized (queue) {
                while (running && queue.isEmpty()) {
                    try { queue.wait(250); } catch (InterruptedException ex) { return; }
                }
                if (!running) return;
                e = queue.remove(0);
            }
            try {
                e.baked = e.recipe.bake();
                bakedTotal++;
                done.add(e);
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }
}
