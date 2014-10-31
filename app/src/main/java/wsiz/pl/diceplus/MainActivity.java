package wsiz.pl.diceplus;

import android.app.Activity;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.ImageView;
import android.widget.TextView;

public class MainActivity extends Activity implements Game.GameListener {

    TextView TVResult;
    ImageView ivBtn;
    ImageView ivStatus;
    TextView tvStart;
    Game game;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ivBtn = (ImageView) findViewById(R.id.ivBtnPressed);
        ivStatus = (ImageView) findViewById(R.id.ivStatus);
        tvStart = (TextView) findViewById(R.id.tvStart);
        TVResult = (TextView) findViewById(R.id.TVResult);

        game = new Game(this);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    protected void onResume() {
        super.onResume();
        game.startDice();
    }

    @Override
    protected void onStop() {
        super.onStop();
        game.stopDice();
    }

    private void setBtnState(final boolean pressed) {
        ivBtn.setVisibility(pressed ? View.VISIBLE : View.INVISIBLE);
    }

    private void setStateImage(final int state) {
        ivStatus.setImageResource(state);
    }

    void showStart() {
        final Animation in = new AlphaAnimation(0.0f, 1.0f);
        in.setDuration(1000);

        tvStart.setVisibility(View.VISIBLE);
        tvStart.setAnimation(in);
    }

    void hideStart() {
        final Animation out = new AlphaAnimation(1.0f, 0.0f);
        out.setDuration(1000);

        tvStart.setAnimation(out);
        tvStart.setVisibility(View.INVISIBLE);
    }

    void showTimer() {
        new CountDownTimer(15000, 1000) {
            public void onTick(long millisUntilFinished) {
                TVResult.setText("" + (millisUntilFinished / 1000));
            }

            public void onFinish() {
                TVResult.setText("Clicks: " + game.getClicksCount());
                game.onFinish();
                onButtonStateChange(false);

                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Thread.sleep(2500);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        MainActivity.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                restart();
                            }
                        });
                    }
                }).start();
            }
        }.start();
    }

    void restart() {
        showStart();
        game.start();
    }

    @Override
    public void onScanning() {
        setStateImage(R.drawable.searching);
    }

    @Override
    public void onScanFailed() {
        setStateImage(R.drawable.notfound);
    }

    @Override
    public void onConnected() {
        setStateImage(R.drawable.connected);
        restart();
    }

    @Override
    public void onDisconnected() {
        setStateImage(R.drawable.disconnected);
    }

    @Override
    public void onLowBattery() {
        setStateImage(R.drawable.lowbattery);
    }

    @Override
    public void onStartGame() {
        showTimer();
        hideStart();
    }

    @Override
    public void onButtonStateChange(boolean pressed) {
        setBtnState(pressed);
    }
}