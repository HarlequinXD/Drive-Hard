package com.harlequinxd.drivehard.core.tex;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/**
 * Invented business names for storefront signs (made-up brands, nothing real).
 * Returns two lines: a brand line and a business-type line.
 */
public final class ShopNames {
    private static final String[] START = {"BEL", "CORA", "LUNA", "MARI", "SOL", "VEN", "ZA", "QUI", "ORA", "PAL",
            "SANTI", "RIO", "BRI", "CAS", "DEL", "FLO", "GAR", "MIRA", "NOVA", "OLA", "PERLA", "ROSA", "TERA",
            "VI", "AZU", "BAHI", "CALI", "DORA", "ESTE", "ISLA", "JAVI", "KORA", "LIDO", "MAYA", "NEVI", "OSTRA",
            "PRIMA", "QUENA", "RENZO", "SIRA", "TULA", "ULMA", "VALE", "XANA", "YARI", "ZEMI"};
    private static final String[] END = {"MAR", "VISTA", "RA", "LO", "NA", "TO", "LIA", "NOS", "ZA", "RIS",
            "VE", "DE", "MO", "TA", "LUZ", "CITA", "NERO", "SOL", "BELLA", "RINA", "VIO", "DA"};
    private static final String[] DESC = {"CORAL", "PALM", "OCEAN", "SUNNY", "TROPIC", "BREEZE", "FLAMINGO",
            "MANGO", "LIME", "AQUA", "PELICAN", "SEASIDE", "SUNSET", "BAYSIDE", "HIBISCUS", "ISLAND",
            "LAGOON", "NEON", "PASTEL", "SANDBAR", "TIDEWAY", "GOLDEN", "SALTWATER", "BLUE HERON", "SEAGRAPE",
            "COCONUT", "TRADEWIND", "MARLIN", "STARFISH", "DRIFTWOOD", "KEYLIME", "SOLANA"};
    private static final String[] TYPE = {"CAFE", "DELI", "BAKERY", "PHARMACY", "LAUNDRY", "SURF SHOP",
            "BOUTIQUE", "PIZZA", "TACOS", "SUSHI", "BAR & GRILL", "CUBAN CAFE", "BODEGA", "BARBER", "NAILS",
            "TATTOO", "LIQUOR", "PAWN", "OPTICAL", "JEWELRY", "SHOES", "RECORDS", "BOOKS", "FLORIST", "GYM",
            "HARDWARE", "ELECTRONICS", "MARKET", "DINER", "ICE CREAM", "CELL REPAIR", "PET SHOP", "TOYS",
            "BIKES", "SAVINGS", "INSURANCE", "TRAVEL", "REALTY", "DENTAL", "SEAFOOD", "JUICE BAR", "VINTAGE",
            "SWIMWEAR", "SMOKE SHOP", "AUTO PARTS", "LOUNGE", "BISTRO", "GELATO", "TAILOR", "PHOTO"};

    private final Random r;
    private final Set<String> used = new HashSet<>();

    public ShopNames(long seed) { r = new Random(seed); }

    private String pick(String[] a) { return a[r.nextInt(a.length)]; }

    public String brand() {
        return pick(START) + pick(END);
    }

    /** {line1, line2} */
    public String[] next() {
        for (int attempt = 0; attempt < 20; attempt++) {
            String type = pick(TYPE);
            String l1;
            switch (r.nextInt(5)) {
                case 0: l1 = brand(); break;
                case 1: l1 = pick(DESC); break;
                case 2: l1 = brand() + "'S"; break;
                case 3: l1 = pick(DESC) + " " + (r.nextBoolean() ? "KEY" : "BAY"); break;
                default: l1 = "LA " + brand(); break;
            }
            if (l1.length() > 20) continue;
            String key = l1 + "|" + type;
            if (used.add(key)) return new String[]{l1, type};
        }
        return new String[]{brand(), pick(TYPE)};
    }
}
