package com.example.splashdemo;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.transition.Scene;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceHolder.Callback;
import android.view.SurfaceView;
import android.view.VelocityTracker;
import android.widget.Toast;

public class MySurfaceView extends SurfaceView implements Callback, Runnable {

	private Context context = null;

	// ���ڿ���SurfaceView
	private SurfaceHolder sfh = null;
	// ����һ������
	private Paint paint;
	// ����һ���߳�
	private Thread th = null;
	// ���ڿ����̵߳ı�ʶ��
	private boolean flag;
	// ����һ������
	private Canvas canvas;
	// ����ߺͿ�
	public static int screenW, screenH;

	/** ��ǰҳ����ţ���һҳ��0 */
	int currentViewNo = 0;
	//��ʱ���ķ�����������Ϊ30������ͬһ���߼�ִ��30�Σ�����Ϊ-1����Ҫ����Ϊ�����Ƿ��ǵ�һ�ν���Ӧ��
	//��һ�ν���Ӧ�ã�û����Ҫ���Ƽ�ʱ���ķ�������ʱ���ķ���ֻ�ǿ��Ƶ�ͼת����ʱ��
	//��һ�ν���Ӧ�õ�����Ҫת��������oneTopCtrl��ֵ���ܴ���-1����logic����������ô�����
	private int oneTopCtrl = -1;
	float oneEartRatOut = 0;	//��ת�ĽǶ�
	int sign = -1;	//���ƻ��������
	
	private Bitmap bmpEart = null;	//��ͼ��Դ
	private int bmpEartX, bmpEartY ;	//������Դ������
	private int ratX, ratY ;	//��ת���ĵ������
	
	public MySurfaceView(Context context) {
		super(context);
		this.context = context;
		// ///////////SurfaceView���/////////////////////////////
		sfh = (SurfaceHolder) this.getHolder();
		sfh.addCallback(this);
		canvas = new Canvas();
		paint = new Paint();
		paint.setColor(Color.WHITE);
		paint.setAntiAlias(true);
		this.setFocusable(true);	
	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {

		screenW = this.getWidth();
		screenH = this.getHeight();
		// ///////////��Դ��ʼ��////////////////////////////////
		initData();
				
		flag = true;
		th = new Thread(this);
		th.start();
	}

	/**
	 * ���ƻ���
	 */
	public void myDraw() {
		try {
			canvas = sfh.lockCanvas();
			if (canvas != null) {
				canvas.drawColor(context.getResources().getColor(R.color.bg));//ˢ����Ļ���ñ���ɫˢ��
				canvas.save();	//ע��canvas.save()��canvas,restore()�ǳɶԴ��ڵ�
				canvas.rotate(oneEartRatOut, ratX, ratY);	//��ת���ƵĽǶ�
				canvas.drawBitmap(bmpEart, bmpEartX, bmpEartY, paint);//���Ƶ�ͼ
				canvas.restore();
			}
		} catch (Exception e) {

		} finally {
			if (canvas != null) {
				sfh.unlockCanvasAndPost(canvas);
			}
		}
	}

	/**
	 * ҳ���߼�
	 */
	public void logic() {
		//oneTopCtrl������ת��ʱ��ͽǶȣ�����Ϊ30��ÿ������3*sign<-1����1>������ÿ����ת�ĽǶȾ���90��
		if (oneTopCtrl >= 0 && oneTopCtrl < 30) {
			oneTopCtrl++;
			oneEartRatOut += 3 * sign;
			if (oneEartRatOut > 360) {
				oneEartRatOut %= 360;
			}
		}
	}

	/**
	 * ���������¼�
	 */
	private VelocityTracker mVt = null;

	int detaX = 0; // ��ָ�ƶ���x�����λ��
	int tmpX = 0, tmpY = 0; // Moveʱ�������

	int startX = 0, startY = 0;
	int endX = 0, endY = 0;

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		synchronized (event) {
			int action = event.getAction();
			switch (action) {
			case MotionEvent.ACTION_DOWN:
				// ��ָ����
				startX = (int) event.getX();
				startY = (int) event.getY();
				break;
			case MotionEvent.ACTION_MOVE:
				// ��Ӳ�����
				// mVt.addMovement(event);
				tmpX = (int) event.getX();
				tmpY = (int) event.getY();
				break;
			case MotionEvent.ACTION_UP:
				// ��ָ�ƶ�
				endX = (int) event.getX();
				endY = (int) event.getY();
				int tmp = (endX - startX);

				int dir = judgeDir(startX, startY, endX, endY);

				if (dir == 1) {// right
					if (currentViewNo > 0) {
						sign = 1;
						oneTopCtrl = 0;
						currentViewNo--;
					}
				} else if (dir == 2) {// left
					if (currentViewNo < 3) {
						sign = -1;
						oneTopCtrl = 0;
						currentViewNo++;
					}
				}
				break;
			case MotionEvent.ACTION_CANCEL:
			case MotionEvent.ACTION_OUTSIDE:
				// �����¼�����
				// �����ٶȵ�ʱ�䵥λ 1����ÿ�����ƶ��������� 1000����ÿ���ƶ���������
				// ��������ڻ���ٶ�֮ǰ����Ҫ����
				mVt.clear();// ���
				mVt.recycle();// ����vt������ڴ�
				break;
			}
		}
		return true;
	}

	/**
	 * ���Ʒ����б�
	 * 
	 */
	private int judgeDir(int startX, int startY, int endX, int endY) {

		if ((endX - startX) > 0 && Math.abs(endX - startX) > screenW / 3) {
			// ���������ҷ� 1
			return 1;
		} else if ((endX - startX) < 0 && Math.abs(endX - startX) > screenW / 3) {
			// ���������� 2
			return 2;
		}
		return 0;
	}

	@Override
	public void run() {
		while (flag) {
			long start = System.currentTimeMillis();
			myDraw();
			logic();
			long end = System.currentTimeMillis();
			try {
				if (end - start < 50) {
					Thread.sleep(50 - (end - start));
				}
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * ����״̬�ı�����¼�
	 */
	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width,
			int height) {

	}

	/**
	 * �������ݻ��¼�
	 */
	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		flag = false;
	}

	/**
	 * ��ʼ��������Դ
	 */
	private void initData() {
		//��ʼ��������Դ
		bmpEart = (Bitmap) BitmapFactory.decodeResource(getResources(),
				R.drawable.revolve_ground);
		//��ʼ��������Դ����
		initAllXY();
	}
	/**
	 * ��ʼ�����������
	 */
	private void initAllXY() {
		bmpEartX = screenW / 2 - bmpEart.getWidth() / 2;
		bmpEartY = screenH - bmpEart.getHeight() / 4;
		
		ratX = screenW / 2;
		ratY = screenH + bmpEart.getHeight() / 4;
	}
}
