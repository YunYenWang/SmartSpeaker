package tw.com.cht.ai.smartspeaker;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.support.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

import tw.com.cht.ai.smartspeaker.protocol.Bullet;
import tw.com.cht.ai.smartspeaker.protocol.InvokeReq;
import tw.com.cht.ai.smartspeaker.protocol.PushMessage;
import tw.com.cht.iot.util.JsonUtils;

/**
 * Created by rickwang on 2017/9/30.
 */

public class MainService extends Service {
    static final Logger LOG = LoggerFactory.getLogger(MainService.class);

    static final String LISTEN = "listen";
    static final String DONE = "done";

    TextToSpeech tts;
    SpeechRecognizer recognizer;
    SpeakerClient client;

    BroadcastReceiver receiver;

    Handler handler;

    public MainService() {
    }

    TextToSpeech newTextToSpeech() {
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
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            doListen();
                        }
                    });

                } else if (DONE.equals(s)) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
//                                standby();
                        }
                    });
                }
            }
        });

        return tts;
    }

    SpeechRecognizer newSpeechRecognizer() {
        SpeechRecognizer recognizer = SpeechRecognizer.createSpeechRecognizer(this);
        recognizer.setRecognitionListener(new RecognitionAdapter() {

            @Override
            public void onResults(Bundle bundle) {
                ArrayList<String> data = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (!data.isEmpty()) {
                    String first = data.get(0);
                    onAsk(first);
                }
            }

            @Override
            public void onError(int i) {
                // ask user to try again
            }
        });

        return recognizer;
    }

    SpeakerClient newSpeakerClient(String url, String vendorID, String deviceNO) {
        return new SpeakerClient(url, vendorID, deviceNO, new SpeakerClient.Listener() {
            @Override
            public void onBullet(Bullet b) {
                doCommand(b);
            }
        });
    }

    // ======

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        LOG.info("Service is started. {}", startId);

        if (receiver == null) {
            handler = new Handler(getApplicationContext().getMainLooper());

            tts = newTextToSpeech();

            recognizer = newSpeechRecognizer();

            String url = getString(R.string.url);
            String vendorID = getString(R.string.vendorID);
            String deviceNO = getString(R.string.deviceNO);
            client = newSpeakerClient(url, vendorID, deviceNO);

            receiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    LOG.info("Receive - {}", intent);

                    hello();
                }
            };

            String action = getString(R.string.trigger_intent_action);
            registerReceiver(receiver, new IntentFilter(action));
        }

        return super.onStartCommand(intent, flags, startId);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(receiver);

        client.close();

        recognizer.destroy();
        tts.shutdown();

        super.onDestroy();
    }

    // ======

    void standby() {
        String action = getString(R.string.standby_intent_action);
        Intent intent = new Intent(action);
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
        if (!text.isEmpty()) {
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
        if (b instanceof PushMessage) {
            PushMessage pm = (PushMessage) b;
            for (PushMessage.CommandType command : pm.Commands) {
                if (command.Content != null) {
                    speak(command.Content, DONE);
                }
            }
        }
    }
}
