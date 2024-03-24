package com.ikalagaming.factory.crafting;

/**
 * Contains various units for power as multipliers, for convenience.
 */
public class PowerConstants {

    /**
     * A single joule, more for annotation than utility.
     */
    public static final long JOULE = 1L;
    /**
     * Represents 10^3 J, a kilojoule (kJ).
     */
    public static final long KILOJOULE = 1_000L;
    /**
     * Represents 10^6 J, a megajoule (MJ).
     */
    public static final long MEGAJOULE = 1_000_000L;
    /**
     * Represents 10^9 J, a gigajoule (GJ).
     */
    public static final long GIGAJOULE = 1_000_000_000L;
    /**
     * Represents 10^12 J, a terajoule (TJ).
     */
    public static final long TERAJOULE = 1_000_000_000_000L;
    /**
     * Represents 10^15 J, a petajoule (PJ).
     */
    public static final long PETAJOULE = 1_000_000_000_000_000L;
    /**
     * Represents 10^18 J, an exajoule (EJ). Please note that we can only store 9 of these.
     */
    public static final long EXAJOULE = 1_000_000_000_000_000_000L;

    /** Private constructor so that this class is not instantiated. */
    private PowerConstants() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
