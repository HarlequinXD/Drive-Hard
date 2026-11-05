package com.harlequinxd.drivehard.core.tex;

import java.util.Random;

import static com.harlequinxd.drivehard.core.tex.FacadeLayers.Material;
import static com.harlequinxd.drivehard.core.tex.FacadeLayers.Recipe;
import static com.harlequinxd.drivehard.core.tex.FacadeLayers.Window;

/**
 * Recipes for the layer stack of {@link FacadeLayers}, one family per kind of Japanese building:
 * colours, materials, windows and fittings drawn from what such buildings are actually made of.
 */
public final class FacadeRecipes {
    private FacadeRecipes() {}

    public enum Type { OFFICE, CURTAIN, MANSION, APARTMENT_CORRIDOR, ZAKKYO, SHOPHOUSE, DEPT_STORE, WAREHOUSE, CIVIC }

    private static final int[] TILE_LIGHT = {0xE8E2D6, 0xDCD4C4, 0xEEEAE2, 0xD8D0C0, 0xE4DCCC, 0xF0ECE4};
    private static final int[] TILE_WARM = {0xC8A888, 0xB89878, 0xA88870, 0xD4B494, 0xB8A08A, 0x9A7A62};
    private static final int[] TILE_COOL = {0xB4B8B8, 0xA8ACAE, 0xC4C6C4, 0x9AA0A4, 0xC8CCD0};
    private static final int[] CONCRETE = {0xB8B6B0, 0xA8A6A0, 0xC4C2BC, 0x9C9A94};
    private static final int[] RENDER = {0xEAE4D8, 0xE0D8C8, 0xD4CCBC, 0xE8E0D4, 0xC8C0B4, 0xDCD0C0};
    private static final int[] FRAMES = {0xB8BCC0, 0xC8CCD0, 0x5A4E44, 0x3A3A3E, 0xE8E8E4};
    private static final int[] GLASS = {0x6A8AA8, 0x587A90, 0x7A94A4, 0x4A6A80, 0x6A8494, 0x5A7088};
    private static final int[] METAL = {0xC8CCCE, 0x8E969C, 0x6E7A84, 0xB4A68E, 0x5E6A5E, 0x9AA4AE, 0xD8D8D0};
    private static final int[] BRICK = {0xA86A50, 0x9A5E48, 0xB87A5E, 0x8A5A48};

    private static int pick(Random r, int[] a) { return a[r.nextInt(a.length)]; }

    public static Recipe make(Type t, long seed) {
        Random r = new Random(seed * 7919 + t.ordinal() * 104729L);
        Recipe c = new Recipe();
        c.seed = seed * 31 + t.ordinal();
        c.frame = pick(r, FRAMES);
        c.glassTint = pick(r, GLASS);
        c.grime = 0.25f + r.nextFloat() * 0.6f;
        switch (t) {
            case OFFICE: {
                int k = r.nextInt(3);
                c.mat = k == 0 ? Material.PANEL : k == 1 ? Material.TILE : Material.CONCRETE;
                c.wall = k == 1 ? pick(r, TILE_COOL) : k == 0 ? pick(r, RENDER) : pick(r, CONCRETE);
                c.accent = Img.mul(0xFF000000 | c.wall, 0.86f) & 0xFFFFFF;
                c.win = r.nextBoolean() ? Window.RIBBON : Window.PICTURE;
                c.ribbonSpandrel = c.win == Window.RIBBON;
                c.winW = 48; c.winH = 52; c.sill = 32;
                c.columns = r.nextBoolean();
                c.ac = r.nextInt(4) == 0;
                c.grime *= 0.7f;
                break;
            }
            case CURTAIN: {
                c.mat = Material.GLASS;
                c.wall = 0x5A6A78;
                c.accent = pick(r, new int[]{0x3A4048, 0x5A6066, 0x8A9096, 0x2A3036});
                c.frame = pick(r, new int[]{0xA8ACB0, 0x3A3E42, 0x6E7276, 0xC8CCD0});
                c.win = Window.CURTAIN_WALL;
                c.slab = false;
                c.grime = 0.1f;
                break;
            }
            case MANSION: {
                c.mat = r.nextInt(3) == 0 ? Material.MOSAIC : Material.TILE;
                c.wall = r.nextInt(3) == 0 ? pick(r, TILE_WARM) : pick(r, TILE_LIGHT);
                c.accent = r.nextBoolean() ? Img.mul(0xFF000000 | c.wall, 0.85f) & 0xFFFFFF : pick(r, TILE_LIGHT);
                c.win = Window.BALCONY_DOOR;
                c.balcony = true;
                c.rail = r.nextInt(3);
                c.ac = true;
                c.pipe = true;
                break;
            }
            case APARTMENT_CORRIDOR: {
                c.mat = Material.TILE;
                c.wall = pick(r, TILE_LIGHT);
                c.accent = Img.mul(0xFF000000 | c.wall, 0.9f) & 0xFFFFFF;
                c.win = Window.CORRIDOR_DOOR;
                c.slab = true;
                break;
            }
            case ZAKKYO: {
                int k = r.nextInt(4);
                c.mat = k == 0 ? Material.MOSAIC : k == 1 ? Material.TILE : k == 2 ? Material.METAL : Material.PANEL;
                c.wall = k == 2 ? pick(r, METAL) : r.nextBoolean() ? pick(r, TILE_WARM) : pick(r, TILE_COOL);
                c.accent = Img.mul(0xFF000000 | c.wall, 0.8f) & 0xFFFFFF;
                c.win = r.nextBoolean() ? Window.PICTURE : Window.TALL;
                c.winW = 44; c.winH = 56; c.sill = 30;
                c.ac = r.nextBoolean();
                c.pipe = true;
                c.grime = 0.5f + r.nextFloat() * 0.5f;
                break;
            }
            case SHOPHOUSE: {
                int k = r.nextInt(4);
                c.mat = k == 0 ? Material.RENDER : k == 1 ? Material.SIDING : k == 2 ? Material.TILE : Material.BRICK;
                c.wall = k == 3 ? pick(r, BRICK) : k == 2 ? pick(r, TILE_WARM) : pick(r, RENDER);
                c.accent = Img.mul(0xFF000000 | c.wall, 0.82f) & 0xFFFFFF;
                c.win = Window.SASH;
                c.winW = 36; c.winH = 44; c.sill = 40;
                c.ac = r.nextBoolean();
                c.pipe = r.nextBoolean();
                break;
            }
            case DEPT_STORE: {
                c.mat = r.nextBoolean() ? Material.PANEL : Material.TILE;
                c.wall = pick(r, new int[]{0xE8E4DA, 0xD8D0C2, 0xC8C4BC, 0xE2D4C0});
                c.accent = Img.mul(0xFF000000 | c.wall, 0.85f) & 0xFFFFFF;
                c.win = r.nextInt(3) == 0 ? Window.SMALL : Window.NONE;
                c.columns = true;
                c.grime = 0.3f;
                break;
            }
            case WAREHOUSE: {
                c.mat = Material.METAL;
                c.wall = pick(r, METAL);
                c.accent = Img.mul(0xFF000000 | c.wall, 0.8f) & 0xFFFFFF;
                c.win = r.nextInt(3) == 0 ? Window.RIBBON : Window.NONE;
                c.winH = 18; c.sill = 70;
                c.slab = false;
                break;
            }
            default: {      // civic: town hall, clinic, post office
                c.mat = r.nextBoolean() ? Material.RENDER : Material.PANEL;
                c.wall = pick(r, RENDER);
                c.accent = Img.mul(0xFF000000 | c.wall, 0.85f) & 0xFFFFFF;
                c.win = Window.RIBBON;
                c.ribbonSpandrel = true;
                c.winH = 44; c.sill = 34;
                c.columns = r.nextBoolean();
                break;
            }
        }
        return c;
    }
}
