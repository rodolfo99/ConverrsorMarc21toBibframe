package mx.ucol.marc2bf.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.Locale;

/** Creates deterministic, URI-safe identifiers. */
public final class UriMinter {
    private final String baseUri;

    public UriMinter(String baseUri) {
        if (baseUri == null || baseUri.isBlank()) {
            throw new IllegalArgumentException("La URI base no puede estar vacía.");
        }
        this.baseUri = baseUri.endsWith("/") ? baseUri : baseUri + "/";
    }

    public String uri(String type, String key) {
        return baseUri + type + "/" + token(key);
    }

    public String baseUri() {
        return baseUri;
    }

    public static String token(String value) {
        String source = value == null ? "sin-identificador" : value.trim();
        String slug = Normalizer.normalize(source, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        if (!slug.isBlank() && slug.length() <= 48) {
            return slug;
        }
        return sha256(source).substring(0, 32);
    }

    private static String sha256(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                result.append(String.format("%02x", b));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no está disponible.", e);
        }
    }
}
