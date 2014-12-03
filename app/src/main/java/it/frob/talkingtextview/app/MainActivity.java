/*
 Copyright (c) 2014, Alessandro Gatti - frob.it
 All rights reserved.

 Redistribution and use in source and binary forms, with or without
 modification, are permitted provided that the following conditions are met:

 1. Redistributions of source code must retain the above copyright notice, this
    list of conditions and the following disclaimer.

 2. Redistributions in binary form must reproduce the above copyright notice,
    this list of conditions and the following disclaimer in the documentation
    and/or other materials provided with the distribution.

 THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package it.frob.talkingtextview.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import java.util.Locale;
import java.util.Set;

import it.frob.talkingtextview.TalkingTextView;

public class MainActivity extends Activity implements TextToSpeech.OnInitListener {

    private static final String LOG = MainActivity.class.getSimpleName();
    private TalkingTextView mTalkingTextView;
    private TextToSpeech mTextToSpeech;
    private boolean mIsInitialized = false;
    private boolean mIsSpeaking = false;

    @SuppressLint("NewApi")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (mTextToSpeech != null) {
            mTextToSpeech.shutdown();
            mIsInitialized = false;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        mTextToSpeech = new TextToSpeech(this, this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem item = menu.findItem(R.id.action_start);
        assert item != null;
        item.setEnabled(mIsInitialized);
        item.setVisible(!mIsSpeaking);
        item = menu.findItem(R.id.action_stop);
        assert item != null;
        item.setEnabled(mIsInitialized && mIsSpeaking);
        item = menu.findItem(R.id.action_pause);
        assert item != null;
        item.setVisible(mIsSpeaking);
        item.setEnabled(mIsInitialized);

        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (mTalkingTextView != null) {
            switch (item.getItemId()) {
                case R.id.action_start:
                    mTalkingTextView.startSpeaking();
                    mIsSpeaking = true;
                    break;

                case R.id.action_stop:
                    mTalkingTextView.stopSpeaking();
                    break;

                case R.id.action_pause:
                    mTalkingTextView.pauseSpeaking();
                    mIsSpeaking = false;
                    break;

                default:
                    return false;
            }

            invalidateOptionsMenu();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    @SuppressLint("NewApi")
    public void onInit(int status) {
        if (status != TextToSpeech.SUCCESS) {
            finish();
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1) {
            Set<String> features = mTextToSpeech.getFeatures(Locale.getDefault());
            if (features != null) {
                for (String string : features) {
                    Log.d(LOG, String.format("TTS Feature: %s", string));
                }
            }
        }

        mTextToSpeech.setLanguage(Locale.getDefault());
        mTalkingTextView = (TalkingTextView) findViewById(R.id.view);
        if (!mTalkingTextView.bindToTextToSpeech(mTextToSpeech)) {
            finish();
        }

        mIsInitialized = true;
        invalidateOptionsMenu();
    }
}
