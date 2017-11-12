package tw.com.cht.ai.smartspeaker;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.support.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
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

    static final long MINIMUM_INTENT_INTERVAL = 5000L;

    static final String LISTEN = "listen";
    static final String DONE = "done";

    SpeechRecognizer recognizer;
    SpeakerClient client;

    BroadcastReceiver receiver;

    Handler handler;

    public MainService() {
    }

    SpeechRecognizer newSpeechRecognizer() {
        SpeechRecognizer recognizer = SpeechRecognizer.createSpeechRecognizer(this);
        recognizer.setRecognitionListener(new RecognitionAdapter() {

            @Override
            public void onResults(Bundle bundle) {
                ArrayList<String> data = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (!data.isEmpty()) {
                    String first = data.get(0); // TODO - just use the first result
                    onAsk(first); // send the question to CHT server
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
                doCommand(b); // reactive the result from CHT server (just speak the message)
            }
        });
    }

    // ======

    // initialize the service
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        LOG.info("Service is started. {}", startId);

        if (receiver == null) {
            handler = new Handler(getApplicationContext().getMainLooper());

            recognizer = newSpeechRecognizer();

            String url = getString(R.string.url);
            String vendorID = getString(R.string.vendorID);
            String deviceNO = getString(R.string.deviceNO);
            client = newSpeakerClient(url, vendorID, deviceNO);

            receiver = new BroadcastReceiver() {
                long last;

                @Override
                public void onReceive(Context context, Intent intent) {
                    LOG.info("Receive - {}", intent);

                    long now = System.currentTimeMillis();
                    if ((now - last) > MINIMUM_INTENT_INTERVAL) { // de-duplicate the intent
                        last = now;

                        hello();

                    } else {
                        LOG.warn("Skip the duplicated intent");
                    }
                }
            };

            String action = getString(R.string.trigger_intent_action); // just receive the specified intent
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

        super.onDestroy();
    }

    // ======

    void standby() {
        String action = getString(R.string.standby_intent_action);
        Intent intent = new Intent(action);
        sendBroadcast(intent);
    }

    void speak(String message, final String utteranceId) {
        HashMap<String, String> params = new HashMap<>();
        params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId);

        try {
            Uri uri = Uri.parse(String.format(getString(R.string.cht_tts_url_format), URLEncoder.encode(message)));
            MediaPlayer player = MediaPlayer.create(this, uri); // play the mp3 audio stream
            player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mediaPlayer) {
                    if (LISTEN.equals(utteranceId)) {
                        doListen();

                    } else {
//                        standby(); // useless now
                    }
                }
            });
            player.start();

        } catch (Exception e) {
            LOG.error("Failed to play voice", e);
        }
    }

    // after the trigger word, I'll response the 'hello'
    void hello() {
        speak(getString(R.string.hello), LISTEN);
    }

    // wait for user's command
    void doListen() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 5000);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 5000);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 5000);

        recognizer.startListening(intent);
    }

    // I got command words from user, send to CHT server
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

    // I got command from CHT server. Do some action now.
    void doCommand(Bullet b) {
        if (b instanceof PushMessage) {
            PushMessage pm = (PushMessage) b;
            for (PushMessage.CommandType command : pm.Commands) {
                if (command.Content != null) {
                    speak(command.Content, (command.Content.endsWith("?") || command.Content.endsWith("？"))? LISTEN : DONE); // user might say the next command
                }
            }
        }
    }
}
