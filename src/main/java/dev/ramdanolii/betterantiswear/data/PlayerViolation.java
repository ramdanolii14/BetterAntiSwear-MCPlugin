package dev.ramdanolii.betterantiswear.data;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Holds all violation data for a single player.
 */
public class PlayerViolation {

    private final String uuid;
    private final String playerName;
    private int count;
    private LocalDateTime lastViolation;

    // Internal mute state (no external plugin required)
    private boolean muted;
    private LocalDateTime muteExpiry;

    // ----------------------------------------------------------------

    public PlayerViolation(String uuid, String playerName) {
        this.uuid = uuid;
        this.playerName = playerName;
        this.count = 0;
        this.lastViolation = null;
        this.muted = false;
        this.muteExpiry = null;
    }

    // ----------------------------------------------------------------
    // Violation helpers
    // ----------------------------------------------------------------

    /** Increment the violation counter and stamp the time. */
    public void incrementViolation() {
        this.count++;
        this.lastViolation = LocalDateTime.now();
    }

    /** Returns true if this record has expired (auto-reset window passed). */
    public boolean isExpired(long resetAfterMinutes) {
        if (resetAfterMinutes <= 0 || lastViolation == null) return false;
        return ChronoUnit.MINUTES.between(lastViolation, LocalDateTime.now()) >= resetAfterMinutes;
    }

    /** How many minutes until the violation record resets (-1 = never). */
    public long minutesUntilReset(long resetAfterMinutes) {
        if (resetAfterMinutes <= 0 || lastViolation == null) return -1;
        long elapsed = ChronoUnit.MINUTES.between(lastViolation, LocalDateTime.now());
        return Math.max(0, resetAfterMinutes - elapsed);
    }

    public void resetViolations() {
        this.count = 0;
        this.lastViolation = null;
    }

    // ----------------------------------------------------------------
    // Mute helpers
    // ----------------------------------------------------------------

    public void mute(int minutes) {
        this.muted = true;
        this.muteExpiry = LocalDateTime.now().plusMinutes(minutes);
    }

    public void unmute() {
        this.muted = false;
        this.muteExpiry = null;
    }

    /** Returns true if the player is currently muted (checks expiry). */
    public boolean isMuted() {
        if (!muted) return false;
        if (muteExpiry != null && LocalDateTime.now().isAfter(muteExpiry)) {
            unmute();
            return false;
        }
        return true;
    }

    /** Minutes remaining on the internal mute, or 0 if not muted. */
    public long muteMinutesRemaining() {
        if (!isMuted() || muteExpiry == null) return 0;
        return Math.max(1, ChronoUnit.MINUTES.between(LocalDateTime.now(), muteExpiry));
    }

    // ----------------------------------------------------------------
    // Getters / setters
    // ----------------------------------------------------------------

    public String getUuid()          { return uuid; }
    public String getPlayerName()    { return playerName; }
    public int getCount()            { return count; }
    public void setCount(int count)  { this.count = count; }
    public LocalDateTime getLastViolation() { return lastViolation; }
    public void setLastViolation(LocalDateTime t) { this.lastViolation = t; }
    public LocalDateTime getMuteExpiry() { return muteExpiry; }
    public void setMuteExpiry(LocalDateTime t) { this.muteExpiry = t; }
    public void setMuted(boolean v)  { this.muted = v; }
}
