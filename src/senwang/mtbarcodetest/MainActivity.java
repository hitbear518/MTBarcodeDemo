package senwang.mtbarcodetest;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Handler.Callback;
import android.os.Message;
import android.serialport.SerialPort;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;


public class MainActivity extends ActionBarActivity {
	
	public static final int KEY_F1 = 112;
	public static final int MSG_READ_BARCODE = 0;
	
	private TextView mBarcodeText;
	private Button mScanButton;
	
	private DeviceControl mDeviceControl;
	private boolean mDeviceOpened = false;
	private SerialPort mSerialPort;
	private boolean mSerialPortOpened = false;
	private int mFileDecriptor;
	private boolean mPowerOn = false;
	private boolean mTriggerOn = false;
	private ReadThread mReadThread;
	
	@Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mBarcodeText = (TextView) findViewById(R.id.barcode_text);
        mScanButton = (Button) findViewById(R.id.scan_button);
        
        // Open DeviceControl
        try {
			mDeviceControl = new DeviceControl("/proc/driver/scan");
			mDeviceOpened = true;
		} catch (IOException e) {
			e.printStackTrace();
			new AlertDialog.Builder(this).setMessage(R.string.device_open_failed_msg)
				.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						finish();
					}
				}).show();
		}
        
        // Set Scan Button
        mScanButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				triggerOn();
			}
		});
    }
    
    @Override
	protected void onResume() {
    	super.onResume();
    	if (!mDeviceOpened) return;
    	if (!mSerialPortOpened) {
    		mSerialPort = new SerialPort();
    		try {
				mSerialPort.OpenSerial("/dev/eser0", 9600);
			} catch (SecurityException e) {
				e.printStackTrace();
				openSerialPortFailedAlert();
				return;
			} catch (IOException e) {
				e.printStackTrace();
				openSerialPortFailedAlert();
				return;
			}
    		
    		mFileDecriptor = mSerialPort.getFd();
    		if (mFileDecriptor > 0) {
    			mSerialPortOpened = true;
    			Log.d(Utils.getTag(), "SerialPort opened, file descriptor = " + mFileDecriptor);
    			mReadThread = new ReadThread();
    			mReadThread.start();
    		}
    		
    		// Power On
    		if (!mPowerOn) {
    			try {
    				mDeviceControl.PowerOnDevice();
    				mPowerOn = true;
    			} catch (IOException e) {
    				e.printStackTrace();
    			}
    		}
    	}
    }

	@Override
    protected void onPause() {
    	super.onPause();
    	if (!mDeviceOpened) return;
    	if (mSerialPortOpened) {
    		mHandler.removeCallbacks(mTriggerOffTask);
    		try {
				mDeviceControl.TriggerOffDevice();
				mTriggerOn = false;
			} catch (IOException e) {
				e.printStackTrace();
			}
    		
    		try {
				mDeviceControl.PowerOffDevice();
				mPowerOn = false;
			} catch (IOException e) {
				e.printStackTrace();
			}
    		mReadThread.interrupt();
    		
    		mSerialPort.CloseSerial(mFileDecriptor);
    		mSerialPortOpened = false;
    	}
    }
    
    @Override
    protected void onDestroy() {
    	super.onDestroy();
    	if (mDeviceOpened) {
    		try {
				mDeviceControl.DeviceClose();
				mDeviceOpened = false;
			} catch (IOException e) {
				e.printStackTrace();
			}
    	}
    }
    
    private void openSerialPortFailedAlert() {
		new AlertDialog.Builder(this).setMessage(R.string.open_serialport_failed_msg)
			.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					finish();
				}
			}).show();
		
		// Close Device
		try {
			mDeviceControl.DeviceClose();
			mDeviceOpened = false;
		} catch (IOException e1) {
			e1.printStackTrace();
		}
	}

	private void triggerOn() {
		if (!mTriggerOn) {
			try {
				mDeviceControl.TriggerOnDevice();
				mTriggerOn = true;
				mScanButton.setEnabled(false);
				mHandler.postDelayed(mTriggerOffTask, 3500);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private Handler mHandler = new Handler(new Callback() {
		
		@Override
		public boolean handleMessage(Message msg) {
			if (msg.what == MSG_READ_BARCODE) {
				mBarcodeText.append((String) msg.obj);
				mBarcodeText.append("\n");
				mScanButton.setEnabled(true);
				mHandler.removeCallbacks(mTriggerOffTask);
			}
			return false;
		}
	});
	private Runnable mTriggerOffTask = new Runnable() {
		public void run() {
			if (mTriggerOn) {
				try {
					mDeviceControl.TriggerOffDevice();
					mTriggerOn = false;
					mScanButton.setEnabled(true);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	};

    private class ReadThread extends Thread {
    	@Override
    	public void run() {
    		while (!isInterrupted()) {
    			try {
    				Log.d(Utils.getTag(), "reading");
    				String buffer = mSerialPort.ReadSerialString(mFileDecriptor, 1024, 100);
    				if (buffer != null) {
    					Message msg = new Message();
    					msg.what = MSG_READ_BARCODE;
    					msg.obj = buffer;
    					mHandler.sendMessage(msg);
    					mTriggerOn = false;
    				}
    			} catch (UnsupportedEncodingException e) {
    				e.printStackTrace();
    			}
    		}
    	}
    }
}
