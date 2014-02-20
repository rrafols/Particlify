package com.oocit.particlify;

//code by @rrafols
//just some code hacked together, not really proud of it ;)

import java.lang.reflect.Method;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.ConfigurationInfo;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Window;
import android.view.WindowManager;

public class Particlify extends Activity {

	private GLSurfaceView mGLSurfaceView;
	private static final String TAG = "Particlify";
	private ParticlifyRenderer renderer;
	public int[] gamepadAxisIndices = null;
    public float[] gamepadAxisMinVals = null;
    public float[] gamepadAxisMaxVals = null;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        
		mGLSurfaceView = new GLSurfaceView(this);
		
	    final ActivityManager activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
	    final ConfigurationInfo configurationInfo = activityManager.getDeviceConfigurationInfo();
	    final boolean supportsEs2 = configurationInfo.reqGlEsVersion >= 0x20000;
	 
	    renderer = new ParticlifyRenderer(this);
	    if (supportsEs2) {
	        mGLSurfaceView.setEGLContextClientVersion(2);
//	        mGLSurfaceView.setEGLConfigChooser(new MultisampleConfigChooser());
	        mGLSurfaceView.setRenderer(renderer);
	    } else {
	        return;
	    }
	    
	    boolean hasJoystickMethods = false;
		try {
			Method level12Method = KeyEvent.class.getMethod("keyCodeToString", new Class[] { int.class } ); 
			hasJoystickMethods = (level12Method != null);
			Log.i(TAG, "****** Found API level 12 function! Joysticks supported");
		} catch (NoSuchMethodException nsme) {
			Log.i(TAG, "****** Did not find API level 12 function! Joysticks NOT supported!");
		}

		if (hasJoystickMethods) {
			InputDevice joystick = findBySource(InputDevice.SOURCE_JOYSTICK);
			renderer.setInputDevice(joystick);
		}

	    setContentView(mGLSurfaceView);
	}
	
	public InputDevice findBySource(int sourceType) {
        int[] ids = InputDevice.getDeviceIds(); 

		int i = 0;
        for (i = 0; i < ids.length; i++) {
			InputDevice dev = InputDevice.getDevice(ids[i]);
			int sources = dev.getSources();

			if ((sources & ~InputDevice.SOURCE_CLASS_MASK & sourceType) != 0) {
				return dev;
			}
        }
        
        return null;
	}

	private static boolean isJoystick(int source) {
        return (source & InputDevice.SOURCE_CLASS_JOYSTICK) != 0;
    }
	
	@Override
	public boolean dispatchGenericMotionEvent(MotionEvent ev) {
		if (isJoystick(ev.getSource())) {
			renderer.processJoystickEvent(ev);
			return true;
		}
		
		return super.dispatchGenericMotionEvent(ev);
	}
	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if(keyCode == KeyEvent.KEYCODE_BUTTON_A) {
			renderer.processKeyEvent(keyCode);
			return true;
		}

		return super.onKeyDown(keyCode, event);
	}
	
	@Override
	protected void onResume() {
	    super.onResume();
	    mGLSurfaceView.onResume();
	}
	 
	@Override
	protected void onPause() {
	    super.onPause();
	    mGLSurfaceView.onPause();
	}
}
