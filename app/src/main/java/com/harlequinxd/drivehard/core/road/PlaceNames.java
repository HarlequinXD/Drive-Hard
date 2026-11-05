package com.harlequinxd.drivehard.core.road;

import java.util.HashSet;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

/** Japanese-style place names built from common name parts (Takahara, Shimo-Matsuzaki, ...). */
public final class PlaceNames implements java.io.Serializable {
    private static final String[] ROOTS = {
            "Taka", "Kawa", "Yama", "Ishi", "Matsu", "Sakura", "Fuji", "Mori", "Hayashi", "Sawa", "Kuro", "Shira",
            "Ao", "Aka", "Hiro", "Naga", "Tsuru", "Kame", "Ume", "Kiri", "Asa", "Yoshi", "Toyo", "Mizu", "Iwa",
            "Hoshi", "Tsuki", "Kane", "Sugi", "Tachi", "Fuku", "Kiku", "Nara", "Hagi", "Kaga", "Mino", "Ise",
            "Tomi", "Kura", "Wada", "Yana", "Sasa", "Ono", "Momo", "Shio", "Kusa", "Ina", "Kaji", "Tama", "Sumi",
            "Hata", "Kita", "Mae", "Oku", "Hira", "Kado", "Tsuchi", "Take", "Hon", "Ari", "Kami", "Seki", "Tsuno",
            "Ebi", "Kaki", "Kuri", "Nishiki", "Uchi", "Tobi", "Hino", "Shiba", "Ashi", "Kiyo", "Sano", "Ichi"};
    private static final String[] ENDS = {
            "kawa", "yama", "hara", "sawa", "saki", "mura", "no", "oka", "shima", "da", "zawa", "mine", "hama",
            "tani", "zaki", "ura", "machi", "gaoka", "bashi", "dera", "ike", "numa", "hata", "ba", "mizu", "ta",
            "gawa", "zono", "kubo", "numa", "suka", "fuchi", "ishi", "matsu", "ya", "dai", "o", "gi"};
    private static final String[] PREFIX = {"Kami", "Shimo", "Naka", "Higashi", "Nishi", "Kita", "Minami", "Shin", "Oo", "Ko"};
    private static final String[] REGION_ENDS = {"District", "Valley", "Plain", "Highlands", "Coast", "Basin"};

    private final Random r;
    private final Set<String> used = new HashSet<>();

    public PlaceNames(long seed) { r = new Random(seed * 977L + 11); }

    private String base() {
        String root = ROOTS[r.nextInt(ROOTS.length)], end = ENDS[r.nextInt(ENDS.length)];
        // avoid doubled syllables and vowel clashes ("Kawakawa", "Ao" + "o")
        for (int k = 0; k < 20; k++) {
            String lr = root.toLowerCase(Locale.ROOT);
            boolean clash = isVowel(root.charAt(root.length() - 1)) && isVowel(end.charAt(0));
            boolean doubled = lr.endsWith(end) || lr.endsWith(end.substring(0, Math.min(2, end.length())));
            if (!clash && !doubled) break;
            end = ENDS[r.nextInt(ENDS.length)];
        }
        return root + end;
    }

    private static boolean isVowel(char c) { return "aeiou".indexOf(c) >= 0; }

    /** A unique town name. */
    public String town() {
        for (int k = 0; k < 200; k++) {
            String n = base();
            if (r.nextInt(4) == 0) n = PREFIX[r.nextInt(PREFIX.length)] + "-" + n;
            if (used.add(n)) return n;
        }
        return base() + " " + used.size();
    }

    /** A unique region name, e.g. "Sakura Plain". */
    public String region(boolean coastal, boolean high) {
        for (int k = 0; k < 200; k++) {
            String n = ROOTS[r.nextInt(ROOTS.length)];
            if (r.nextBoolean()) n = n + ENDS[r.nextInt(ENDS.length)];
            String end = coastal ? "Coast" : (high ? (r.nextBoolean() ? "Highlands" : "Valley") : REGION_ENDS[r.nextInt(3)]);
            String full = n + " " + end;
            if (used.add(full) && used.add(n)) return full;
        }
        return "Region " + used.size();
    }
}
