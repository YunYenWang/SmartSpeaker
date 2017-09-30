package tw.com.cht.ai.smartspeaker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by rickwang on 2017/9/30.
 */

public class WakeupBroadcastReceiver extends BroadcastReceiver {
    static final Logger LOG = LoggerFactory.getLogger(WakeupBroadcastReceiver.class);

    public WakeupBroadcastReceiver() {
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        LOG.info("Receiver intent - {}", intent);

        Intent i = new Intent(context, MainService.class);
        context.startService(i);
    }
}
