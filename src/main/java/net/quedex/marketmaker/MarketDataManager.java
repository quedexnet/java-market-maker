package net.quedex.marketmaker;

import net.quedex.api.market.PriceQuantity;
import net.quedex.api.market.Quotes;
import net.quedex.api.market.QuotesListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkArgument;

public class MarketDataManager implements QuotesListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(MarketDataManager.class);
    private static final BigDecimal TWO = BigDecimal.valueOf(2);

    private final Map<Integer, Quotes> instrumentIdToQuotes = new HashMap<>();

    public BigDecimal getLastTradePrice(final int instrumentId) {
        final Quotes quotes = instrumentIdToQuotes.get(instrumentId);
        checkArgument(quotes != null, "No quotes for %s", instrumentId);
        return quotes.getLast();
    }

    public BigDecimal getMid(final int instrumentId) {
        final Quotes quotes = instrumentIdToQuotes.get(instrumentId);
        checkArgument(quotes != null, "No quotes for %s", instrumentId);

        final Optional<BigDecimal> bidPrice = quotes.getBid().orElse(new PriceQuantity(0)).getPrice();
        final Optional<BigDecimal> askPrice = quotes.getAsk().orElse(new PriceQuantity(0)).getPrice();

        if (bidPrice.isPresent() && askPrice.isPresent()) {
            return bidPrice.get().add(askPrice.get()).divide(TWO, 8, RoundingMode.HALF_EVEN);
        } else if (bidPrice.isPresent()) {
            return bidPrice.get();
        } else if (askPrice.isPresent()) {
            return askPrice.get();
        } else {
            return quotes.getLast(); // use last in case of empty OB
        }
    }

    @Override
    public void onQuotes(final Quotes quotes) {
        LOGGER.trace("{}", quotes);
        instrumentIdToQuotes.put(quotes.getInstrumentId(), quotes);
    }
}
