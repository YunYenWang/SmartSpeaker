package tw.com.cht.ai.smartspeaker;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import javax.net.ssl.SSLContext;

import tw.com.cht.ai.smartspeaker.protocol.Bullet;
import tw.com.cht.ai.smartspeaker.protocol.PushMessage;
import tw.com.cht.ai.smartspeaker.protocol.RegisterRsp;
import tw.com.cht.iot.util.JsonUtils;

/**
 * Created by rickwang on 2017/9/4.
 */

public class SpeakerClient {
    static final Logger LOG = LoggerFactory.getLogger(SpeakerClient.class);

    static final long RETRY_INTERVAL = 3000L;
    static final int CONNECTION_TIMEOUT = 5;
    static final int KEEP_ALIVE_INTERVAL = 30;
    static final int QOS = 0;

    static final String REQ_TOPIC_FORMAT = "ai/speaker/%s/%s/req";
    static final String RSP_TOPIC_FORMAT = "ai/speaker/%s/%s/rsp";

    static final String UTF8 = "UTF-8";

    static final DateFormat DF = new SimpleDateFormat("yyyy-MM-DD'T'HH:mm:ss.SSS'Z'");
    static {
        DF.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    final String uri;
    final String vendorId;
    final String deviceId;

    final String reqTopic;
    final String rspTopic;

    final Listener listener;

    BlockingQueue<Bullet> requests = new LinkedBlockingQueue<>();
    Thread thread;
    int requestId = 1;

    public SpeakerClient(String uri, String vendorId, String deviceId, Listener listener) {
        this.uri = uri;
        this.vendorId = vendorId;
        this.deviceId = deviceId;

        reqTopic = String.format(REQ_TOPIC_FORMAT, vendorId, deviceId);
        rspTopic = String.format(RSP_TOPIC_FORMAT, vendorId, deviceId);

        this.listener = listener;

        thread = new Thread(new Runnable() {
            @Override
            public void run() {
                doConnect();
            }
        });
        thread.start();
    }

    public void close() {
        Thread th = thread;
        thread = null;
        th.interrupt();
    }

    void doConnect() {
        MemoryPersistence persistence = new MemoryPersistence();

        while (thread != null) {
            try {
                Thread.sleep(RETRY_INTERVAL);

                MqttClient client = new MqttClient(uri, "", persistence);
                try {
                    // prepare the callback for subscribing
                    client.setCallback(new MqttCallback() {

                        @Override
                        public void messageArrived(String topic, MqttMessage message) throws Exception {
                            String json = new String(message.getPayload(), UTF8);

                            LOG.info("Message from {} - {}", topic, json);

                            try {
                                String action = JsonUtils.getField(json, "Action");
                                if ("RegisterRsp".equals(action)) {
                                    RegisterRsp b = JsonUtils.fromJson(json, RegisterRsp.class);
                                    handle(b);

                                } else if ("PushMessage".equals(action)) {
                                    PushMessage b = JsonUtils.fromJson(json, PushMessage.class);
                                    handle(b);
                                }

                            } catch (Exception e) {
                                LOG.error("Failed to handle the message", e);
                            }
                        }

                        @Override
                        public void deliveryComplete(IMqttDeliveryToken token) {
                        }

                        @Override
                        public void connectionLost(Throwable cause) {
                        }
                    });

                    MqttConnectOptions opts = new MqttConnectOptions();
                    opts.setUserName(vendorId);
                    opts.setPassword(deviceId.toCharArray());
                    opts.setConnectionTimeout(CONNECTION_TIMEOUT);
                    opts.setKeepAliveInterval(KEEP_ALIVE_INTERVAL);
                    opts.setCleanSession(true);

                    opts.setSocketFactory(SSLContext.getDefault().getSocketFactory());

                    client.connect(opts);

                    // subscribe the topic
                    client.subscribe(rspTopic);

                    for (;;) {
                        Bullet req = requests.take();
                        req.Time = DF.format(new Date());
                        req.RequestID = String.valueOf(requestId++);

                        String json = JsonUtils.toJson(req);

                        LOG.info(json);

                        // publish the message
                        MqttMessage msg = new MqttMessage();
                        msg.setQos(QOS);
                        msg.setRetained(false);
                        msg.setPayload(json.getBytes(UTF8));

                        // publish the message and wait for reply
                        client.publish(reqTopic, msg);
                    }

                } finally {
                    client.disconnect();
                }

            } catch (Exception e) {
                LOG.error("Failed to connect to the MQTT server", e);
            }
        }
    }

    public void send(Bullet b) throws InterruptedException {
        requests.put(b);
    }

    void handle(Bullet b) {
        listener.onBullet(b);
    }

    public static interface Listener {
        void onBullet(Bullet b);
    }
}
