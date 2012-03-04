package com.morgan.design.android.broadcast;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.morgan.design.Logger;
import com.morgan.design.weatherslider.R;

public class ServiceUpdateReceiver extends BroadcastReceiver {

	private static final int SEVEN_SECONDS = 7000;

	private static final String LOG_TAG = "ServiceUpdateReceiver";

	public static final String ACTION = "com.morgan.design.android.SERVICE_UPDATE";

	public static final String LOADING = "LOADING";
	public static final String ONGOING = "ONGOING";
	public static final String COMPLETE = "COMPLETE";

	private final Activity context;
	private final TextView service_update_area;
	private final RelativeLayout service_update_area_container;
	private final ProgressBar service_update_progress_bar;

	private final Handler handler;

	private final Runnable clearTextRunnable;

	public ServiceUpdateReceiver(final Activity context) {
		this.context = context;
		this.service_update_area = (TextView) this.context.findViewById(R.id.service_update_area);
		this.service_update_area_container = (RelativeLayout) this.context.findViewById(R.id.service_update_area_container);
		this.service_update_progress_bar = (ProgressBar) this.context.findViewById(R.id.service_update_progress_bar);

		this.context.registerReceiver(this, new IntentFilter(ACTION));
		this.handler = new Handler();
		this.clearTextRunnable = new Runnable() {
			@Override
			public void run() {
				service_update_area.setText("");
				service_update_area_container.setVisibility(View.GONE);
			}
		};
	}

	public void unregisterReceiver() {
		this.context.unregisterReceiver(this);
		this.handler.removeCallbacks(this.clearTextRunnable);
	}

	@Override
	public void onReceive(final Context context, final Intent intent) {
		if (null == intent) {
			if (null != this.service_update_area_container) {
				this.service_update_area_container.setVisibility(View.GONE);
			}
			return;
		}
		final String action = intent.getAction();
		if (View.VISIBLE != this.service_update_area_container.getVisibility()) {
			this.service_update_area_container.setVisibility(View.VISIBLE);
		}
		if (ACTION.equals(action)) {
			if (intent.hasExtra(LOADING)) {
				final String loading = intent.getStringExtra(LOADING);
				Logger.d(LOG_TAG, "Loading : " + loading);
				service_update_progress_bar.setVisibility(View.VISIBLE);
				setNewText(loading);
			}
			else if (intent.hasExtra(ONGOING)) {
				final String ongoing = intent.getStringExtra(ONGOING);
				Logger.d(LOG_TAG, "onGoing : " + ongoing);
				service_update_progress_bar.setVisibility(View.VISIBLE);
				setNewText(ongoing);
			}
			else if (intent.hasExtra(COMPLETE)) {
				final String complete = intent.getStringExtra(COMPLETE);
				Logger.d(LOG_TAG, "Complete : " + complete);
				service_update_progress_bar.setVisibility(View.INVISIBLE);
				setNewText(complete);
			}
		}
	}

	private void setNewText(final CharSequence message) {
		this.handler.removeCallbacks(this.clearTextRunnable);
		this.service_update_area.setText(message);
		this.handler.postDelayed(this.clearTextRunnable, SEVEN_SECONDS);
	}

}
