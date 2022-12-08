package chotto.template;

import static org.assertj.core.api.Assertions.assertThat;

import chotto.Constants;
import chotto.TestUtil;
import chotto.objects.BatchTranscript;
import chotto.objects.G2Point;
import chotto.objects.SubContributionContext;
import java.util.List;
import java.util.Optional;
import org.json.JSONException;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;

class TemplateResolverTest {

  private final TemplateResolver templateEngine = new TemplateResolver();

  @Test
  public void createsTypedDataFromBatchTranscriptAndSubContributionContexts() throws JSONException {
    final BatchTranscript batchTranscript = TestUtil.getBatchTranscript("initialTranscript.json");

    final List<SubContributionContext> subContributionContexts =
        List.of(
            createSubContributionContext(
                "0xb1dfd3c632734a9cfc8ae46d9f517b74d4fbb0e814360ce01e791dd78809db090cb16e26b5f02fd82b3fad41e0da74320ab4e5f6be8584e8f34f39f13c8b9c6fe04078dfdd2f2a6d298554edb837cca5a5619fc854f68a337737e01193cc1224"),
            createSubContributionContext(
                "0xaf4fdc29c543cfb98ab0afbe65edd686852c2114020eb4cd16e51c5039463d6635b0d7deba732042eb1b388b99e95ca604a1dad58698e02d538c64a52e7538fcb09af042b20ab07956bc8e5be4190f1d13d44f773956a483e945542e97fe16ae"),
            createSubContributionContext(
                "0x962fdf0563533ef71d1568260e833c111bf1cba06c4228a97e2e09535a65c9372b22ba28ce7b1285f3d0af54cf8e22dc04beac2bdf7e7a56cba1558570c08601e69e0c254de981b713b87c1bfe8f9d01ef8585b2fb80d6fe7f131c31b6c79b02"),
            createSubContributionContext(
                "0x98f49582d9a5f2184b7abd2498649d22a141da14ad4054a9c4d1aecfc9e86c882c3cbed899042cd60f76f849bc3bd12a0dc6f04d9d8f5f7e393e88b7493c933975d4c2aea72271132637135593aa47a21f2b93b89f60cbd64d666ba5162ab2dd"));

    final String typedData =
        templateEngine.createTypedData(batchTranscript, subContributionContexts);

    JSONAssert.assertEquals(
        TestUtil.readResource("template/expectedTypedData.json"), typedData, true);
  }

  @Test
  public void createsSignContributionHtml() {
    final String html =
        templateEngine.createSignContributionHtml(
            "0x33b187514f5Ea150a007651bEBc82eaaBF4da5ad", "{}", Constants.ECDSA_SIGN_CALLBACK_PATH);

    assertThat(html).contains("const typedData = \"{}\"");
    assertThat(html).contains("const ethAddress = \"0x33b187514f5Ea150a007651bEBc82eaaBF4da5ad\"");
    assertThat(html).contains("const callbackPath = \"/sign/ecdsa/callback\"");
  }

  private SubContributionContext createSubContributionContext(final String potPubkeyAsHex) {
    return new SubContributionContext(
        null, Optional.empty(), G2Point.fromHexString(potPubkeyAsHex));
  }
}
