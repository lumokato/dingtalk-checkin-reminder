package com.kanon.dingpunchguard;

import android.Manifest;
import android.app.KeyguardManager;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.widget.Toast;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Locale;

public class GuardService extends Service {
    private static final long TICK_VERY_NEAR_MILLIS = CheckInPollingPolicy.TICK_VERY_NEAR_MILLIS;
    private static final long TICK_APPROACHING_MILLIS = CheckInPollingPolicy.TICK_APPROACHING_MILLIS;
    private static final long TICK_DEFAULT_MILLIS = CheckInPollingPolicy.TICK_DEFAULT_MILLIS;
    private static final long TICK_FAR_MILLIS = CheckInPollingPolicy.TICK_FAR_MILLIS;
    private static final long UNLOCK_BURST_MILLIS = 20_000L;
    private static final long UNLOCK_PROBE_MILLIS = 15_000L;
    private static final long CHECKOUT_UNLOCK_BURST_MILLIS = 15_000L;
    private static final long CHECKOUT_UNLOCK_TICK_MILLIS = 1_000L;
    private static final long UNLOCK_PROBE_COOLDOWN_MILLIS = 60_000L;
    private static final long LOCATION_STALE_MILLIS = CheckInPollingPolicy.LOCATION_STALE_MILLIS;
    // Keep these thresholds in sync with docs/checkin-flow.md.
    private static final float EDGE_EXTRA_METERS = CheckInPollingPolicy.EDGE_EXTRA_METERS;
    private static final int PHASE_IDLE = 0;
    private static final int PHASE_CHECKIN = 1;
    private static final int PHASE_CHECKOUT = 2;
    private static final long ASSUMED_OPEN_SUCCESS_DELAY_MILLIS = 5_000L;
    private static final long OPEN_FOREGROUND_VERIFY_DELAY_MILLIS = 2_500L;
    private static final long AUTO_OPEN_RETRY_WINDOW_MILLIS = 30_000L;
    private static final long AUTO_OPEN_RETRY_INTERVAL_MILLIS = 5_000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private LocationManager locationManager;
    private Location lastLocation;
    private boolean locationUpdatesStarted;
    private long locationRequestMillis;
    private float locationRequestMeters;
    private long lastAlertMillis;
    private long lastAutoOpenMillis;
    private long unlockBurstUntilMillis;
    private long lastUnlockProbeMillis;
    private long lastInteractiveSignalMillis;
    private long lastUserPresentMillis;
    private String lastInteractiveSignalAction = "";
    private boolean checkoutUnlockBurstActive;
    private boolean autoOpenReadyAtLastEvaluate;
    private long autoOpenRetryUntilMillis;
    private Float previousDistanceMeters;
    private long previousDistanceAtMillis;
    private int lastPhase = PHASE_IDLE;
    private boolean checkInApproachLatched;
    private boolean checkInInsideLatched;
    private boolean checkoutConfirmAlertPosted;

    private final Runnable tickRunnable = new Runnable() {
        @Override
        public void run() {
            evaluate(false);
        }
    };

    private final BroadcastReceiver unlockReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            handleInteractiveSignal(intent == null ? "" : intent.getAction());
        }
    };

    private final LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            lastLocation = location;
            evaluate(false);
        }

        @Override
        public void onProviderEnabled(String provider) {
            evaluate(true);
        }

        @Override
        public void onProviderDisabled(String provider) {
            evaluate(true);
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationHelper.ensureChannels(this);
        locationManager = getSystemService(LocationManager.class);
        NotificationHelper.startForeground(this, "等待打卡窗口", false);
        registerUnlockReceiver();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? Config.ACTION_REFRESH : intent.getAction();
        AppLog.i(this, "service start action=" + action + " startId=" + startId);
        if (Config.ACTION_STOP.equals(action)) {
            Config.setEnabled(this, false);
            TimeScheduler.cancelAll(this);
            stopEverything();
            return START_NOT_STICKY;
        }

        if (Config.ACTION_OPEN_DING.equals(action)) {
            int phase = currentPhase(this);
            if (openDingTalk()) {
                long openMillis = Config.lastDingTalkOpenMillis(this);
                verifyDingTalkForegroundAfterLaunch(phase, openMillis, false);
                maybeRecordAssumedOpenSuccess(phase, openMillis);
            }
            if (currentPhase(this) == PHASE_IDLE) {
                handler.postDelayed(this::stopEverything, 1_000L);
                return START_NOT_STICKY;
            }
            return START_STICKY;
        }

        if (Config.ACTION_CONFIRM_CHECKIN.equals(action)) {
            PunchRecorder.recordCheckIn(this, System.currentTimeMillis(), "手动确认");
            Toast.makeText(this, "已记录上班打卡", Toast.LENGTH_SHORT).show();
            evaluate(true);
            return currentPhase(this) == PHASE_IDLE ? START_NOT_STICKY : START_STICKY;
        }

        if (Config.ACTION_CONFIRM_CHECKOUT.equals(action)) {
            PunchRecorder.recordCheckOut(this, System.currentTimeMillis(), "手动确认");
            Toast.makeText(this, "已记录下班打卡", Toast.LENGTH_SHORT).show();
            evaluate(true);
            return currentPhase(this) == PHASE_IDLE ? START_NOT_STICKY : START_STICKY;
        }

        if (Config.ACTION_USER_PRESENT.equals(action)) {
            handleInteractiveSignal(action);
            return currentPhase(this) == PHASE_IDLE ? START_NOT_STICKY : START_STICKY;
        }

        if (Config.ACTION_START.equals(action)) {
            Config.setEnabled(this, true);
        }
        if (!Config.isEnabled(this)) {
            stopEverything();
            return START_NOT_STICKY;
        }
        TimeScheduler.scheduleAll(this);
        handler.removeCallbacks(tickRunnable);
        evaluate(true);
        return currentPhase(this) == PHASE_IDLE ? START_NOT_STICKY : START_STICKY;
    }

    static boolean isWindowActiveNow(Context context) {
        return currentPhase(context) != PHASE_IDLE;
    }

    private static int currentPhase(Context context) {
        LocalDate today = LocalDate.now();
        if (!ChinaWorkdayCalendar.isWorkday(today)) {
            return PHASE_IDLE;
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime checkInStart = today.atTime(Config.checkInStart(context)).minusMinutes(30);
        LocalDateTime checkInEnd = today.atTime(Config.checkInStart(context)).plusMinutes(Config.lateMinutes(context));
        if (!Config.hasCheckedInToday(context)
                && !now.isBefore(checkInStart)
                && !now.isAfter(checkInEnd)) {
            return PHASE_CHECKIN;
        }

        if (!Config.hasCheckedOutToday(context)) {
            long due = TimeScheduler.checkoutDueMillis(context);
            long stop = due + Config.checkoutGraceMinutes(context) * 60_000L;
            long current = System.currentTimeMillis();
            if (due > 0L && current >= due && current <= stop) {
                return PHASE_CHECKOUT;
            }
        }
        return PHASE_IDLE;
    }

    private void evaluate(boolean forceAlert) {
        if (!Config.isEnabled(this)) {
            AppLog.i(this, "guard disabled; stopping service");
            stopEverything();
            return;
        }

        int phase = currentPhase(this);
        DeviceState device = deviceState();
        boolean autoOpenReady = canAutoOpenDingTalk(device);
        boolean becameAutoOpenReady = autoOpenReady && !autoOpenReadyAtLastEvaluate;
        autoOpenReadyAtLastEvaluate = autoOpenReady;
        lastPhase = phase;
        if (phase != PHASE_CHECKIN) {
            checkInApproachLatched = false;
            checkInInsideLatched = false;
        }
        if (phase == PHASE_IDLE) {
            autoOpenRetryUntilMillis = 0L;
            checkoutUnlockBurstActive = false;
        }
        if (phase != PHASE_CHECKOUT) {
            checkoutConfirmAlertPosted = false;
            checkoutUnlockBurstActive = false;
        }
        if (phase == PHASE_IDLE) {
            AppLog.i(this, "outside active window; stopping service until next alarm device="
                    + device.logText()
                    + " autoOpenReady=" + autoOpenReady
                    + " becameAutoOpenReady=" + becameAutoOpenReady
                    + " userPresentAgeMs=" + LaunchGate.userPresentAgeMillis(lastUserPresentMillis, System.currentTimeMillis()));
            if (TimeScheduler.markExpiredCheckInIfNeeded(this)) {
                AppLog.i(this, "check-in window expired without confirmation; checkout remains scheduled");
                TimeScheduler.scheduleAll(this);
            }
            stopEverything();
            return;
        }

        boolean phaseNeedsLocation = (phase == PHASE_CHECKIN && !checkInInsideLatched)
                || Config.requireLocationForCheckout(this);
        AppLog.i(this, "evaluate phase=" + phase
                + " force=" + forceAlert
                + " needsLocation=" + phaseNeedsLocation
                + " insideLatched=" + checkInInsideLatched
                + " device=" + device.logText()
                + " autoOpenReady=" + autoOpenReady
                + " becameAutoOpenReady=" + becameAutoOpenReady
                + " userPresentAgeMs=" + LaunchGate.userPresentAgeMillis(lastUserPresentMillis, System.currentTimeMillis())
                + " lastSignal=" + lastInteractiveSignalAction
                + " signalAgeMs=" + signalAgeMillis());
        NotificationHelper.startForeground(
                this,
                phase == PHASE_CHECKIN ? "上班窗口检测中" : "下班窗口提醒中",
                phaseNeedsLocation && hasFineLocationPermission()
        );

        if (phaseNeedsLocation) {
            if (!Config.isLocationConfigured(this)) {
                NotificationHelper.postAlert(
                        this,
                        "请先配置打卡地点",
                        "源码不内置任何个人地点。请打开应用填写高德坐标和范围半径后再启动守护。",
                        null
                );
                return;
            }
            if (!hasFineLocationPermission()) {
                NotificationHelper.postAlert(
                        this,
                        "打卡提醒需要精确定位",
                        "请打开精确定位权限。只有大概位置时，应用无法可靠判断是否进入 " + Config.placeName(this) + " "
                                + Config.radiusMeters(this) + "米范围。",
                        phase == PHASE_CHECKIN ? Config.ACTION_CONFIRM_CHECKIN : Config.ACTION_CONFIRM_CHECKOUT
                );
                return;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !hasBackgroundLocationPermission()) {
                NotificationHelper.postAlert(
                        this,
                        "后台定位未完全授权",
                        "Android 后台启动定位服务需要“始终允许定位”。当前仍会尝试提醒，但后台自动判断范围可能失效。",
                        phase == PHASE_CHECKIN ? Config.ACTION_CONFIRM_CHECKIN : Config.ACTION_CONFIRM_CHECKOUT
                );
            }
            startLocationUpdates();
        } else {
            stopLocationUpdates();
        }

        if (phase == PHASE_CHECKIN) {
            handleCheckInPhase(forceAlert, device, becameAutoOpenReady);
        } else if (phase == PHASE_CHECKOUT) {
            handleCheckOutPhase(forceAlert, device, becameAutoOpenReady);
        }
        scheduleNextTick(phase);
    }

    private void handleCheckInPhase(boolean forceAlert, DeviceState device, boolean becameAutoOpenReady) {
        DistanceState state = distanceState();
        boolean autoOpenReady = canAutoOpenDingTalk(device);
        GuardDecisionEngine.CheckInAction action = GuardDecisionEngine.decideCheckIn(
                state.hasLocation,
                isLocationStale(state),
                state.inside,
                autoOpenReady
        );
        switch (action) {
            case WAIT_LOCATION:
                AppLog.i(this, "check-in waiting for location device=" + device.logText());
                maybeStartUnlockedProbe(device, "checkin_no_location");
                maybeAlert(
                        forceAlert,
                        "正在等待定位",
                        "现在是上班打卡窗口。请确认系统定位已开启，进入 " + Config.placeName(this) + " "
                                + Config.radiusMeters(this) + "米范围后会提醒并尝试打开钉钉。",
                        Config.ACTION_CONFIRM_CHECKIN
                );
                return;
            case WAIT_FRESH_LOCATION:
                AppLog.i(this, String.format(Locale.CHINA, "check-in waiting for fresh location staleAgeMs=%d distance=%.0f device=%s",
                        state.ageMillis,
                        state.distanceMeters,
                        device.logText()));
                maybeStartUnlockedProbe(device, "checkin_stale_location");
                maybeAlert(
                        forceAlert,
                        "正在刷新定位",
                        "当前位置已过期，正在刷新后再判断是否进入 " + Config.placeName(this) + " "
                                + Config.radiusMeters(this) + "米范围。",
                        Config.ACTION_CONFIRM_CHECKIN
                );
                return;
            case OUTSIDE_TARGET:
                boolean wasLatched = checkInApproachLatched;
                boolean approachActive = isCheckInApproachActive(state);
                if (!wasLatched && approachActive) {
                    updateLocationRequestPolicy(false);
                }
                AppLog.i(this, String.format(Locale.CHINA, "check-in outside target distance=%.0f radius=%d ageMs=%d device=%s",
                        state.distanceMeters,
                        Config.radiusMeters(this),
                        state.ageMillis,
                        device.logText()));
                maybeStartUnlockedProbe(device, "checkin_outside");
                NotificationHelper.startForeground(
                        this,
                        String.format(Locale.CHINA, "距%s约%.0f米，继续等待进入范围", Config.placeName(this), state.distanceMeters),
                        true
                );
                return;
            case INSIDE_WAIT_UNLOCK:
            case INSIDE_OPEN_DINGTALK:
                if (!checkInInsideLatched) {
                    checkInInsideLatched = true;
                    stopLocationUpdates();
                    AppLog.i(this, "check-in inside latched; continuous location paused");
                }
                AppLog.i(this, "check-in inside target action=" + action
                        + " device=" + device.logText()
                        + " becameAutoOpenReady=" + becameAutoOpenReady);
                String text = String.format(Locale.CHINA, "已进入%s %d米范围内。%s",
                        Config.placeName(this),
                        Config.radiusMeters(this),
                        autoOpenReady ? "正在打开钉钉；若未成功，请点通知手动打开。" : "解锁手机后会继续尝试打开钉钉。");
                if (action != GuardDecisionEngine.CheckInAction.INSIDE_OPEN_DINGTALK
                        || !ForegroundAppVerifier.hasUsageStatsAccess(this)) {
                    maybeAlert(forceAlert, "上班打卡", text, Config.ACTION_CONFIRM_CHECKIN);
                }
                if (action == GuardDecisionEngine.CheckInAction.INSIDE_OPEN_DINGTALK) {
                    maybeOpenDingTalk(forceAlert || becameAutoOpenReady);
                }
        }
    }

    private void handleCheckOutPhase(boolean forceAlert, DeviceState device, boolean becameAutoOpenReady) {
        boolean requiresLocation = Config.requireLocationForCheckout(this);
        DistanceState state = requiresLocation ? distanceState() : DistanceState.noLocation();
        boolean autoOpenReady = canAutoOpenDingTalk(device);
        GuardDecisionEngine.CheckOutAction action = GuardDecisionEngine.decideCheckOut(
                requiresLocation,
                state.hasLocation,
                isLocationStale(state),
                state.inside,
                autoOpenReady
        );
        switch (action) {
            case WAIT_LOCATION:
                AppLog.i(this, "check-out waiting for location device=" + device.logText());
                if (forceAlert) {
                    maybeAlert(true, "下班打卡", "已到下班时间，正在等待定位确认范围。", Config.ACTION_CONFIRM_CHECKOUT);
                }
                return;
            case WAIT_FRESH_LOCATION:
                AppLog.i(this, String.format(Locale.CHINA, "check-out waiting for fresh location staleAgeMs=%d distance=%.0f device=%s",
                        state.ageMillis,
                        state.distanceMeters,
                        device.logText()));
                if (forceAlert) {
                    maybeAlert(true, "下班打卡", "已到下班时间，正在刷新定位确认范围。", Config.ACTION_CONFIRM_CHECKOUT);
                }
                return;
            case OUTSIDE_TARGET:
                AppLog.i(this, String.format(Locale.CHINA, "check-out outside target distance=%.0f radius=%d", state.distanceMeters, Config.radiusMeters(this)));
                if (forceAlert) {
                    maybeAlert(true, "下班打卡", "已到下班时间，但当前位置看起来不在打卡范围内。请确认是否需要手动处理。", Config.ACTION_CONFIRM_CHECKOUT);
                }
                return;
            case WAIT_UNLOCK:
                AppLog.i(this, "check-out ready but waiting unlock device=" + device.logText() + " becameAutoOpenReady=" + becameAutoOpenReady);
                NotificationHelper.startForeground(this, "下班待处理，解锁后打开钉钉", false);
                return;
            case OPEN_DINGTALK:
                AppLog.i(this, "check-out ready action=" + action + " device=" + device.logText() + " becameAutoOpenReady=" + becameAutoOpenReady);
                maybeOpenDingTalk(forceAlert || becameAutoOpenReady);
                if (!Config.assumeDingTalkOpenMeansSuccess(this)) {
                    postCheckoutConfirmAlertOnce();
                }
        }
    }

    private void handleInteractiveSignal(String action) {
        DeviceState device = deviceState();
        long now = System.currentTimeMillis();
        lastInteractiveSignalMillis = now;
        lastInteractiveSignalAction = action == null ? "" : action;
        if (Intent.ACTION_USER_PRESENT.equals(action) || Config.ACTION_USER_PRESENT.equals(action)) {
            lastUserPresentMillis = now;
        }
        AppLog.i(this, "interactive signal received action=" + lastInteractiveSignalAction + " device=" + device.logText());
        if (!Config.isEnabled(this)) {
            AppLog.i(this, "interactive signal ignored because guard is disabled action=" + action);
            stopEverything();
            return;
        }

        int phase = currentPhase(this);
        lastPhase = phase;
        if (phase == PHASE_IDLE) {
            AppLog.i(this, "interactive signal ignored outside active window action=" + action);
            stopEverything();
            return;
        }

        long burstMillis = interactiveBurstDurationMillis(phase, action);
        if (burstMillis > 0L) {
            if (phase == PHASE_CHECKOUT && !Config.requireLocationForCheckout(this)) {
                startCheckoutUnlockBurst(action);
            } else {
                startUnlockBurst(burstMillis, action);
            }
            evaluate(true);
            return;
        }

        boolean force = (phase == PHASE_CHECKOUT && !Config.requireLocationForCheckout(this))
                || (phase == PHASE_CHECKIN && checkInInsideLatched);
        AppLog.i(this, "interactive signal evaluated without burst action=" + action + " phase=" + phase + " force=" + force);
        evaluate(force);
    }

    private boolean maybeStartUnlockedProbe(DeviceState device, String reason) {
        if (!device.keyguardClear) {
            return false;
        }
        if (!Config.isLocationConfigured(this) || !hasFineLocationPermission()) {
            AppLog.i(this, "unlocked probe skipped because location is not ready reason=" + reason);
            return false;
        }
        long now = System.currentTimeMillis();
        if (now - lastUnlockProbeMillis < UNLOCK_PROBE_COOLDOWN_MILLIS) {
            AppLog.i(this, "unlocked probe skipped by cooldown reason=" + reason);
            return false;
        }
        lastUnlockProbeMillis = now;
        AppLog.i(this, "unlocked probe started reason=" + reason);
        startUnlockBurst(UNLOCK_PROBE_MILLIS, reason);
        return true;
    }

    private void maybeAlert(boolean force, String title, String text, String confirmAction) {
        long now = System.currentTimeMillis();
        long interval = Config.reminderMinutes(this) * 60_000L;
        if (force || now - lastAlertMillis >= interval) {
            NotificationHelper.postAlert(this, title, text, confirmAction);
            lastAlertMillis = now;
        }
    }

    private void maybeOpenDingTalk(boolean force) {
        DeviceState device = deviceState();
        if (!canAutoOpenDingTalk(device)) {
            AppLog.i(this, "DingTalk auto open skipped because screen/keyguard gate is not satisfied device="
                    + device.logText()
                    + " userPresentAgeMs=" + LaunchGate.userPresentAgeMillis(lastUserPresentMillis, System.currentTimeMillis()));
            return;
        }
        long now = System.currentTimeMillis();
        long interval = Config.reminderMinutes(this) * 60_000L;
        boolean retryAfterForegroundFailure = now <= autoOpenRetryUntilMillis
                && now - lastAutoOpenMillis >= AUTO_OPEN_RETRY_INTERVAL_MILLIS;
        if (force || retryAfterForegroundFailure || now - lastAutoOpenMillis >= interval) {
            int phase = currentPhase(this);
            AppLog.i(this, "attempting to open DingTalk phase=" + phase + " force=" + force + " retry=" + retryAfterForegroundFailure);
            if (openDingTalk()) {
                long openMillis = Config.lastDingTalkOpenMillis(this);
                verifyDingTalkForegroundAfterLaunch(phase, openMillis, retryAfterForegroundFailure);
                maybeRecordAssumedOpenSuccess(phase, openMillis);
            }
            lastAutoOpenMillis = now;
        }
    }

    private long interactiveBurstDurationMillis(int phase, String action) {
        if (phase == PHASE_CHECKOUT && !Config.requireLocationForCheckout(this)) {
            return CHECKOUT_UNLOCK_BURST_MILLIS;
        }
        if (phase == PHASE_CHECKIN && checkInInsideLatched) {
            return 0L;
        }
        if (!Config.isLocationConfigured(this) || !hasFineLocationPermission()) {
            return 0L;
        }

        if (phase == PHASE_CHECKIN) {
            long now = System.currentTimeMillis();
            if (now - lastUnlockProbeMillis < UNLOCK_PROBE_COOLDOWN_MILLIS) {
                AppLog.i(this, "check-in interactive probe skipped by cooldown action=" + action);
                return 0L;
            }
            lastUnlockProbeMillis = now;
            AppLog.i(this, "check-in interactive probe forced action=" + action);
            return UNLOCK_PROBE_MILLIS;
        }

        DistanceState state = distanceState();
        if (!state.hasLocation || state.ageMillis > LOCATION_STALE_MILLIS) {
            long now = System.currentTimeMillis();
            if (now - lastUnlockProbeMillis < UNLOCK_PROBE_COOLDOWN_MILLIS) {
                return 0L;
            }
            lastUnlockProbeMillis = now;
            return UNLOCK_PROBE_MILLIS;
        }

        int radius = Math.max(1, Config.radiusMeters(this));
        float extraMeters = Math.max(0.0f, state.distanceMeters - radius);
        if (state.inside || extraMeters <= EDGE_EXTRA_METERS) {
            return UNLOCK_BURST_MILLIS;
        }
        return 0L;
    }

    private void startCheckoutUnlockBurst(String reason) {
        long now = System.currentTimeMillis();
        unlockBurstUntilMillis = Math.max(unlockBurstUntilMillis, now + CHECKOUT_UNLOCK_BURST_MILLIS);
        checkoutUnlockBurstActive = true;
        handler.removeCallbacks(tickRunnable);
        handler.postDelayed(tickRunnable, CHECKOUT_UNLOCK_TICK_MILLIS);
        AppLog.i(this, "checkout unlock burst started duration="
                + CHECKOUT_UNLOCK_BURST_MILLIS
                + "ms tick="
                + CHECKOUT_UNLOCK_TICK_MILLIS
                + "ms reason="
                + reason);
    }

    private void startUnlockBurst(long durationMillis, String reason) {
        if (durationMillis <= 0L) {
            return;
        }
        long now = System.currentTimeMillis();
        unlockBurstUntilMillis = Math.max(unlockBurstUntilMillis, now + durationMillis);
        checkoutUnlockBurstActive = false;
        handler.removeCallbacks(tickRunnable);
        handler.postDelayed(tickRunnable, TICK_VERY_NEAR_MILLIS);
        updateLocationRequestPolicy(true);
        AppLog.i(this, "unlock burst started duration=" + durationMillis + "ms reason=" + reason);
    }

    private boolean inUnlockBurst() {
        return System.currentTimeMillis() <= unlockBurstUntilMillis;
    }

    private boolean isLocationStale(DistanceState state) {
        return LocationFreshness.isStale(state.hasLocation, state.ageMillis, LOCATION_STALE_MILLIS);
    }

    private void scheduleNextTick(int phase) {
        if (!Config.isEnabled(this) || phase == PHASE_IDLE) {
            return;
        }
        long delay = nextTickDelayMillis(phase);
        handler.removeCallbacks(tickRunnable);
        handler.postDelayed(tickRunnable, delay);
        AppLog.i(this, "next evaluate in " + delay + "ms");
    }

    private long nextTickDelayMillis(int phase) {
        if (phase == PHASE_CHECKIN && checkInInsideLatched) {
            return TICK_VERY_NEAR_MILLIS;
        }
        if (inUnlockBurst()) {
            if (phase == PHASE_CHECKOUT && checkoutUnlockBurstActive) {
                return CHECKOUT_UNLOCK_TICK_MILLIS;
            }
            return TICK_VERY_NEAR_MILLIS;
        }
        checkoutUnlockBurstActive = false;
        if (phase != PHASE_CHECKIN && !Config.requireLocationForCheckout(this)) {
            return phase == PHASE_CHECKOUT ? TICK_FAR_MILLIS : TICK_DEFAULT_MILLIS;
        }
        DistanceState state = distanceState();
        if (!state.hasLocation) {
            return phase == PHASE_CHECKIN ? TICK_VERY_NEAR_MILLIS : TICK_APPROACHING_MILLIS;
        }

        boolean approaching = isCheckInApproachActive(state);
        long delay = CheckInPollingPolicy.nextTickDelayMillis(
                phase == PHASE_CHECKIN,
                phase == PHASE_CHECKOUT,
                Config.requireLocationForCheckout(this),
                checkInInsideLatched,
                inUnlockBurst(),
                state.hasLocation,
                state.inside,
                state.distanceMeters,
                Config.radiusMeters(this),
                approaching
        );
        rememberDistance(state.distanceMeters);
        return delay;
    }

    private boolean isCheckInApproachActive(DistanceState state) {
        boolean active = CheckInPollingPolicy.approachActive(
                lastPhase == PHASE_CHECKIN,
                checkInApproachLatched,
                state.hasLocation,
                state.inside,
                state.distanceMeters,
                Config.radiusMeters(this),
                previousDistanceAtMillis <= 0L ? null : previousDistanceMeters
        );
        if (active && !checkInApproachLatched) {
            checkInApproachLatched = true;
            float extraMeters = Math.max(0.0f, state.distanceMeters - Math.max(1, Config.radiusMeters(this)));
            AppLog.i(this, String.format(Locale.CHINA, "check-in approach latched distance=%.0f extra=%.0f", state.distanceMeters, extraMeters));
        }
        return active;
    }

    private void rememberDistance(float distanceMeters) {
        previousDistanceMeters = distanceMeters;
        previousDistanceAtMillis = System.currentTimeMillis();
    }

    private void postCheckoutConfirmAlertOnce() {
        if (checkoutConfirmAlertPosted) {
            return;
        }
        String text = Config.hasCheckedInToday(this)
                ? "已尝试打开钉钉。完成下班极速打卡后，如果没有自动识别，请点此确认。"
                : "未记录上班确认，已按正常下班时间尝试打开钉钉。完成后如果没有自动识别，请点此确认。";
        NotificationHelper.postAlert(this, "下班打卡", text, Config.ACTION_CONFIRM_CHECKOUT);
        checkoutConfirmAlertPosted = true;
        lastAlertMillis = System.currentTimeMillis();
    }

    private boolean openDingTalk() {
        boolean appForeground = AppVisibility.isForeground();
        BackgroundLaunchPermission.Status backgroundLaunchStatus = BackgroundLaunchPermission.status(this);
        boolean posted = NotificationHelper.postDingTalkLaunchRequest(
                this,
                currentPhase(this) == PHASE_CHECKIN ? "上班打卡" : "下班打卡",
                "正在通过后台拉起通道打开钉钉。",
                confirmActionForPhase(currentPhase(this))
        );
        if (posted) {
            long now = System.currentTimeMillis();
            Config.markDingTalkOpened(this, now);
            AppLog.i(this, "DingTalk launch requested through background activity pending intent appForeground="
                    + appForeground
                    + " "
                    + backgroundLaunchStatus.logText());
        }
        return posted;
    }

    private String confirmActionForPhase(int phase) {
        if (phase == PHASE_CHECKIN) {
            return Config.ACTION_CONFIRM_CHECKIN;
        }
        if (phase == PHASE_CHECKOUT) {
            return Config.ACTION_CONFIRM_CHECKOUT;
        }
        return null;
    }

    private void verifyDingTalkForegroundAfterLaunch(int phase, long openMillis, boolean retryAttempt) {
        if (phase != PHASE_CHECKIN && phase != PHASE_CHECKOUT) {
            return;
        }
        if (!ForegroundAppVerifier.hasUsageStatsAccess(this)) {
            AppLog.i(this, "DingTalk foreground verification unavailable because usage stats access is not granted");
            return;
        }
        handler.postDelayed(() -> {
            if (Config.lastDingTalkOpenMillis(this) != openMillis) {
                AppLog.i(this, "DingTalk foreground verification skipped because open attempt is stale");
                return;
            }
            boolean dingTalkWasForeground = ForegroundAppVerifier.wasPackageForegroundSince(this, Config.DINGTALK_PACKAGE, openMillis);
            String latestForegroundPackage = ForegroundAppVerifier.lastForegroundPackageSince(this, openMillis);
            if (dingTalkWasForeground) {
                autoOpenRetryUntilMillis = 0L;
                Config.markDingTalkVerifiedForeground(this, System.currentTimeMillis());
                AppLog.i(this, "DingTalk foreground verified after launch dingTalkSeen=true latestForeground=" + latestForegroundPackage);
                return;
            }

            long now = System.currentTimeMillis();
            boolean startingRetryWindow = !retryAttempt && now > autoOpenRetryUntilMillis;
            if (startingRetryWindow) {
                autoOpenRetryUntilMillis = now + AUTO_OPEN_RETRY_WINDOW_MILLIS;
            }
            AppLog.i(this, "DingTalk foreground verification failed; retryUntil="
                    + autoOpenRetryUntilMillis
                    + " startingRetry=" + startingRetryWindow
                    + " retryAttempt=" + retryAttempt
                    + " latestForeground=" + latestForegroundPackage);
            if (startingRetryWindow) {
                String action = phase == PHASE_CHECKIN ? Config.ACTION_CONFIRM_CHECKIN : Config.ACTION_CONFIRM_CHECKOUT;
                NotificationHelper.postAlert(
                        this,
                        phase == PHASE_CHECKIN ? "上班打卡未弹出钉钉" : "下班打卡未弹出钉钉",
                        "已尝试打开钉钉，但系统没有让钉钉进入前台。请点通知打开钉钉，完成极速打卡后确认。",
                        action
                );
                lastAlertMillis = now;
            }
            if (currentPhase(this) == phase && isUnlocked()) {
                handler.removeCallbacks(tickRunnable);
                handler.postDelayed(tickRunnable, AUTO_OPEN_RETRY_INTERVAL_MILLIS);
            }
        }, OPEN_FOREGROUND_VERIFY_DELAY_MILLIS);
    }

    private void maybeRecordAssumedOpenSuccess(int phase, long openMillis) {
        if (!Config.assumeDingTalkOpenMeansSuccess(this)) {
            AppLog.i(this, "assumed open success skipped because setting is off");
            return;
        }
        if (phase != PHASE_CHECKIN && phase != PHASE_CHECKOUT) {
            AppLog.i(this, "assumed open success skipped outside punch phase phase=" + phase);
            return;
        }
        if (!ForegroundAppVerifier.hasUsageStatsAccess(this)) {
            AppLog.i(this, "assumed open success skipped because usage stats access is not granted");
            NotificationHelper.postAlert(
                    this,
                    phase == PHASE_CHECKIN ? "上班打卡待确认" : "下班打卡待确认",
                    "系统未允许本应用验证钉钉是否真的进入前台，不能自动记录成功。请确认钉钉极速打卡完成后手动确认。",
                    phase == PHASE_CHECKIN ? Config.ACTION_CONFIRM_CHECKIN : Config.ACTION_CONFIRM_CHECKOUT
            );
            return;
        }
        AppLog.i(this, "assumed open success scheduled phase=" + phase + " delayMs=" + ASSUMED_OPEN_SUCCESS_DELAY_MILLIS);
        handler.postDelayed(() -> {
            if (!Config.assumeDingTalkOpenMeansSuccess(this)) {
                AppLog.i(this, "assumed open success canceled because setting is off");
                return;
            }
            if (Config.lastDingTalkOpenMillis(this) != openMillis) {
                AppLog.i(this, "assumed open success canceled because open attempt is stale");
                return;
            }
            boolean dingTalkWasForeground = ForegroundAppVerifier.wasPackageForegroundSince(this, Config.DINGTALK_PACKAGE, openMillis);
            if (!dingTalkWasForeground) {
                String latestForegroundPackage = ForegroundAppVerifier.lastForegroundPackageSince(this, openMillis);
                AppLog.i(this, "assumed open success canceled because DingTalk was not seen in foreground latestForeground=" + latestForegroundPackage);
                NotificationHelper.postAlert(
                        this,
                        phase == PHASE_CHECKIN ? "上班打卡未确认" : "下班打卡未确认",
                        "已尝试打开钉钉，但未检测到钉钉进入前台，因此没有自动记录成功。请手动打开钉钉完成极速打卡后确认。",
                        phase == PHASE_CHECKIN ? Config.ACTION_CONFIRM_CHECKIN : Config.ACTION_CONFIRM_CHECKOUT
                );
                return;
            }
            Config.markDingTalkVerifiedForeground(this, System.currentTimeMillis());
            boolean recorded = false;
            if (phase == PHASE_CHECKIN) {
                recorded = PunchRecorder.recordCheckIn(this, System.currentTimeMillis(), "钉钉进入前台兜底");
            } else if (phase == PHASE_CHECKOUT) {
                recorded = PunchRecorder.recordCheckOut(this, System.currentTimeMillis(), "钉钉进入前台兜底");
            }
            AppLog.i(this, "assumed open success fired phase=" + phase + " recorded=" + recorded);
        }, ASSUMED_OPEN_SUCCESS_DELAY_MILLIS);
    }

    private void startLocationUpdates() {
        updateLocationRequestPolicy(false);
    }

    private void updateLocationRequestPolicy(boolean forceFast) {
        if (!hasFineLocationPermission()) {
            return;
        }
        long wantedMillis = wantedLocationMillis(forceFast);
        float wantedMeters = wantedLocationMeters(forceFast);
        if (locationUpdatesStarted
                && locationRequestMillis == wantedMillis
                && Math.abs(locationRequestMeters - wantedMeters) < 0.01f) {
            return;
        }
        stopLocationUpdates();
        try {
            Location gps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            Location network = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            Location passive = locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER);
            lastLocation = newer(lastLocation, newer(gps, newer(network, passive)));

            boolean requested = false;
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, wantedMillis, wantedMeters, locationListener, Looper.getMainLooper());
                requested = true;
            }
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, wantedMillis, wantedMeters, locationListener, Looper.getMainLooper());
                requested = true;
            }
            locationUpdatesStarted = requested;
            locationRequestMillis = wantedMillis;
            locationRequestMeters = wantedMeters;
            AppLog.i(this, "location updates requested=" + requested + " interval=" + wantedMillis + " minMeters=" + wantedMeters + " hasLast=" + (lastLocation != null));
        } catch (SecurityException ignored) {
            locationUpdatesStarted = false;
        } catch (Exception e) {
            locationUpdatesStarted = false;
            AppLog.e(this, "location updates failed", e);
        }
    }

    private long wantedLocationMillis(boolean forceFast) {
        DistanceState state = distanceState();
        boolean approaching = isCheckInApproachActive(state);
        return CheckInPollingPolicy.locationRequest(
                checkInInsideLatched,
                forceFast,
                inUnlockBurst(),
                state.hasLocation,
                state.inside,
                state.distanceMeters,
                Config.radiusMeters(this),
                approaching
        ).intervalMillis;
    }

    private float wantedLocationMeters(boolean forceFast) {
        DistanceState state = distanceState();
        boolean approaching = isCheckInApproachActive(state);
        return CheckInPollingPolicy.locationRequest(
                checkInInsideLatched,
                forceFast,
                inUnlockBurst(),
                state.hasLocation,
                state.inside,
                state.distanceMeters,
                Config.radiusMeters(this),
                approaching
        ).minDistanceMeters;
    }

    private void stopLocationUpdates() {
        if (!locationUpdatesStarted) {
            return;
        }
        try {
            locationManager.removeUpdates(locationListener);
        } catch (SecurityException ignored) {
        }
        locationUpdatesStarted = false;
    }

    private DistanceState distanceState() {
        if (lastLocation == null) {
            return DistanceState.noLocation();
        }
        float distance = CoordinateKit.distanceMetersFromWgsToGcjTarget(
                lastLocation.getLatitude(),
                lastLocation.getLongitude(),
                Config.gcjLat(this),
                Config.gcjLon(this)
        );
        long ageMillis = lastLocation.getTime() > 0L
                ? Math.max(0L, System.currentTimeMillis() - lastLocation.getTime())
                : Long.MAX_VALUE;
        return new DistanceState(true, distance, distance <= Config.radiusMeters(this), ageMillis);
    }

    private boolean isUnlocked() {
        return canAutoOpenDingTalk(deviceState());
    }

    private DeviceState deviceState() {
        PowerManager powerManager = getSystemService(PowerManager.class);
        KeyguardManager keyguardManager = getSystemService(KeyguardManager.class);
        boolean interactive = powerManager != null && powerManager.isInteractive();
        boolean keyguardLocked = keyguardManager != null && keyguardManager.isKeyguardLocked();
        return new DeviceState(interactive, keyguardLocked);
    }

    private boolean canAutoOpenDingTalk(DeviceState device) {
        return LaunchGate.canAutoLaunch(
                device.interactive,
                device.keyguardLocked,
                lastUserPresentMillis,
                System.currentTimeMillis()
        );
    }

    private long signalAgeMillis() {
        if (lastInteractiveSignalMillis <= 0L) {
            return -1L;
        }
        return Math.max(0L, System.currentTimeMillis() - lastInteractiveSignalMillis);
    }

    private boolean hasFineLocationPermission() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasBackgroundLocationPermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                || checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void registerUnlockReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_USER_PRESENT);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(unlockReceiver, filter, RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(unlockReceiver, filter);
        }
        AppLog.i(this, "interactive receiver registered for USER_PRESENT and SCREEN_ON");
    }

    private void stopEverything() {
        handler.removeCallbacks(tickRunnable);
        stopLocationUpdates();
        checkInApproachLatched = false;
        checkInInsideLatched = false;
        checkoutConfirmAlertPosted = false;
        checkoutUnlockBurstActive = false;
        autoOpenReadyAtLastEvaluate = false;
        autoOpenRetryUntilMillis = 0L;
        NotificationHelper.cancelAlert(this);
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(unlockReceiver);
        } catch (Exception ignored) {
        }
        stopLocationUpdates();
        handler.removeCallbacks(tickRunnable);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private static Location newer(Location a, Location b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return a.getTime() >= b.getTime() ? a : b;
    }

    private static final class DistanceState {
        final boolean hasLocation;
        final float distanceMeters;
        final boolean inside;
        final long ageMillis;

        DistanceState(boolean hasLocation, float distanceMeters, boolean inside, long ageMillis) {
            this.hasLocation = hasLocation;
            this.distanceMeters = distanceMeters;
            this.inside = inside;
            this.ageMillis = ageMillis;
        }

        static DistanceState noLocation() {
            return new DistanceState(false, Float.MAX_VALUE, false, Long.MAX_VALUE);
        }
    }

    private static final class DeviceState {
        final boolean interactive;
        final boolean keyguardLocked;
        final boolean keyguardClear;

        DeviceState(boolean interactive, boolean keyguardLocked) {
            this.interactive = interactive;
            this.keyguardLocked = keyguardLocked;
            this.keyguardClear = interactive && !keyguardLocked;
        }

        String logText() {
            return "interactive=" + interactive + ",keyguardLocked=" + keyguardLocked + ",keyguardClear=" + keyguardClear;
        }
    }
}
