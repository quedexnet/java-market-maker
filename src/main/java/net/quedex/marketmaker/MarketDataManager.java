package net.quedex.marketmaker;

import net.quedex.api.market.Quotes;
import net.quedex.api.market.QuotesListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

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

        final BigDecimal bidPrice = quotes.getBid() != null ? quotes.getBid().getPrice() : null;
        final BigDecimal askPrice = quotes.getAsk() != null ? quotes.getAsk().getPrice() : null;

        if (bidPrice != null && askPrice != null) {
            return bidPrice.add(askPrice).divide(TWO, 8, RoundingMode.HALF_EVEN);
        } else if (bidPrice != null) {
            return bidPrice;
        } else if (askPrice != null) {
            return askPrice;
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
