package com.harlequinxd.drivehard.core.data;

import com.harlequinxd.drivehard.core.city.Chunk;
import com.harlequinxd.drivehard.core.city.City;
import com.harlequinxd.drivehard.core.game.Box;
import com.harlequinxd.drivehard.core.render.Geo;
import com.harlequinxd.drivehard.core.render.Ground;
import com.harlequinxd.drivehard.core.tex.TextureLibrary;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

/**
 * The game data folder: the generated island and every chunk the player has been near, saved on
 * disk the way Minecraft keeps its region files.
 *
 * The first time a seed is played, the island (terrain, roads, towns, farms, building textures) is
 * generated and written to {@code island.bin} in the background; later launches read it back
 * instead of generating it. Each 64 m chunk is generated the first time the player comes near and
 * written to its own file; after that it is read from disk, which is much cheaper than building it
 * again. Only the chunks around the player are ever in memory: the {@link
 * com.harlequinxd.drivehard.core.city.ChunkManager} drops them when the player moves on.
 *
 * Nothing in the world changes during play, so the cache only saves time. It is keyed on the seed,
 * the ground detail and the game build: a new build of the game starts a fresh cache (so changes to
 * the generators always show) and deletes the old one. The chunk files are kept under a size
 * budget, oldest-used dropped first.
 */
public final class WorldCache {
    /** Bump when the file layout changes. */
    public static final int FORMAT = 11;
    private static final int CHUNK_MAGIC = 0x44484331; // "DHC1"

    /** The game data folder (set by the platform before the game starts); null turns the cache off. */
    public static volatile File root;
    /** Identifies the game build (set by the platform); a different build starts a fresh cache. */
    public static volatile String build = "dev";

    public final File dir;
    private final File chunkDir;
    private final long budget;
    private volatile long bytes;
    private final LinkedBlockingQueue<Runnable> jobs = new LinkedBlockingQueue<>();
    public volatile int chunkReads, chunkWrites, chunkMisses;

    private WorldCache(File dir, long budget) {
        this.dir = dir;
        this.chunkDir = new File(dir, "chunks");
        this.budget = budget;
        chunkDir.mkdirs();
        long total = 0;
        File[] fs = chunkDir.listFiles();
        if (fs != null) for (File f : fs) {
            if (f.getName().endsWith(".tmp")) f.delete();
            else total += f.length();
        }
        bytes = total;
        Thread t = new Thread(null, this::writer, "game-data-writer", 64L << 20);
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
    }

    /**
     * Opens (or starts) the cache for a world, or returns null when there is no game data folder
     * or it cannot be written. Caches of other builds are deleted.
     */
    public static WorldCache open(long seed, boolean lowMemory) {
        File r = root;
        if (r == null) return null;
        try {
            r.mkdirs();
            String key = "world-" + Long.toHexString(seed) + "-tiled-"
                    + Integer.toHexString((FORMAT + ":" + build).hashCode());
            File dir = new File(r, key);
            File[] old = r.listFiles();
            String prefix = "world-" + Long.toHexString(seed) + "-";
            if (old != null) for (File f : old) {
                // a world of this seed from another build or detail level is stale
                if (f.isDirectory() && f.getName().startsWith(prefix) && !f.getName().equals(key)) deleteTree(f);
            }
            dir.mkdirs();
            if (!dir.isDirectory() || !dir.canWrite()) return null;
            // other seeds' worlds are kept, but only a few
            pruneWorlds(r, dir, 3);
            return new WorldCache(dir, lowMemory ? 160L << 20 : 400L << 20);
        } catch (SecurityException e) {
            return null;
        }
    }

    /** Disk used by the saved chunks, in bytes. */
    public long chunkBytes() { return bytes; }

    // ------------------------------------------------------------------ island

    /** The island read back from disk. */
    public static final class Island {
        public final TextureLibrary lib;
        public final City city;
        Island(TextureLibrary lib, City city) { this.lib = lib; this.city = city; }
    }

    public interface Progress { void done(float fraction); }

    /** Reads the saved island, or null if there is none (or it cannot be read). */
    public Island loadIsland(Progress progress) {
        File f = new File(dir, "island.bin");
        if (!f.isFile()) return null;
        final long size = Math.max(1, f.length());
        Island[] out = new Island[1];
        // the road graph is deep: read it on a thread with a big stack
        Thread t = new Thread(null, () -> {
            try (InputStream raw = new CountingInput(new FileInputStream(f), size, progress);
                 ObjectInputStream in = new ObjectInputStream(new BufferedInputStream(new InflaterInputStream(raw), 1 << 16))) {
                if (in.readInt() != FORMAT || !build.equals(in.readUTF())) return;
                TextureLibrary lib = (TextureLibrary) in.readObject();
                City city = (City) in.readObject();
                out[0] = new Island(lib, city);
            } catch (Throwable e) {
                // unreadable or from another version: generate again
                f.delete();
            }
        }, "game-data-island", 256L << 20);
        t.start();
        try { t.join(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        return out[0];
    }

    /**
     * Writes the island in the background. The library and the city must not change afterwards
     * (nothing in them does once the game is running).
     */
    public void saveIsland(TextureLibrary lib, City city) {
        jobs.add(() -> {
            File f = new File(dir, "island.bin"), tmp = new File(dir, "island.bin.tmp");
            try (ObjectOutputStream o = new ObjectOutputStream(new BufferedOutputStream(
                    new DeflaterOutputStream(new FileOutputStream(tmp), new Deflater(1)), 1 << 16))) {
                o.writeInt(FORMAT);
                o.writeUTF(build);
                o.writeObject(lib);
                o.writeObject(city);
            } catch (Throwable e) {
                tmp.delete();
                return;
            }
            f.delete();
            if (!tmp.renameTo(f)) tmp.delete();
        });
    }

    public boolean hasIsland() { return new File(dir, "island.bin").isFile(); }

    // ------------------------------------------------------------------ chunks

    private File chunkFile(int cx, int cz) { return new File(chunkDir, cx + "_" + cz + ".bin"); }

    /** Reads a saved chunk, or null if it has not been saved yet. */
    public Chunk readChunk(int cx, int cz) {
        File f = chunkFile(cx, cz);
        if (!f.isFile()) { chunkMisses++; return null; }
        try {
            byte[] packed = readAll(f);
            ByteBuffer head = ByteBuffer.wrap(packed, 0, 16).order(ByteOrder.LITTLE_ENDIAN);
            if (head.getInt() != CHUNK_MAGIC || head.getInt() != cx || head.getInt() != cz) throw new IOException("bad chunk");
            int rawSize = head.getInt();
            byte[] raw = new byte[rawSize];
            Inflater inf = new Inflater();
            inf.setInput(packed, 16, packed.length - 16);
            int n = 0;
            while (n < rawSize && !inf.finished()) {
                int k = inf.inflate(raw, n, rawSize - n);
                if (k == 0 && (inf.needsInput() || inf.needsDictionary())) break;
                n += k;
            }
            inf.end();
            if (n != rawSize) throw new IOException("short chunk");
            Chunk c = decode(ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN), cx, cz);
            chunkReads++;
            // most recently used (the size budget drops the oldest first)
            f.setLastModified(System.currentTimeMillis());
            return c;
        } catch (IOException | RuntimeException | DataFormatException e) {
            f.delete();
            chunkMisses++;
            return null;
        }
    }

    /** Saves a freshly generated chunk in the background. */
    public void writeChunk(Chunk c) {
        jobs.add(() -> {
            byte[] raw = encode(c);
            Deflater d = new Deflater(1);
            d.setInput(raw);
            d.finish();
            byte[] buf = new byte[raw.length / 2 + 1024];
            ByteBuffer head = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN);
            head.putInt(CHUNK_MAGIC).putInt(c.cx).putInt(c.cz).putInt(raw.length);
            File f = chunkFile(c.cx, c.cz), tmp = new File(chunkDir, c.cx + "_" + c.cz + ".tmp");
            long written = 16;
            try (OutputStream o = new FileOutputStream(tmp)) {
                o.write(head.array());
                while (!d.finished()) {
                    int k = d.deflate(buf);
                    o.write(buf, 0, k);
                    written += k;
                }
            } catch (IOException e) {
                tmp.delete();
                return;
            } finally {
                d.end();
            }
            long before = f.isFile() ? f.length() : 0;
            f.delete();
            if (!tmp.renameTo(f)) { tmp.delete(); return; }
            bytes += written - before;
            chunkWrites++;
            if (bytes > budget) trim();
        });
    }

    /** Waits until everything queued has been written (tools and tests). */
    public void flush() {
        final Object done = new Object();
        final boolean[] flag = {false};
        jobs.add(() -> { synchronized (done) { flag[0] = true; done.notifyAll(); } });
        synchronized (done) {
            while (!flag[0]) {
                try { done.wait(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
            }
        }
    }

    private void writer() {
        while (true) {
            try {
                jobs.take().run();
            } catch (InterruptedException e) {
                return;
            } catch (Throwable t) {
                // a failed write only means the chunk is generated again next time
            }
        }
    }

    /** Drops the least recently used chunk files until the cache is at 80% of its budget. */
    private void trim() {
        File[] fs = chunkDir.listFiles();
        if (fs == null) return;
        long[] age = new long[fs.length];
        Integer[] order = new Integer[fs.length];
        long total = 0;
        for (int i = 0; i < fs.length; i++) { age[i] = fs[i].lastModified(); order[i] = i; total += fs[i].length(); }
        Arrays.sort(order, (a, b) -> Long.compare(age[a], age[b]));
        long target = budget * 4 / 5;
        for (int i = 0; i < order.length && total > target; i++) {
            File f = fs[order[i]];
            long len = f.length();
            if (f.delete()) total -= len;
        }
        bytes = total;
    }

    // ------------------------------------------------------------------ chunk format

    private static byte[] encode(Chunk c) {
        int size = 64;
        for (Geo g : c.batches) {
            int n = g.count;
            size += 64 + n * (12 + 8 + 4 + 3 + 1) * 4 + n * 8 + (g.alpha != null ? n * 16 : 0);
        }
        size += c.boxes.size() * 24 + c.spriteCount * Chunk.SPRITE_STRIDE * 4;
        Ground.Tile t = c.ground;
        if (t != null) {
            size += 16 + t.h.length * 4 + (t.water != null ? t.water.length : 0);
            size += paintSize(t);
        }
        ByteBuffer b = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(c.batches.size());
        for (Geo g : c.batches) {
            int n = g.count;
            b.putInt(n);
            b.put((byte) ((g.object ? 1 : 0) | (g.alpha != null ? 2 : 0) | (c.objectBatches.contains(g) ? 4 : 0) | (g.cpuGround ? 8 : 0)));
            b.putFloat(g.minX).putFloat(g.minY).putFloat(g.minZ).putFloat(g.maxX).putFloat(g.maxY).putFloat(g.maxZ);
            b.asFloatBuffer().put(g.pos, 0, n * 12); b.position(b.position() + n * 48);
            b.asFloatBuffer().put(g.uv, 0, n * 8); b.position(b.position() + n * 32);
            b.asFloatBuffer().put(g.light, 0, n * 4); b.position(b.position() + n * 16);
            b.asFloatBuffer().put(g.nrm, 0, n * 3); b.position(b.position() + n * 12);
            b.asFloatBuffer().put(g.bias, 0, n); b.position(b.position() + n * 4);
            b.asIntBuffer().put(g.tex, 0, n); b.position(b.position() + n * 4);
            b.asIntBuffer().put(g.meta, 0, n); b.position(b.position() + n * 4);
            if (g.alpha != null) { b.asFloatBuffer().put(g.alpha, 0, n * 4); b.position(b.position() + n * 16); }
        }
        b.putInt(c.boxes.size());
        for (Box x : c.boxes) b.putFloat(x.minX).putFloat(x.minY).putFloat(x.minZ).putFloat(x.maxX).putFloat(x.maxY).putFloat(x.maxZ);
        b.putInt(c.spriteCount);
        b.asFloatBuffer().put(c.sprites, 0, c.spriteCount * Chunk.SPRITE_STRIDE);
        b.position(b.position() + c.spriteCount * Chunk.SPRITE_STRIDE * 4);
        b.putFloat(c.minX).putFloat(c.minZ).putFloat(c.maxX).putFloat(c.maxZ).putInt(c.polyCount);
        if (t == null) b.putInt(0);
        else {
            b.putInt(1);
            b.putInt(t.h.length);
            b.asFloatBuffer().put(t.h); b.position(b.position() + t.h.length * 4);
            b.putInt(t.water == null ? -1 : t.water.length);
            if (t.water != null) b.put(t.water);
            writePaint(b, t);
        }
        return Arrays.copyOf(b.array(), b.position());
    }

    // the painted terrain (see GroundPaint): mesh heights and light, layers, materials, roads

    private static int paintSize(Ground.Tile t) {
        int n = 64;
        if (t.land != null) n += (t.land.length + t.lit.length) * 4;
        if (t.splat != null) n += t.splat.length + 16 * 8;
        if (t.mat != null) n += t.mat.length + t.matTex.length * 12;
        if (t.road != null) n += t.road.length * 4 + t.roadInfo.length * 2;
        return n;
    }

    private static void writePaint(ByteBuffer b, Ground.Tile t) {
        b.put((byte) ((t.land != null ? 1 : 0) | (t.splat != null ? 2 : 0) | (t.mat != null ? 4 : 0) | (t.road != null ? 8 : 0)));
        if (t.land != null) {
            for (float v : t.land) b.putFloat(v);
            for (float v : t.lit) b.putFloat(v);
        }
        if (t.splat != null) {
            for (int k = 0; k < Ground.Tile.LAYERS; k++) b.putInt(t.layerTex[k]).putFloat(t.layerScale[k]);
            b.put(t.splat);
        }
        if (t.mat != null) {
            b.putInt(t.matTex.length);
            for (int k = 0; k < t.matTex.length; k++) b.putInt(t.matTex[k]).putFloat(t.matScale[k * 2]).putFloat(t.matScale[k * 2 + 1]);
            b.put(t.mat);
        }
        if (t.road != null) {
            for (float v : t.road) b.putFloat(v);
            for (short v : t.roadInfo) b.putShort(v);
        }
    }

    private static void readPaint(ByteBuffer b, Ground.Tile t) {
        int f = b.get();
        if ((f & 1) != 0) {
            int m = Ground.Tile.MESH * Ground.Tile.MESH;
            t.land = new float[m];
            t.lit = new float[m];
            for (int k = 0; k < m; k++) t.land[k] = b.getFloat();
            for (int k = 0; k < m; k++) t.lit[k] = b.getFloat();
        }
        if ((f & 2) != 0) {
            t.layerTex = new int[Ground.Tile.LAYERS];
            t.layerScale = new float[Ground.Tile.LAYERS];
            for (int k = 0; k < Ground.Tile.LAYERS; k++) { t.layerTex[k] = b.getInt(); t.layerScale[k] = b.getFloat(); }
            t.splat = new byte[Ground.Tile.SPLAT * Ground.Tile.SPLAT * Ground.Tile.LAYERS];
            b.get(t.splat);
        }
        if ((f & 4) != 0) {
            int n = b.getInt();
            t.matTex = new int[n];
            t.matScale = new float[n * 2];
            for (int k = 0; k < n; k++) { t.matTex[k] = b.getInt(); t.matScale[k * 2] = b.getFloat(); t.matScale[k * 2 + 1] = b.getFloat(); }
            t.mat = new byte[Ground.Tile.MAT * Ground.Tile.MAT];
            b.get(t.mat);
        }
        if ((f & 8) != 0) {
            int n = Ground.Tile.ROAD * Ground.Tile.ROAD * 4;
            t.road = new float[n];
            t.roadInfo = new short[n];
            for (int k = 0; k < n; k++) t.road[k] = b.getFloat();
            for (int k = 0; k < n; k++) t.roadInfo[k] = b.getShort();
        }
    }

    private static Chunk decode(ByteBuffer b, int cx, int cz) throws IOException {
        Chunk c = new Chunk(cx, cz);
        int batches = b.getInt();
        for (int k = 0; k < batches; k++) {
            int n = b.getInt();
            int flags = b.get();
            Geo g = new Geo();
            g.count = n;
            g.object = (flags & 1) != 0;
            g.cpuGround = (flags & 8) != 0;
            g.minX = b.getFloat(); g.minY = b.getFloat(); g.minZ = b.getFloat();
            g.maxX = b.getFloat(); g.maxY = b.getFloat(); g.maxZ = b.getFloat();
            int m = Math.max(1, n);
            g.pos = new float[m * 12]; b.asFloatBuffer().get(g.pos, 0, n * 12); b.position(b.position() + n * 48);
            g.uv = new float[m * 8]; b.asFloatBuffer().get(g.uv, 0, n * 8); b.position(b.position() + n * 32);
            g.light = new float[m * 4]; b.asFloatBuffer().get(g.light, 0, n * 4); b.position(b.position() + n * 16);
            g.nrm = new float[m * 3]; b.asFloatBuffer().get(g.nrm, 0, n * 3); b.position(b.position() + n * 12);
            g.bias = new float[m]; b.asFloatBuffer().get(g.bias, 0, n); b.position(b.position() + n * 4);
            g.tex = new int[m]; b.asIntBuffer().get(g.tex, 0, n); b.position(b.position() + n * 4);
            g.meta = new int[m]; b.asIntBuffer().get(g.meta, 0, n); b.position(b.position() + n * 4);
            if ((flags & 2) != 0) { g.alpha = new float[m * 4]; b.asFloatBuffer().get(g.alpha, 0, n * 4); b.position(b.position() + n * 16); }
            c.batches.add(g);
            if ((flags & 4) != 0) c.objectBatches.add(g);
        }
        int boxes = b.getInt();
        for (int k = 0; k < boxes; k++)
            c.boxes.add(new Box(b.getFloat(), b.getFloat(), b.getFloat(), b.getFloat(), b.getFloat(), b.getFloat()));
        int sprites = b.getInt();
        c.sprites = new float[Math.max(1, sprites) * Chunk.SPRITE_STRIDE];
        b.asFloatBuffer().get(c.sprites, 0, sprites * Chunk.SPRITE_STRIDE);
        b.position(b.position() + sprites * Chunk.SPRITE_STRIDE * 4);
        c.spriteCount = sprites;
        c.minX = b.getFloat(); c.minZ = b.getFloat(); c.maxX = b.getFloat(); c.maxZ = b.getFloat();
        c.polyCount = b.getInt();
        if (b.getInt() != 0) {
            float[] h = new float[b.getInt()];
            b.asFloatBuffer().get(h); b.position(b.position() + h.length * 4);
            int wl = b.getInt();
            byte[] water = null;
            if (wl >= 0) { water = new byte[wl]; b.get(water); }
            c.ground = new Ground.Tile(cx, cz, h, water);
            readPaint(b, c.ground);
        }
        c.heights();
        return c;
    }

    // ------------------------------------------------------------------ files

    private static byte[] readAll(File f) throws IOException {
        long len = f.length();
        if (len < 16 || len > (64L << 20)) throw new IOException("bad size");
        byte[] out = new byte[(int) len];
        try (InputStream in = new FileInputStream(f)) {
            int n = 0;
            while (n < out.length) {
                int k = in.read(out, n, out.length - n);
                if (k < 0) throw new IOException("truncated");
                n += k;
            }
        }
        return out;
    }

    private static void deleteTree(File f) {
        File[] fs = f.listFiles();
        if (fs != null) for (File x : fs) deleteTree(x);
        f.delete();
    }

    /** Keeps the {@code keep} most recently used world folders (the current one always stays). */
    private static void pruneWorlds(File root, File current, int keep) {
        File[] fs = root.listFiles((d, n) -> n.startsWith("world-"));
        if (fs == null || fs.length <= keep) return;
        current.setLastModified(System.currentTimeMillis());
        Arrays.sort(fs, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        int kept = 0;
        for (File f : fs) {
            if (f.equals(current) || kept < keep - 1) { if (!f.equals(current)) kept++; continue; }
            deleteTree(f);
        }
    }

    /** Reports how far through a file a reader is. */
    private static final class CountingInput extends FilterInputStream {
        private final long size;
        private final Progress progress;
        private long read, reported;

        CountingInput(InputStream in, long size, Progress progress) {
            super(in);
            this.size = size;
            this.progress = progress;
        }

        private void count(long n) {
            if (n <= 0) return;
            read += n;
            if (progress != null && read - reported > (256 << 10)) {
                reported = read;
                progress.done(Math.min(1f, read / (float) size));
            }
        }

        @Override public int read() throws IOException { int v = super.read(); if (v >= 0) count(1); return v; }
        @Override public int read(byte[] b, int o, int l) throws IOException { int n = super.read(b, o, l); count(n); return n; }
    }

    /** A build stamp for the desktop: the size and date of the jar (or class folder) the game runs from. */
    public static String desktopBuild() {
        try {
            java.net.URL u = WorldCache.class.getProtectionDomain().getCodeSource().getLocation();
            File f = new File(u.toURI());
            if (f.isDirectory()) f = new File(f, WorldCache.class.getName().replace('.', '/') + ".class");
            return f.length() + "-" + f.lastModified();
        } catch (Throwable e) {
            return "dev";
        }
    }

    /** The game data folder beside the game on the desktop (the jar's folder, else the working folder). */
    public static File desktopRoot() {
        try {
            java.net.URL u = WorldCache.class.getProtectionDomain().getCodeSource().getLocation();
            File f = new File(u.toURI());
            if (f.isFile()) {
                File d = new File(f.getParentFile(), "GameData");
                if (writable(d)) return d;
            }
        } catch (Throwable ignored) {
        }
        File d = new File(System.getProperty("user.dir"), "GameData");
        if (writable(d)) return d;
        // a read-only install folder: keep the game data in the user's profile
        return new File(System.getProperty("user.home"), "DriveHard" + File.separator + "GameData");
    }

    private static boolean writable(File d) {
        try {
            d.mkdirs();
            return d.isDirectory() && d.canWrite();
        } catch (SecurityException e) {
            return false;
        }
    }
}
