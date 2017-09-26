package tw.com.cht.ai.smartspeaker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

import tw.com.cht.ai.smartspeaker.protocol.Bullet;
import tw.com.cht.ai.smartspeaker.protocol.InvokeReq;
import tw.com.cht.ai.smartspeaker.protocol.PushMessage;
import tw.com.cht.iot.util.JsonUtils;

public class MainActivity extends AppCompatActivity {
    static final Logger LOG = LoggerFactory.getLogger(MainActivity.class);

    static final String LISTEN = "listen";
    static final String DONE = "done";

    TextToSpeech tts;
    SpeechRecognizer recognizer;
    SpeakerClient client;

    BroadcastReceiver receiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tts = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            public void onInit(int i) {
                if (i == TextToSpeech.SUCCESS) {
                    if (tts.isLanguageAvailable(Locale.TAIWAN) == TextToSpeech.LANG_COUNTRY_AVAILABLE) {
                        tts.setLanguage(Locale.TAIWAN);

                    } else { // otherwise, Locale is not yet supported
                        LOG.error("Taiwan language is not supported!");

                    }
                }
            }
        });

        tts.setOnUtteranceCompletedListener(new TextToSpeech.OnUtteranceCompletedListener() {
            public void onUtteranceCompleted(String s) {
                if (LISTEN.equals(s)) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            doListen();
                        }
                    });

                } else if (DONE.equals(s)) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
//                            standby();
                        }
                    });
                }
            }
        });

        recognizer = SpeechRecognizer.createSpeechRecognizer(this);
        recognizer.setRecognitionListener(new RecognitionListener() {

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
                onAsk("");
            }

            @Override
            public void onResults(Bundle bundle) {
                ArrayList<String> data = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (!data.isEmpty()) {
                    String first = data.get(0);
                    onAsk(first);
                }
            }

            @Override
            public void onPartialResults(Bundle bundle) {
            }

            @Override
            public void onEvent(int i, Bundle bundle) {
            }
        });

        receiver = new WakeupVoiceReceiver();
        registerReceiver(receiver, new IntentFilter("com.js.wakeup"));

        try {
            String url = getString(R.string.url);
            String vendorID = getString(R.string.vendorID);
            String deviceNO = getString(R.string.deviceNO);

            client = new SpeakerClient(url, vendorID, deviceNO, new SpeakerClient.Listener() {
                @Override
                public void onBullet(Bullet b) {
                    doCommand(b);
                }
            });

        } catch (Exception e) {
            LOG.error("Failed to create the speaker client", e);
        }
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(receiver);

        client.close();

        super.onDestroy();
    }

    // ======

    void standby() {
        Intent intent = new Intent("com.js.standby");
        sendBroadcast(intent);
    }

    void speak(String message, String utteranceId) {
        HashMap<String, String> params = new HashMap<>();
        params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId);
        tts.speak(message, TextToSpeech.QUEUE_FLUSH, params);
    }

    void hello() {
        speak(getString(R.string.hello), LISTEN);
    }

    void doListen() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 5000);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 5000);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 5000);

        recognizer.startListening(intent);
    }

    void onAsk(String text) {
        if (text.length() > 0) {
            LOG.info("Ask {}", text);

            InvokeReq req = new InvokeReq();
            req.Text = text;

            try {
                client.send(req);

            } catch (Exception e) {
                LOG.error("Failed to send the command", e);
            }
        }
    }

    void doCommand(Bullet b) {
        LOG.info("Command: \n{}", JsonUtils.toPrettyPrintJson(b));

        if (b instanceof PushMessage) {
            PushMessage pm = (PushMessage) b;
            for (PushMessage.CommandType command : pm.Commands) {
                if (command.Content != null) {
                    speak(command.Content, DONE);
                }
            }
        }
    }

    // ======

    class WakeupVoiceReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            LOG.info("Wakeup - {}", intent);

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    hello();
                }
            });
        }
    }
}
