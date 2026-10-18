package com.eyeblink.lsposed;

import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * The players this app has created, held weakly, and a uniform way to ask one for its state and to
 * pause or resume it. Two kinds are understood: the framework's {@link MediaPlayer} and ExoPlayer in
 * its un-obfuscated forms (ExoPlayer 2 and Media3). A player whose class was renamed by the app's own
 * obfuscation is simply not seen, and the module then reports nothing playing - which EyeBlink treats
 * as "cannot pause this app".
 */
final class Players {

    enum State { PLAYING, PAUSED, NONE }

    private static final class Ref {
        final WeakReference<Object> player;
        Ref(Object p) { player = new WeakReference<>(p); }
    }

    private static final List<Ref> ALL = new ArrayList<>();
    private static WeakReference<Object> lastPausedByUs = new WeakReference<>(null);

    static synchronized void track(Object player) {
        for (Iterator<Ref> it = ALL.iterator(); it.hasNext(); ) {
            Object p = it.next().player.get();
            if (p == null) it.remove();
            else if (p == player) return;
        }
        ALL.add(new Ref(player));
    }

    private static synchronized List<Object> live() {
        List<Object> out = new ArrayList<>();
        for (Iterator<Ref> it = ALL.iterator(); it.hasNext(); ) {
            Object p = it.next().player.get();
            if (p == null) it.remove();
            else out.add(p);
        }
        return out;
    }

    /** What EyeBlink should be told: a playing player if there is one, else the one we paused. */
    static Snapshot snapshot() {
        for (Object p : live()) {
            if (isPlaying(p)) return new Snapshot(State.PLAYING, System.identityHashCode(p), position(p));
        }
        Object paused = lastPausedByUs.get();
        if (paused != null && !isPlaying(paused)) return new Snapshot(State.PAUSED, System.identityHashCode(paused), position(paused));
        return new Snapshot(State.NONE, 0, -1L);
    }

    /** Pauses the playing player. Returns whether a command was delivered. */
    static boolean pause() {
        for (Object p : live()) {
            if (isPlaying(p)) {
                lastPausedByUs = new WeakReference<>(p);
                return command(p, false);
            }
        }
        return false;
    }

    /** Resumes only the player this module paused, never any other. */
    static boolean play() {
        Object p = lastPausedByUs.get();
        if (p == null) return false;
        boolean ok = command(p, true);
        if (ok) lastPausedByUs = new WeakReference<>(null);
        return ok;
    }

    // ── the two kinds of player ──────────────────────────────────

    private static boolean isPlaying(Object p) {
        try {
            if (p instanceof MediaPlayer) return ((MediaPlayer) p).isPlaying();
            Boolean ready = onLooper(p, () -> {
                boolean playWhenReady = (Boolean) call(p, "getPlayWhenReady");
                int state = (Integer) call(p, "getPlaybackState");
                return playWhenReady && state == 3; // STATE_READY
            });
            return ready != null && ready;
        } catch (Throwable t) {
            return false;
        }
    }

    private static long position(Object p) {
        try {
            if (p instanceof MediaPlayer) return ((MediaPlayer) p).getCurrentPosition();
            Long pos = onLooper(p, () -> (Long) call(p, "getCurrentPosition"));
            return pos == null ? -1L : pos;
        } catch (Throwable t) {
            return -1L;
        }
    }

    private static boolean command(Object p, boolean play) {
        try {
            if (p instanceof MediaPlayer) {
                if (play) ((MediaPlayer) p).start(); else ((MediaPlayer) p).pause();
                return true;
            }
            Boolean done = onLooper(p, () -> { call(p, "setPlayWhenReady", play); return Boolean.TRUE; });
            return done != null && done;
        } catch (Throwable t) {
            return false;
        }
    }

    private interface Job<T> { T run() throws Throwable; }

    /** ExoPlayer wants to be touched on its own thread: run there, and wait briefly for the answer. */
    private static <T> T onLooper(Object player, Job<T> job) throws Throwable {
        Looper looper = (Looper) call(player, "getApplicationLooper");
        if (looper == null || looper == Looper.myLooper()) return job.run();
        final Object[] box = new Object[1];
        final Throwable[] error = new Throwable[1];
        final CountDownLatch done = new CountDownLatch(1);
        new Handler(looper).post(() -> {
            try { box[0] = job.run(); } catch (Throwable t) { error[0] = t; } finally { done.countDown(); }
        });
        if (!done.await(300, TimeUnit.MILLISECONDS)) return null;
        if (error[0] != null) throw error[0];
        @SuppressWarnings("unchecked") T result = (T) box[0];
        return result;
    }

    private static Object call(Object target, String name, Object... args) throws Exception {
        Method m = find(target.getClass(), name, args.length);
        m.setAccessible(true);
        return m.invoke(target, args);
    }

    private static Method find(Class<?> type, String name, int argCount) throws NoSuchMethodException {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().equals(name) && m.getParameterTypes().length == argCount) return m;
            }
        }
        throw new NoSuchMethodException(type.getName() + "." + name);
    }

    static final class Snapshot {
        final State state;
        final int content;
        final long position;
        Snapshot(State state, int content, long position) {
            this.state = state;
            this.content = content;
            this.position = position;
        }
    }

    private Players() {}
}
