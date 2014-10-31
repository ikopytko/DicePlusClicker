package wsiz.pl.diceplus;

import android.app.Activity;
import android.content.Context;
import android.util.Log;

import us.dicepl.android.sdk.BluetoothManipulator;
import us.dicepl.android.sdk.DiceConnectionListener;
import us.dicepl.android.sdk.DiceController;
import us.dicepl.android.sdk.DiceResponseAdapter;
import us.dicepl.android.sdk.DiceScanningListener;
import us.dicepl.android.sdk.Die;
import us.dicepl.android.sdk.protocol.constants.Constants;
import us.dicepl.android.sdk.responsedata.TouchData;

/**
 * Created by Ivan on 10/30/2014.
 */
public class Game extends DiceResponseAdapter implements DiceScanningListener, DiceConnectionListener {
    private static final String TAG = "DICEPlus";

    public interface GameListener {
        void onScanning();

        void onScanFailed();

        void onConnected();

        void onDisconnected();

        void onLowBattery();

        void onStartGame();

        void onButtonStateChange(boolean pressed);
    }

    enum GameState {
        NotReady, Ready, InGame, Finished
    }

    private static final int[] developerKey = new int[]{0x83, 0xed, 0x60, 0x0e, 0x5d, 0x31, 0x8f, 0xe7};

    private GameListener listener;
    private Die dicePlus;
    private GameState state;

    private int doubleTapSeed = 300;
    private int diceFace = 1;

    private long lastClickTimestamp;
    private int clicks = 0;

    public Game(GameListener listener) {
        this.listener = listener;
        state = GameState.NotReady;
    }

    public void startDice() {
        // Initiating
        BluetoothManipulator.initiate((Context) listener);
        DiceController.initiate(developerKey);

        // Listen to all the state occurring during the discovering process of DICE+
        BluetoothManipulator.registerDiceScanningListener(this);

        // When connecting to DICE+ you get two responses: a good one and a bad one ;)
        DiceController.registerDiceConnectionListener(this);

        // Attaching to DICE+ events that we subscribed to.
        DiceController.registerDiceResponseListener(this);

        // Scan for a DICE+
        BluetoothManipulator.startScan();
    }

    public void stopDice() {
        state = GameState.NotReady;
        // Unregister all the listeners
        DiceController.unregisterDiceConnectionListener(this);
        BluetoothManipulator.unregisterDiceScanningListener(this);
        DiceController.unregisterDiceResponseListener(this);

        DiceController.disconnectDie(dicePlus);
        dicePlus = null;
    }

    public String decToBin(int x) {
        String res = "";
        for (int i = 32; i > 0; i >>= 1) {
            res += ((x & i) > 0) ? 1 : 0;
        }
        return res;
    }

    public int getClicksCount() {
        return clicks;
    }

    private void animate() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                int sleepTime = 0;
                while (state != GameState.NotReady) {
                    switch (state) {
                        case NotReady:
                            break;
                        case Ready:
                            DiceController.runBlinkAnimation(dicePlus, 1, 10, 0, 255, 0, 1000, 1400, 1);
                            sleepTime = 1400;
                            break;
                        case InGame:
                            DiceController.runBlinkAnimation(dicePlus, 1, 8, 255, 0, 0, 200, 400, 1);
                            sleepTime = 400;
                            break;
                        case Finished:
                            DiceController.runFadeAnimation(dicePlus, 1, 8, 255 , 0, 255, 200, 800);
                            sleepTime = 1000;
                            break;
                    }

                    try {
                        Thread.sleep(sleepTime);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }).start();
    }

    public void start() {
        clicks = 0;
        state = GameState.Ready;
    }

    public void onFinish() {
        state = GameState.Finished;
    }

    // region DiceScanningListener
    @Override
    public void onNewDie(Die die) {
        Log.d(TAG, "New DICE+ found");
        dicePlus = die;
        DiceController.connect(dicePlus);
    }

    @Override
    public void onScanStarted() {
        Log.d(TAG, "Scan Started");

        ((Activity) listener).runOnUiThread(new Runnable() {
            @Override
            public void run() {
                listener.onScanning();
            }
        });
    }

    @Override
    public void onScanFailed() {
        Log.d(TAG, "Scan Failed");
        BluetoothManipulator.startScan();

        ((Activity) listener).runOnUiThread(new Runnable() {
            @Override
            public void run() {
                listener.onScanFailed();
            }
        });
    }

    @Override
    public void onScanFinished() {
        Log.d(TAG, "Scan Finished");

        if (dicePlus == null) {
            BluetoothManipulator.startScan();
        }
    }
    // endregion

    // region DiceConnectionListener
    @Override
    public void onConnectionEstablished(Die die) {
        Log.d(TAG, "DICE+ Connected");
        start();
        animate();

        DiceController.subscribeTouchReadouts(dicePlus);
        DiceController.subscribeBatteryState(dicePlus);

        ((Activity) listener).runOnUiThread(new Runnable() {
            @Override
            public void run() {
                listener.onConnected();
            }
        });
    }

    @Override
    public void onConnectionFailed(Die die, Exception e) {
        Log.d(TAG, "Connection failed", e);
        state = GameState.NotReady;

        ((Activity) listener).runOnUiThread(new Runnable() {
            @Override
            public void run() {
                listener.onScanFailed();
            }
        });

        dicePlus = null;
        BluetoothManipulator.startScan();
    }

    @Override
    public void onConnectionLost(Die die) {
        Log.d(TAG, "Connection lost");
        state = GameState.NotReady;

        ((Activity) listener).runOnUiThread(new Runnable() {
            @Override
            public void run() {
                listener.onDisconnected();
            }
        });

        dicePlus = null;

        BluetoothManipulator.startScan();
    }
    // endregion

    // region DiceResponseListener
    @Override
    public void onTouchReadout(Die die, TouchData data, Exception exception) {
        super.onTouchReadout(die, data, exception);

        final int current_state_mask = data.current_state_mask;
        final int change_mask = data.change_mask;

        if ((change_mask & 1) == 0)
            return;

        switch (state) {
            case NotReady:
                break;
            case Ready:
                if ((current_state_mask & diceFace) != 0 && (data.timestamp - lastClickTimestamp) < doubleTapSeed) {
                    state = GameState.InGame;
                    ((Activity) listener).runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            listener.onStartGame();
                        }
                    });
                }
                break;
            case InGame:
                if ((current_state_mask & diceFace) != 0)
                    clicks++;

                ((Activity) listener).runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        listener.onButtonStateChange((current_state_mask & diceFace) != 0);
                    }
                });
                break;
            case Finished:
                break;
        }

        lastClickTimestamp = data.timestamp;
        /*
        if ((change_mask & 1) != 0)
            setBtnState((current_state_mask & 32) != 0);

        if (current_state_mask == 33)
            DiceController.runBlinkAnimation(die, 1, 10, 255, 0, 0, 500, 1000, 5);*/
    }

    @Override
    public void onBatteryState(Die die, Constants.BatteryState status, int percentage, boolean low, Exception exception) {
        super.onBatteryState(die, status, percentage, low, exception);

        if (low)
            ((Activity) listener).runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    listener.onLowBattery();
                }
            });
    }
    // endregion
}
