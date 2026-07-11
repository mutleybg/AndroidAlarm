package com.example.androidalarm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;

import com.example.androidalarm.databinding.ActivityRingBinding;

/**
 * Full-screen ring UI, launched by {@link AlarmService}'s full-screen-intent
 * notification. Declared in the manifest with showWhenLocked/turnScreenOn so it
 * appears over the lock screen. It no longer plays the sound itself — the
 * service owns the ring lifecycle; this screen only shows "Dismiss" (which tells
 * the service to stop) and closes itself when the service says the alarm ended.
 */
public class AlarmRingActivity extends BaseActivity {

    /** Broadcast from {@link AlarmService} telling this screen to close. */
    static final String ACTION_FINISH = "com.example.androidalarm.action.FINISH_RING";

    private int index;

    private final BroadcastReceiver finishReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            finish();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityRingBinding binding = ActivityRingBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        index = getIntent().getIntExtra(AlarmScheduler.EXTRA_INDEX, 0);
        binding.dismissButton.setOnClickListener(v -> dismiss());

        IntentFilter filter = new IntentFilter(ACTION_FINISH);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(finishReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(finishReceiver, filter);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        index = intent.getIntExtra(AlarmScheduler.EXTRA_INDEX, index);
    }

    /** Stop the alarm: hand off to the service, which cancels any re-ring. */
    private void dismiss() {
        Intent stop = new Intent(this, AlarmService.class);
        stop.setAction(AlarmService.ACTION_DISMISS);
        stop.putExtra(AlarmScheduler.EXTRA_INDEX, index);
        startService(stop);
        finish();
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(finishReceiver);
        super.onDestroy();
    }
}
