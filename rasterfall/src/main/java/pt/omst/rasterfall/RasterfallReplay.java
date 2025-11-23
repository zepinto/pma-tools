//***************************************************************************
// Copyright 2025 OceanScan - Marine Systems & Technology, Lda.             *
//***************************************************************************
// Author: José Pinto                                                       *
//***************************************************************************
package pt.omst.rasterfall;

import java.io.Closeable;
import java.io.IOException;
import java.time.Instant;
import java.util.Timer;
import java.util.TimerTask;

import lombok.extern.slf4j.Slf4j;
import pt.omst.rasterfall.replay.LogReplay;

@Slf4j
public class RasterfallReplay implements LogReplay.Listener, Closeable {

    private final RasterfallScrollbar scrollbar;

    private long realTime = System.currentTimeMillis();
    private long replayTime = 0;
    private double speed = 1.0;
    private RasterfallTiles waterfall;
    private java.util.Timer timer = null;
    private final long startTime;
    private final long endTime;
    private final long totalDuration;

    public RasterfallReplay(RasterfallScrollbar scrollbar, RasterfallTiles waterfall) {
        this.scrollbar = scrollbar;
        this.waterfall = waterfall;
        realTime = System.currentTimeMillis();
        startTime = waterfall.getStartTime();
        endTime = waterfall.getEndTime();
        totalDuration = endTime - startTime;
        replayTime = getTimeRelativeToStart(waterfall);
        LogReplay.addReplayListener(this);
    }

    private long getTimeRelativeToStart(RasterfallTiles waterfall) {
        long replayTime = waterfall.getMiddleTimestamp() - startTime;
        log.info("start time: {}, middle time: {}, replay time: {}", startTime, waterfall.getMiddleTimestamp(), replayTime/1000.0);        
        return replayTime;
    }

    private boolean goToTime(long timestamp) {
        if (timestamp > scrollbar.getEndTime()) {
            timestamp = scrollbar.getEndTime();
            stopReplay(scrollbar.getEndTime());
            return false;
        }
        scrollbar.scrollToTime(timestamp, true);
        return true;
    }

    private TimerTask createTimerTask() {
        log.info("Starting replay timer task with speed {}X", speed);
        realTime = System.currentTimeMillis();
        this.replayTime = getTimeRelativeToStart(waterfall);
        return new TimerTask() {
            @Override
            public void run() {
                log.info("Replay time: {}, Speed: {}X", replayTime, speed);
                long newRealtime = System.currentTimeMillis();
                long elapsedRealTime = newRealtime - realTime;
                replayTime += (long) (elapsedRealTime * speed);
                realTime = newRealtime;                      
                if (!goToTime(replayTime+startTime)) {
                    this.cancel();
                    timer = null;
                    return;
                }
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
