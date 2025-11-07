package pt.omst.rasterfall.replay;

import java.time.Instant;
import java.util.concurrent.CopyOnWriteArrayList;

public class LogReplay {

    private static final CopyOnWriteArrayList<Listener> replayListeners = new CopyOnWriteArrayList<>();

    public static void addReplayListener(Listener listener) {
        if (!replayListeners.contains(listener))
            replayListeners.add(listener);
    }

    public static void removeReplayListener(Listener listener) {
        replayListeners.remove(listener);
    }

    public static void setReplayState(Instant realTime, Instant replayTime, double speed) {
        for (Listener listener : replayListeners)
             listener.replayStateChanged(realTime, replayTime, speed);
    }

    public interface Listener {
        void replayStateChanged(Instant realTime, Instant replayTime, double speed);
    }
}