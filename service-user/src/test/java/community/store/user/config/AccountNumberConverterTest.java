package community.store.user.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AccountNumberConverterTest {

    private final AccountNumberConverter converter = converter("GI78pck3q9utqwEFugNtpndt05HZEKvIKR6D499L3Xk=");

    @Test
    void roundTripsAndNeverStoresPlaintext() {
        String stored = converter.convertToDatabaseColumn("62000012345");

        assertThat(stored).doesNotContain("62000012345");
        assertThat(converter.convertToEntityAttribute(stored)).isEqualTo("62000012345");
    }

    @Test
    void usesRandomIvPerValue() {
        assertThat(converter.convertToDatabaseColumn("123456")).isNotEqualTo(converter.convertToDatabaseColumn("123456"));
    }

    @Test
    void decryptsSeededValue() {
        assertThat(converter.convertToEntityAttribute("mh/e4UsNPJjiL5c/w+Jk0KS4KVJUKEJr1DfIqyXoxt0f+HktEJWo")).isEqualTo("62000012345");
    }

    private static AccountNumberConverter converter(String key) {
        UserProperties properties = new UserProperties();
        properties.setEncryptionKey(key);
        return new AccountNumberConverter(properties);
    }
}
