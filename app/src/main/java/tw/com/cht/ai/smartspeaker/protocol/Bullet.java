package tw.com.cht.ai.smartspeaker.protocol;

import java.util.Map;

public class Bullet {
	public String Action;
	public String Time;
	public String RequestID;
	
	public Map<String, String> Extra;

	public Bullet() {
		Action = getClass().getSimpleName();
	}
}
