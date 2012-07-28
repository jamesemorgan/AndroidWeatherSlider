package com.morgan.design.android.service;

import static com.morgan.design.Constants.FAILED_LOOKUP;
import static com.morgan.design.Constants.FROM_FRESH_LOOKUP;
import static com.morgan.design.Constants.FROM_INACTIVE_LOCATION;
import static com.morgan.design.Constants.LOOPING_ALARM;
import static com.morgan.design.Constants.UPDATE_WEATHER_LIST;
import static com.morgan.design.Constants.WEATHER_ID;
import static com.morgan.design.android.util.ObjectUtils.isNotNull;
import static com.morgan.design.android.util.ObjectUtils.isNotZero;
import static com.morgan.design.android.util.ObjectUtils.isNull;

import java.util.Date;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.location.Location;
import android.net.ConnectivityManager;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.widget.Toast;

import com.j256.ormlite.android.apptools.OrmLiteBaseService;
import com.morgan.design.Logger;
import com.morgan.design.RateMe;
import com.morgan.design.android.broadcast.CancelAllLookupsReciever;
import com.morgan.design.android.broadcast.CancelAllLookupsReciever.OnCancelAll;
import com.morgan.design.android.broadcast.IServiceUpdateBroadcaster;
import com.morgan.design.android.broadcast.ReloadWeatherReciever;
import com.morgan.design.android.broadcast.ReloadWeatherReciever.OnReloadWeather;
import com.morgan.design.android.broadcast.ServiceUpdateBroadcasterImpl;
import com.morgan.design.android.dao.WeatherChoiceDao;
import com.morgan.design.android.dao.orm.WeatherChoice;
import com.morgan.design.android.domain.GeocodeResult;
import com.morgan.design.android.domain.YahooWeatherInfo;
import com.morgan.design.android.domain.types.Temperature;
import com.morgan.design.android.factory.HttpWeatherLookupFactory;
import com.morgan.design.android.repository.DatabaseHelper;
import com.morgan.design.android.tasks.GeocodeWOIEDDataTaskFromLocation;
import com.morgan.design.android.tasks.OnAsyncCallback;
import com.morgan.design.android.util.PreferenceUtils;
import com.morgan.design.android.util.TimeUtils;
import com.morgan.design.weatherslider.R;

public class RoamingLookupService extends OrmLiteBaseService<DatabaseHelper> implements OnAsyncCallback<YahooWeatherInfo>, ServiceConnection, OnReloadWeather,
		OnCancelAll {

	private static final String LOG_TAG = "RoamingLookupService";

	private BroadcastReceiver locationChangedBroadcastReciever;
	private OnAsyncCallback<GeocodeResult> onGeocodeDataCallback;

	private IServiceUpdateBroadcaster serviceUpdate;
	private ConnectivityManager cnnxManager;

	protected WeatherChoiceDao weatherDao;
	protected WeatherNotificationControllerService mBoundNotificationControllerService;

	private ReloadWeatherReciever reloadWeatherReciever;
	protected CancelAllLookupsReciever cancelAllLookupsReciever;

	private GeocodeWOIEDDataTaskFromLocation geocodeWOIEDDataTaskFromLocation;
	private GetYahooWeatherInformationTask getYahooWeatherInformationTask;

	// ///////////////////////////////////
	// Service creation and destruction //
	// ///////////////////////////////////

	@Override
	public void onCreate() {
		super.onCreate();
		this.cnnxManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
		this.serviceUpdate = new ServiceUpdateBroadcasterImpl(this);
		this.weatherDao = new WeatherChoiceDao(getHelper());
		this.reloadWeatherReciever = new ReloadWeatherReciever(this, this);
		this.cancelAllLookupsReciever = new CancelAllLookupsReciever(this, this);

		bindService(new Intent(this, WeatherNotificationControllerService.class), this, BIND_AUTO_CREATE);
		sendBroadcast(new Intent(LOOPING_ALARM));

		registerForLocationChangedUpdates();

		this.onGeocodeDataCallback = new OnAsyncCallback<GeocodeResult>() {
			@Override
			public void onPostLookup(final GeocodeResult result) {
				Logger.d(LOG_TAG, "onPostLookup -> GeocodeResult = %s", result);
				if (null == result) {
					RoamingLookupService.this.serviceUpdate.complete(getString(R.string.service_update_geocode_location_found));
				}
				else {
					RoamingLookupService.this.serviceUpdate.complete(getString(R.string.service_update_unable_to_fina_geocode_location));
					onLocationFound(result);
				}
			}

			@Override
			public void onPreLookup() {
				RoamingLookupService.this.serviceUpdate.loading(getString(R.string.service_update_finding_geocode_location));
			}

			@Override
			public void onInitiateExecution() {
				RoamingLookupService.this.serviceUpdate.onGoing(getString(R.string.service_update_lookup_geocode_location));
			}
		};
	}

	@Override
	public void onReload() {
		Logger.d(LOG_TAG, "Alarm recieved, reloading roaming weathers");
		if (null == this.weatherChoice) {
			this.weatherChoice = this.weatherDao.getActiveRoamingLocation();
		}
		if (null != this.weatherChoice) {
			triggerGetGpsLocation();
		}
	}

	@Override
	public void onCancelAll() {
		stopService(new Intent(LocationLookupService.GET_ROAMING_LOCATION_LOOKUP));
		if (null != this.getYahooWeatherInformationTask) {
			this.getYahooWeatherInformationTask.cancel(true);
		}
		if (null != this.geocodeWOIEDDataTaskFromLocation) {
			this.geocodeWOIEDDataTaskFromLocation.cancel(true);
		}
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		unregisterLocationChangedUpdates();
		unbindService(this);
		this.reloadWeatherReciever.unregister();
		this.cancelAllLookupsReciever.unregister();
	}

	@Override
	public void onServiceConnected(final ComponentName className, final IBinder service) {
		this.mBoundNotificationControllerService = ((WeatherNotificationControllerService.LocalBinder) service).getService();
	}

	@Override
	public void onServiceDisconnected(final ComponentName className) {
		this.mBoundNotificationControllerService = null;
	}

	@Override
	public int onStartCommand(final Intent intent, final int flags, final int startId) {
		super.onStartCommand(intent, flags, startId);

		if (isNotNull(intent)) {
			if (intent.hasExtra(FROM_INACTIVE_LOCATION)) {
				final int id = intent.getIntExtra(WEATHER_ID, 0);
				if (isNotZero(id)) {
					Logger.d(LOG_TAG, "Initiating roaming weather for existing location, id=[%s]", id);
					initiateRoamingWeatherProcess(id);
				}
			}
			else if (intent.hasExtra(FROM_FRESH_LOOKUP)) {
				Logger.d(LOG_TAG, "Initiating roaming weather lookup for fresh location");
				initiateRoamingWeatherProcess();
			}
		}
		return START_STICKY;
	}

	// //////////////////////////////
	// publicly accessible methods //
	// //////////////////////////////

	private WeatherChoice weatherChoice;

	private GeocodeResult geocodeResult;

	public void initiateRoamingWeatherProcess() {
		this.weatherChoice = this.weatherDao.getRoamingLocation();

		if (isNull(this.weatherChoice)) {
			this.weatherChoice = new WeatherChoice();
			this.weatherChoice.setCreatedDateTime(new Date());
			this.weatherDao.create(this.weatherChoice);
		}

		this.weatherChoice.setRoaming(true);
		this.weatherDao.update(this.weatherChoice);
		triggerGetGpsLocation();
	}

	public void initiateRoamingWeatherProcess(final int weatherId) {
		this.weatherChoice = this.weatherDao.getById(weatherId);
		this.weatherChoice.setRoaming(true);
		this.weatherDao.update(this.weatherChoice);
		triggerGetGpsLocation();
	}

	protected void onLocationFound(final GeocodeResult result) {
		this.geocodeResult = result;
		this.getYahooWeatherInformationTask = new GetYahooWeatherInformationTask(this.cnnxManager, result, getTempMode(), this);
		this.getYahooWeatherInformationTask.execute();
	}

	@Override
	public void onPostLookup(final YahooWeatherInfo weather) {
		this.serviceUpdate.complete(getString(R.string.service_update_completed_weather_lookup));

		if (null == weather || weather.isError()) {
			onFailedLookup();
		}
		else {
			onSuccessfulLookup(weather);
		}
	}

	private void onFailedLookup() {

		// Remove immediately if cannot find find location
		if (this.weatherChoice.isFirstAttempt()) {
			Toast.makeText(this, R.string.toast_unable_to_find_the_weather_for_your_location, Toast.LENGTH_SHORT)
				.show();
			this.weatherDao.delete(this.weatherChoice);
		}
		// If active and failed, report failure and inform user of re-try
		else {
			if (PreferenceUtils.reportErrorOnFailedLookup(this)) {
				Toast.makeText(
						this,
						String.format(getString(R.string.toast_unable_to_get_weather_details),
								TimeUtils.convertMinutesHumanReadableTime(PreferenceUtils.getPollingSchedule(this))), Toast.LENGTH_SHORT)
					.show();
			}
			this.weatherChoice.failedQuery();
			this.weatherDao.update(this.weatherChoice);
		}
		sendBroadcast(new Intent(UPDATE_WEATHER_LIST).putExtra(FAILED_LOOKUP, true));
	}

	private void onSuccessfulLookup(final YahooWeatherInfo weather) {

		// Set updated lat/long and woeid
		this.weatherChoice.setWoeid(this.geocodeResult.getWoeid());
		this.weatherChoice.setLatitude(this.geocodeResult.getLatitude());
		this.weatherChoice.setLongitude(this.geocodeResult.getLonditude());

		// Successful execution
		this.weatherChoice.successfullyQuery(weather);
		this.weatherChoice.setActive(this.mBoundNotificationControllerService.addWeatherNotification(this.weatherChoice, weather));

		this.weatherDao.update(this.weatherChoice);

		RateMe.setSuccessIfRequired(this);

		sendBroadcast(new Intent(UPDATE_WEATHER_LIST));
	}

	@Override
	public void onPreLookup() {
		this.serviceUpdate.loading(getString(R.string.service_update_initalizing_weather_lookup));

	}

	@Override
	public void onInitiateExecution() {
		this.serviceUpdate.onGoing(getString(R.string.service_update_running_weather_lookup));
	}

	protected Temperature getTempMode() {
		return PreferenceUtils.getTemperatureMode(getApplicationContext());
	}

	private void unregisterLocationChangedUpdates() {
		if (isNotNull(this.locationChangedBroadcastReciever)) {
			unregisterReceiver(this.locationChangedBroadcastReciever);
			this.locationChangedBroadcastReciever = null;
		}
	}

	private void registerForLocationChangedUpdates() {
		if (isNull(this.locationChangedBroadcastReciever)) {
			this.locationChangedBroadcastReciever = new BroadcastReceiver() {

				@Override
				public void onReceive(final Context context, final Intent intent) {

					final Bundle extras = intent.getExtras();

					if (null != extras) {
						boolean providersFound = false;
						Location location = null;
						if (intent.hasExtra(LocationLookupService.PROVIDERS_FOUND)) {
							providersFound = extras.getBoolean(LocationLookupService.PROVIDERS_FOUND);
						}
						if (intent.hasExtra(LocationLookupService.CURRENT_LOCAION)) {
							location = (Location) extras.getParcelable(LocationLookupService.CURRENT_LOCAION);

						}

						if (!providersFound) {
							Logger.d(LOG_TAG, "No location providers found, GPS and MOBILE are disabled");
						}
						else if (null != location && providersFound) {
							Logger.d(LOG_TAG, "Listened to location change lat=[%s], long=[%s]", location.getLatitude(), location.getLatitude());
							RoamingLookupService.this.geocodeWOIEDDataTaskFromLocation =
									new GeocodeWOIEDDataTaskFromLocation(location, RoamingLookupService.this.onGeocodeDataCallback);
							RoamingLookupService.this.geocodeWOIEDDataTaskFromLocation.execute();
						}
						else {
							Logger.d(LOG_TAG, "GPS location not found");
							onFailedLookup();
						}
					}
				}
			};
			registerReceiver(this.locationChangedBroadcastReciever, new IntentFilter(LocationLookupService.ROAMING_LOCATION_FOUND_BROADCAST));
		}
	}

	private void triggerGetGpsLocation() {
		Logger.d(LOG_TAG, "Triggering get GPS location");
		final Intent findLocationBroadcast = new Intent(LocationLookupService.GET_ROAMING_LOCATION_LOOKUP);
		findLocationBroadcast.putExtra(LocationLookupService.LOCATION_LOOKUP_TIMEOUT, LocationLookupService.DEFAULT_LOCATION_TIMEOUT);
		startService(findLocationBroadcast);
	}

	// //////////////////
	// Binding details //
	// //////////////////

	private final IBinder mBinder = new LocalBinder();

	@Override
	public IBinder onBind(final Intent intent) {
		return this.mBinder;
	}

	public class LocalBinder extends Binder {
		RoamingLookupService getService() {
			return RoamingLookupService.this;
		}
	}

	// //////////////
	// Async Tasks //
	// //////////////

	public class GetYahooWeatherInformationTask extends AsyncTask<Void, Void, YahooWeatherInfo> {

		private final Temperature temperature;
		private final ConnectivityManager manager;
		private final OnAsyncCallback<YahooWeatherInfo> asyncCallback;
		private final GeocodeResult result;

		public GetYahooWeatherInformationTask(final ConnectivityManager cnnxManager, final GeocodeResult result, final Temperature temperature,
				final OnAsyncCallback<YahooWeatherInfo> asyncCallback) {
			this.manager = cnnxManager;
			this.result = result;
			this.asyncCallback = asyncCallback;
			this.temperature = temperature;
		}

		@Override
		protected YahooWeatherInfo doInBackground(final Void... params) {
			this.asyncCallback.onInitiateExecution();
			return HttpWeatherLookupFactory.getForGeocodeResult(this.result, this.temperature, this.manager);
		}

		@Override
		protected void onPreExecute() {
			this.asyncCallback.onPreLookup();
		}

		@Override
		protected void onPostExecute(final YahooWeatherInfo weatherInfo) {
			this.asyncCallback.onPostLookup(weatherInfo);
		}
	}

}
