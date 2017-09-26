package tw.com.cht.ai.smartspeaker.protocol;

public class PushMessage extends Bullet {
	public CommandType[] Commands;
	
	public static class CommandType {
		public String Type;
		public String SubType;
		public String Content;
	}
}
