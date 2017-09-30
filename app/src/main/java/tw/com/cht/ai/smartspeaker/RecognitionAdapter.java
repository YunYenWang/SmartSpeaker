package tw.com.cht.ai.smartspeaker;

import android.os.Bundle;
import android.speech.RecognitionListener;

/**
 * Created by rickwang on 2017/9/30.
 */

public class RecognitionAdapter implements RecognitionListener {

    @Override
    public void onReadyForSpeech(Bundle bundle) {
    }

    @Override
    public void onBeginningOfSpeech() {
    }

    @Override
    public void onRmsChanged(float v) {
    }

    @Override
    public void onBufferReceived(byte[] bytes) {
    }

    @Override
    public void onEndOfSpeech() {
    }

    @Override
    public void onError(int i) {
    }

    @Override
    public void onResults(Bundle bundle) {
    }

    @Override
    public void onPartialResults(Bundle bundle) {
    }

    @Override
    public void onEvent(int i, Bundle bundle) {
    }
}
