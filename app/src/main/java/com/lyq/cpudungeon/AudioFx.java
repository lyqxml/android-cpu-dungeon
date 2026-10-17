package com.lyq.cpudungeon;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

/**
 * Tiny procedural SFX: a 120 ms noise/sine "zap" generated at startup and
 * played through a small pool of static AudioTracks. No audio files.
 */
public final class AudioFx {
    private static final int SAMPLE_RATE = 22050;
    private static final int VOICES = 6;

    private final short[] shotPcm;
    private final AudioTrack[] voices = new AudioTrack[VOICES];
    private int next;

    public AudioFx() {
        shotPcm = buildShot();
    }

    /** Lazily (re)creates tracks so a released instance can come back. */
    private boolean ensure() {
        if (voices[0] != null) {
            return true;
        }
        try {
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            AudioFormat format = new AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build();
            for (int i = 0; i < VOICES; i++) {
                AudioTrack track = new AudioTrack.Builder()
                        .setAudioAttributes(attrs)
                        .setAudioFormat(format)
                        .setBufferSizeInBytes(shotPcm.length * 2)
                        .setTransferMode(AudioTrack.MODE_STATIC)
                        .build();
                track.write(shotPcm, 0, shotPcm.length);
                track.setVolume(0.55f);
                voices[i] = track;
            }
            return true;
        } catch (Throwable t) {
            release();
            return false;
        }
    }

    public void prewarm() {
        ensure();
    }

    public void shot() {
        if (!ensure()) {
            return;
        }
        try {
            int start = next;
            for (int i = 0; i < VOICES; i++) {
                int idx = (start + i) % VOICES;
                AudioTrack track = voices[idx];
                if (track.getPlayState() != AudioTrack.PLAYSTATE_PLAYING) {
                    next = (idx + 1) % VOICES;
                    track.stop();
                    track.reloadStaticData();
                    track.play();
                    return;
                }
            }
            AudioTrack track = voices[next];
            next = (next + 1) % VOICES;
            track.stop();
            track.reloadStaticData();
            track.play();
        } catch (Throwable ignored) {
            // audio is decorative; never crash the renderer
        }
    }

    public void release() {
        for (int i = 0; i < VOICES; i++) {
            if (voices[i] != null) {
                try {
                    voices[i].stop();
                    voices[i].release();
                } catch (Throwable ignored) {
                }
                voices[i] = null;
            }
        }
    }

    private static short[] buildShot() {
        int samples = (int) (SAMPLE_RATE * 0.12f);
        short[] pcm = new short[samples];
        long state = 0x9E3779B97F4A7C15L;
        for (int i = 0; i < samples; i++) {
            float t = i / (float) SAMPLE_RATE;
            float env = (float) Math.exp(-t * 36f);
            state ^= state << 13;
            state ^= state >>> 7;
            state ^= state << 17;
            float noise = ((int) (state & 0xFFFF) - 32768) / 32768f;
            float sweep = 190f - 1500f * t;
            float tone = (float) Math.sin(2.0 * Math.PI * sweep * t);
            float value = env * (noise * 0.55f + tone * 0.5f);
            if (t < 0.004f) {
                value += (1f - t / 0.004f) * 0.35f;
            }
            int s = (int) (value * 22000f);
            if (s > 32767) s = 32767;
            if (s < -32768) s = -32768;
            pcm[i] = (short) s;
        }
        return pcm;
    }
}
