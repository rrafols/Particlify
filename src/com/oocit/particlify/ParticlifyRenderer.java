package com.oocit.particlify;

// code by @rrafols
// just some code hacked together, not really proud of it ;)

import java.io.DataInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import android.util.Log;
import android.view.InputDevice;
import android.view.InputDevice.MotionRange;
import android.view.KeyEvent;
import android.view.MotionEvent;

public class ParticlifyRenderer implements GLSurfaceView.Renderer {

	private static final String TAG = "Particlify";
	private int TRACK_SECTIONS = 3000;
	private int TRACK_LENGTH = 300000; //150000
	private int TRACK_WIDTH = 140;
	private int DEFAULT_CAMERA_HEIGHT = 22;
	private int NPARTICLES = 150;	//3000;
	private	int GAME_MENUS = 0;
	private	int GAME_PLAYING = 1;
	
	private final FloatBuffer trackBuffer;
	private final FloatBuffer particleBuffer;
	private final FloatBuffer obsBuffer;
	
	private float[] mModelMatrix = new float[16];
	private float[] mViewMatrix = new float[16];
	private float[] mProjectionMatrix = new float[16];
	private float[] mMVPMatrix = new float[16];
	
	private final int mBytesPerFloat = 4;
	
	private final int trackStrideBytes = 7 * mBytesPerFloat;
	private final int trackPositionOffset = 0;
	private final int trackPositionDataSize = 3;
	private final int trackColorOffset = 3;
	private final int trackColorDataSize = 4;
	private int trackMVPMatrixHandle;
	private int trackPositionHandle;
	private int trackColorHandle;
	
	private final int obsStrideBytes = 3 * mBytesPerFloat;
	private final int obsPositionOffset = 0;
	private final int obsPositionDataSize = 3;
	private int obsMVPMatrixHandle;
	private int obsPositionHandle;
	
	private int barMVPMatrixHandle;
	private int barPositionHandle;
	
	private final int particleStrideBytes = 5 * mBytesPerFloat;
	private final int particlePositionOffset = 0;
	private final int particlePositionDataSize = 3;
	private final int particleTextureOffset = 3;
	private final int particleTextureDataSize = 2;
	private int particleMVPMatrixHandle;
	private int particlePositionHandle;
	private int particleTextureHandle;
	private int particleTextureUniformHandle;
	private int particleTextureId;
	
	private float[] trackVerticesData;
	private float[] trackSectorNormals;
	
	private float trackPosition = 0.f;
	private InputDevice joystick;
	private float camX, camY, camZ;
	private long lastFrameTime;
	private long accTime;
	
	private float animAccel = 0.f;
	private float targetX = 0.f;
	private float angleX = 0.f;
	private float _friction = 0.1f;
	private float _speed = 0.f;
	private float _xpos = 0.f;
	private float _anglex = 0.f;
	
	private int trackProgramHandle;
	private int particleProgramHandle;
	private int obsProgramHandle;
	private int barProgramHandle;
	
	private Random rnd;
	private Context context;
	
	private int P_WIDTH = 4;
	private int P_HEIGHT = 2;
	private int P_MAX_DEPTH = 20;
	private int P_MIN_DEPTH = 4;
	
	private float[] pPos;
	private float[] pVel;
	
	private float[] sectionObsX;
	private float[] sectionObsW;
	private int aliveParticles;
	private float[] killedParticlePos;
	private int particlesToKill;
	private int gameState = GAME_PLAYING;
	private int playTexture;
	private int times;
	private float maxDistance = 0;
	
	public ParticlifyRenderer(Context context) {
		this.context = context;
		
		int pos = 0;
		
		trackVerticesData = new float[TRACK_SECTIONS * (3 + 4) * 2];
        trackSectorNormals = new float[TRACK_SECTIONS * 3];
        
        float[] particleVerticesData = new float[] {
        	-1,  1, 0,  0,  1,
        	-1, -1, 0,  0,  0,
        	 1,  1, 0,  1,  1,
        	 1, -1, 0,  1,  0
        };
        
        float sectionLength = (((float) TRACK_LENGTH) / ((float) TRACK_SECTIONS));
        
        float[][] colors = {
//    		{1.f, 0.f, 0.f, 1.f},
//    		{0.f, 1.f, 0.f, 1.f},
//    		{0.f, 0.f, 1.f, 1.f},
//    		{0.f, 1.f, 1.f, 1.f},
//    		{1.f, 1.f, 0.f, 1.f},
//    		{1.f, 0.f, 1.f, 1.f},
    		{1.f, 1.f, 1.f, 1.f},
    		{0.f, 0.f, 0.f, 1.f}
        };
        
        rnd = new Random(1337l);
        
        for(int i = 0; i < TRACK_SECTIONS; i++) {
        	pos = i * (3 + 4) * 2;
        	
        	float w = TRACK_WIDTH;
        	float R = DEFAULT_CAMERA_HEIGHT * 2;
        	float k = ((float) i) * 0.1f;
        	
        	float[] rv = new float[] {(float) (R * Math.cos(k)), (float) (R * Math.sin(k))};
        	float[] tv = new float[] {-rv[1], rv[0]};
        	unitVector2(tv);
        	
        	float x0 = (float) (rv[0] + tv[0] * w);
        	float y0 = (float) (rv[1] + tv[1] * w);
        	
        	float x1 = (float) (rv[0] - tv[0] * w);
        	float y1 = (float) (rv[1] - tv[1] * w);
        	
        	float z0 = ((float) i) * sectionLength;
        	
        	float[] cross = new float[] {0.f, 1, 0};
        	unitVector(cross);
        	
        	float r = colors[i % colors.length][0];
        	float g = colors[i % colors.length][1];
        	float b = colors[i % colors.length][2];
        	
        	trackSectorNormals[i * 3 + 0] = cross[0];
        	trackSectorNormals[i * 3 + 1] = cross[1];
        	trackSectorNormals[i * 3 + 2] = cross[2];
        	
        	trackVerticesData[pos + 0] = x0;
        	trackVerticesData[pos + 1] = y0;
        	trackVerticesData[pos + 2] = z0;
        	
        	trackVerticesData[pos + 3] = r;
        	trackVerticesData[pos + 4] = g;
        	trackVerticesData[pos + 5] = b;
        	trackVerticesData[pos + 6] = 1.f;
        	
        	pos += 3 + 4;
        	
        	trackVerticesData[pos + 0] = x1;
        	trackVerticesData[pos + 1] = y1;
        	trackVerticesData[pos + 2] = z0;
        	
        	trackVerticesData[pos + 3] = r;
        	trackVerticesData[pos + 4] = g;
        	trackVerticesData[pos + 5] = b;
        	trackVerticesData[pos + 6] = 1.f;
        }
        
        float[] obsVerticesData = new float[] {
            	 0, 25,  0,
            	 0, -5,  0,
            	10, 25,  0,
            	10, -5,  0,
            	10, 25, 10,
            	10, -5, 10,
            	 0, 25, 10,
            	 0, -5, 10,
            	 0, 25,  0,
            	 0, -5,  0
        };

        // Initialize the buffers.
        trackBuffer = ByteBuffer.allocateDirect(trackVerticesData.length * mBytesPerFloat).order(ByteOrder.nativeOrder()).asFloatBuffer();
        trackBuffer.put(trackVerticesData).position(0);
        
        particleBuffer = ByteBuffer.allocateDirect(particleVerticesData.length * mBytesPerFloat).order(ByteOrder.nativeOrder()).asFloatBuffer();
        particleBuffer.put(particleVerticesData).position(0);
        
        pPos = new float[NPARTICLES * 3];
        pVel = new float[NPARTICLES * 3];
        for(int i = 0; i < NPARTICLES; i++) {
        	resetParticlePos(i);
        	resetParticleVel(i);
        }
    
        obsBuffer = ByteBuffer.allocateDirect(obsVerticesData.length * mBytesPerFloat).order(ByteOrder.nativeOrder()).asFloatBuffer();
        obsBuffer.put(obsVerticesData).position(0);
        
        sectionObsX = new float[TRACK_SECTIONS];
        sectionObsW = new float[TRACK_SECTIONS];
        for(int i = 0; i < TRACK_SECTIONS; i++) {
        	sectionObsX[i] = rnd.nextFloat() * 12.f - 6.f -0.5f;
        	sectionObsW[i] = rnd.nextFloat() * 2.f + 1.f;
        }
        
        aliveParticles = NPARTICLES;
        killedParticlePos = new float[NPARTICLES * 3];
        particlesToKill = 0;
	}
	
	private void resetParticlePos(int part) {
		pPos[part * 3 + 0] = rnd.nextFloat() * 4.f - 2.f;
    	pPos[part * 3 + 1] = rnd.nextFloat() * 4.f - 2.f;
    	pPos[part * 3 + 2] = rnd.nextFloat() * P_MAX_DEPTH - P_MIN_DEPTH;
	}
	
	private void resetParticleVel(int part) {
		pVel[part * 3 + 0] = rnd.nextFloat() * 0.1f;
    	pVel[part * 3 + 1] = rnd.nextFloat() * 0.1f;
    	pVel[part * 3 + 2] = rnd.nextFloat() * 0.1f;
	}
	
	public void setInputDevice(InputDevice joystick) {
		this.joystick = joystick;
	}
	
	private static final void unitVector(float[] v) {
		float m = (float) Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
		if(m != 0) {
			m = 1.f / m;
			v[0] *= m;
			v[1] *= m;
			v[2] *= m;
		}
	}
	
	private static final void unitVector2(float[] v) {
		float m = (float) Math.sqrt(v[0] * v[0] + v[1] * v[1]);
		if(m != 0) {
			m = 1.f / m;
			v[0] *= m;
			v[1] *= m;
		}
	}
	
	private static final float[] crossProduct(float x0, float y0, float z0, float x1, float y1, float z1) {
		float[] out = new float[3];
		
		out[0] = y0 * z1 - y1 * z0;
		out[1] = z0 * x1 - z1 * x0;
		out[2] = x0 * y1 - x1 * y0;
		
		return out;
	}
	
	@Override
	public void onSurfaceChanged(GL10 gl, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
        
        final float ratio = (float) width / height;
//        Matrix.perspectiveM(mProjectionMatrix, 0, 45.f, ratio, 1.f, 5000000.f);
        Matrix.perspectiveM(mProjectionMatrix, 0, 10.f, ratio, 1.f, 50000.f);
	}

	@Override
	public void onSurfaceCreated(GL10 arg0, EGLConfig arg1) {
        GLES20.glClearColor(0.5f, 0.5f, 0.5f, 0.5f);

        final String trackVertexShader = readShader("track.vs");  
        final String trackFragmentShader = readShader("track.fs");
  
        trackProgramHandle = createProgram(trackVertexShader, trackFragmentShader, new String[] {"a_Position", "a_Color"});
		trackMVPMatrixHandle = GLES20.glGetUniformLocation(trackProgramHandle, "u_MVPMatrix");        
		trackPositionHandle = GLES20.glGetAttribLocation(trackProgramHandle, "a_Position");
		trackColorHandle = GLES20.glGetAttribLocation(trackProgramHandle, "a_Color");        

		final String particleVertexShader = readShader("particle.vs");
		final String particleFragmentShader = readShader("particle.fs");
		
		particleProgramHandle = createProgram(particleVertexShader, particleFragmentShader, new String[] {"a_Position", "a_TexCoordinate"});
        particleMVPMatrixHandle = GLES20.glGetUniformLocation(particleProgramHandle, "u_MVPMatrix");        
		particlePositionHandle = GLES20.glGetAttribLocation(particleProgramHandle, "a_Position");
		particleTextureHandle = GLES20.glGetAttribLocation(particleProgramHandle, "a_TexCoordinate");
		particleTextureUniformHandle = GLES20.glGetUniformLocation(particleProgramHandle, "u_Texture");
		particleTextureId = genTexture();
		
		final String obsVertexShader = readShader("obs.vs");  
        final String obsFragmentShader = readShader("obs.fs");
  
        obsProgramHandle = createProgram(obsVertexShader, obsFragmentShader, new String[] {"a_Position"});
		obsMVPMatrixHandle = GLES20.glGetUniformLocation(obsProgramHandle, "u_MVPMatrix");        
		obsPositionHandle = GLES20.glGetAttribLocation(obsProgramHandle, "a_Position");
		
		final String barVertexShader = readShader("bar.vs");  
        final String barFragmentShader = readShader("bar.fs");
        
        barProgramHandle = createProgram(barVertexShader, barFragmentShader, new String[] {"a_Position"});
		barMVPMatrixHandle = GLES20.glGetUniformLocation(barProgramHandle, "u_MVPMatrix");        
		barPositionHandle = GLES20.glGetAttribLocation(barProgramHandle, "a_Position");
		
		playTexture = genTextTexture("Press [A] to Play!");
		lastFrameTime = System.currentTimeMillis();
		accTime = 0;
	}
	
	private static int createProgram(String vertexShader, String fragmentShader, String[] attribs) {
        int vertexShaderHandle = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER);
        
        if(vertexShaderHandle != 0)  {
            GLES20.glShaderSource(vertexShaderHandle, vertexShader);
            GLES20.glCompileShader(vertexShaderHandle);
            
            final int[] compileStatus = new int[1];
            GLES20.glGetShaderiv(vertexShaderHandle, GLES20.GL_COMPILE_STATUS, compileStatus, 0);
            if(compileStatus[0] == 0) {
            	Log.e(TAG, "Error: " + GLES20.glGetShaderInfoLog(vertexShaderHandle));
                GLES20.glDeleteShader(vertexShaderHandle);
                vertexShaderHandle = 0;
            }
        }

        if(vertexShaderHandle == 0) throw new RuntimeException("Error creating vertex shader.");
        
        int fragmentShaderHandle = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER);
        if(fragmentShaderHandle != 0)  {
            GLES20.glShaderSource(fragmentShaderHandle, fragmentShader);
            GLES20.glCompileShader(fragmentShaderHandle);
            
            final int[] compileStatus = new int[1];
            GLES20.glGetShaderiv(fragmentShaderHandle, GLES20.GL_COMPILE_STATUS, compileStatus, 0);
            if(compileStatus[0] == 0)  {
            	Log.e(TAG, "Error: " + GLES20.glGetShaderInfoLog(fragmentShaderHandle));
            	
                GLES20.glDeleteShader(fragmentShaderHandle);
                fragmentShaderHandle = 0;
            }
        }

        if (fragmentShaderHandle == 0) throw new RuntimeException("Error creating fragment shader.");
        
        int programHandle =  GLES20.glCreateProgram();
        if (programHandle != 0)  {
        	GLES20.glAttachShader(programHandle, vertexShaderHandle);                        
            GLES20.glAttachShader(programHandle, fragmentShaderHandle);
            GLES20.glLinkProgram(programHandle);

            final int[] linkStatus = new int[1];
            GLES20.glGetProgramiv(programHandle, GLES20.GL_LINK_STATUS, linkStatus, 0);
            if (linkStatus[0] == 0) {                                
	            GLES20.glDeleteProgram(programHandle);
	            programHandle = 0;
            }
        }
        
        if(programHandle == 0) throw new RuntimeException("Error creating program.");
        
        for(int i = 0; i < attribs.length; i++) {
        	GLES20.glBindAttribLocation(programHandle, i, attribs[i]);
        }
        
        return programHandle;
	}
	
	private int genTexture() {
		final int[] textureHandle = new int[1];
		GLES20.glGenTextures(1, textureHandle, 0);
		
		if(textureHandle[0] != 0) {
			int[] pixels = new int[32*32];
			
			for(int i = 0; i < 16; i++) {
				for(int j = 0; j < 16; j++) {
					float d = (float) Math.pow(i * i + j * j, 2.f) / 202500.f;
					
					if(d > 0.3) {
						d = (d - 0.3f) / 0.7f;
						int idist = (int) (d * 192.f + 63.f);
						pixels[j + i * 32] = 0x00ffffff | (idist << 24);
					} else {
						pixels[j + i * 32] = 0x00;
					}
				}
			}
			
			for(int i = 0; i < 16; i++) {
				for(int j = 16; j < 32; j++) {
					pixels[j + i * 32] = pixels[31 - j + i * 32];
				}
			}
			
			for(int i = 16; i < 32; i++) {
				for(int j = 0; j < 32; j++) {
					pixels[j + i * 32] = pixels[j + (31 - i) * 32];
				}
			}
			
			Bitmap particle = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888);
			particle.setPixels(pixels, 0, 32, 0, 0, 32, 32);
			
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureHandle[0]);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);
            
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, particle, 0);
            particle.recycle();        
		}
		
		if(textureHandle[0] == 0) throw new RuntimeException("Error loading texture.");
		return textureHandle[0];
	}
	
	private int genTextTexture(String text) {
		final int[] textureHandle = new int[1];
		GLES20.glGenTextures(1, textureHandle, 0);
		
		if(textureHandle[0] != 0) {
			
			Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
			paint.setColor(0xffffffff);
			paint.setTextSize(140);
			paint.setShadowLayer(1f, 0f, 1f, Color.BLACK);
			
			Rect bounds = new Rect();
			paint.getTextBounds(text, 0, text.length(), bounds);
			
			Bitmap bmp = Bitmap.createBitmap(1024, 1024, Bitmap.Config.ARGB_8888);
			Canvas canvas = new Canvas(bmp);
			canvas.drawText(text, (1024 - bounds.width()) / 2, (1024 - bounds.height()) / 2, paint);
			
			android.graphics.Matrix mtx = new android.graphics.Matrix();
			mtx.reset();
			mtx.preTranslate(512, 512);
			mtx.setRotate(180, 512, 512);
			mtx.postTranslate(-512, -512);
			
			Bitmap rotated = Bitmap.createBitmap(bmp, 0, 0, 1024, 1024, mtx, true);
			
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureHandle[0]);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);
            
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, rotated, 0);
            bmp.recycle();
            rotated.recycle();
		}
		
		if(textureHandle[0] == 0) throw new RuntimeException("Error loading texture.");
		return textureHandle[0];
	}
	
	static List<Integer> getAxisIds(InputDevice device) {
        List<Integer> axisList = new ArrayList<Integer>();
        if (device == null) {
                return axisList;
        }
        for (MotionRange range : device.getMotionRanges()) {
                axisList.add(range.getAxis());
        }

        return axisList;
	}
	
	public void processJoystickEvent(MotionEvent ev) {
		for (int p = 0; p < ev.getPointerCount(); p++) {
			animAccel = -4.f * ev.getAxisValue(MotionEvent.AXIS_Y);
			angleX = 8.f * ev.getAxisValue(MotionEvent.AXIS_X);
		}
	}
	
	public void processKeyEvent(int keyCode) {
		if(keyCode == KeyEvent.KEYCODE_BUTTON_A) {
			if(gameState == GAME_MENUS) gameState = GAME_PLAYING;
		}
	}

	
	@Override
	public void onDrawFrame(GL10 arg0) {
		//lockstep animation
		long currentTime = System.currentTimeMillis();
		
		
		GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT | GLES20.GL_COLOR_BUFFER_BIT);
		GLES20.glEnable(GLES20.GL_DEPTH_TEST);
		
		float sectionLength = (((float) TRACK_LENGTH) / ((float) TRACK_SECTIONS));
		int currentTrackSector = (int) (trackPosition / sectionLength);
		float height = getTrackHeight(trackPosition);
		
        float x = 0;	//_xpos;
        float y = 0;	//DEFAULT_CAMERA_HEIGHT + height;
        float z = trackPosition;
        
        float tx = 0;	//_xpos;
        float ty = 0;	//DEFAULT_CAMERA_HEIGHT / 2 + height;
        float tz = z + sectionLength * 10;
        
        Matrix.setLookAtM(mViewMatrix, 0, x, y, z, tx, ty, tz, 0, 1, 0);
        
        
        Matrix.setIdentityM(mModelMatrix, 0);
//		if(gameState == GAME_MENUS) {
//			accTime += currentTime - lastFrameTime;
//			
//			while(accTime > 20) {
//				_anglex += 0.03f;
//				accTime -= 20;
//			}
//			lastFrameTime = currentTime;
//			
//			Matrix.rotateM(mViewMatrix, 0, targetX, 0, 0, 1);
//			GLES20.glUseProgram(obsProgramHandle);
//			trackBuffer.position(obsPositionOffset);
//		    GLES20.glVertexAttribPointer(obsPositionHandle, obsPositionDataSize, GLES20.GL_FLOAT, false, obsStrideBytes, obsBuffer);        
//		    GLES20.glEnableVertexAttribArray(obsPositionHandle);
//		    
//	    	float xo = sectionObsX[0] * 10.f;
//	    	float zo = getSectionObstaclePos(0) - 0.2f;
//	    	float yo = getTrackHeight(zo) + 5;
//	    	
//		    Matrix.setIdentityM(mModelMatrix, 0);
//		    Matrix.translateM(mModelMatrix, 0, xo, yo, zo);
//		    Matrix.scaleM(mModelMatrix, 0, sectionObsW[0], 1.f, 1.f);
//		    
//		    Matrix.multiplyMM(mMVPMatrix, 0, mViewMatrix,       0, mModelMatrix, 0);
//		    Matrix.multiplyMM(mMVPMatrix, 0, mProjectionMatrix, 0, mMVPMatrix,   0);
//		
//		    GLES20.glUniformMatrix4fv(trackMVPMatrixHandle, 1, false, mMVPMatrix, 0);
//		    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 10);
//		    
//		    GLES20.glDisableVertexAttribArray(obsPositionHandle);
//		    
//		    
//		    
//		    Matrix.rotateM(mViewMatrix, 0, targetX, 0, 0, 1);
//		    
//		    GLES20.glDisable(GLES20.GL_DEPTH_TEST);
//		    GLES20.glEnable(GLES20.GL_BLEND);
//		    GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_DST_ALPHA);
//		    
//		    GLES20.glUseProgram(particleProgramHandle);
//		    particleBuffer.position(particlePositionOffset);
//		    GLES20.glVertexAttribPointer(particlePositionHandle, particlePositionDataSize, GLES20.GL_FLOAT, false, particleStrideBytes, particleBuffer);
//		    GLES20.glEnableVertexAttribArray(particlePositionHandle);
//		    
//		    particleBuffer.position(particleTextureOffset);
//		    GLES20.glVertexAttribPointer(particleTextureHandle, particleTextureDataSize, GLES20.GL_FLOAT, false, particleStrideBytes, particleBuffer);
//		    GLES20.glEnableVertexAttribArray(particleTextureHandle);
//		    
//		    GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
//	        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, playTexture);
////	        
//	        GLES20.glUniform1i(particleTextureUniformHandle, 0);
//	        
//	        Matrix.setIdentityM(mModelMatrix, 0);
//	        Matrix.translateM(mModelMatrix, 0, _xpos + (float) (Math.sin(_anglex) / 8.f), height + DEFAULT_CAMERA_HEIGHT - 0.20f, z + sectionLength/20.f);
//		    
//		    Matrix.multiplyMM(mMVPMatrix, 0, mViewMatrix,       0, mModelMatrix, 0);
//		    Matrix.multiplyMM(mMVPMatrix, 0, mProjectionMatrix, 0, mMVPMatrix, 0);
//		    
//		    GLES20.glUniformMatrix4fv(particleMVPMatrixHandle, 1, false, mMVPMatrix, 0);
//		    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
//		    
//		    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, particleTextureId);
//		    
//		    for(int i = 0; i < 10; i++) {
//		    	Matrix.setIdentityM(mModelMatrix, 0);
//		    
//		        Matrix.translateM(mModelMatrix, 0, _xpos + 6.5f + rnd.nextFloat() * 1.5f, height + DEFAULT_CAMERA_HEIGHT - 0.50f + rnd.nextFloat() * 1.5f, z + sectionLength/4.f);
//			    
//			    Matrix.multiplyMM(mMVPMatrix, 0, mViewMatrix,       0, mModelMatrix, 0);
//			    Matrix.multiplyMM(mMVPMatrix, 0, mProjectionMatrix, 0, mMVPMatrix, 0);
//			    
//			    GLES20.glUniformMatrix4fv(particleMVPMatrixHandle, 1, false, mMVPMatrix, 0);
//			    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
//		    }
//		    
//		    GLES20.glDisableVertexAttribArray(particlePositionHandle);
//		    GLES20.glDisableVertexAttribArray(particleTextureHandle);
//	        
//	        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
//	        GLES20.glDisable(GLES20.GL_BLEND);
//		} else {
			Matrix.rotateM(mViewMatrix, 0, targetX, 0, 0, 1);
			
			accTime += currentTime - lastFrameTime;
			
			float _animAccel = animAccel;
			
			while(accTime > 20) {
				_speed += _animAccel - _friction * _speed; 
				if(_speed < 0) _speed = 0;
			    trackPosition += _speed;
			    
//			    _anglex += (angleX - _anglex) / 4.f;
			    targetX += angleX;
//			    if(targetX >  60 + 2) targetX = 60 + 2;
//			    if(targetX < -60 - 2) targetX = -60 - 2;
			    		
//			    _xpos += (targetX - _xpos) / 4.f;
			    
//			    if(_xpos < -60) { particlesToKill += 5; _xpos = -55; targetX = -55;}
//			    if(_xpos >  60) { particlesToKill += 5; _xpos =  55; targetX =  55; }
//			    
//			    
//			    for(int i = 0; i < aliveParticles; i++) {
//			    	pPos[i * 3 + 0] += pVel[i * 3 + 0];
//			    	pPos[i * 3 + 1] += pVel[i * 3 + 1];
//			    	pPos[i * 3 + 2] += pVel[i * 3 + 2];
//			    	
//			    	if (	pPos[i * 3 + 0] > P_WIDTH/2   || pPos[i * 3 + 0] < -P_WIDTH/2 ||
//			    			pPos[i * 3 + 1] > P_HEIGHT/2  || pPos[i * 3 + 1] < -P_HEIGHT/2 ||
//			    			pPos[i * 3 + 2] > P_MAX_DEPTH || pPos[i * 3 + 2] < -P_MIN_DEPTH) {
//			    		
//			    		resetParticlePos(i);
//			    		resetParticleVel(i);
//			    	}
//			    }
			    
			    float particleTrackPos = trackPosition + sectionLength/1.1f - 12.f;
				int collisionSector = (int) (particleTrackPos / sectionLength);
				
				float fractional = (particleTrackPos - collisionSector * sectionLength);// / sectionLength);
				
				float minx = sectionObsX[collisionSector] * 10.f;
				float maxx = minx + 10.f * sectionObsW[collisionSector];
				
				boolean collision = collisionSector > 0 && (_xpos >= minx) && (_xpos <= maxx)  && fractional < 45;
			    if(collision) particlesToKill += 2;
			    
			    if(times == 20) {
			    	particlesToKill++;
			    	times = 0;
			    } else {
			    	times++;
			    }
			    
			    accTime -= 20.f;
			}
			
			lastFrameTime = currentTime;
			
	        GLES20.glUseProgram(trackProgramHandle);
			trackBuffer.position(trackPositionOffset);
		    GLES20.glVertexAttribPointer(trackPositionHandle, trackPositionDataSize, GLES20.GL_FLOAT, false, trackStrideBytes, trackBuffer);        
		    GLES20.glEnableVertexAttribArray(trackPositionHandle);        
		    
		    trackBuffer.position(trackColorOffset);
		    GLES20.glVertexAttribPointer(trackColorHandle, trackColorDataSize, GLES20.GL_FLOAT, false, trackStrideBytes, trackBuffer);        
		    GLES20.glEnableVertexAttribArray(trackColorHandle);
		    
		    Matrix.multiplyMM(mMVPMatrix, 0, mViewMatrix,       0, mModelMatrix, 0);
		    Matrix.multiplyMM(mMVPMatrix, 0, mProjectionMatrix, 0, mMVPMatrix,   0);
		
		    GLES20.glUniformMatrix4fv(trackMVPMatrixHandle, 1, false, mMVPMatrix, 0);
		    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, TRACK_SECTIONS * 2);
		    
		    GLES20.glDisableVertexAttribArray(trackPositionHandle);
		    GLES20.glDisableVertexAttribArray(trackColorHandle);
		    
		    
//		    // obstacles
//		    GLES20.glUseProgram(obsProgramHandle);
//			trackBuffer.position(obsPositionOffset);
//		    GLES20.glVertexAttribPointer(obsPositionHandle, obsPositionDataSize, GLES20.GL_FLOAT, false, obsStrideBytes, obsBuffer);        
//		    GLES20.glEnableVertexAttribArray(obsPositionHandle);
//		    
//		    for(int i = currentTrackSector + 20 - 1; i >= currentTrackSector ; i--) {
//		    	if(i == 0) break;
//		    	float xo = sectionObsX[i] * 10.f;
//		    	float zo = getSectionObstaclePos(i);
//		    	float yo = getTrackHeight(zo) + 5;
//		    	
//			    Matrix.setIdentityM(mModelMatrix, 0);
//			    Matrix.translateM(mModelMatrix, 0, xo, yo, zo);
//			    Matrix.scaleM(mModelMatrix, 0, sectionObsW[i], 1.f, 1.f);
//			    
//			    Matrix.multiplyMM(mMVPMatrix, 0, mViewMatrix,       0, mModelMatrix, 0);
//			    Matrix.multiplyMM(mMVPMatrix, 0, mProjectionMatrix, 0, mMVPMatrix,   0);
//			
//			    GLES20.glUniformMatrix4fv(trackMVPMatrixHandle, 1, false, mMVPMatrix, 0);
//			    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 10);
//		    }
//		    
//		    GLES20.glDisableVertexAttribArray(obsPositionHandle);
//		    
//		    
//		    // particles
//		    GLES20.glDisable(GLES20.GL_DEPTH_TEST);
//		    
//		    GLES20.glEnable(GLES20.GL_BLEND);
//		    GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_DST_ALPHA);
//		    
//		    GLES20.glUseProgram(particleProgramHandle);
//		    particleBuffer.position(particlePositionOffset);
//		    GLES20.glVertexAttribPointer(particlePositionHandle, particlePositionDataSize, GLES20.GL_FLOAT, false, particleStrideBytes, particleBuffer);
//		    GLES20.glEnableVertexAttribArray(particlePositionHandle);
//		    
//		    particleBuffer.position(particleTextureOffset);
//		    GLES20.glVertexAttribPointer(particleTextureHandle, particleTextureDataSize, GLES20.GL_FLOAT, false, particleStrideBytes, particleBuffer);
//		    GLES20.glEnableVertexAttribArray(particleTextureHandle);
//		    
//		    GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
//	        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, particleTextureId);
//	        GLES20.glUniform1i(particleTextureUniformHandle, 0);
	        
//	        for(int i = 0; aliveParticles > 0 && i < particlesToKill; i++) {
//	        	aliveParticles--;
//	        	
//	        	killedParticlePos[aliveParticles * 3 + 0] = pPos[i * 3 + 0] + _xpos;
//	        	killedParticlePos[aliveParticles * 3 + 2] = z + sectionLength/1.5f + pPos[i * 3 + 2];
//	        	killedParticlePos[aliveParticles * 3 + 1] = getTrackHeight(killedParticlePos[aliveParticles * 3 + 2]);
//		    }
//		    particlesToKill = 0;
//		    
//		    for(int i = 0; i < aliveParticles; i++) {
//		    	Matrix.setIdentityM(mModelMatrix, 0);
//			    Matrix.translateM(mModelMatrix, 0, pPos[i * 3 + 0] + _xpos, height + 2 + pPos[i * 3 + 1], z + sectionLength/1.1f + pPos[i * 3 + 2]);
//			    
//			    Matrix.multiplyMM(mMVPMatrix, 0, mViewMatrix,       0, mModelMatrix, 0);
//			    Matrix.multiplyMM(mMVPMatrix, 0, mProjectionMatrix, 0, mMVPMatrix, 0);
//			    
//			    GLES20.glUniformMatrix4fv(particleMVPMatrixHandle, 1, false, mMVPMatrix, 0);
//			    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
//		    }
//		    
//		    
//		    for(int i = aliveParticles; i < NPARTICLES; i++) {
//		    	Matrix.setIdentityM(mModelMatrix, 0);
//			    Matrix.translateM(mModelMatrix, 0, killedParticlePos[i * 3 + 0], killedParticlePos[i * 3 + 1], killedParticlePos[i * 3 + 2]);
//			    
//			    Matrix.multiplyMM(mMVPMatrix, 0, mViewMatrix,       0, mModelMatrix, 0);
//			    Matrix.multiplyMM(mMVPMatrix, 0, mProjectionMatrix, 0, mMVPMatrix, 0);
//			    
//			    GLES20.glUniformMatrix4fv(particleMVPMatrixHandle, 1, false, mMVPMatrix, 0);
//			    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
//		    }
//		    
//		    
//		    GLES20.glDisableVertexAttribArray(particlePositionHandle);
//		    GLES20.glDisableVertexAttribArray(particleTextureHandle);
//		    GLES20.glDisable(GLES20.GL_BLEND);
//		    
//		    
//		    GLES20.glUseProgram(barProgramHandle);
//		    particleBuffer.position(particlePositionOffset);
//		    GLES20.glVertexAttribPointer(particlePositionHandle, particlePositionDataSize, GLES20.GL_FLOAT, false, particleStrideBytes, particleBuffer);
//		    GLES20.glEnableVertexAttribArray(particlePositionHandle);
//		    
//		    Matrix.setLookAtM(mViewMatrix, 0, 0, 0, 0, 0, 0, 10, 0, 1, 0);
//		    Matrix.setIdentityM(mModelMatrix, 0);
//		    Matrix.translateM(mModelMatrix, 0, 7, 3.5f, 10);
//		    Matrix.scaleM(mModelMatrix, 0, -trackPosition / 5000.f, 0.25f, 1);
//		    Matrix.translateM(mModelMatrix, 0, 1, 0, 0);
//		    
//		    Matrix.multiplyMM(mMVPMatrix, 0, mViewMatrix,       0, mModelMatrix, 0);
//		    Matrix.multiplyMM(mMVPMatrix, 0, mProjectionMatrix, 0, mMVPMatrix, 0);
//		    
//		    GLES20.glUniformMatrix4fv(particleMVPMatrixHandle, 1, false, mMVPMatrix, 0);
//		    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
//		    
//		    GLES20.glDisableVertexAttribArray(particlePositionHandle);
		    
		    
		    
//		    //max distance
//		    GLES20.glDisable(GLES20.GL_DEPTH_TEST);
//		    
//		    GLES20.glEnable(GLES20.GL_BLEND);
//		    GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_DST_ALPHA);
//		    
//		    GLES20.glUseProgram(particleProgramHandle);
//		    particleBuffer.position(particlePositionOffset);
//		    GLES20.glVertexAttribPointer(particlePositionHandle, particlePositionDataSize, GLES20.GL_FLOAT, false, particleStrideBytes, particleBuffer);
//		    GLES20.glEnableVertexAttribArray(particlePositionHandle);
//		    
//		    particleBuffer.position(particleTextureOffset);
//		    GLES20.glVertexAttribPointer(particleTextureHandle, particleTextureDataSize, GLES20.GL_FLOAT, false, particleStrideBytes, particleBuffer);
//		    GLES20.glEnableVertexAttribArray(particleTextureHandle);
//		    
//		    GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
//	        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, particleTextureId);
//	        GLES20.glUniform1i(particleTextureUniformHandle, 0);
//		    
//	        Matrix.setLookAtM(mViewMatrix, 0, 0, 0, 0, 0, 0, 10, 0, 1, 0);
//		    Matrix.setIdentityM(mModelMatrix, 0);
//		    Matrix.translateM(mModelMatrix, 0, 7 - (maxDistance / 5000.f) * 2, 3.5f, 10);
//		    
//		    Matrix.multiplyMM(mMVPMatrix, 0, mViewMatrix,       0, mModelMatrix, 0);
//		    Matrix.multiplyMM(mMVPMatrix, 0, mProjectionMatrix, 0, mMVPMatrix, 0);
//		    
//		    GLES20.glUniformMatrix4fv(particleMVPMatrixHandle, 1, false, mMVPMatrix, 0);
//		    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
//		    
//		    GLES20.glDisableVertexAttribArray(particlePositionHandle);
//		    GLES20.glDisableVertexAttribArray(particleTextureHandle);
//		    GLES20.glDisable(GLES20.GL_BLEND);
		    if(trackPosition > maxDistance) maxDistance = trackPosition;
		    
		    if(aliveParticles <= 0) {		    	
		    	restartGame();
		    	gameState = GAME_MENUS;
		    }
//		}
    }
	
	private void restartGame() {
		aliveParticles = NPARTICLES;
		trackPosition = 0;
		_xpos = 0;
		targetX = 0;
		angleX = 0;
		_speed = 0;
		accTime = 0;
		animAccel = 0;
		lastFrameTime = System.currentTimeMillis();
	}
	
	private float getSectionObstaclePos(int currentTrackSector) {
		return trackVerticesData[currentTrackSector * 7  * 2];
	}
	
	private float getTrackHeight(float trackPosition) {
		float sectionLength = (((float) TRACK_LENGTH) / ((float) TRACK_SECTIONS));
		int currentTrackSector = (int) (trackPosition / sectionLength);
		int nextTrackSector = currentTrackSector + 1;
		
		float currentSectionHeight = trackVerticesData[currentTrackSector * 7  * 2 + 1];
		float nextSectionHeight = trackVerticesData[nextTrackSector * 7  * 2 + 1];
		float fractional = ((trackPosition - currentTrackSector * sectionLength) / sectionLength);
		
		return currentSectionHeight * (1.f - fractional) + nextSectionHeight * fractional;
	}

	private String readShader(String file) {
		InputStream is = null;
		DataInputStream dis = null;
		
		try {
			is = context.getAssets().open(file);
			dis = new DataInputStream(is);
	            
			int size = dis.available();
			byte[] buffer = new byte[size];
			dis.readFully(buffer);
			return new String(buffer);
		} catch(Exception e) {
			Log.e(TAG, "Error loading " + file + " from assets", e);
		} finally {
			try { dis.close(); } catch(Exception e) {}
			try { is.close(); } catch(Exception e) {}
		}
		
		return null;
	}
	
	private void checkGLError(String text) {
		int err = GLES20.glGetError();
		if (err != GLES20.GL_NO_ERROR) {
			Log.e(TAG, "****** GL ERROR " + text + " :: " + err);
		}
	}
}
