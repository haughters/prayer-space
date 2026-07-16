import java.util.Base64;
public class TestB64 {
    public static void main(String[] args) {
        String body = "{\"deviceId\":\"91af252d-9128-45cf-90a3-e4410bd73556\",\"prayerText\":\"This is a test\",\"groupId\":null}";
        System.out.println(Base64.getEncoder().encodeToString(body.getBytes()));
    }
}
