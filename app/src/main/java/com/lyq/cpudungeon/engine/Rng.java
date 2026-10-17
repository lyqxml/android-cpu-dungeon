package com.lyq.cpudungeon.engine;

/**
 * Deterministic xorshift RNG. The whole game (map, loot, AI jitter) must be
 * reproducible from one seed so levels can be replayed and tested headlessly.
 */
public final class Rng {
    private long state;

    public Rng(long seed) {
        state = seed != 0L ? seed : 0x2545F4914F6CDD1DL;
    }

    public int nextInt(int bound) {
        state ^= state << 13;
        state ^= state >>> 7;
        state ^= state << 17;
        int v = (int) (state & 0x7FFFFFFFL);
        return bound <= 0 ? 0 : v % bound;
    }

    public float nextFloat() {
        return nextInt(1 << 24) / (float) (1 << 24);
    }

    public float range(float min, float max) {
        return min + (max - min) * nextFloat();
    }
}
