package com.mobvcasting.mjpegvideocapture;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.View.OnClickListener;
import android.widget.Button;

/*
 * This version formats the output in the same manner that something like an 
 * axis camera sends out. 
 */

public class VideoCapture extends Activity implements OnClickListener, SurfaceHolder.Callback, Camera.PreviewCallback {

	public static final String LOGTAG = "VIDEOCAPTURE";
	
	String boundaryStart = "--myboundary\r\nContent-type: image/jpg\r\nContent-Length: ";
	String boundaryEnd = "\r\n\r\n";
		
	private SurfaceHolder holder;
	private Camera camera;	
	private CamcorderProfile camcorderProfile;
	
	boolean recording = false;
	boolean previewRunning = false;
	
	byte[] previewCallbackBuffer;
	
	File mjpegFile;
	FileOutputStream fos;
	BufferedOutputStream bos;
	Button recordButton;
	
	Camera.Parameters p;

	//http://www.damonkohler.com/2010/10/mjpeg-streaming-protocol.html
	//http://sourceforge.net/projects/jipcam/files/jipcam-src/0.9.2/jipcam-0.9.2-src.zip/download
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		try {
			mjpegFile = File.createTempFile("videocapture", ".mjpeg", Environment.getExternalStorageDirectory());			
		} catch (Exception e) {
			Log.v(LOGTAG,e.getMessage());
			finish();
		}
		
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
				WindowManager.LayoutParams.FLAG_FULLSCREEN);
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

		setContentView(R.layout.main);
		
		recordButton = (Button) this.findViewById(R.id.RecordButton);
		recordButton.setOnClickListener(this);
		
		camcorderProfile = CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH);

		SurfaceView cameraView = (SurfaceView) findViewById(R.id.CameraView);
		holder = cameraView.getHolder();
		holder.addCallback(this);
		holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

		cameraView.setClickable(true);
		cameraView.setOnClickListener(this);
	}

	public void onClick(View v) {
		if (recording) {
			recording = false;
			
			try {
				bos.flush();
				bos.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			
			Log.v(LOGTAG, "Recording Stopped");
		} else {
			
			try {
				fos = new FileOutputStream(mjpegFile);
				bos = new BufferedOutputStream(fos);
				
				recording = true;
				Log.v(LOGTAG, "Recording Started");
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			}
		}
	}

	public void surfaceCreated(SurfaceHolder holder) {
		Log.v(LOGTAG, "surfaceCreated");
		
		camera = Camera.open();
		
		/*
		try {
			camera.setPreviewDisplay(holder);
			camera.startPreview();
			previewRunning = true;
		}
		catch (IOException e) {
			Log.e(LOGTAG,e.getMessage());
			e.printStackTrace();
		}	
		*/
	}

	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
		Log.v(LOGTAG, "surfaceChanged");

		if (!recording) {
			if (previewRunning){
				camera.stopPreview();
			}

			try {
				p = camera.getParameters();

				p.setPreviewSize(camcorderProfile.videoFrameWidth, camcorderProfile.videoFrameHeight);
			    p.setPreviewFrameRate(camcorderProfile.videoFrameRate);
				camera.setParameters(p);
				
				camera.setPreviewDisplay(holder);
				
				/*
				Log.v(LOGTAG,"Setting up preview callback buffer");
				previewCallbackBuffer = new byte[(camcorderProfile.videoFrameWidth * camcorderProfile.videoFrameHeight * 
													ImageFormat.getBitsPerPixel(p.getPreviewFormat()) / 8)];
				Log.v(LOGTAG,"setPreviewCallbackWithBuffer");
				camera.addCallbackBuffer(previewCallbackBuffer);				
				camera.setPreviewCallbackWithBuffer(this);
				*/
				
				camera.setPreviewCallback(this);
				
				Log.v(LOGTAG,"startPreview");
				camera.startPreview();
				previewRunning = true;
			}
			catch (IOException e) {
				Log.e(LOGTAG,e.getMessage());
				e.printStackTrace();
			}	
		}
	}

	public void surfaceDestroyed(SurfaceHolder holder) {
		Log.v(LOGTAG, "surfaceDestroyed");
		if (recording) {
			recording = false;

			try {
				bos.flush();
				bos.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		previewRunning = false;
		camera.release();
		finish();
	}

	public void onPreviewFrame(byte[] b, Camera c) {
		//Log.v(LOGTAG,"onPreviewFrame");
		if (recording) {

			// Assuming ImageFormat.NV21
			if (p.getPreviewFormat() == ImageFormat.NV21) {
				Log.v(LOGTAG,"Started Writing Frame");

				try {
					
					ByteArrayOutputStream jpegByteArrayOutputStream = new ByteArrayOutputStream();
					YuvImage im = new YuvImage(b, ImageFormat.NV21, p.getPreviewSize().width, p.getPreviewSize().height, null);
					Rect r = new Rect(0,0,p.getPreviewSize().width,p.getPreviewSize().height);
					im.compressToJpeg(r, 5, jpegByteArrayOutputStream);							
					byte[] jpegByteArray = jpegByteArrayOutputStream.toByteArray();
					
					byte[] boundaryBytes = (boundaryStart + jpegByteArray.length + boundaryEnd).getBytes();
					bos.write(boundaryBytes);

					bos.write(jpegByteArray);
					bos.flush();
				} catch (IOException e) {
					e.printStackTrace();
				}
				
				Log.v(LOGTAG,"Finished Writing Frame");
			} else {
				Log.v(LOGTAG,"NOT THE RIGHT FORMAT");
			}
		}
	}
	
    @Override
    public void onConfigurationChanged(Configuration conf) 
    {
        super.onConfigurationChanged(conf);
        
    }	
}
