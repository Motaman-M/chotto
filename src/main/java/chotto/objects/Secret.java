package chotto.objects;

import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import supranational.blst.SecretKey;

public class Secret {

  public static Secret fromIkm(final byte[] ikm) {
    final SecretKey secretKey = new SecretKey();
    secretKey.keygen(ikm);
    return new Secret(secretKey);
  }

  public static Secret fromText(final String text) {
    final SecretKey secretKey = new SecretKey();
    final byte[] in = new byte[32];
    final byte[] textBytes = text.getBytes();
    System.arraycopy(textBytes, 0, in, 0, Math.min(textBytes.length, 32));
    secretKey.from_bendian(in);
    return new Secret(secretKey);
  }

  public static Secret fromHexString(final String hexString) {
    final SecretKey secretKey = new SecretKey();
    final Bytes32 hexStringBytes = Bytes32.fromHexString(hexString);
    secretKey.from_bendian(hexStringBytes.toArray());
    return new Secret(secretKey);
  }

  private final SecretKey secretKey;

  private Secret(final SecretKey secretKey) {
    this.secretKey = secretKey;
  }

  public SecretKey getKey() {
    return secretKey;
  }

  public Bytes toBytes() {
    return Bytes.wrap(secretKey.to_bendian());
  }

  public UInt256 toUInt256() {
    return UInt256.fromBytes(toBytes());
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final Secret secret = (Secret) o;
    return Objects.equals(toBytes(), secret.toBytes());
  }

  @Override
  public int hashCode() {
    return Objects.hash(toBytes());
  }
}
