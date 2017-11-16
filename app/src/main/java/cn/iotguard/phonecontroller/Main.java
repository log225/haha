package cn.iotguard.phonecontroller;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.hardware.input.InputManager;
import android.os.Build;
import android.os.Looper;
import android.os.SystemClock;
import android.support.annotation.RequiresApi;
import android.support.v4.view.InputDeviceCompat;
import android.view.InputEvent;
import android.view.MotionEvent;

import com.koushikdutta.async.http.WebSocket;
import com.koushikdutta.async.http.server.AsyncHttpServer;
import com.koushikdutta.async.http.server.AsyncHttpServerRequest;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by caowentao on 2016/11/23.
 */

public class Main {

    private static final int LISTEN_PORT = 56789;
    private static final String KEY_FINGER_DOWN = "fingerdown";
    private static final String KEY_FINGER_UP = "fingerup";
    private static final String KEY_FINGER_MOVE = "fingermove";
    private static final String KEY_CHANGE_SIZE = "change_size";
    private static final String KEY_BEATHEART = "beatheart";
    private static final String KEY_EVENT_TYPE = "type";
    private static InputManager sInputManager;
    private static Method sInjectInputEventMethod;
    private static final float BASE_WIDTH = 720;
    private static final float BASE_HEIGHT = 1280;
    private static int sPictureWidth = 360;
    private static int sPictureHeight = 640;
    private static int sRotate = 0;
    private static Thread sSendImageThread;
    private static Timer sTimer;
    private static boolean sViewerIsAlive;
    private static boolean sThreadKeepRunning;
    private static volatile int currentPointerNumber = 0;
    private static Map<Integer,Integer> pointerIds = new ConcurrentHashMap<>();
    private static Map<Integer, MotionEvent.PointerCoords> pointerCoordsMap = new ConcurrentHashMap<>();

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    @SuppressLint("PrivateApi")
    public static void main(String[] args) {
        Looper.prepare();
        System.out.println("PhoneController start...");
//        sTimer = new Timer();
        try {
            sInputManager = (InputManager) InputManager.class.getDeclaredMethod("getInstance").invoke(null);
            sInjectInputEventMethod = InputManager.class.getMethod("injectInputEvent", InputEvent.class, Integer.TYPE);
            AsyncHttpServer httpServer = new AsyncHttpServer();
            httpServer.websocket("/input", new InputHandler());
            httpServer.listen(LISTEN_PORT);
            Looper.loop();
        } catch (Exception e) {
            System.out.println("error: " + e.getMessage());
        }
    }

    private static class InputHandler implements AsyncHttpServer.WebSocketRequestCallback {
        @Override
        public void onConnected(WebSocket webSocket, AsyncHttpServerRequest request) {
            System.out.println("websocket connected.");
            if (sSendImageThread == null) {
                sThreadKeepRunning = true;
                sSendImageThread = new Thread(new SendScreenShotThread((webSocket)));
                sSendImageThread.start();
                webSocket.setStringCallback(new WebSocket.StringCallback() {
                    @Override
                    public void onStringAvailable(String s) {
                        try {
                            JSONObject event = new JSONObject(s);
                            String eventType = event.getString(KEY_EVENT_TYPE);
                            switch (eventType) {
                                case KEY_FINGER_DOWN: {
                                    currentPointerNumber++;
                                    pointerIds.put(currentPointerNumber, currentPointerNumber);
                                    float x = event.getInt("x");
                                    float y = event.getInt("y");
                                    int index = event.getInt("index");
                                    MotionEvent.PointerCoords ss = new MotionEvent.PointerCoords();
                                    ss.setAxisValue(MotionEvent.AXIS_X, x);
                                    ss.setAxisValue(MotionEvent.AXIS_Y, y);
                                    pointerCoordsMap.put(index, ss);
                                    int action;
                                    switch (currentPointerNumber) {
                                        case 1:
                                            action = 0;
                                            break;
                                        case 2:
                                            action = 261;
                                            break;
                                        case 3:
                                            action = 517;
                                            break;
                                        default:
                                            action = 0;
                                            break;
                                    }
                                    System.out.println("receive down " + action + "current pointer number = " + currentPointerNumber);
                                    injectMotionEvent(InputDeviceCompat.SOURCE_TOUCHSCREEN, action,
                                            SystemClock.uptimeMillis(), x, y, 1.0f);
                                }
                                break;
                                case KEY_FINGER_UP: {
                                    float x = event.getInt("x");
                                    float y = event.getInt("y");
                                    int index = event.getInt("index");  //第几个触摸事件抬起来
                                    int action = 1;
                                    if (index == 1) {
                                        if (currentPointerNumber == 3) {
                                            action = 6;
                                        } else if (currentPointerNumber == 2) {
                                            action = 6;
                                        } else if (currentPointerNumber == 1) {
                                            action = 1;
                                        } else {
                                            action = 1;
                                        }
                                    } else if (index == 2) {
                                        if (currentPointerNumber == 3) {
                                            action = 262;
                                        } else if (currentPointerNumber == 2) {
                                            action = 262;
                                        } else {
                                            action = 1;
                                        }
                                    } else if (index == 3) {
                                        if (currentPointerNumber == 3) {
                                            action = 518;
                                        } else {
                                            action = 1;
                                        }
                                    }
                                    System.out.println("receive up " + action + "current pointer number = " + currentPointerNumber);
                                    currentPointerNumber--;
                                    pointerIds.remove(currentPointerNumber);
                                    injectMotionEvent(InputDeviceCompat.SOURCE_TOUCHSCREEN, action,
                                            SystemClock.uptimeMillis(), x, y, 1.0f);
                                }
                                break;
                                case KEY_FINGER_MOVE: {
                                    float x = event.getInt("x");
                                    float y = event.getInt("y");
                                    int index = event.getInt("index");
                                    MotionEvent.PointerCoords ss = new MotionEvent.PointerCoords();
                                    ss.setAxisValue(MotionEvent.AXIS_X, x);
                                    ss.setAxisValue(MotionEvent.AXIS_Y, y);
                                    pointerCoordsMap.put(index, ss);
                                    injectMotionEvent(InputDeviceCompat.SOURCE_TOUCHSCREEN, 2,
                                            SystemClock.uptimeMillis(), x, y, 1.0f);
                                }
                                break;
                                case KEY_BEATHEART:
                                    sViewerIsAlive = true;
                                    break;
                                case KEY_CHANGE_SIZE:
                                    sPictureWidth = event.getInt("w");
                                    sPictureHeight = event.getInt("h");
                                    sRotate = event.getInt("r");
                                    break;
                                default:
                                    break;
                            }
                        } catch (Exception e) {
                            System.out.println(e.getMessage());
                        }
                    }
                });
            } else {
                webSocket.close();
            }
        }
    }

    private static class SendScreenShotThreadWatchDog extends TimerTask {
        @Override
        public void run() {
            if (sViewerIsAlive) {
                sViewerIsAlive = false;
            } else if (sSendImageThread != null) {
                System.out.println("exit thread");
                sThreadKeepRunning = false;
                sSendImageThread = null;
                cancel();
                sTimer.purge();
            }
        }
    }

    private static class SendScreenShotThread implements Runnable {

        WebSocket mWebSocket;
        String mSurfaceName;

        SendScreenShotThread(WebSocket webSocket) {
            mWebSocket = webSocket;
            if (Build.VERSION.SDK_INT <= 17) {
                mSurfaceName = "android.view.Surface";
            } else {
                mSurfaceName = "android.view.SurfaceControl";
            }
        }

        @Override
        public void run() {
            while (sThreadKeepRunning) {
                try {
                    Bitmap bitmap = (Bitmap) Class.forName(mSurfaceName)
                            .getDeclaredMethod("screenshot", new Class[]{Integer.TYPE, Integer.TYPE})
                            .invoke(null, sPictureWidth, sPictureHeight);
                    Matrix matrix = new Matrix();
                    matrix.setRotate(sRotate);
                    Bitmap resultBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, false);
                    ByteArrayOutputStream bout = new ByteArrayOutputStream();
                    resultBitmap.compress(Bitmap.CompressFormat.JPEG, 100, bout);
                    bout.flush();
                    mWebSocket.send(bout.toByteArray());
                } catch (Exception e) {
                    System.out.println(e.getMessage());
                    break;
                }
            }
            mWebSocket.close();
        }
    }

    private static void injectMotionEvent(int inputSource, int action, long when, float x, float y, float pressure) {
        try {
//            static public MotionEvent obtain(long downTime, long eventTime,
//            int action, int pointerCount, int[] pointerIds, PointerCoords[] pointerCoords,
//            int metaState, float xPrecision, float yPrecision, int deviceId,
//            int edgeFlags, int source, int flags) {
            System.out.println("currentPointerNumber " + currentPointerNumber);
            MotionEvent event = MotionEvent.obtain(when, when, action, currentPointerNumber, getInt(),create(), 0, 1.0f, 1.0f, 1, 0, inputSource, 1);
            event.setSource(inputSource);
            sInjectInputEventMethod.invoke(sInputManager, event, 0);
            event.recycle();
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }
    private static int[] getInt() {
        Set<Integer>  keySet=  pointerIds.keySet();
        int[] result = new int[keySet.size()];
        int index =0;
        for (Integer integer : keySet) {
            result[index] = pointerIds.get(integer);
            index++;
        }
        System.out.println("int[] = " + result.length);
        return result;
    }

    private static MotionEvent.PointerCoords[] create() {
        Set<Integer>  keySet=  pointerCoordsMap.keySet();
        MotionEvent.PointerCoords[] result = new MotionEvent.PointerCoords[keySet.size()];
        int index =0;
        for (Integer integer : keySet) {
            result[index] = pointerCoordsMap.get(integer);
            index++;
        }
        System.out.println("PointerCoords[] = " + result.length);
        return result;
    }
}