package net.quedex.marketmaker;

import com.google.common.io.Resources;
import net.quedex.api.common.Config;
import net.quedex.api.market.MarketStream;
import net.quedex.api.market.WebsocketMarketStream;
import net.quedex.api.user.UserStream;
import net.quedex.api.user.WebsocketUserStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;

import static com.google.common.base.Preconditions.checkState;

public class Main {
    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

    private Main() {
        throw new AssertionError();
    }

    public static void main(final String... args) throws Exception {
        try {
            if (args.length != 0 && args.length != 2) {
                printUsageAndExit();
            }

            final InputStream qdxConfigIS;
            final String mmConfigPath;

            if (args.length > 0) {
                qdxConfigIS = Files.newInputStream(Paths.get(args[0]));
                mmConfigPath = args[1];
            } else {
                qdxConfigIS = Resources.getResource("quedex-config.properties").openStream();
                mmConfigPath = Resources.getResource("market-maker.properties").toString();
            }

            final char[] keyPassword = readPassphrase();

            final Config qdxConfig = Config.fromInputStream(qdxConfigIS, keyPassword);
            Arrays.fill(keyPassword, (char) 2);

            final MarketStream marketStream = new WebsocketMarketStream(qdxConfig);
            final UserStream userStream = new WebsocketUserStream(qdxConfig);

            final MarketMakerRunner mm = new MarketMakerRunner(
                marketStream,
                userStream,
                MarketMakerConfiguration.fromPropertiesFile(mmConfigPath)
            );

            Runtime.getRuntime().addShutdownHook(new Thread() {
                @Override
                public void run() {
                    mm.stop();
                    try {
                        Thread.sleep(10_000);
                    } catch (final InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            });

            mm.runLoop();
        } catch (final RuntimeException e) {
            LOGGER.error("Uncaught exception", e);
        }
    }

    private static char[] readPassphrase() throws IOException {
        System.out.println("Private key passphrase (will be echoed):");

        final char[] input = new char[100];
        final int read = new InputStreamReader(System.in, StandardCharsets.US_ASCII).read(input);
        checkState(read != 100, "Input too long");

        final char[] keyPassword = Arrays.copyOfRange(input, 0, read - 1); // -1 because we don't want \n
        Arrays.fill(input, (char) 5);

        return keyPassword;
    }

    private static void printUsageAndExit() {
        System.out.println(
            "Usage: java -jar <jar name> <Quedex properties filename> <market maker properties file name>");
        System.exit(1);
    }
}
