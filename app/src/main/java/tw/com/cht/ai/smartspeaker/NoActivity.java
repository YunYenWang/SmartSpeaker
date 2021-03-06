package tw.com.cht.ai.smartspeaker;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NoActivity extends AppCompatActivity {
    static final Logger LOG = LoggerFactory.getLogger(NoActivity.class);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Intent i = new Intent(this, MainService.class);
        startService(i);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
