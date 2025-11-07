package pt.omst.rasterfall;

import java.io.Closeable;
import java.io.IOException;
import java.time.Instant;
import java.util.Timer;
import java.util.TimerTask;

import pt.omst.rasterfall.replay.LogReplay;

public class RasterfallReplay implements LogReplay.Listener, Closeable {

    private final RasterfallScrollbar scrollbar;

    private long realTime = System.currentTimeMillis();
    private long replayTime = 0;
    private double speed = 1.0;
    private java.util.Timer timer = null;

    public RasterfallReplay(RasterfallScrollbar scrollbar) {
        this.scrollbar = scrollbar;
        realTime = System.currentTimeMillis();
        replayTime = scrollbar.getStartTime();
        LogReplay.addReplayListener(this);
    }

    private TimerTask createTimerTask() {
        return new TimerTask() {
            @Override
            public void run() {
                long replayTime = (long)(RasterfallReplay.this.replayTime + (System.currentTimeMillis() - realTime) * speed);
                if (replayTime > scrollbar.getEndTime()) {
                    stopReplay(scrollbar.getEndTime());
                    return;
                }
                scrollbar.scrollToTime(replayTime, true);
                RasterfallReplay.this.replayTime = replayTime;
                RasterfallReplay.this.realTime = System.currentTimeMillis();
            }
        };
    }

    @Override
    public void replayStateChanged(Instant realTime, Instant replayTime, double speed) {
        this.replayTime = replayTime.toEpochMilli();
        this.realTime = realTime.toEpochMilli();
        this.speed = speed;

        if (timer != null)
            timer.cancel();
        if (speed != 0) {
            timer = new Timer();
            long millisBetweenUpdates = 1000 / 30;
            timer.scheduleAtFixedRate(createTimerTask(), 0, millisBetweenUpdates);
        }
    }

    public void stopReplay(long replayTime) {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }

        this.speed = 0;
        this.replayTime = replayTime;
        //scrollbar.scrollToTime(replayTime, true);
        LogReplay.setReplayState(Instant.ofEpochMilli(realTime), Instant.ofEpochMilli(replayTime), 0);
    }

    public void setSpeed(double speed) {
        if (speed == 0) {
            stopReplay(replayTime);
            return;
        }
        this.replayTime = (long) (RasterfallReplay.this.replayTime + (System.currentTimeMillis() - realTime) * speed);
        this.speed = speed;
        LogReplay.setReplayState(Instant.ofEpochMilli(realTime), Instant.ofEpochMilli(replayTime), speed);
    }

    public void cleanup() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
        LogReplay.removeReplayListener(this);
    }

    @Override
    public void close() throws IOException {
        cleanup();
    }
}
