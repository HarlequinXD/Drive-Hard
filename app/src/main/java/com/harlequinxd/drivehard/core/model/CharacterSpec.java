package com.harlequinxd.drivehard.core.model;

import java.util.Random;

/**
 * Everything that defines how a generated character looks. The player is one preset;
 * pedestrians will be produced by {@link #randomPedestrian(long)} with different seeds.
 */
public final class CharacterSpec {
    public enum Hair { PONYTAIL, SHORT, LONG, BALD }
    public enum Top { TANK, TSHIRT, LONGSLEEVE }
    public enum Bottom { SHORTS, PANTS }

    public boolean female = true;
    public float height = 1.68f;      // metres
    public float build = 0.35f;       // 0 slim .. 1 heavy
    public int skin = 0xE6B894;
    public Hair hair = Hair.PONYTAIL;
    public int hairColor = 0xD9A650;  // honey blonde
    public Top top = Top.TANK;
    public int topColor = 0xF1EFE8;
    public Bottom bottom = Bottom.SHORTS;
    public int bottomColor = 0x4D6E9C; // denim
    public int shoeColor = 0xF2F2F0;
    public int soleColor = 0x9A9A98;
    public int eyeColor = 0x3B2A22;
    public int lipColor = 0xC0766E;

    /** The main character: a woman in a tank top, denim shorts and sneakers, honey blonde hair. */
    public static CharacterSpec player() {
        return new CharacterSpec();
    }

    private static final int[] SKINS = {0xF3D2B8, 0xE6B894, 0xD9A27A, 0xC08660, 0xA36D4A, 0x8A5A3C, 0x6E4530, 0x553424};
    private static final int[] HAIRS = {0x1E1612, 0x2E2018, 0x4A3020, 0x6B4A2C, 0x9C6B3C, 0xD9A650, 0xE8CF92, 0x8C8C88, 0xB04A28};
    private static final int[] CLOTH = {0xF1EFE8, 0x22252B, 0xC8443C, 0x2F5C9E, 0x3E8A5A, 0xE8B33A, 0xE07AA8, 0x40B8B0,
            0x7A4AA0, 0xF08A3C, 0x6E6E6E, 0xB8D8F0, 0x8C6A48, 0x101820};
    private static final int[] BOTTOMS = {0x4D6E9C, 0x2C3E5C, 0x22252B, 0xC8B89A, 0x6E6E6E, 0xF1EFE8, 0x5A6A3C, 0x8C2A2A};

    public static CharacterSpec randomPedestrian(long seed) {
        Random r = new Random(seed * 7919L + 17L);
        CharacterSpec s = new CharacterSpec();
        s.female = r.nextBoolean();
        s.height = s.female ? 1.58f + r.nextFloat() * 0.16f : 1.68f + r.nextFloat() * 0.18f;
        s.build = r.nextFloat() * r.nextFloat();
        s.skin = SKINS[r.nextInt(SKINS.length)];
        Hair[] hairs = s.female ? new Hair[]{Hair.PONYTAIL, Hair.LONG, Hair.LONG, Hair.SHORT}
                : new Hair[]{Hair.SHORT, Hair.SHORT, Hair.SHORT, Hair.BALD, Hair.PONYTAIL};
        s.hair = hairs[r.nextInt(hairs.length)];
        s.hairColor = HAIRS[r.nextInt(HAIRS.length)];
        s.top = Top.values()[r.nextInt(Top.values().length)];
        s.topColor = CLOTH[r.nextInt(CLOTH.length)];
        s.bottom = r.nextInt(3) == 0 ? Bottom.SHORTS : Bottom.PANTS;
        if (s.female && r.nextBoolean()) s.bottom = Bottom.SHORTS;
        s.bottomColor = BOTTOMS[r.nextInt(BOTTOMS.length)];
        s.shoeColor = r.nextInt(3) == 0 ? 0x22252B : (r.nextBoolean() ? 0xF2F2F0 : CLOTH[r.nextInt(CLOTH.length)]);
        s.soleColor = r.nextBoolean() ? 0xEDEDEA : 0x3A3A3A;
        s.lipColor = s.female ? 0xB86A68 : mix(s.skin, 0x8A4A44, 0.35f);
        return s;
    }

    static int mix(int a, int b, float t) {
        int ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return (Math.round(ar + (br - ar) * t) << 16) | (Math.round(ag + (bg - ag) * t) << 8) | Math.round(ab + (bb - ab) * t);
    }
}
