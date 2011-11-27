package com.morgan.design.android.broadcast;

import static com.morgan.design.android.util.ObjectUtils.isNotNull;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import com.morgan.design.android.service.YahooWeatherLoaderService;
import com.morgan.design.android.util.Logger;

public class LaunchReceiver extends BroadcastReceiver {

	public static final String FROM_LAUNCHER = "FROM_LAUNCHER";
	public static final String GET_WEATHER_FORCAST = "com.morgan.design.android.broadcast.GET_WEATHER_FORCAST";

	@Override
	public void onReceive(final Context context, final Intent intent) {
		Toast.makeText(context, "LaunchReceiver invoked !!!!.", Toast.LENGTH_LONG).show();
		Logger.d("LaunchReceiver", "LaunchReceiver recieved command to start service, serivce will be re-issued");

		final Intent service = new Intent(context, YahooWeatherLoaderService.class);

		if (isNotNull(intent)) {
			final Bundle extras = intent.getExtras();
			if (isNotNull(extras)) {
				final String woeidId = intent.getStringExtra(YahooWeatherLoaderService.CURRENT_WEATHER_WOEID);
				Logger.d("LaunchReceiver", "LaunchReceiver recieved intent containing CURRENT_WEATHER_WOEID [%s]", woeidId);
				service.putExtra(YahooWeatherLoaderService.CURRENT_WEATHER_WOEID, woeidId);
			}
		}

		service.putExtra(FROM_LAUNCHER, true);
		context.startService(service);
	}
}