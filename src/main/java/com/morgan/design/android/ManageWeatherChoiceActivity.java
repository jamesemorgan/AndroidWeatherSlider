package com.morgan.design.android;

import static com.morgan.design.android.util.ObjectUtils.isNotNull;
import static com.morgan.design.android.util.ObjectUtils.isNull;

import java.util.List;

import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ListView;

import com.j256.ormlite.android.apptools.OrmLiteBaseListActivity;
import com.morgan.design.android.SimpleGestureFilter.SimpleGestureListener;
import com.morgan.design.android.adaptor.CurrentChoiceAdaptor;
import com.morgan.design.android.dao.WoeidChoiceDao;
import com.morgan.design.android.domain.orm.WoeidChoice;
import com.morgan.design.android.repository.DatabaseHelper;
import com.morgan.design.android.service.YahooWeatherLoaderService;
import com.morgan.design.android.util.DateUtils;
import com.morgan.design.android.util.Logger;
import com.morgan.design.android.util.PreferenceUtils;
import com.weatherslider.morgan.design.R;

public class ManageWeatherChoiceActivity extends OrmLiteBaseListActivity<DatabaseHelper> implements SimpleGestureListener {

	// FIXME -> google analytics
	// FIXME -> add provider
	// FIXME -> paid version
	// FIXME -> localisation
	// FIXME -> pop-up overview mode
	// FIXME -> improve notification when no locations found

	// FIXME -> handle on click event notification event. opening overview, no design

	// FIXME -> DONE - On click notification user preference (paid version only)
	// FIXME -> DONE - start service on phone boot boot up (paid version only)
	// FIXME -> DONE - start last known service on open (paid version only)
	// FIXME -> DONE - swipe navigation path - (add to manual)
	// FIXME -> DONE - periodically query for weather
	// FIXME -> DONE - check phone has Internet before launching?

	private static final String LOG_TAG = "ManageWeatherChoiceActivity";

	public static final String LATEST_WEATHER_QUERY_COMPLETE = "com.morgan.design.intent.COMPLETED_LATEST_WEATHER_LOAD";
	public static final int ENTER_LOCATION = 1;
	public static final int SELECT_LOCATION = 2;
	public static final int UPDATED_PREFERENCES = 3;

	private WoeidChoiceDao woeidChoiceDao;

	private List<WoeidChoice> woeidChoices;

	private CurrentChoiceAdaptor adaptor;

	private NotificationManager notificationManager;

	private SimpleGestureFilter detector;
	private BroadcastReceiver broadcastReceiver;

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.weather_choice_layout);
		this.woeidChoiceDao = new WoeidChoiceDao(getHelper());
		this.detector = new SimpleGestureFilter(this, this);
		this.detector.setEnabled(true);

		this.woeidChoices = this.getWoeidChoiceDao().findAllWoeidChoices();
		if (this.woeidChoices.isEmpty()) {
			onAddNewLocation(null);
		}
		else {
			this.adaptor = new CurrentChoiceAdaptor(this, this.woeidChoices);
			setListAdapter(this.adaptor);
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		if (isNull(this.broadcastReceiver)) {
			this.broadcastReceiver = new BroadcastReceiver() {
				@Override
				public void onReceive(final Context context, final Intent intent) {
					reLoadWoeidChoices();
				}
			};
			registerReceiver(this.broadcastReceiver, new IntentFilter(LATEST_WEATHER_QUERY_COMPLETE));
		}
	}

	@Override
	public boolean onCreateOptionsMenu(final Menu menu) {
		final MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.home_menu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item) {
		switch (item.getItemId()) {
			case R.id.home_menu_changelog:
				return true;
			case R.id.home_menu_settings:
				// TODO -> listen for call back on preferences and restart service
				PreferenceUtils.openUserPreferenecesActivity(this);
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	@Override
	protected void onPause() {
		super.onPause();
		if (isNotNull(this.broadcastReceiver)) {
			unregisterReceiver(this.broadcastReceiver);
			this.broadcastReceiver = null;
		}
	}

	@Override
	public void onSwipe(final int direction) {
		switch (direction) {
			case SimpleGestureFilter.SWIPE_LEFT:
				onAddNewLocation(null);
				break;
		}
	}

	@Override
	public boolean dispatchTouchEvent(final MotionEvent me) {
		this.detector.onTouchEvent(me);
		return super.dispatchTouchEvent(me);
	}

	@Override
	public void onDoubleTap() {
		// Do nothing at present
	}

	public void onAddNewLocation(final View view) {
		final Intent intent = new Intent(this, EnterLocationActivity.class);
		startActivityForResult(intent, ENTER_LOCATION);
	}

	@Override
	protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		switch (requestCode) {
			case ENTER_LOCATION:
				if (resultCode == RESULT_OK) {
					Logger.d(LOG_TAG, "ENTER_LOCATION -> RESULT_OK");
				}
				else if (resultCode == RESULT_CANCELED) {
					Logger.d(LOG_TAG, "ENTER_LOCATION -> RESULT_CANCELED");
				}
				break;
			case SELECT_LOCATION:
				if (resultCode == RESULT_OK) {
					Logger.d(LOG_TAG, "SELECT_LOCATION -> RESULT_OK");
				}
				else if (resultCode == RESULT_CANCELED) {
					Logger.d(LOG_TAG, "SELECT_LOCATION -> RESULT_CANCELED");
				}
				break;
			case UPDATED_PREFERENCES:
				if (resultCode == RESULT_OK) {
					Logger.d(LOG_TAG, "UPDATED_PREFERENCES -> RESULT_OK");
				}
				break;
			default:
				break;
		}
	}

	private void reLoadWoeidChoices() {
		this.woeidChoices = this.getWoeidChoiceDao().findAllWoeidChoices();
		this.adaptor = new CurrentChoiceAdaptor(this, this.woeidChoices);
		setListAdapter(this.adaptor);
	}

	@Override
	protected void onListItemClick(final ListView l, final View v, final int position, final long id) {
		super.onListItemClick(l, v, position, id);
		final WoeidChoice woeidChoice = this.woeidChoices.get(position);

		final AlertDialog alertDialog = new AlertDialog.Builder(this).create();
		alertDialog.setTitle("Manage Location");
		alertDialog.setCancelable(false);

		final String dialogText =
				" Location:\n" + woeidChoice.getCurrentLocationText() + "\nLast updated:\n"
					+ DateUtils.dateToSimpleDateFormat(woeidChoice.getLastUpdatedDateTime());
		alertDialog.setMessage(dialogText);

		alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, "Cancel", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(final DialogInterface dialog, final int id) {
				dialog.cancel();
			}
		});
		alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "Load", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(final DialogInterface dialog, final int id) {
				loadWoeidLocation(woeidChoice);
			}
		});
		alertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, "Remove", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(final DialogInterface dialog, final int id) {
				getWoeidChoiceDao().delete(woeidChoice);
				attemptToKillNotifcation(woeidChoice);
				removeItemFromList(woeidChoice);
			}
		});
		alertDialog.show();
	}

	protected void loadWoeidLocation(final WoeidChoice woeidChoice) {
		final Bundle bundle = new Bundle();
		bundle.putSerializable(YahooWeatherLoaderService.CURRENT_WEATHER_WOEID, woeidChoice.getWoeid());

		final Intent intent = new Intent(this, YahooWeatherLoaderService.class);
		intent.putExtras(bundle);

		startService(intent);
	}

	protected void removeItemFromList(final WoeidChoice woeidChoice) {
		this.adaptor.remove(woeidChoice);
	}

	protected void attemptToKillNotifcation(final WoeidChoice woeidChoice) {
		if (isNull(this.notificationManager)) {
			this.notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		}
		this.notificationManager.cancel(woeidChoice.getLastknownNotifcationId());
	}

	protected WoeidChoiceDao getWoeidChoiceDao() {
		return this.woeidChoiceDao;
	}
}
