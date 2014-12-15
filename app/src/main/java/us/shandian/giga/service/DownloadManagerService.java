package us.shandian.giga.service;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.drawable.BitmapDrawable;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;

import us.shandian.giga.R;
import us.shandian.giga.get.DownloadManager;
import us.shandian.giga.get.DownloadMission;
import us.shandian.giga.ui.main.MainActivity;
import static us.shandian.giga.BuildConfig.DEBUG;

public class DownloadManagerService extends Service implements DownloadMission.MissionListener
{
	
	private static final String TAG = DownloadManagerService.class.getSimpleName();
	
	private DMBinder mBinder;
	private DownloadManager mManager;
	private Notification mNotification;
	private Handler mHandler;
	private long mLastTimeStamp = System.currentTimeMillis();

	@Override
	public void onCreate() {
		super.onCreate();
		
		if (DEBUG) {
			Log.d(TAG, "onCreate");
		}
		
		mBinder = new DMBinder();
		if (mManager == null) {

			if (DEBUG) {
				Log.d(TAG, "mManager == null");
			}

			mManager = new DownloadManager(this, "/storage/sdcard0/GigaGet");
		}
		
		Intent i = new Intent();
		i.setAction(Intent.ACTION_MAIN);
		i.setClass(this, MainActivity.class);
		mNotification = new Notification.Builder(this)
			.setContentIntent(PendingIntent.getActivity(this, 0, i, 0))
			.setContentTitle(getString(R.string.msg_running))
			.setContentText(getString(R.string.msg_running_detail))
			.setLargeIcon(((BitmapDrawable) getResources().getDrawable(R.drawable.gigaget)).getBitmap())
			.setSmallIcon(android.R.drawable.stat_sys_download)
			.build();
			
		HandlerThread thread = new HandlerThread("ServiceMessenger");
		thread.start();
			
		mHandler = new Handler(thread.getLooper()) {
			@Override
			public void handleMessage(Message msg) {
				if (msg.what == 0) {
					int runningCount = 0;
					
					for (int i = 0; i < mManager.getCount(); i++) {
						if (mManager.getMission(i).running) {
							runningCount++;
						}
					}
					
					updateState(runningCount);
				}
			}
		};
		
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if (DEBUG) {
			Log.d(TAG, "Starting");
		}
		
		return START_NOT_STICKY;
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		
		if (DEBUG) {
			Log.d(TAG, "Destroying");
		}
		
		for (int i = 0; i < mManager.getCount(); i++) {
			mManager.pauseMission(i);
		}
		
		stopForeground(true);
	}
	
	@Override
	public IBinder onBind(Intent intent) {
		return mBinder;
	}
	

	@Override
	public void onProgressUpdate(long done, long total) {

		long now = System.currentTimeMillis();

		long delta = now - mLastTimeStamp;

		if (delta > 2000) {
			postUpdateMessage();
			mLastTimeStamp = now;
		}
	}

	@Override
	public void onFinish() {
		postUpdateMessage();
	}

	@Override
	public void onError(int errCode) {
		postUpdateMessage();
	}
	
	private void postUpdateMessage() {
		mHandler.sendEmptyMessage(0);
	}
	
	private void updateState(int runningCount) {
		if (runningCount == 0) {
			stopForeground(true);
		} else {
			startForeground(1000, mNotification);
		}
	}
	
	
	// Wrapper of DownloadManager
	public class DMBinder extends Binder {
		// Do not start missions from outside
		public DownloadManager getDownloadManager() {
			return mManager;
		}
		
		public int startMission(final String url, final String name, final int threads) {
			int ret = mManager.startMission(url, name, threads);
			mManager.getMission(ret).addListener(DownloadManagerService.this);
			postUpdateMessage();
			return ret;
		}
		
		public void resumeMission(final int id) {
			mManager.resumeMission(id);
			mManager.getMission(id).addListener(DownloadManagerService.this);
			postUpdateMessage();
		}
		
		public void pauseMission(final int id) {
			mManager.pauseMission(id);
			mManager.getMission(id).removeListener(DownloadManagerService.this);
			postUpdateMessage();
		}
		
	}

}