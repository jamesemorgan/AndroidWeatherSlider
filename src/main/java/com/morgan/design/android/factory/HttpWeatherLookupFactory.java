package com.morgan.design.android.factory;

import static com.morgan.design.android.util.ObjectUtils.isBlank;
import static com.morgan.design.android.util.ObjectUtils.isNull;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import com.morgan.design.Logger;
import com.morgan.design.android.dao.orm.WeatherChoice;
import com.morgan.design.android.domain.GeocodeResult;
import com.morgan.design.android.domain.Woeid;
import com.morgan.design.android.domain.YahooWeatherInfo;
import com.morgan.design.android.domain.YahooWeatherLookup;
import com.morgan.design.android.domain.types.Temperature;
import com.morgan.design.android.util.ACRAErrorLogger;
import com.morgan.design.android.util.YahooRequestUtils;
import com.morgan.design.android.util.ACRAErrorLogger.Type;

public class HttpWeatherLookupFactory {

	private static final String LOG_TAG = "HttpWeatherLookupFactory";

	public static YahooWeatherLookup getForWeatherChoice(final WeatherChoice weatherChoice, final Temperature temperature,
			final ConnectivityManager cnnxManager) {
		if (null == weatherChoice) {
			Logger.d(LOG_TAG, "WeatherChoice is null, no WOIED found. Unable to get yahoo weather info.");
			return null;
		}
		try {
			if (isBlank(weatherChoice.getWoeid())) {
				Logger.d(LOG_TAG, "No locaiton WOIED found.");
				return null;
			}
			if (isNotConnectedToNetwork(cnnxManager)) {
				Logger.d(LOG_TAG, "No usable network.");
				return null;
			}
			else {
				Logger.d(LOG_TAG, "Looking up weather details for woeid=[%s]", weatherChoice.getWoeid());

				final String url = YahooRequestUtils.getInstance().createWeatherQuery(weatherChoice, temperature);

				final YahooWeatherInfo weatherInfo = YahooRequestUtils.getInstance().getWeatherInfo(RestTemplateFactory.createAndQuery(url));
				return new YahooWeatherLookup(weatherChoice, weatherInfo);
			}
		}
		catch (final Throwable e) {
			ACRAErrorLogger.recordUnknownIssue(Type.HTTP_REQUEST_FAILURE,
					String.format("Woeid=[%s], Temperature=[%s]", woeid(weatherChoice), temp(temperature)));
			Logger.w(LOG_TAG, "Error when getting weather data task", e);
		}
		return null;
	}

	public static YahooWeatherInfo getForGeocodeResult(final GeocodeResult geocodeResult, final Temperature temperature,
			final ConnectivityManager cnnxManager) {
		if (null == geocodeResult) {
			Logger.d(LOG_TAG, "GeocodeResult is null, no WOIED found. Unable to get yahoo weather info.");
			return null;
		}
		try {
			final String woeidId = geocodeResult.getWoeid();

			if (isBlank(woeidId)) {
				Logger.d(LOG_TAG, "No locaiton WOIED found.");
				return null;
			}
			if (isNotConnectedToNetwork(cnnxManager)) {
				Logger.d(LOG_TAG, "No usable network.");
				return null;
			}
			else {
				Logger.d(LOG_TAG, "Looking up weather details for woeid=[%s]", woeidId);

				final String url = YahooRequestUtils.getInstance().createWeatherQuery(geocodeResult, temperature);

				return YahooRequestUtils.getInstance().getWeatherInfo(RestTemplateFactory.createAndQuery(url));
			}
		}
		catch (final Throwable e) {
			ACRAErrorLogger.recordUnknownIssue(Type.HTTP_REQUEST_FAILURE,
					String.format("Woeid=[%s], Temperature=[%s]", woeid(geocodeResult), temp(temperature)));
			Logger.e(LOG_TAG, "Unknonw error when getting weather data task", e);
		}
		return null;
	}

	private static boolean isNotConnectedToNetwork(final ConnectivityManager cnnxManager) {
		if (isNull(cnnxManager)) {
			return false;
		}
		final NetworkInfo ni = cnnxManager.getActiveNetworkInfo();
		return ni == null || !ni.isAvailable() || !ni.isConnected();
	}

	private static String temp(Temperature temperature) {
		return null != temperature ? temperature.abrev() : "N/A";
	}

	private static String woeid(Woeid woeid) {
		return null != woeid ? woeid.getWoeid() : "N/A";
	}
}
