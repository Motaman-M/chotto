package chotto;

import chotto.auth.AuthCallback;
import chotto.auth.Provider;
import chotto.auth.SessionInfo;
import chotto.auth.SessionStore;
import chotto.cli.AsciiArtPrinter;
import chotto.cli.LoginInstructor;
import chotto.cli.PropertiesVersionProvider;
import chotto.contribution.ContributionVerification;
import chotto.contribution.Contributor;
import chotto.identity.IdentityRetriever;
import chotto.lifecycle.ApiLifecycle;
import chotto.lifecycle.ContributeTrier;
import chotto.objects.CeremonyStatus;
import chotto.sequencer.SequencerClient;
import chotto.serialization.ChottoObjectMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pivovarit.function.ThrowingRunnable;
import io.javalin.Javalin;
import io.javalin.http.HandlerType;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Visibility;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Spec;

@Command(
    name = "chotto",
    mixinStandardHelpOptions = true,
    versionProvider = PropertiesVersionProvider.class,
    header = {
      "  _____ _           _   _        ",
      " / ____| |         | | | |       ",
      "| |    | |__   ___ | |_| |_ ___  ",
      "| |    | '_ \\ / _ \\| __| __/ _ \\ ",
      "| |____| | | | (_) | |_| || (_) |",
      " \\_____|_| |_|\\___/ \\__|\\__\\___/ "
    },
    description = "Ethereum's Power of Tau client implementation written in Java")
public class Chotto implements Runnable {

  private static final Logger LOG = LoggerFactory.getLogger(Chotto.class);

  private static final String AUTH_CALLBACK_PATH = "/auth/callback";

  @Spec CommandSpec spec;

  @Option(
      names = {"--sequencer"},
      description = "The URL of the sequencer which would be used for the ceremony",
      required = true)
  private URI sequencer;

  @Option(
      names = {"--entropy-entry"},
      description =
          "A text which would be used as a seed to generate random secrets in the background. There will be several layers of randomness on top of this text, so there is no need to worry about its uniqueness or keeping it safe.",
      required = true)
  private String entropyEntry;

  @Option(
      names = {"--server-port"},
      description = "The port on which to start the local server",
      showDefaultValue = Visibility.ALWAYS)
  private int serverPort = 8080;

  @Option(
      names = {"--authentication"},
      description =
          "The authentication provider which will be used for logging in. Valid values: ${COMPLETION-CANDIDATES}",
      showDefaultValue = Visibility.ALWAYS)
  private Provider provider = Provider.ETHEREUM;

  private int contributionAttemptPeriod = 5;

  @Option(
      names = {"--contribution-attempt-period"},
      description = "How often (in seconds) to attempt contribution once authenticated.",
      defaultValue = "5",
      showDefaultValue = Visibility.ALWAYS)
  public void setContributionAttemptPeriod(final int value) {
    if (value < 1) {
      throw new ParameterException(
          spec.commandLine(),
          String.format(
              "Invalid value '%d' for option '--contribution-attempt-period': "
                  + "value should be bigger than 0.",
              value));
    }
    contributionAttemptPeriod = value;
  }

  @Option(
      names = {"--sign-contributions"},
      description = "Sign your contributions with your identity. Doing so is RECOMMENDED.",
      showDefaultValue = Visibility.ALWAYS)
  private boolean signContributions = true;

  @Option(
      names = {"--auth-callback-endpoint"},
      description =
          "The URL of this process which will be used as an authentication callback endpoint. Specify this option ONLY if you decide to login from a browser on a different computer. Make sure the URL is accessible from that browser.")
  private URI authCallbackEndpoint = null;

  @Option(
      names = {"--output-directory"},
      description = "The directory where the outputs of the ceremony will be stored",
      showDefaultValue = Visibility.ALWAYS)
  private Path outputDirectory =
      Paths.get(System.getProperty("user.home") + File.separator + "kzg-ceremony");

  @Override
  public void run() {
    try {
      runSafely();
    } catch (final Throwable ex) {
      LOG.error(
          "There was an error during the ceremony. You can restart Chotto to try to contribute again.",
          ex);
      System.exit(1);
    }
  }

  private void runSafely() {
    createOutputDirectoryIfNeeded();

    AsciiArtPrinter.printBanner();

    final SessionStore sessionStore = new SessionStore();
    final AuthCallback authCallback = new AuthCallback(sessionStore);

    final Javalin app = Javalin.create().start(serverPort);
    app.addHandler(HandlerType.GET, AUTH_CALLBACK_PATH, authCallback);

    final HttpClient httpClient = HttpClient.newBuilder().build();
    final ObjectMapper objectMapper = ChottoObjectMapper.getInstance();

    final ContributionVerification contributionVerification =
        new ContributionVerification(objectMapper);

    final SequencerClient sequencerClient =
        new SequencerClient(httpClient, sequencer, objectMapper, contributionVerification);

    final CeremonyStatus ceremonyStatus = sequencerClient.getCeremonyStatus();
    AsciiArtPrinter.printCeremonyStatus(ceremonyStatus);

    final Csprng csprng = new Csprng(entropyEntry);

    Runtime.getRuntime().addShutdownHook(new Thread(csprng::destroySecrets));

    final String redirectTo =
        Optional.ofNullable(authCallbackEndpoint)
                .map(URI::toString)
                .orElse("http://localhost:" + serverPort)
            + AUTH_CALLBACK_PATH;

    final String loginLink = sequencerClient.getLoginLink(provider, redirectTo);

    LoginInstructor.instructUserToLogin(loginLink, authCallbackEndpoint);

    while (sessionStore.getSessionInfo().isEmpty()) {
      LOG.info("Waiting for user login...");
      ThrowingRunnable.unchecked(() -> TimeUnit.SECONDS.sleep(5)).run();
    }

    final SessionInfo sessionInfo = sessionStore.getSessionInfo().get();

    final IdentityRetriever identityRetriever =
        IdentityRetriever.create(sessionInfo.getProvider(), httpClient, objectMapper);

    final String identity = identityRetriever.getIdentity(sessionInfo.getNickname());

    LOG.info("Your identity is {}", identity);

    final Contributor contributor = new Contributor(csprng, identity, signContributions);

    final ContributeTrier contributeTrier =
        new ContributeTrier(sequencerClient, contributionAttemptPeriod);

    final ApiLifecycle apiLifecycle =
        new ApiLifecycle(
            sessionInfo,
            contributeTrier,
            sequencerClient,
            contributor,
            objectMapper,
            outputDirectory);

    apiLifecycle.runLifecycle();

    AsciiArtPrinter.printThankYou();
    System.exit(0);
  }

  private void createOutputDirectoryIfNeeded() {
    try {
      Files.createDirectories(outputDirectory);
    } catch (final IOException ex) {
      throw new UncheckedIOException(ex);
    }
  }

  public static void main(String[] args) {
    System.setProperty("picocli.ansi", "false");
    new CommandLine(new Chotto()).setCaseInsensitiveEnumValuesAllowed(true).execute(args);
  }
}
