package com.harlequinxd.drivehard.core.model;

import java.util.Random;

/**
 * Everything that defines how a generated character looks. The player is one preset;
 * pedestrians will be produced by {@link #randomPedestrian(long)} with different seeds.
 */
public final class CharacterSpec {
    public enum Hair { PONYTAIL, SHORT, LONG, BALD, BUN, AFRO }
    public enum Top { TANK, TSHIRT, LONGSLEEVE, DRESS, SUIT }
    public enum Bottom { SHORTS, PANTS, SKIRT }
    public enum Hat { NONE, CAP, SUNHAT }

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
    public Hat hat = Hat.NONE;
    public int hatColor = 0xF2F2EE;
    public boolean sunglasses, beard, mask;

    /** The main character: a woman in a tank top, denim shorts and sneakers, honey blonde hair. */
    public static CharacterSpec player() {
        return new CharacterSpec();
    }

    private static final int[] SKINS = {0xF3D2B8, 0xE6B894, 0xD9A27A, 0xC08660, 0xA36D4A, 0x8A5A3C, 0x6E4530, 0x553424};
    private static final int[] HAIRS = {0x1E1612, 0x2E2018, 0x4A3020, 0x6B4A2C, 0x9C6B3C, 0xD9A650, 0xE8CF92, 0x8C8C88, 0xB04A28};
    private static final int[] CLOTH = {0xF1EFE8, 0x22252B, 0xC8443C, 0x2F5C9E, 0x3E8A5A, 0xE8B33A, 0xE07AA8, 0x40B8B0,
            0x7A4AA0, 0xF08A3C, 0x6E6E6E, 0xB8D8F0, 0x8C6A48, 0x101820};
    private static final int[] BOTTOMS = {0x4D6E9C, 0x2C3E5C, 0x22252B, 0xC8B89A, 0x6E6E6E, 0xF1EFE8, 0x5A6A3C, 0x8C2A2A};

    private static final int[] GREYS = {0xB8B8B4, 0xD8D8D4, 0x8C8C88};
    private static final int[] JP_SKINS = {0xF3D7BE, 0xEDCDB0, 0xE6C4A2, 0xDDB892, 0xD2A882, 0xC69A74};
    private static final int[] JP_HAIRS = {0x141210, 0x1E1814, 0x1E1814, 0x2A2018, 0x3A2A1E, 0x5A3E28, 0x8A6A44, 0xB89A70, 0x6A6A70};
    private static final int[] SUITS = {0x1E2230, 0x2A2C30, 0x3A3E46, 0x22283A, 0x4A4A4E};
    private static final int[] CASUAL = {0xF1EFE8, 0x22252B, 0xC8443C, 0x2F5C9E, 0x3E8A5A, 0xE8B33A, 0xE07AA8, 0x40B8B0,
            0x7A4AA0, 0xF08A3C, 0x6E6E6E, 0xB8D8F0, 0x8C6A48, 0xD8CDB8, 0xA8B89A, 0xF4E4D4};

    /**
     * One of the 100 pedestrian styles for a modern Japanese city: half women, half men, all
     * ages, from salaryman suits and office wear to casual and summer clothes, with some visitors.
     */
    public static CharacterSpec randomPedestrian(long seed) {
        Random r = new Random(seed * 7919L + 17L);
        CharacterSpec s = new CharacterSpec();
        s.female = (seed & 1) == 0;
        boolean elder = r.nextInt(6) == 0;
        boolean visitor = r.nextInt(7) == 0;
        s.height = s.female ? 1.52f + r.nextFloat() * 0.16f : 1.62f + r.nextFloat() * 0.18f;
        if (elder) s.height -= 0.05f;
        s.build = r.nextFloat() * r.nextFloat() * 0.8f + (elder ? 0.15f : 0f);
        s.skin = visitor ? SKINS[r.nextInt(SKINS.length)] : JP_SKINS[r.nextInt(JP_SKINS.length)];
        Hair[] hairs = s.female ? new Hair[]{Hair.PONYTAIL, Hair.LONG, Hair.LONG, Hair.SHORT, Hair.BUN, Hair.SHORT}
                : new Hair[]{Hair.SHORT, Hair.SHORT, Hair.SHORT, Hair.SHORT, Hair.BALD};
        s.hair = hairs[r.nextInt(hairs.length)];
        s.hairColor = elder ? GREYS[r.nextInt(GREYS.length)] : (visitor ? HAIRS[r.nextInt(HAIRS.length)] : JP_HAIRS[r.nextInt(JP_HAIRS.length)]);
        int look = r.nextInt(10);
        if (look < 3 && !elder) {                 // office wear
            if (s.female && r.nextBoolean()) {
                s.top = Top.LONGSLEEVE; s.topColor = r.nextBoolean() ? 0xF4F2EE : 0xE8E0F0;
                s.bottom = Bottom.SKIRT; s.bottomColor = SUITS[r.nextInt(SUITS.length)];
            } else {
                s.top = Top.SUIT; s.topColor = SUITS[r.nextInt(SUITS.length)];
                s.bottom = s.female && r.nextBoolean() ? Bottom.SKIRT : Bottom.PANTS; s.bottomColor = s.topColor;
            }
            s.shoeColor = 0x1A1A1C; s.soleColor = 0x1A1A1C;
        } else {
            Top[] tops = s.female ? new Top[]{Top.TSHIRT, Top.LONGSLEEVE, Top.DRESS, Top.DRESS, Top.TSHIRT, Top.TANK}
                    : new Top[]{Top.TSHIRT, Top.TSHIRT, Top.LONGSLEEVE, Top.TANK};
            s.top = tops[r.nextInt(tops.length)];
            s.topColor = CASUAL[r.nextInt(CASUAL.length)];
            if (s.top == Top.DRESS) { s.bottom = Bottom.SKIRT; s.bottomColor = s.topColor; }
            else {
                Bottom[] bs = s.female ? new Bottom[]{Bottom.PANTS, Bottom.SKIRT, Bottom.SHORTS}
                        : new Bottom[]{Bottom.PANTS, Bottom.PANTS, Bottom.SHORTS};
                s.bottom = bs[r.nextInt(bs.length)];
                s.bottomColor = BOTTOMS[r.nextInt(BOTTOMS.length)];
            }
            s.shoeColor = r.nextInt(3) == 0 ? 0x22252B : (r.nextBoolean() ? 0xF2F2F0 : CASUAL[r.nextInt(CASUAL.length)]);
            s.soleColor = r.nextBoolean() ? 0xEDEDEA : 0x3A3A3A;
        }
        s.lipColor = s.female ? 0xC07070 : mix(s.skin, 0x8A4A44, 0.35f);
        int hatRoll = r.nextInt(12);
        s.hat = hatRoll < 2 ? Hat.CAP : (hatRoll == 2 && s.female ? Hat.SUNHAT : Hat.NONE);
        s.hatColor = r.nextBoolean() ? CASUAL[r.nextInt(CASUAL.length)] : (s.hat == Hat.SUNHAT ? 0xE8D8A8 : 0xF2F2EE);
        s.sunglasses = r.nextInt(10) == 0;
        s.beard = !s.female && visitor && r.nextInt(3) == 0;
        s.mask = r.nextInt(4) == 0;
        return s;
    }

    static int mix(int a, int b, float t) {
        int ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return (Math.round(ar + (br - ar) * t) << 16) | (Math.round(ag + (bg - ag) * t) << 8) | Math.round(ab + (bb - ab) * t);
    }
}
