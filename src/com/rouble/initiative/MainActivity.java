/*
 * Copyright (C) 2014 Google Inc. All Rights Reserved. 
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at 
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software 
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and 
 * limitations under the License.
 */

package com.rouble.initiative;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import android.app.Dialog;
import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.app.MediaRouteActionProvider;
import android.support.v7.media.MediaRouteSelector;
import android.support.v7.media.MediaRouter;
import android.support.v7.media.MediaRouter.RouteInfo;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.Toast;

import com.google.android.gms.cast.ApplicationMetadata;
import com.google.android.gms.cast.Cast;
import com.google.android.gms.cast.Cast.ApplicationConnectionResult;
import com.google.android.gms.cast.Cast.MessageReceivedCallback;
import com.google.android.gms.cast.CastDevice;
import com.google.android.gms.cast.CastMediaControlIntent;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Main activity to send messages to the receiver.
 */
public class MainActivity extends ActionBarActivity {

	private static final String TAG = MainActivity.class.getSimpleName();

	private MediaRouter mMediaRouter;
	private MediaRouteSelector mMediaRouteSelector;
	private MediaRouter.Callback mMediaRouterCallback;
	private CastDevice mSelectedDevice;
	private GoogleApiClient mApiClient;
	private Cast.Listener mCastListener;
	private ConnectionCallbacks mConnectionCallbacks;
	private ConnectionFailedListener mConnectionFailedListener;
	private HelloWorldChannel mHelloWorldChannel;
	private boolean mApplicationStarted;
	private boolean mWaitingForReconnect;
	private String mSessionId;
    private TableLayout mTable;
    private View contextFor; //todo find some other way to get the view that spawned a context menu

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

        addRow();
        loadTable("autosave");

        ActionBar actionBar = getSupportActionBar();
		actionBar.setBackgroundDrawable(new ColorDrawable(
				android.R.color.transparent));

        Button updateCast = (Button) findViewById(R.id.updateCast);
        updateCast.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {

                updateStuffOnScreen();
            }
        });

        Button addARow = (Button) findViewById(R.id.addrow);
        addARow.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                addRow();
            }
        });

        Button removeARow = (Button) findViewById(R.id.removerow);
        removeARow.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                removeRow();
            }
        });

		// Configure Cast device discovery
		mMediaRouter = MediaRouter.getInstance(getApplicationContext());
		mMediaRouteSelector = new MediaRouteSelector.Builder()
				.addControlCategory(
						CastMediaControlIntent.categoryForCast(getResources()
								.getString(R.string.app_id))).build();
		mMediaRouterCallback = new MyMediaRouterCallback();
	}

    private void updateStuffOnScreen() {
        String tableString = createTableString();
        saveTable(tableString, "autosave"); //create/update an autosave in case the app is unintentionally closed
        sendMessage(tableString);
    }

    //create json string for the table
    private String createTableString() {
        JSONArray tableArray = new JSONArray();
        JSONObject tableObj = new JSONObject();

        for (int i = 0; i < mTable.getChildCount(); i++){
            JSONObject tableRow = new JSONObject();
            TableRow mRow = (TableRow) mTable.getChildAt(i);    //get row i
            CheckBox mCheck = (CheckBox) mRow.getChildAt(0);    // get check box in row i
            EditText mName = (EditText) mRow.getChildAt(1);     //get name field in row i
            EditText mIniti = (EditText) mRow.getChildAt(2);    //get initiative field in row i

            try {
                tableRow.put("checked", mCheck.isChecked());
                tableRow.put("name", mName.getText());
                tableRow.put("initiative", mIniti.getText());
                tableArray.put(tableRow);
            }
            catch (Exception e)
            {
                Log.e(TAG, "failed adding new rows: ", e);
            }

        }
        try {
            tableObj.put("table", tableArray);
        }
        catch (Exception e) {
            Log.e(TAG, "failed adding array to tableobj: ", e);
        }

        return  tableObj.toString();
    }

    //clears all rows
    private void clearRows(){
        for(int i = 0; i < mTable.getChildCount(); i++){
            TableRow mRow = (TableRow) mTable.getChildAt(i);
            CheckBox mCheck = (CheckBox) mRow.getChildAt(0);
            EditText mName = (EditText) mRow.getChildAt(1);
            EditText mInit = (EditText) mRow.getChildAt(2);

            mCheck.setChecked(false);
            mName.setText("");
            mInit.setText("");
        }
    }

    //clears row selected by long press
    private void clearThisRow(){
        TableRow mRow = (TableRow) contextFor.getParent();
        CheckBox mCheck = (CheckBox) mRow.getChildAt(0);
        EditText mName = (EditText) mRow.getChildAt(1);
        EditText mInit = (EditText) mRow.getChildAt(2);

        mCheck.setChecked(false);
        mName.setText("");
        mInit.setText("");
    }

    //allows user to sort table rows locally
    private void sortRows() {
        int pass = 0;
        int init1;
        int init2;
        boolean checkTemp;
        boolean notDone = true;
        String tempName;
        String tempInit;

        while(notDone) {
            notDone = false; //this lets the bubble sort end early
            pass++;

            for (int i = 0; i < mTable.getChildCount() - pass; i++) {
                //load up 2 rows to compare
                TableRow mRow = (TableRow) mTable.getChildAt(i);
                CheckBox mCheck = (CheckBox) mRow.getChildAt(0);
                EditText mName = (EditText) mRow.getChildAt(1);
                EditText mInit = (EditText) mRow.getChildAt(2);
                    //todo find a way to swap rows instead of their contents
                TableRow mRow2 = (TableRow) mTable.getChildAt(i+1);
                CheckBox mCheck2 = (CheckBox) mRow2.getChildAt(0);
                EditText mName2 = (EditText) mRow2.getChildAt(1);
                EditText mInit2 = (EditText) mRow2.getChildAt(2);

                //deal with empty initiatives without breaking everything
                if(mInit.getText().toString().equals("")) {
                    init1 = -500;
                }
                else{
                    init1 = Integer.parseInt(mInit.getText().toString());
                }

                if(mInit2.getText().toString().equals("")) {
                    init2 = -500;
                }
                else{
                    init2 = Integer.parseInt(mInit2.getText().toString());
                }

                //swapping things around
                if (init1 < init2){
                    checkTemp = mCheck.isChecked();
                    tempName = mName.getText().toString();
                    tempInit = mInit.getText().toString();

                    mCheck.setChecked(mCheck2.isChecked());
                    mName.setText(mName2.getText().toString());
                    mInit.setText(mInit2.getText().toString());

                    mCheck2.setChecked(checkTemp);
                    mName2.setText(tempName);
                    mInit2.setText(tempInit);

                    notDone = true;
                }
            }
        }
    }

    private void fileDialog(){
        final Dialog fileDialog = new Dialog(MainActivity.this);

        fileDialog.setContentView(R.layout.file_dialog);
        fileDialog.setTitle(R.string.choosefile);

        Button savebutton = (Button) fileDialog.findViewById(R.id.savebutton);
        savebutton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                EditText filename = (EditText) fileDialog.findViewById(R.id.filename);
                saveTable(createTableString(), filename.getText().toString());

                fileDialog.dismiss();
            }
        });

        Button loadbutton = (Button) fileDialog.findViewById((R.id.loadbutton));
        loadbutton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                EditText filename = (EditText) fileDialog.findViewById(R.id.filename);
                loadTable(filename.getText().toString());

                fileDialog.dismiss();
            }
        });

        fileDialog.show();
    }

    //save table json string for loading later
    public void saveTable(String tableString, String filename) {
        Log.d(TAG, "table save");
        FileOutputStream outputStream;

        try{
            outputStream = openFileOutput(filename, Context.MODE_PRIVATE);
            outputStream.write(tableString.getBytes());
            outputStream.close();
        }
        catch (Exception e) {
            Log.e(TAG, "Writing file failed ", e);
        }
    }

    public void loadTable(String filename) {
        FileInputStream inputStream;
        String tablestring = "";
        int i;

        Log.d(TAG, "table load");

        try{
            inputStream = openFileInput(filename);
            i = inputStream.read();
            while (i != -1) {
                tablestring += Character.toString((char) i);
                i = inputStream.read();
            }
        }
        catch (Exception e){
            makeToast("File does not exist");
        }
        try {
            mTable = (TableLayout) findViewById(R.id.subTable);


            for (int j = 0; j < mTable.getChildCount() - 1; j++) {
                removeRow();
            }

            JSONObject tableobj = new JSONObject(tablestring);
            JSONArray tablearray = tableobj.optJSONArray("table");

            for (int j = 0; j < tablearray.length() - 1; j++) {
                addRow();
            }

            for (int j = 0; j < tablearray.length(); j++){
                TableRow mRow = (TableRow) mTable.getChildAt(j);
                CheckBox mCheck = (CheckBox) mRow.getChildAt(0);
                EditText mName = (EditText) mRow.getChildAt(1);
                EditText mInit = (EditText) mRow.getChildAt(2);

                mCheck.setChecked(tablearray.getJSONObject(j).optBoolean("checked"));
                mName.setText(tablearray.getJSONObject(j).optString("name"));
                mInit.setText(tablearray.getJSONObject(j).optString("initiative"));
            }
        }
        catch (Exception e){
            Log.e(TAG, "converting file to json failed", e);
        }

    }

    public void addRow(){
        mTable = (TableLayout) findViewById(R.id.subTable);

        TableRow row = (TableRow) LayoutInflater.from(MainActivity.this).inflate(R.layout.addrow, null);
        mTable.addView(row);

        TableRow mRow = (TableRow) mTable.getChildAt(mTable.getChildCount() - 1);
        registerForContextMenu(mRow.getChildAt(1));

    }

    public void removeRow(){
        mTable = (TableLayout) findViewById(R.id.subTable);

        TableRow mRow = (TableRow) mTable.getChildAt(mTable.getChildCount() - 1);

        if(mTable.getChildCount() == 1){
            makeToast("There are no rows to remove.");
        }
        else {
            mTable.removeView(mRow);
        }
    }

    public void makeToast(String toaster){
        Toast.makeText(MainActivity.this, toaster, Toast.LENGTH_LONG)
                .show();
    }

	@Override
	protected void onResume() {
		super.onResume();
		// Start media router discovery
		mMediaRouter.addCallback(mMediaRouteSelector, mMediaRouterCallback,
				MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY);
	}

	@Override
	protected void onPause() {
		if (isFinishing()) {
			// End media router discovery
			mMediaRouter.removeCallback(mMediaRouterCallback);
		}
		super.onPause();
	}

	@Override
	public void onDestroy() {
		teardown();
		super.onDestroy();
	}

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {

        Log.d(TAG, "create context");

        if(v.getId()==R.id.name) {
            menu.add(Menu.NONE, v.getId(), Menu.NONE, R.string.context1);
            contextFor = v;
        }
        super.onCreateContextMenu(menu, v, menuInfo);
    }

    @Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		getMenuInflater().inflate(R.menu.main, menu);
		MenuItem mediaRouteMenuItem = menu.findItem(R.id.media_route_menu_item);
		MediaRouteActionProvider mediaRouteActionProvider = (MediaRouteActionProvider) MenuItemCompat
				.getActionProvider(mediaRouteMenuItem);
		// Set the MediaRouteActionProvider selector for device discovery.
		mediaRouteActionProvider.setRouteSelector(mMediaRouteSelector);
		return true;
	}

    @Override
    public boolean onOptionsItemSelected(MenuItem item){
        switch(item.getItemId()){
            case R.id.clr_rows:
                clearRows();
                return true;
            case R.id.sort_local:
                sortRows();
                return true;
            case R.id.save_load:
                fileDialog();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        if(item.getTitle().toString().equals("Clear Row")){
            clearThisRow();

            Toast.makeText(MainActivity.this, "row cleared?", Toast.LENGTH_LONG)
                    .show();
        }
        return super.onContextItemSelected(item);
    }

    /**
	 * Callback for MediaRouter events
	 */
	private class MyMediaRouterCallback extends MediaRouter.Callback {

		@Override
		public void onRouteSelected(MediaRouter router, RouteInfo info) {
			Log.d(TAG, "onRouteSelected");
			// Handle the user route selection.
			mSelectedDevice = CastDevice.getFromBundle(info.getExtras());

			launchReceiver();
		}

		@Override
		public void onRouteUnselected(MediaRouter router, RouteInfo info) {
			Log.d(TAG, "onRouteUnselected: info=" + info);
			teardown();
			mSelectedDevice = null;
		}
	}

	/**
	 * Start the receiver app
	 */
	private void launchReceiver() {
		try {
			mCastListener = new Cast.Listener() {

				@Override
				public void onApplicationDisconnected(int errorCode) {
					Log.d(TAG, "application has stopped");
					teardown();
				}

			};
			// Connect to Google Play services
			mConnectionCallbacks = new ConnectionCallbacks();
			mConnectionFailedListener = new ConnectionFailedListener();
			Cast.CastOptions.Builder apiOptionsBuilder = Cast.CastOptions
					.builder(mSelectedDevice, mCastListener);
			mApiClient = new GoogleApiClient.Builder(this)
					.addApi(Cast.API, apiOptionsBuilder.build())
					.addConnectionCallbacks(mConnectionCallbacks)
					.addOnConnectionFailedListener(mConnectionFailedListener)
					.build();

			mApiClient.connect();
		} catch (Exception e) {
			Log.e(TAG, "Failed launchReceiver", e);
		}
	}

	/**
	 * Google Play services callbacks
	 */
	private class ConnectionCallbacks implements
			GoogleApiClient.ConnectionCallbacks {
		@Override
		public void onConnected(Bundle connectionHint) {
			Log.d(TAG, "onConnected");

			if (mApiClient == null) {
				// We got disconnected while this runnable was pending
				// execution.
				return;
			}

			try {
				if (mWaitingForReconnect) {
					mWaitingForReconnect = false;

					// Check if the receiver app is still running
					if ((connectionHint != null)
							&& connectionHint
									.getBoolean(Cast.EXTRA_APP_NO_LONGER_RUNNING)) {
						Log.d(TAG, "App  is no longer running");
						teardown();
					} else {
						// Re-create the custom message channel
						try {
							Cast.CastApi.setMessageReceivedCallbacks(
									mApiClient,
									mHelloWorldChannel.getNamespace(),
									mHelloWorldChannel);
						} catch (IOException e) {
							Log.e(TAG, "Exception while creating channel", e);
						}
					}
				} else {
					// Launch the receiver app
					Cast.CastApi
							.launchApplication(mApiClient,
									getString(R.string.app_id), false)
							.setResultCallback(
									new ResultCallback<Cast.ApplicationConnectionResult>() {
										@Override
										public void onResult(
												ApplicationConnectionResult result) {
											Status status = result.getStatus();
											Log.d(TAG,
													"ApplicationConnectionResultCallback.onResult: statusCode"
															+ status.getStatusCode());
											if (status.isSuccess()) {
												ApplicationMetadata applicationMetadata = result
														.getApplicationMetadata();
												mSessionId = result
														.getSessionId();
												String applicationStatus = result
														.getApplicationStatus();
												boolean wasLaunched = result
														.getWasLaunched();
												Log.d(TAG,
														"application name: "
																+ applicationMetadata
																		.getName()
																+ ", status: "
																+ applicationStatus
																+ ", sessionId: "
																+ mSessionId
																+ ", wasLaunched: "
																+ wasLaunched);
												mApplicationStarted = true;

												// Create the custom message
												// channel
												mHelloWorldChannel = new HelloWorldChannel();
												try {
													Cast.CastApi
															.setMessageReceivedCallbacks(
																	mApiClient,
																	mHelloWorldChannel
																			.getNamespace(),
																	mHelloWorldChannel);
												} catch (IOException e) {
													Log.e(TAG,
															"Exception while creating channel",
															e);
												}

												// set the initial instructions
												// on the receiver
												//sendMessage(getString(R.string.instructions));
                                                Log.e(TAG, "success");
											} else {
												Log.e(TAG,
														"application could not launch");
												teardown();
											}
										}
									});
				}
			} catch (Exception e) {
				Log.e(TAG, "Failed to launch application", e);
			}
		}

		@Override
		public void onConnectionSuspended(int cause) {
			Log.d(TAG, "onConnectionSuspended");
			mWaitingForReconnect = true;
		}
	}

	/**
	 * Google Play services callbacks
	 */
	private class ConnectionFailedListener implements
			GoogleApiClient.OnConnectionFailedListener {
		@Override
		public void onConnectionFailed(ConnectionResult result) {
			Log.e(TAG, "onConnectionFailed ");

			teardown();
		}
	}

	/**
	 * Tear down the connection to the receiver
	 */
	private void teardown() {
		Log.d(TAG, "teardown");
		if (mApiClient != null) {
			if (mApplicationStarted) {
				if (mApiClient.isConnected()  || mApiClient.isConnecting()) {
					try {
						Cast.CastApi.stopApplication(mApiClient, mSessionId);
						if (mHelloWorldChannel != null) {
							Cast.CastApi.removeMessageReceivedCallbacks(
									mApiClient,
									mHelloWorldChannel.getNamespace());
							mHelloWorldChannel = null;
						}
					} catch (IOException e) {
						Log.e(TAG, "Exception while removing channel", e);
					}
					mApiClient.disconnect();
				}
				mApplicationStarted = false;
			}
			mApiClient = null;
		}
		mSelectedDevice = null;
		mWaitingForReconnect = false;
		mSessionId = null;
	}

	/**
	 * Send a text message to the receiver
	 * 
	 * @param message
	 */
	private void sendMessage(String message) {
        Log.d(TAG, message);
		if (mApiClient != null && mHelloWorldChannel != null) {
			try {

				Cast.CastApi.sendMessage(mApiClient,
						mHelloWorldChannel.getNamespace(), message)
						.setResultCallback(new ResultCallback<Status>() {
							@Override
							public void onResult(Status result) {
								if (!result.isSuccess()) {
									Log.e(TAG, "Sending message failed");

								}
							}
						});
			} catch (Exception e) {
				Log.e(TAG, "Exception while sending message", e);
			}
		} else {
			Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT)
					.show(); //todo make this useless by hiding the update button unless casting
		}
	}

	/**
	 * Custom message channel
	 */
	class HelloWorldChannel implements MessageReceivedCallback {

		/**
		 * @return custom namespace
		 */
		public String getNamespace() {
			return getString(R.string.namespace);
		}

		/*
		 * Receive message from the receiver app
		 */
		@Override
		public void onMessageReceived(CastDevice castDevice, String namespace,
				String message) {
			Log.d(TAG, "onMessageReceived: " + message);
		}

	}

}
