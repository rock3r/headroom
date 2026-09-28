package dev.sebastiano.headroom.widget.render;

import android.annotation.SuppressLint;

import androidx.compose.remote.creation.actions.Action;
import androidx.compose.remote.creation.actions.HostAction;
import androidx.compose.remote.creation.compose.action.RemoteAction;
import androidx.compose.remote.creation.compose.state.RemoteStateScope;

import org.jspecify.annotations.NonNull;

/**
 * A tap that the widget host turns into a click on {@code tapId}.
 *
 * <p>The platform widget player only reports id-based host actions to {@code RemoteViews}, which
 * then sends the pending intent registered with {@code setOnClickPendingIntent(tapId, ...)}. The
 * public {@code pendingIntentAction} writes a named host action instead, which the platform never
 * listens to, so taps did nothing on a real launcher.
 *
 * <p>Remote Compose alpha20 has no public id-based action, and {@code RemoteAction} declares its
 * writer as a Kotlin {@code internal} member, which Kotlin code in another module cannot
 * implement. At the bytecode level it is a public method with a module-mangled name, so this one
 * class is written in Java. Like {@code RestrictedRemoteApis.kt}, it is a restricted API use that
 * the project accepts for the pinned alpha; it breaks at compile time if the name changes.
 */
// RemoteAction and HostAction are restricted in alpha20; see above.
@SuppressLint("RestrictedApi")
final class WidgetTapAction extends RemoteAction {
    private final int mTapId;

    WidgetTapAction(int tapId) {
        if (tapId == 0) {
            throw new IllegalArgumentException("Tap ids must not be 0");
        }
        mTapId = tapId;
    }

    int getTapId() {
        return mTapId;
    }

    @Override
    public @NonNull Action toRemoteAction$remote_creation_compose(@NonNull RemoteStateScope scope) {
        return new HostAction(mTapId);
    }
}
