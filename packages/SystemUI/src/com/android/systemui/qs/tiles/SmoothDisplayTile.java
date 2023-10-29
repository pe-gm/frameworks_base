package com.android.systemui.qs.tiles;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.text.TextUtils;
import android.view.View;

import androidx.annotation.Nullable;

import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.systemui.R;
import com.android.systemui.dagger.qualifiers.Background;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.plugins.FalsingManager;
import com.android.systemui.plugins.qs.QSTile.BooleanState;
import com.android.systemui.plugins.statusbar.StatusBarStateController;
import com.android.systemui.statusbar.policy.BatteryController;
import com.android.systemui.qs.QSHost;
import com.android.systemui.qs.logging.QSLogger;
import com.android.systemui.qs.tileimpl.QSTileImpl;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import javax.inject.Inject;

public class SmoothDisplayTile extends QSTileImpl<BooleanState> implements
        BatteryController.BatteryStateChangeCallback {

    public static final String TILE_SPEC = "smooth_display";

    private static final String TAG = "SmoothDisplayTile";
    private static final String ACTION_SMOOTH_DISPLAY_SETTINGS =
            "android.settings.SMOOTH_DISPLAY_SETTINGS";

    private static final int DEFAULT_REFRESH_RATE = 60;

    private List<Integer> mHighRefreshRates;
    private int mLeastHighRefreshRate, mMaxRefreshRate, mRefreshRateIndex;
    public long mLastClickTime = -1;

    private final BatteryController mBatteryController;

    @Inject
    public SmoothDisplayTile(
            QSHost host,
            @Background Looper backgroundLooper,
            @Main Handler mainHandler,
            FalsingManager falsingManager,
            MetricsLogger metricsLogger,
            StatusBarStateController statusBarStateController,
            ActivityStarter activityStarter,
            QSLogger qsLogger,
            BatteryController batteryController
    ) {
        super(host, backgroundLooper, mainHandler, falsingManager, metricsLogger, statusBarStateController,
                activityStarter, qsLogger);
        mHighRefreshRates = getHighRefreshRates();
        mMaxRefreshRate = Collections.max(mHighRefreshRates);
        mLeastHighRefreshRate = Collections.min(mHighRefreshRates);

        mBatteryController = batteryController;
        batteryController.observe(getLifecycle(), this);
    }

    @Override
    public void onPowerSaveChanged(boolean isPowerSave) {
        refreshState();
    }

    @Override
    public boolean isAvailable() {
        return mHighRefreshRates.size() > 0;
    }

    @Override
    public BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    protected void handleClick(@Nullable View view) {
        if (getState().state == Tile.STATE_UNAVAILABLE) {
            return;
        }
        // If last user clicks < 5 seconds
        // we cycle different refresh rates (if we have more than 2)
        // otherwise toggle on/off
        if (mHighRefreshRates.size() > 1 && mLastClickTime != -1
                && (SystemClock.elapsedRealtime() - mLastClickTime) < 5000) {
            // cycle refresh rate
            mRefreshRateIndex++;
            if (mRefreshRateIndex >= mHighRefreshRates.size()) {
                // all refresh rates cycled, turn if off
                mRefreshRateIndex = -1;
                setPeakRefreshRate(DEFAULT_REFRESH_RATE);
            } else {
                // change refresh rate
                final int refreshRate = mHighRefreshRates.get(mRefreshRateIndex);
                setSmoothRefreshRate(refreshRate);
            }
        } else {
            // toggle
            if (isSmoothDisplayEnabled()) {
                setPeakRefreshRate(DEFAULT_REFRESH_RATE);
                mRefreshRateIndex = -1;
            } else {
                final int refreshRate = getSmoothRefreshRate();
                setPeakRefreshRate(refreshRate);
                mRefreshRateIndex = mHighRefreshRates.indexOf(refreshRate);
            }
        }
        mLastClickTime = SystemClock.elapsedRealtime();
        refreshState();
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        state.value = isSmoothDisplayEnabled();
        state.label = getTileLabel();
        state.icon = ResourceIcon.get(R.drawable.ic_qs_refresh_rate);
        if (mBatteryController.isPowerSave()) {
            state.state = Tile.STATE_UNAVAILABLE;
            state.secondaryLabel = mContext.getString(R.string.battery_detail_switch_title);
        } else {
            state.state = state.value ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE;
            state.secondaryLabel = getSecondaryLabel();
        }
        state.contentDescription = TextUtils.concat(state.label, ", ", state.secondaryLabel);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.QS_CUSTOM;
    }

    @Override
    public Intent getLongClickIntent() {
        return new Intent(mHighRefreshRates.size() > 1 ? ACTION_SMOOTH_DISPLAY_SETTINGS
                : Settings.ACTION_DISPLAY_SETTINGS);
    }

    @Override
    public CharSequence getTileLabel() {
        return mContext.getString(R.string.smooth_display);
    }

    private String getSecondaryLabel() {
        final boolean on = isSmoothDisplayEnabled();
        final String status = mContext.getString(
                on ? R.string.switch_bar_on : R.string.switch_bar_off);
        final int refreshRate = on ? getPeakRefreshRate() : DEFAULT_REFRESH_RATE;
        return String.format("%s (%d Hz)", status, refreshRate);
    }

    private List<Integer> getHighRefreshRates() {
        return Arrays.stream(mContext.getDisplay().getSupportedModes())
                .mapToInt(m -> Math.round(m.getRefreshRate()))
                .filter(r -> r > DEFAULT_REFRESH_RATE)
                .sorted().boxed().collect(Collectors.toList());
    }

    private int roundToNearestHighRefreshRate(int refreshRate) {
        if (mHighRefreshRates.contains(refreshRate)) return refreshRate;
        int findRefreshRate = mLeastHighRefreshRate;
        for (Integer highRefreshRate : mHighRefreshRates) {
            if (highRefreshRate > refreshRate) break;
            findRefreshRate = highRefreshRate;
        }
        return findRefreshRate;
    }

    private int getDefaultPeakRefreshRate() {
        return mContext.getResources().getInteger(
                com.android.internal.R.integer.config_defaultPeakRefreshRate);
    }

    private int getPeakRefreshRate() {
        final int peakRefreshRate = Math.round(Settings.System.getFloat(
                mContext.getContentResolver(),
                Settings.System.PEAK_REFRESH_RATE, getDefaultPeakRefreshRate()));
        if (peakRefreshRate < DEFAULT_REFRESH_RATE) {
            return mMaxRefreshRate;
        } else if (peakRefreshRate < mLeastHighRefreshRate) {
            return DEFAULT_REFRESH_RATE;
        }
        return roundToNearestHighRefreshRate(peakRefreshRate);
    }

    private void setPeakRefreshRate(int refreshRate) {
        Settings.System.putFloat(mContext.getContentResolver(),
                Settings.System.PEAK_REFRESH_RATE, (float) refreshRate);
    }

    private int getSmoothRefreshRate() {
        final int smoothRefreshRate = Math.round(Settings.System.getFloat(
                mContext.getContentResolver(),
                Settings.System.SMOOTH_REFRESH_RATE, (float) getPeakRefreshRate()));
        if (smoothRefreshRate == DEFAULT_REFRESH_RATE) {
            return Math.max(mLeastHighRefreshRate, getDefaultPeakRefreshRate());
        }
        return roundToNearestHighRefreshRate(smoothRefreshRate);
    }

    private boolean setSmoothRefreshRate(int refreshRate) {
        setPeakRefreshRate(refreshRate);
        Settings.System.putFloat(mContext.getContentResolver(),
                Settings.System.SMOOTH_REFRESH_RATE, (float) refreshRate);
        return true;
    }

    private boolean isSmoothDisplayEnabled() {
        return getPeakRefreshRate() >= mLeastHighRefreshRate;
    }

}
