package net.quedex.marketmaker;

import com.google.common.collect.Lists;
import net.quedex.api.common.CommunicationException;
import net.quedex.api.market.Instrument;
import net.quedex.api.market.MarketStream;
import net.quedex.api.user.AccountState;
import net.quedex.api.user.CancelAllOrdersSpec;
import net.quedex.api.user.OrderSpec;
import net.quedex.api.user.UserStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import static com.google.common.base.Preconditions.checkNotNull;

public class MarketMakerRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(MarketMakerRunner.class);

    private final MarketStream marketStream;
    private final UserStream userStream;
    private final MarketMakerConfiguration marketMakerConfiguration;
    private final int sleepTimeSeconds;

    private volatile boolean running = false;
    private volatile Thread runningThread;

    public MarketMakerRunner(final MarketStream marketStream,
                             final UserStream userStream,
                             final MarketMakerConfiguration mmConfig) {
        this.marketStream = checkNotNull(marketStream, "null marketStream");
        this.userStream = checkNotNull(userStream, "null userStream");
        this.marketMakerConfiguration = checkNotNull(mmConfig, "null marketMakerConfiguration");
        this.sleepTimeSeconds = mmConfig.getTimeSleepSeconds();
    }

    public void runLoop() {
        runningThread = Thread.currentThread();

        marketStream.registerStreamFailureListener(this::onError);
        userStream.registerStreamFailureListener(this::onError);

        try {
            marketStream.start();
            userStream.start();
        } catch (final CommunicationException e) {
            LOGGER.error("Error starting streams", e);
            return;
        }

        final CompletableFuture<Map<Integer, Instrument>> instrumentsFuture = new CompletableFuture<>();

        marketStream.registerInstrumentsListener(instrumentsFuture::complete);

        MarketMaker marketMaker = null;

        try {
            LOGGER.info("Initialising");

            marketMaker = new MarketMaker(
                new RealTimeProvider(),
                marketMakerConfiguration,
                instrumentsFuture.get(),
                this::onError
            );

            final CompletableFuture<AccountState> initialAccountStateFuture = new CompletableFuture<>();

            marketStream.registerQuotesListener(marketMaker).subscribe(instrumentsFuture.get().keySet());

            userStream.registerOpenPositionListener(marketMaker);
            userStream.registerOrderListener(marketMaker);
            userStream.registerAccountStateListener(initialAccountStateFuture::complete);
            userStream.subscribeListeners();

            initialAccountStateFuture.get(); // await initial state
            userStream.registerAccountStateListener(null); // not used anymore

            LOGGER.info("Running");
            running = true;

            while (running) {
                final Future<List<OrderSpec>> orderSpecs = marketMaker.recalculate();
                send(orderSpecs.get());
                Thread.sleep(sleepTimeSeconds * 1000);
            }
        } catch (final InterruptedException e) {
            // ignore
        } catch (final ExecutionException | RuntimeException e) {
            LOGGER.error("Terminal error", e);
        } finally {
            LOGGER.info("Stopping");
            try {
                LOGGER.info("Cancelling all pending orders");
                if (marketMaker != null) {
                    try {
                        send(Collections.singletonList(CancelAllOrdersSpec.INSTANCE));
                        Thread.sleep(10_000);
                    } catch (final InterruptedException e) {
                        // ignore
                    }
                }
            } finally {
                if (marketMaker != null) {
                    marketMaker.stop();
                }
                try {
                    userStream.stop();
                    marketStream.stop();
                } catch (final CommunicationException e) {
                    LOGGER.error("Error stopping streams", e);
                }
                LOGGER.info("Stopped");
            }
        }
    }

    public void stop() {
        LOGGER.info("Stopped externally");
        running = false;
        runningThread.interrupt();
    }

    private void onError(final Exception e) {
        LOGGER.error("Async terminal error", e);
        stop();
    }

    private void send(final List<OrderSpec> orderSpecs) {
        LOGGER.debug("send({})", orderSpecs);
        if (orderSpecs.isEmpty()) {
            return;
        }
        for (final List<OrderSpec> batch : Lists.partition(orderSpecs, marketMakerConfiguration.getMaxBatchSize())) {
            userStream.batch(batch);
        }
    }
}
