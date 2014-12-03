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

package it.frob.talkingtextview;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Parcelable;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.BackgroundColorSpan;
import android.text.style.CharacterStyle;
import android.text.style.ForegroundColorSpan;
import android.util.AttributeSet;
import android.widget.TextView;

import java.util.HashMap;

public class TalkingTextView extends TextView implements Handler.Callback {

    public interface OnNewWordListener {
        void onNewWord(int start, int end, CharSequence word);
    }

    private final static int MESSAGE_ID = 1;
    private final static String UTTERANCE_ID_BASE = "it.frob.talkingtextview.TalkingTextViewUtterance";
    private final static String INSTANCE_STATE_KEY = "it.frob.talkingtextview.InstanceState";
    private final static String OFFSET_KEY = "it.frob.talkingtextview.Offset";

    private final HashMap<String, String> mSpeechParameters = new HashMap<>();
    private final Bundle mSpeechParametersBundle = new Bundle();
    private final Handler mHandler = new Handler(this);
    private CharacterStyle mBackgroundStyle;
    private CharacterStyle mForegroundStyle;
    private OnNewWordListener mListener;
    private int mDelay;

    private TextToSpeech mTextToSpeech;
    private int mOffset = 0;
    private boolean mIsSpeaking = false;

    public TalkingTextView(Context context) {
        super(context, null);

        if (isInEditMode()) {
            return;
        }

        initialise(context, null, 0);
    }

    public TalkingTextView(Context context, AttributeSet attrs) {
        super(context, attrs, R.style.TalkingTextView);

        if (isInEditMode()) {
            return;
        }

        initialise(context, attrs, R.style.TalkingTextView);
    }

    public TalkingTextView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        if (isInEditMode()) {
            return;
        }

        initialise(context, attrs, defStyle);
    }

    private void setParameter(String key, String value) {
        if (Build.VERSION.SDK_INT < 21) {
            mSpeechParameters.put(key, value);
        } else {
            mSpeechParametersBundle.putString(key, value);
        }
    }

    private void initialise(Context context, AttributeSet attributeSet, int defaultStyle) {
        if (Build.VERSION.SDK_INT < 21) {
            mSpeechParameters.put(TextToSpeech.Engine.KEY_PARAM_STREAM,
                    String.valueOf(AudioManager.STREAM_SYSTEM));
        } else {
            mSpeechParametersBundle.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM,
                    AudioManager.STREAM_SYSTEM);
        }

        if (attributeSet == null) {
            return;
        }

        TypedArray array = context.obtainStyledAttributes(attributeSet, R.styleable.TalkingTextView,
                defaultStyle, R.style.TalkingTextView);
        if (array != null) {
            for (int index = 0; index < array.getIndexCount(); index++) {
                int attribute = array.getIndex(index);

                if (attribute == R.styleable.TalkingTextView_spokenBackground) {
                    mBackgroundStyle = new BackgroundColorSpan(array.getColor(attribute, 0));
                } else if (attribute == R.styleable.TalkingTextView_spokenForeground) {
                    mForegroundStyle = new ForegroundColorSpan(array.getColor(attribute, 0));
                } else if (attribute == R.styleable.TalkingTextView_voicePan) {
                    setPan(array.getFloat(attribute, 0.0f));
                } else if (attribute == R.styleable.TalkingTextView_voiceVolume) {
                    setVolume(array.getFloat(attribute, 1.0f));
                } else if (attribute == R.styleable.TalkingTextView_delay) {
                    mDelay = array.getInt(attribute, 0);
                }
            }
            array.recycle();
        }
    }

    public void setOnNewWordListener(OnNewWordListener listener) {
        mListener = listener;
    }

    @SuppressWarnings("WeakerAccess")
    public void setPan(float pan) {
        setParameter(TextToSpeech.Engine.KEY_PARAM_PAN, String.valueOf(pan));
    }

    @SuppressWarnings("WeakerAccess")
    public void setVolume(float volume) {
        setParameter(TextToSpeech.Engine.KEY_PARAM_VOLUME, String.valueOf(volume));
    }

    public void setDelay(int milliseconds) {
        mDelay = milliseconds;
    }

    public void startSpeaking() {
        if (mTextToSpeech.isSpeaking()) {
            return;
        }

        if (TextUtils.isEmpty(getText())) {
            mIsSpeaking = false;
            return;
        }

        mIsSpeaking = true;
        nextWord();
    }

    public void stopSpeaking() {
        pauseSpeaking();
        mOffset = 0;
        setText(getCleanSpannable());
    }

    public void pauseSpeaking() {
        mIsSpeaking = false;
        mHandler.removeMessages(MESSAGE_ID);
        mTextToSpeech.stop();
    }

    private void nextWord() {
        final Message message = getMessageFromOffset(mOffset);
        if (message == null) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    stopSpeaking();
                }
            });
            return;
        }

        if (mListener != null) {
            CharSequence text = getText();
            assert text != null;
            mListener.onNewWord(message.arg1, message.arg2, text.subSequence(message.arg1,
                    message.arg2));
        }

        mOffset = message.arg2 + 1;
        mHandler.sendMessageDelayed(message, mDelay);
    }

    private Spannable getCleanSpannable() {
        CharSequence text = getText();

        assert text != null;
        Spannable spannable = new SpannableString(text);
        removeStyles(spannable, 0, text.length());
        return spannable;
    }

    void applyStyles(Spannable spannable, int start, int end) {
        spannable.setSpan(mBackgroundStyle, start, end, Spannable.SPAN_INCLUSIVE_INCLUSIVE);
        spannable.setSpan(mForegroundStyle, start, end, Spannable.SPAN_INCLUSIVE_INCLUSIVE);
    }

    @SuppressWarnings("WeakerAccess")
    protected void removeStyles(Spannable spannable,
                                @SuppressWarnings({"UnusedParameters", "SameParameterValue"}) int start,
                                @SuppressWarnings("UnusedParameters") int end) {
        spannable.removeSpan(mBackgroundStyle);
        spannable.removeSpan(mForegroundStyle);
    }

    private Message getMessageFromOffset(int currentOffset) {
        CharSequence charSequence = getText();
        assert charSequence != null;

        if (currentOffset >= charSequence.length()) {
            return null;
        }

        int start = getFirstNonWhitespace(currentOffset, charSequence);
        if (start == -1) {
            return null;
        }

        int end = getFirstWhitespace(start, charSequence);
        if (end == -1) {
            end = charSequence.length();
        }

        final Message message = new Message();
        message.arg1 = start;
        message.arg2 = end;
        message.what = MESSAGE_ID;

        return message;
    }

    private int getFirstNonWhitespace(int start, CharSequence sequence) {
        for (int offset = start; offset < sequence.length(); offset++) {
            if (!Character.isWhitespace(sequence.charAt(offset))) {
                return offset;
            }
        }

        return -1;
    }

    private int getFirstWhitespace(int start, CharSequence sequence) {
        for (int offset = start; offset < sequence.length(); offset++) {
            if (Character.isWhitespace(sequence.charAt(offset))) {
                return offset;
            }
        }

        return -1;
    }

    @SuppressWarnings("deprecated")
    @SuppressLint("NewApi")
    public boolean bindToTextToSpeech(TextToSpeech textToSpeech) {
        mTextToSpeech = textToSpeech;
        int result;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1) {
            result = mTextToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {

                @Override
                public void onStart(String utteranceId) {
                }

                @Override
                public void onDone(String utteranceId) {
                    if (mIsSpeaking) {
                        nextWord();
                    }
                }

                @Override
                public void onError(String utteranceId) {
                    stopSpeaking();
                }
            });
        } else {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2) {
                //noinspection deprecation
                result = mTextToSpeech.setOnUtteranceCompletedListener(
                        new TextToSpeech.OnUtteranceCompletedListener() {

                            @Override
                            public void onUtteranceCompleted(String utteranceId) {
                                if (mIsSpeaking) {
                                    nextWord();
                                }
                            }
                        }
                );
            } else {
                result = TextToSpeech.ERROR;
            }
        }

        if (result == TextToSpeech.ERROR) {
            mTextToSpeech = null;
        }

        return result == TextToSpeech.SUCCESS;
    }

    @Override
    @SuppressLint("NewApi")
    public boolean handleMessage(Message msg) {
        Spannable spannable = getCleanSpannable();
        applyStyles(spannable, msg.arg1, msg.arg2);
        setText(spannable);
        setParameter(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID,
                UTTERANCE_ID_BASE + String.valueOf(msg.arg1));
        if (Build.VERSION.SDK_INT < 21) {
            mTextToSpeech.speak(TextUtils.substring(getText(), msg.arg1, msg.arg2),
                    TextToSpeech.QUEUE_ADD, mSpeechParameters);
        } else {
            mTextToSpeech.speak(TextUtils.substring(getText(), msg.arg1, msg.arg2),
                    TextToSpeech.QUEUE_ADD, mSpeechParametersBundle,
                    TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID);
        }

        return true;
    }

    @Override
    public Parcelable onSaveInstanceState() {
        Bundle state = new Bundle();
        state.putParcelable(INSTANCE_STATE_KEY, super.onSaveInstanceState());
        state.putInt(OFFSET_KEY, mOffset);

        return state;
    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {
        if (state instanceof Bundle) {
            Bundle stateBundle = (Bundle) state;
            mOffset = stateBundle.getInt(OFFSET_KEY);
            super.onRestoreInstanceState(stateBundle.getParcelable(INSTANCE_STATE_KEY));
        } else {
            super.onRestoreInstanceState(state);
        }
    }

    public void updateText(CharSequence text) {
        stopSpeaking();
        setText(text);
    }
}
