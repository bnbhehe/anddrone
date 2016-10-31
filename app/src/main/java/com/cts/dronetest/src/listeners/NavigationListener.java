package com.cts.dronetest.src.listeners;

import android.os.Handler;
import android.view.MotionEvent;
import android.view.View;

import com.cts.dronetest.src.activities.BebopActivity;
import com.cts.dronetest.src.activities.MainActivity;

import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by cts on 21-9-16.
 */
public class NavigationListener implements View.OnTouchListener,View.OnClickListener,View.OnLongClickListener {
    Handler repeatUpdateHandler = new Handler();
    private boolean autoIncrement = false;
    private boolean autoDecrement = false;
    private double acceleration;
    private final long REPEAT_DELAY = 100;


    public NavigationListener(int btnId,double acceleration){
        this.acceleration = acceleration;
    }
    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP && autoIncrement) {
            autoIncrement = false;
            autoDecrement = true;
        }
        return false;
    }



    @Override
    public void onClick(View v) {
        touchDown();
    }

    @Override
    public boolean onLongClick(View v) {
        autoIncrement = true;
        repeatUpdateHandler.post(new RepetitiveUpdater());
        return false;
    }


    class RepetitiveUpdater implements Runnable {

        @Override
        public void run() {
            if (autoIncrement) {
                touchDown();
                repeatUpdateHandler.postDelayed(new RepetitiveUpdater(), REPEAT_DELAY);
            } else if (autoDecrement) {
                touchReleased();
                repeatUpdateHandler.postDelayed(new RepetitiveUpdater(), REPEAT_DELAY);
            }
        }

    }

    public void touchDown() {
        BebopActivity.pressedIncr += 0.1f;
        System.out.println("TOUCHING "+BebopActivity.pressedIncr);
    }

    public void touchReleased(){
        if(BebopActivity.pressedIncr > 0.0f){
            BebopActivity.pressedIncr -= 0.1f;
        }
        else{
            BebopActivity.pressedIncr = 0.0f;
            return;
        }
        System.out.println("RELEASED "+BebopActivity.pressedIncr);


    }


}
