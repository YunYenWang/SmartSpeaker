package tw.com.cht.ai.smartspeaker.protocol;

public class RegisterRsp extends Bullet {
	public String ResultCode;
	public String ResultMessage;
	public VersionCheckResultType VersionCheckResult;
	
	public static class VersionCheckResultType {
		public String NewVersion;
		public String VersionUrl;
	}
}
