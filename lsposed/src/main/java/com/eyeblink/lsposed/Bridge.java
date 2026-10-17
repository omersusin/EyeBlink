package com.eyeblink.lsposed;

import android.annotation.SuppressLint;
import android.app.Application;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

/**
 * The module's only conversation with the outside world.
 *
 * EyeBlink broadcasts a command to this app's package. The receiver below is registered with a
 * signature-level permission, so Android delivers the broadcast only if the sender holds it, which
 * means only an app signed with the same key as this module. The answer goes back through the
 * {@link PendingIntent} that arrived with the command: it names one component inside EyeBlink and
 * can be used by nobody else.
 *
 * The protocol is three commands - query, pause, play - and one reply. The strings are a copy of
 * those in the app's LsposedBackend; keep the two in step.
 */
final class Bridge {

    private static final String ACTION_COMMAND = "com.eyeblink.lsposed.COMMAND";
    private static final String PERMISSION = "com.eyeblink.lsposed.permission.BRIDGE";
    private static final String EXTRA_COMMAND = "cmd";
    private static final String EXTRA_NONCE = "nonce";
    private static final String EXTRA_REPLY = "reply";
    private static final String EXTRA_STATE = "state";
    private static final String EXTRA_CONTENT = "content";
    private static final String EXTRA_POSITION = "pos";

    private static boolean registered;

    // The pre-Android-13 branch cannot pass the exported flag, which does not exist there.
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    static synchronized void register(final Application app) {
        if (registered) return;
        registered = true;

        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String cmd = intent.getStringExtra(EXTRA_COMMAND);
                String nonce = intent.getStringExtra(EXTRA_NONCE);
                PendingIntent reply = intent.getParcelableExtra(EXTRA_REPLY);
                if (cmd == null || nonce == null || reply == null) return;

                if ("pause".equals(cmd)) Players.pause();
                else if ("play".equals(cmd)) Players.play();
                else if (!"query".equals(cmd)) return;

                Players.Snapshot s = Players.snapshot();
                Intent answer = new Intent()
                    .putExtra(EXTRA_NONCE, nonce)
                    .putExtra(EXTRA_STATE, s.state.name());
                if (s.state != Players.State.NONE) {
                    answer.putExtra(EXTRA_CONTENT, s.content);
                    if (s.position >= 0) answer.putExtra(EXTRA_POSITION, s.position);
                }
                try {
                    reply.send(context, 0, answer);
                } catch (PendingIntent.CanceledException ignored) {
                    // EyeBlink stopped waiting; nothing to do.
                }
            }
        };

        IntentFilter filter = new IntentFilter(ACTION_COMMAND);
        Handler main = new Handler(Looper.getMainLooper());
        if (Build.VERSION.SDK_INT >= 33) {
            // 2 = RECEIVER_EXPORTED: the sender is another app. The permission is what keeps everyone else out.
            app.registerReceiver(receiver, filter, PERMISSION, main, 2);
        } else {
            app.registerReceiver(receiver, filter, PERMISSION, main);
        }
    }

    private Bridge() {}
}
