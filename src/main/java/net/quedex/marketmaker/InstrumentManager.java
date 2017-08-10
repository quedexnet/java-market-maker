package net.quedex.marketmaker;

import net.quedex.api.market.Instrument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class InstrumentManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(InstrumentManager.class);

    private final TimeProvider timeProvider;
    private final Map<Integer, Instrument> instruments;

    public InstrumentManager(final TimeProvider timeProvider, final Map<Integer, Instrument> instruments) {
        this.timeProvider = checkNotNull(timeProvider, "null timeProvider");
        this.instruments = checkNotNull(instruments, "null instruments");

        LOGGER.info("Initialised with instruments: {}", instruments);
    }

    public Instrument getInstrument(final int instrumentId) {
        checkArgument(instruments.containsKey(instrumentId), "instrumentId=%s not found", instrumentId);
        return instruments.get(instrumentId);
    }

    public List<Instrument> getTradedInstruments() {
        return instruments.values().stream()
            .filter(i -> i.isTraded(timeProvider.getCurrentTime()))
            .collect(Collectors.toList());
    }

    public List<Instrument> getTradedFutures() {
        return instruments.values().stream()
            .filter(Instrument::isFutures)
            .filter(i -> i.isTraded(timeProvider.getCurrentTime()))
            .collect(Collectors.toList());
    }

    public List<Instrument> getTradedOptions() {
        return instruments.values().stream()
            .filter(i -> !i.isFutures())
            .filter(i -> i.isTraded(timeProvider.getCurrentTime()))
            .collect(Collectors.toList());
    }

    public Instrument getFuturesAtExpiration(final long expirationDate) {
        for (final Instrument futures : getTradedFutures()) {
            if (futures.getExpirationDate() == expirationDate) {
                return futures;
            }
        }
        throw new IllegalArgumentException("Futures with expiration date: " + expirationDate + " not found");
    }
}
