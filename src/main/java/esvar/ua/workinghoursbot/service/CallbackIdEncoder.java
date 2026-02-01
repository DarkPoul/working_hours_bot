package esvar.ua.workinghoursbot.service;

import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.UUID;

public final class CallbackIdEncoder {

    private CallbackIdEncoder() {
    }

    public static String encode(UUID id) {
        ByteBuffer buffer = ByteBuffer.allocate(16);
        buffer.putLong(id.getMostSignificantBits());
        buffer.putLong(id.getLeastSignificantBits());
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer.array());
    }

    public static UUID decode(String encoded) {
        byte[] bytes = Base64.getUrlDecoder().decode(encoded);
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        return new UUID(buffer.getLong(), buffer.getLong());
    }
}
