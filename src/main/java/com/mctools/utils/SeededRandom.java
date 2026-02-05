package com.mctools.utils;

/**
 * Seeded random number generator for reproducible procedural generation.
 * Uses a hash-based PRNG ported from ShapeGenerator.
 */
public class SeededRandom {

    private final long initialSeed;
    private long state;

    public SeededRandom(long seed) {
        this.initialSeed = seed;
        this.state = seed;
    }

    public void reset() {
        this.state = initialSeed;
    }

    public double next() {
        long t = state += 0x6D2B79F5L;
        t = (t ^ (t >>> 15)) * (t | 1);
        t ^= t + ((t ^ (t >>> 7)) * (t | 61));
        return ((t ^ (t >>> 14)) & 0xFFFFFFFFL) / 4294967296.0;
    }

    public int nextInt(int min, int max) {
        return (int) Math.floor(next() * (max - min + 1)) + min;
    }

    public double nextFloat(double min, double max) {
        return next() * (max - min) + min;
    }

    public double nextAngle() {
        return next() * Math.PI * 2;
    }

    public double vary(double base, double variation) {
        return base + (next() * 2 - 1) * variation;
    }
}
